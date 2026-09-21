package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.SortedMap;

import rs.ac.bg.etf.model.os.FIFOEvictionPolicy;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.page.PageInstructionFetchStep;
import rs.ac.bg.etf.model.table.PageTable;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageSimulationContext extends SimulationContext
{
    private ArrayList<PageTable> pageTables = new ArrayList<>();
    private ArrayList<Long> pageTableStartAddresses = new ArrayList<>();
    private long maxPages;
    private long pageTableSize;
    private PageOSMemoryManager memoryManager;
    private long currentDescriptorAddress = 0;

    private PageTableDescriptor currentDescriptor = null;
    private long currentFrame = -1;
    // Set only by PageLoadIntoMemoryStep, at the disk address of the page it is loading.
    // Deliberately separate from currentDescriptor: that field is shared scratch state that
    // an unrelated (later) instruction's PageFaultStep.undo() can null out during a
    // multi-instruction rewind, which would otherwise blank this display value even while
    // this instruction's load step is still the active one.
    private long currentLoadDiskAddress = -1;

    // Identity of whichever frame/page PageEvictionStep most recently evicted, and whether that
    // also invalidated a TLB entry -- set in its execute(), read by consumers (e.g. the MMU/TLB
    // tabs' notification side notes).
    private int pageEvictionVictimUser = -1;
    private long pageEvictionVictimPage = -1;
    private long pageEvictionVictimFrame = -1;
    private boolean pageEvictionInvalidatedTlbEntry = false;
    private long pageEvictionInvalidatedTlbTag = -1;
    private long pageEvictionInvalidatedTlbBlock = -1;
    private boolean pageEvictionInvalidatedTlbWasDirty = false;
    private int pageEvictionInvalidatedTlbIndex = -1;

    // Identity of whichever evicted entry's descriptor PageTLBUpdateStep's dirty write-back most
    // recently targeted, set in its execute(), read by consumers (e.g. the MMU tab's notification
    // side note).
    private int tlbWritebackUser = -1;
    private long tlbWritebackPage = -1;

    public PageSimulationContext(SimulationConfig config)
    {
        super(config);
    }

    @Override
    public void init()
    {
        super.init();

        maxPages = 1l << config.getPageBits(); 

        memoryManager = new PageOSMemoryManager(new FIFOEvictionPolicy(), numberOfUsers, config.getPhysicalAddressBits(), config.getWordBits());
        pageTableSize = getPageTableDescriptorSize() * maxPages;
        long pagesPerPageTable = Math.ceilDiv(pageTableSize, getPageSize());

        System.out.println(pagesPerPageTable);

        HashMap<PageTableDescriptor, Integer> frameInit = new HashMap<>();

        for (int i = 0; i < numberOfUsers; i++)
        {
            PageTable table = new PageTable(diskAddressGenerator, i, maxPages);
            table.init(config.getPageTables().get(i));
            pageTables.add(table);

            ArrayList<PageTableDescriptor> validEntries = table.getValidEntries();

            for (PageTableDescriptor entry: validEntries)
                frameInit.put(entry, i);
        }

        memoryManager.init(frameInit);

        for (int i = 0; i < numberOfUsers; i++)
        {
            long startAddress = memoryManager.allocateAndLock(pagesPerPageTable) << config.getWordBits();
            pageTableStartAddresses.add(startAddress);
        }

        initDisk();
    }

    @Override
    protected void initDisk() 
    {
        HashMap<String, SortedMap<Long, Long>> diskInitConfig = config.getDiskInitContent();

        HashMap<Long, SortedMap<Long, Long>> diskInit = new HashMap<>();

        for (String key: diskInitConfig.keySet())
        {
            String[] values = key.split(":");
            int user = Integer.parseInt(values[0]);
            long page = Long.parseLong(values[1]);

            diskInit.put(diskAddressGenerator.getDiskAddress((user << config.getPageBits()) + page), diskInitConfig.get(key));
        }

        disk.init(diskInit);
    }

    @Override
    public SimulationStep<? extends SimulationContext> getFirstStep() {
        return new PageInstructionFetchStep<PageSimulationContext>(this);
    }

    @Override
    public long getValueAtAddress(long address)
    {
        int user = checkIfAddressReferencesPageTable(address);

        if (user == -1)
        {
            return super.getValueAtAddress(address);
        }
        else
        {
            long descriptorIndex = (address - pageTableStartAddresses.get(user)) / getPageTableDescriptorSize();
            int unitIndex = (int)((address - pageTableStartAddresses.get(user)) % getPageTableDescriptorSize());

            long[] memoryRepresentation = getDescriptorMemoryRepresentation(pageTables.get(user).getEntryAndAdd(descriptorIndex));

            return memoryRepresentation[unitIndex];
        }
    }

    private int checkIfAddressReferencesPageTable(long address)
    {
        for (int i = 0; i < numberOfUsers; i++)
        {
            if (Long.compareUnsigned(address, pageTableStartAddresses.get(i)) >= 0 && Long.compareUnsigned(address, pageTableStartAddresses.get(i) + pageTableSize) < 0)
                return i;
        }

        return -1;
    }

    private long[] getDescriptorMemoryRepresentation(PageTableDescriptor descriptor)
    {
        int bitsPreUnit = config.getAddressableUnit() * 8;
        int frameBits = config.getPhysicalAddressBits() - config.getWordBits();
        int descriptorSize = getPageTableDescriptorSize();

        int vBit = descriptor.isValid() ? 1 : 0;
        int dBit = descriptor.isDirty() ? 1 : 0;
        long block = descriptor.getBlock();
        long disk = descriptor.getDisk();

        long[] memoryCells = new long[descriptorSize];

        long unifiedValue = (disk << (2 + frameBits)) | (block << 2) | (dBit << 1) | vBit;

        long unitBitMask = (1L << bitsPreUnit) - 1;

        for (int i = 0; i < descriptorSize; i++)
        {
            memoryCells[i] = (unifiedValue >>> (i * bitsPreUnit)) & unitBitMask;
        }

        return memoryCells;
    }

    public long getCurrentPTP()
    {
        return pageTableStartAddresses.get(getCurrentInstruction().getUser());
    }

    public PageTableDescriptor getCurrentDescriptor() 
    {
        return currentDescriptor;
    }

    public long getCurrentDescriptorOffset() 
    {
        long page = getPageComponent();

        return page << (31 - Integer.numberOfLeadingZeros(getPageTableDescriptorSize()));
    }

    public long getCurrentDescriptorAddress() {
        return currentDescriptorAddress;
    }

    public void setCurrentDescriptorAddress(long currentDescriptorAddress) {
        this.currentDescriptorAddress = currentDescriptorAddress;
    }

    public void setCurrentDescriptor(PageTableDescriptor currentDescriptor)
     {
        this.currentDescriptor = currentDescriptor;
    }

    public long getCurrentFrame()
    {
        return currentFrame;
    }

    public void setCurrentFrame(long currentFrame)
    {
        this.currentFrame = currentFrame;
    }

    public long getCurrentLoadDiskAddress()
    {
        return currentLoadDiskAddress;
    }

    public void setCurrentLoadDiskAddress(long currentLoadDiskAddress)
    {
        this.currentLoadDiskAddress = currentLoadDiskAddress;
    }

    public void setPageEvictionVictim(int user, long page, long frame)
    {
        this.pageEvictionVictimUser = user;
        this.pageEvictionVictimPage = page;
        this.pageEvictionVictimFrame = frame;
    }

    public int getPageEvictionVictimUser()
    {
        return pageEvictionVictimUser;
    }

    public long getPageEvictionVictimPage()
    {
        return pageEvictionVictimPage;
    }

    public long getPageEvictionVictimFrame()
    {
        return pageEvictionVictimFrame;
    }

    public void setPageEvictionInvalidatedTlbEntry(boolean invalidated, long tag, long block, boolean wasDirty, int index)
    {
        this.pageEvictionInvalidatedTlbEntry = invalidated;
        this.pageEvictionInvalidatedTlbTag = tag;
        this.pageEvictionInvalidatedTlbBlock = block;
        this.pageEvictionInvalidatedTlbWasDirty = wasDirty;
        this.pageEvictionInvalidatedTlbIndex = index;
    }

    public boolean didPageEvictionInvalidateTlbEntry()
    {
        return pageEvictionInvalidatedTlbEntry;
    }

    public long getPageEvictionInvalidatedTlbTag()
    {
        return pageEvictionInvalidatedTlbTag;
    }

    public long getPageEvictionInvalidatedTlbBlock()
    {
        return pageEvictionInvalidatedTlbBlock;
    }

    /** The TLB slot the invalidated entry occupied, or -1 if it wasn't cached there. */
    public int getPageEvictionInvalidatedTlbIndex()
    {
        return pageEvictionInvalidatedTlbIndex;
    }

    public boolean wasPageEvictionInvalidatedTlbDirty()
    {
        return pageEvictionInvalidatedTlbWasDirty;
    }

    public void setTlbWritebackVictim(int user, long page)
    {
        this.tlbWritebackUser = user;
        this.tlbWritebackPage = page;
    }

    public int getTlbWritebackUser()
    {
        return tlbWritebackUser;
    }

    public long getTlbWritebackPage()
    {
        return tlbWritebackPage;
    }

    public PageTable getPageTable(int user)
    {
        return pageTables.get(user);
    }

    public long getMaxPages()
    {
        return maxPages;
    }

    public long getPageTableStartAddress(int user)
    {
        return pageTableStartAddresses.get(user);
    }

    public long getPageComponent()
    {
        long address = getCurrentInstruction().getVirtualAddress();
        int wordBits = getWordBits();
        return address >> wordBits;
    }

    public int getPageTableDescriptorSize()
    {
        int descriptorBits = 2 + config.getPhysicalAddressBits() - config.getWordBits() + config.getDiskBits();
        int addressableUnit = config.getAddressableUnit();
        int unitSize = Math.ceilDiv(descriptorBits, addressableUnit * 8);

        if ((unitSize & (unitSize - 1)) == 0)
            return unitSize;

        return Integer.highestOneBit(unitSize) << 1;
    }

    public long getPageSize()
    {
        return 1L << config.getWordBits();
    }

    public PageOSMemoryManager getOSMemoryManager()
    {
        return memoryManager;
    }

    

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "PageSimulationContext[%s, tlb=%s, maxPages=%d]",
            super.toString(), tlb, maxPages));
        
        if (pageTables != null && !pageTables.isEmpty())
        {
            sb.append(String.format("%n  PageTables (%d):", pageTables.size()));
            for (int i = 0; i < pageTables.size(); i++)
            {
                long startAddress = i < pageTableStartAddresses.size() ? pageTableStartAddresses.get(i) : -1;
                sb.append(String.format("%n    [%d] (startAddress=0x%x): %s", i, startAddress, pageTables.get(i).toString()));
            }
        }
        
        return sb.toString();
    }

    

    
}
