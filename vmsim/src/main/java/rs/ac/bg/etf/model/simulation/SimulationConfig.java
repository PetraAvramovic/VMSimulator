package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.io.File;

import rs.ac.bg.etf.model.simulation.exceptions.InvalidConfig;
import rs.ac.bg.etf.model.memory.Instruction;

public class SimulationConfig 
{
    public enum TranslationType
    {
        PAGED,
        SEGMENTED,
        SEGMENTED_PAGED
    }

    public enum TLBType
    {
        ASSOCIATIVE,
        DIRECT,
        SET_ASSOCIATIVE 
    }

    private TranslationType translationType = TranslationType.PAGED;
    private TLBType tlbType = TLBType.ASSOCIATIVE;

    private int addressableUnit = 1; 
    private int physicalAddressBits = -1;
    private int numberOfUsers = -1;

    private int wordBits = -1;
    private int pageBits = -1;
    private int segmentBits = -1;

    private int diskBits = 32;

    private int tlbSize = -1;
    private int tlbEntriesPerSet = -1;

    private ArrayList<Instruction> instructions;
    //private ArrayList<MemoryInitializationBlock> memoryInit;
    private ArrayList<InitialPage> initialPages;
    private Map<Integer, Map<Long, PageTableDescriptorInit>> pageTables = new HashMap<>();

    public static record MemoryInitializationBlock(
        long startAddress,
        List<Long> data
    ) {}

    public record PageTableDescriptorInit(
        boolean valid,
        boolean dirty,
        long block
    ) {}

    public static record InitialPage(
        int userId,
        long page,
        Map<Long, Long> content
    ) {}

    public static SimulationConfig loadFromFile(String filePath, SimulationConfig config)
    {
        try {
            TomlMapper tomlMapper = TomlMapper.builder()
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL) 
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)   
            .build();

            tomlMapper.setDefaultMergeable(true); 

            File file = new File(filePath);

