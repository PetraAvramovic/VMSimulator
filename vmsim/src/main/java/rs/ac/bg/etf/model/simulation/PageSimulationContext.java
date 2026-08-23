package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;

import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.table.PageTable;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageSimulationContext extends SimulationContext
{
    private ArrayList<PageTable> pageTables = new ArrayList<>();
    private long maxPages;
    private PageOSMemoryManager memoryManager;

    private PageTableDescriptor currentDescriptor = null;

    public PageSimulationContext(SimulationConfig config) 
    {
        super(config);
    }

    @Override
    public void init()
    {
        super.init();

        maxPages = 1l << config.getPageBits(); 

        for (int i = 0; i < numberOfUsers; i++)
        {
            PageTable table = new PageTable(diskAddressGenerator, i, maxPages);
            table.init(config.getPageTables().get(i));
            pageTables.add(table);
        }
    }

    public PageTableDescriptor getCurrentDescriptor() 
    {
        return currentDescriptor;
    }

    public void setCurrentDescriptor(PageTableDescriptor currentDescriptor) {
        this.currentDescriptor = currentDescriptor;
    }

    public PageTable getPageTable(int user)
    {
        return pageTables.get(user);
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
        return Math.ceilDiv(descriptorBits, addressableUnit * 8);
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
                sb.append(String.format("%n    [%d]: %s", i, pageTables.get(i).toString()));
            }
        }
        
        return sb.toString();
    }
}