            return tomlMapper.readerForUpdating(config).readValue(file);

        } catch (Exception e) {
            throw new RuntimeException("Failed to merge external configuration properties", e);
        }
    }

    public void validate() throws InvalidConfig
    {
        if (physicalAddressBits <= 0)
        {
            throw new InvalidConfig("physicalAddressBits is required");
        }
        if (!isPowerOfTwo(numberOfUsers))
        {
            throw new InvalidConfig("numberOfUsers is required and must be a power of 2");
        }
        if (wordBits <= 0)
        {
            throw new InvalidConfig("wordBits is required");
        }
        if (!isPowerOfTwo(tlbSize))
        {
            throw new InvalidConfig("tlbSize is required and must be a power of 2");
        }

        if (translationType == TranslationType.PAGED || translationType == TranslationType.SEGMENTED_PAGED)
        {
            if (pageBits <= 0)
            {
                throw new InvalidConfig("pageBits is required for " + translationType);
            }
        }
        if (translationType == TranslationType.SEGMENTED || translationType == TranslationType.SEGMENTED_PAGED)
        {
            if (segmentBits <= 0)
            {
                throw new InvalidConfig("segmentBits is required for " + translationType);
            }
        }

        if (tlbType == TLBType.SET_ASSOCIATIVE && !isPowerOfTwo(tlbEntriesPerSet))
        {
            throw new InvalidConfig("tlbEntriesPerSet is required and must be a power of 2 for SET_ASSOCIATIVE");
        }

        // Validate initialPages content fits within page size
        if (initialPages != null && !initialPages.isEmpty())
        {
            long pageSize = 1L << wordBits; // Page size in addressable units
            for (int i = 0; i < initialPages.size(); i++)
            {
                InitialPage page = initialPages.get(i);
                if (page.content() != null && !page.content().isEmpty())
                {
                    for (Long offset : page.content().keySet())
                    {
                        if (offset < 0 || offset >= pageSize)
                        {
                            throw new InvalidConfig(
                                "InitialPage[" + i + "] (userId=" + page.userId() + ", page=" + page.page() + ") "
                                + "has content at offset " + offset + " which exceeds page size " + pageSize
                            );
                        }
                    }
                }
            }
        }

        // Validate that multiple valid page table descriptors don't reference the same frame
        if (pageTables != null && !pageTables.isEmpty())
        {
            Map<Long, String> frameReferences = new HashMap<>(); // block -> "userId:page"
            
            for (Map.Entry<Integer, Map<Long, PageTableDescriptorInit>> userEntry : pageTables.entrySet())
            {
                int userId = userEntry.getKey();
                Map<Long, PageTableDescriptorInit> pageMap = userEntry.getValue();
                
                for (Map.Entry<Long, PageTableDescriptorInit> pageEntry : pageMap.entrySet())
                {
                    long pageNum = pageEntry.getKey();
                    PageTableDescriptorInit descriptor = pageEntry.getValue();
                    
                    if (descriptor.valid())
                    {
                        long block = descriptor.block();
                        String reference = userId + ":" + pageNum;
                        
                        if (frameReferences.containsKey(block))
                        {
                            String existingRef = frameReferences.get(block);
                            throw new InvalidConfig(
                                "Multiple valid page table descriptors reference the same frame " + block + ": "
                                + existingRef + " and " + reference
                            );
                        }
                        frameReferences.put(block, reference);
                    }
                }
            }
        }

        // Validate that there is enough consecutive space for kernel structures (page tables)
        validatePageTableSpace();
    }

    /**
     * Validates that there is enough space in physical memory for all page tables
     * after all user frames are allocated. Each page table must occupy consecutive frames,
     * but different page tables can occupy non-consecutive frames.
     * All numberOfUsers page tables will be allocated, even if not explicitly defined in config.
     */
    private void validatePageTableSpace() throws InvalidConfig
    {
        // Calculate page table descriptor size in addressable units
        long descriptorSize = calculatePageTableDescriptorSize();

        // Track occupied frames as ranges: SortedMap of start -> end (inclusive)
        SortedMap<Long, Long> occupiedRanges = new TreeMap<>();
        
        if (pageTables != null && !pageTables.isEmpty())
        {
            for (Map.Entry<Integer, Map<Long, PageTableDescriptorInit>> userEntry : pageTables.entrySet())
            {
                Map<Long, PageTableDescriptorInit> pageMap = userEntry.getValue();
                
                for (PageTableDescriptorInit descriptor : pageMap.values())
                {
                    if (descriptor.valid())
                    {
                        long block = descriptor.block();
                        occupiedRanges.put(block, block);
                    }
                }
            }
        }

        // Merge overlapping/adjacent ranges
        mergeOccupiedRanges(occupiedRanges);

        // Validate no out-of-bounds frames
        long totalFrames = 1L << (physicalAddressBits - wordBits);
        for (long endFrame : occupiedRanges.values())
        {
            if (endFrame >= totalFrames)
            {
                throw new InvalidConfig("Page table descriptor references frame " + endFrame + " which exceeds physical memory");
            }
        }

        // Maximum pages per table is determined by pageBits
        long maxPages = 1L << pageBits;
        
        // Calculate size of a single page table (same for all users)
        long singlePageTableSize = descriptorSize * maxPages;
        long pageSize = 1L << wordBits; // Page size in addressable units
        long framesPerPageTable = (singlePageTableSize + pageSize - 1) / pageSize; // Ceiling division

        // Check for deadlock: if kernel structures consume all frames, there's no space for user data
        long totalKernelFrames = framesPerPageTable * numberOfUsers;
        if (totalKernelFrames >= totalFrames)
        {
            throw new InvalidConfig(
                "Kernel structures would consume all available physical memory, leaving no space for user data. " +
                "Need " + totalKernelFrames + " frames for " + numberOfUsers + " page tables, but only " + 
                totalFrames + " frames available. " +
                "Each page table requires " + framesPerPageTable + " frames " +
                "(descriptor size: " + descriptorSize + " AU, max pages: " + maxPages + ")"
            );
        }

        // Try to fit numberOfUsers page tables in available gaps
        int totalPageTablesNeeded = numberOfUsers;
        int pageTablesFitted = 0;
        
        if (occupiedRanges.isEmpty())
        {
            // No occupied frames, all frames are free
            pageTablesFitted = (int) Math.min((long) totalPageTablesNeeded, totalFrames / framesPerPageTable);
        }
        else
        {
            long currentFrame = 0L;
            
            // Check each gap and count how many page tables fit
            for (Map.Entry<Long, Long> range : occupiedRanges.entrySet())
            {
                if (pageTablesFitted >= totalPageTablesNeeded)
                {
                    break; // Stop if we've already fitted all needed page tables
                }
                
                long gapStart = currentFrame;
                long gapEnd = range.getKey() - 1;
                long gapSize = gapEnd - gapStart + 1;
                
                // Count how many page tables fit in this gap
                long pageTablesInGap = gapSize / framesPerPageTable;
                pageTablesFitted += (int) Math.min(pageTablesInGap, (long) (totalPageTablesNeeded - pageTablesFitted));
                
                currentFrame = range.getValue() + 1;
            }
            
            // Check final gap after last occupied range (if we haven't fitted all yet)
            if (pageTablesFitted < totalPageTablesNeeded)
            {
                long gapStart = currentFrame;
                long gapEnd = totalFrames - 1;
                long gapSize = gapEnd - gapStart + 1;
                
                if (gapSize > 0)
                {
                    long pageTablesInGap = gapSize / framesPerPageTable;
                    pageTablesFitted += (int) Math.min(pageTablesInGap, (long) (totalPageTablesNeeded - pageTablesFitted));
                }
            }
        }

        if (pageTablesFitted < totalPageTablesNeeded)
        {
            throw new InvalidConfig(
                "Insufficient space for all page tables. Need " + totalPageTablesNeeded + 
                " page tables but could only fit " + pageTablesFitted + ". " +
                "Each page table requires " + framesPerPageTable + " consecutive frames " +
                "(size: " + singlePageTableSize + " AU, descriptor size: " + descriptorSize + " AU, max pages: " + maxPages + ")"
            );
        }
    }

    /**
     * Merges overlapping or adjacent ranges in the occupied ranges map.
     */
    private void mergeOccupiedRanges(SortedMap<Long, Long> occupiedRanges)
    {
        if (occupiedRanges.size() <= 1)
        {
            return;
        }

        boolean merged = true;
        while (merged)
        {
            merged = false;
            Iterator<Map.Entry<Long, Long>> iter = occupiedRanges.entrySet().iterator();
            
            if (!iter.hasNext())
            {
                return;
            }
            
            long prevStart = -1;
            long prevEnd = -1;
            
            while (iter.hasNext())
            {
                Map.Entry<Long, Long> entry = iter.next();
                long currStart = entry.getKey();
                long currEnd = entry.getValue();
                
                if (prevStart != -1 && currStart <= prevEnd + 1)
                {
                    // Overlapping or adjacent ranges - merge them
                    occupiedRanges.put(prevStart, Math.max(prevEnd, currEnd));
                    iter.remove();
                    merged = true;
                    prevEnd = Math.max(prevEnd, currEnd);
                }
                else
                {
                    prevStart = currStart;
                    prevEnd = currEnd;
                }
            }
        }
    }

    /**
     * Calculates the size of a single page table descriptor in addressable units.
     * Size = ceil((1 + 1 + frameBits + 32) / 8 / addressableUnit)
     * Then rounds up to the next power of 2.
     */
    private long calculatePageTableDescriptorSize()
    {
        // Calculate bits needed
        int frameBits = physicalAddressBits - wordBits; // Frame address width
        int totalBits = 1 + 1 + frameBits + 32; // valid + dirty + block + disk
        
        // Convert to addressable units (bits / (8 * addressableUnit))
        long sizeInBytes = (totalBits + 7) / 8; // Ceiling division for bits to bytes
        long sizeInAU = (sizeInBytes + addressableUnit - 1) / addressableUnit; // Ceiling division for bytes to AU
        
        // Round up to next power of 2
        if (sizeInAU <= 0)
        {
            sizeInAU = 1;
        }
        // Find the smallest power of 2 >= sizeInAU
        return isPowerOfTwo(sizeInAU) ? sizeInAU : (1L << (64 - Long.numberOfLeadingZeros(sizeInAU)));
    }

    private static boolean isPowerOfTwo(long value)
    {
        return value > 0 && (value & (value - 1)) == 0;
    }

    public long generateDiskSeed() 
    {
        return Objects.hash(
            this.translationType, 
            this.wordBits, 
            this.pageBits, 
            this.segmentBits,
            this.numberOfUsers, 
            this.physicalAddressBits
        );
    }

    public int getVirtualMemoryBits()
    {
        int bitsWidth = wordBits;

        switch (translationType) {
            case TranslationType.PAGED:
                bitsWidth += pageBits;
                break;
            case TranslationType.SEGMENTED:
                bitsWidth += segmentBits;
                break;
            case TranslationType.SEGMENTED_PAGED:
                bitsWidth += pageBits + segmentBits;
            default:
                break;
        }

        return bitsWidth;
    }

    public int getAddressableUnit() {
        return addressableUnit;
    }

    public void setAddressableUnit(int addressableUnit) {
        this.addressableUnit = addressableUnit;
    }

    public int getDiskBits()
    {
        return diskBits;
    }

    public long getVirtualMemorySize()
    {
        return 1 << getVirtualMemoryBits();
    }
    
    public TranslationType getTranslationType() {
        return translationType;
    }

    public void setTranslationType(TranslationType translationType) {
        this.translationType = translationType;
    }

    public TLBType getTlbType() {
        return tlbType;
    }

    public void setTlbType(TLBType tlbType) {
        this.tlbType = tlbType;
    }

    public long getMemorySize() {
        return 1L << physicalAddressBits;
    }

    public int getNumberOfUsers() {
        return numberOfUsers;
    }

    public void setNumberOfUsers(int numberOfUsers) {
        this.numberOfUsers = numberOfUsers;
    }

    public int getWordBits() {
        return wordBits;
    }

    public void setWordBits(int wordBitsWidth) {
        this.wordBits = wordBitsWidth;
    }

    public int getPageBits() {
        return pageBits;
    }

    public void setPageBits(int pageBitsWidth) {
        this.pageBits = pageBitsWidth;
    }

    public int getSegmentBits() {
        return segmentBits;
    }

    public void setSegmentBits(int segmentBitsWidth) {
        this.segmentBits = segmentBitsWidth;
    }

    public int getTlbSize() {
        return tlbSize;
    }

    public void setTlbSize(int tlbSize) {
        this.tlbSize = tlbSize;
    }

    public int getTlbEntriesPerSet() {
        return tlbEntriesPerSet;
    }

    public void setTlbEntriesPerSet(int tlbEntriesPerSet) {
        this.tlbEntriesPerSet = tlbEntriesPerSet;
    }

    public ArrayList<Instruction> getInstructions() {
        return instructions;
    }

    public void setInstructions(ArrayList<Instruction> instructions) {
        this.instructions = instructions;
    }

    public int getPhysicalAddressBits() {
        return physicalAddressBits;
    }

    public void setPhysicalAddressBits(int physicalAddressBits) {
        this.physicalAddressBits = physicalAddressBits;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
            "SimulationConfig[translationType=%s, tlbType=%s, physicalAddressBits=%d, numberOfUsers=%d, "
            + "wordBits=%d, pageBits=%d, segmentBits=%d, tlbSize=%d, tlbEntriesPerSet=%d, addressableUnit=%d]",
            translationType, tlbType, physicalAddressBits, numberOfUsers,
            wordBits, pageBits, segmentBits, tlbSize, tlbEntriesPerSet, addressableUnit));

        int count = instructions == null ? 0 : instructions.size();
        sb.append("\nInstructions (").append(count).append("):");
        for (int i = 0; i < count; i++)
        {
            sb.append("\n  [").append(i).append("] ").append(instructions.get(i));
        }



        int pageCount = initialPages == null ? 0 : initialPages.size();
        sb.append("\nInitial pages (").append(pageCount).append("):");
        for (int i = 0; i < pageCount; i++)
        {
            InitialPage page = initialPages.get(i);
            sb.append("\n  [").append(i).append("] userId=").append(page.userId())
              .append(", page=").append(page.page())
              .append(", content=").append(page.content());
        }

        return sb.toString();
    }

    public Map<Integer, Map<Long, PageTableDescriptorInit>> getPageTables() {
        return pageTables;
    }

    public void setPageTables(Map<Integer, Map<Long, PageTableDescriptorInit>> pageTables) {
        this.pageTables = pageTables;
    }

    /**
     * Returns a map of all frames (blocks) referenced by valid page table descriptors.
     * Key: frame (block) number
     * Value: "userId:page" string identifying which user's page is in that frame
     */
    public Map<Long, String> getValidFrames() {
        Map<Long, String> validFrames = new HashMap<>();
        
        if (pageTables == null || pageTables.isEmpty()) {
            return validFrames;
        }
        
        for (Map.Entry<Integer, Map<Long, PageTableDescriptorInit>> userEntry : pageTables.entrySet()) {
            int userId = userEntry.getKey();
            Map<Long, PageTableDescriptorInit> pageMap = userEntry.getValue();
            
            for (Map.Entry<Long, PageTableDescriptorInit> pageEntry : pageMap.entrySet()) {
                long pageNum = pageEntry.getKey();
                PageTableDescriptorInit descriptor = pageEntry.getValue();
                
                if (descriptor.valid()) {
                    long block = descriptor.block();
                    validFrames.put(block, userId + ":" + pageNum);
                }
            }
        }
        
        return validFrames;
    }

    /**
     * Returns disk initialization content: HashMap keyed by "userId:page" for invalid descriptors
     * with starting content. Each value is a TreeMap of relative address-value pairs from the config.
     */
    public HashMap<String, SortedMap<Long, Long>> getDiskInitContent() {
        HashMap<String, SortedMap<Long, Long>> diskContent = new HashMap<>();
        
        if (initialPages == null || initialPages.isEmpty()) {
            return diskContent;
        }
        
        for (InitialPage initialPage : initialPages) {
            int userId = initialPage.userId();
            long pageNum = initialPage.page();
            Map<Long, Long> content = initialPage.content();
            
            // Skip if no content
            if (content == null || content.isEmpty()) {
                continue;
            }
            
            // Check if this page has a corresponding descriptor
            Map<Long, PageTableDescriptorInit> userPageTables = pageTables.get(userId);
            if (userPageTables == null || !userPageTables.containsKey(pageNum)) {
                continue;
            }
            
            PageTableDescriptorInit descriptor = userPageTables.get(pageNum);
            
            // Only include if descriptor is invalid (content goes to disk)
            if (!descriptor.valid()) {
                String key = userId + ":" + pageNum;
                diskContent.put(key, new TreeMap<>(content));
            }
        }
        
        return diskContent;
    }

    public ArrayList<InitialPage> getInitialPages() {
        return initialPages;
    }

    public void setInitialPages(ArrayList<InitialPage> initialPages) {
        this.initialPages = initialPages;
    }

    /**
     * Constructs a SortedMap of physical address to value pairs from initialPages
     * for only valid pages. For each valid page, computes physical addresses as
     * (block << wordBits) + offset and populates the map with page content.
     * @return SortedMap<Long, Long> of physical addresses to values for valid pages only
     */
    public SortedMap<Long, Long> getMemoryInit()
    {
        SortedMap<Long, Long> memoryMap = new TreeMap<>();
        
        if (initialPages == null || initialPages.isEmpty() || pageTables == null || pageTables.isEmpty())
        {
            return memoryMap;
        }
        
        for (InitialPage initialPage : initialPages)
        {
            int userId = initialPage.userId();
            long pageNum = initialPage.page();
            Map<Long, Long> content = initialPage.content();
            
            // Check if this page has a valid descriptor
            Map<Long, PageTableDescriptorInit> userPageTables = pageTables.get(userId);
            if (userPageTables == null || !userPageTables.containsKey(pageNum))
            {
                continue; // No descriptor for this page, skip
            }
            
            PageTableDescriptorInit descriptor = userPageTables.get(pageNum);
            if (!descriptor.valid())
            {
                continue; // Descriptor is invalid, skip
            }
            
            // Page is valid - add content entries to memory map
            long block = descriptor.block();
            long startAddress = block << wordBits;
            
            if (content != null && !content.isEmpty())
            {
                for (Map.Entry<Long, Long> contentEntry : content.entrySet())
                {
                    long offset = contentEntry.getKey();
                    long value = contentEntry.getValue();
                    long physicalAddress = startAddress + offset;
                    memoryMap.put(physicalAddress, value);
                }
            }
        }
        
        return memoryMap;
    }

    
}
