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
    // Upper bound on physicalAddressBits - wordBits (i.e. log2 of the physical frame count).
    // PageOSMemoryManager tracks frames sparsely (see getFreeFrame()/allocateAndLock()), so this
    // isn't chasing a performance cliff -- 32 is just a round, memorable ceiling (a full 32-bit
    // frame space) comfortably clear of the 62-bit MAX_VIRTUAL_ADDRESS_BITS ceiling below.
    private static final int MAX_FRAME_BITS = 32;
    // Upper bound on the combined virtual address width (wordBits + pageBits, or + segmentBits).
    // Must stay under 63 so virtual addresses fit in a non-negative long; capped a bit below that
    // hard ceiling for the same "stay usable" reason as MAX_FRAME_BITS.
    private static final int MAX_VIRTUAL_ADDRESS_BITS = 62;

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

    // Default to empty (not null) like pageTables below: a config with valid parameters but no
    // loaded file (or a file that simply omits these sections) must still hand back a usable,
    // iterable list rather than forcing every caller to null-check.
    private ArrayList<Instruction> instructions = new ArrayList<>();
    //private ArrayList<MemoryInitializationBlock> memoryInit;
    private ArrayList<InitialPage> initialPages = new ArrayList<>();
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
        if (wordBits >= physicalAddressBits)
        {
            throw new InvalidConfig(
                "wordBits (" + wordBits + ") must be less than physicalAddressBits (" + physicalAddressBits
                + "); physical memory must have at least one frame bit"
            );
        }
        if (physicalAddressBits - wordBits > MAX_FRAME_BITS)
        {
            throw new InvalidConfig(
                "physicalAddressBits - wordBits (" + (physicalAddressBits - wordBits) + ") exceeds the supported "
                + "maximum of " + MAX_FRAME_BITS + " frame bits (" + (1L << MAX_FRAME_BITS) + " frames)"
            );
        }
        if (!isPowerOfTwo(tlbSize))
        {
            throw new InvalidConfig("tlbSize is required and must be a power of 2");
        }
        if (addressableUnit <= 0 || addressableUnit > 8 || !isPowerOfTwo(addressableUnit))
        {
            throw new InvalidConfig("addressableUnit must be a power of 2 between 1 and 8 (bytes)");
        }

        if (translationType == TranslationType.SEGMENTED || translationType == TranslationType.SEGMENTED_PAGED)
        {
            throw new InvalidConfig(translationType + " translation is not yet supported; use PAGED");
        }

        // Only PAGED reaches this point; SEGMENTED and SEGMENTED_PAGED were rejected above.
        if (pageBits <= 0)
        {
            throw new InvalidConfig("pageBits is required for " + translationType);
        }

        if (getVirtualMemoryBits() > MAX_VIRTUAL_ADDRESS_BITS)
        {
            throw new InvalidConfig(
                "Combined virtual address width (" + getVirtualMemoryBits() + " bits) exceeds the supported "
                + "maximum of " + MAX_VIRTUAL_ADDRESS_BITS + " bits"
            );
        }

        if (tlbType == TLBType.SET_ASSOCIATIVE)
        {
            if (!isPowerOfTwo(tlbEntriesPerSet))
            {
                throw new InvalidConfig("tlbEntriesPerSet is required and must be a power of 2 for SET_ASSOCIATIVE");
            }
            if (tlbEntriesPerSet > tlbSize)
            {
                throw new InvalidConfig("tlbEntriesPerSet (" + tlbEntriesPerSet + ") cannot exceed tlbSize (" + tlbSize + ")");
            }
        }

        long maxPages = 1L << pageBits;

        // Validate initialPages: userId/page bounds, content offsets fit within a page, and
        // content values fit within a single memory word (addressableUnit * 8 bits)
        if (initialPages != null && !initialPages.isEmpty())
        {
            long pageSize = 1L << wordBits; // Page size in addressable units
            for (int i = 0; i < initialPages.size(); i++)
            {
                InitialPage page = initialPages.get(i);
                String label = "InitialPage[" + i + "] (userId=" + page.userId() + ", page=" + page.page() + ")";

                if (page.userId() < 0 || page.userId() >= numberOfUsers)
                {
                    throw new InvalidConfig(label + " has userId out of range [0, " + numberOfUsers + ")");
                }
                if (page.page() < 0 || page.page() >= maxPages)
                {
                    throw new InvalidConfig(label + " has page out of range [0, " + maxPages + ")");
                }

                if (page.content() != null && !page.content().isEmpty())
                {
                    for (Map.Entry<Long, Long> contentEntry : page.content().entrySet())
                    {
                        long offset = contentEntry.getKey();
                        if (offset < 0 || offset >= pageSize)
                        {
                            throw new InvalidConfig(
                                label + " has content at offset " + offset + " which exceeds page size " + pageSize
                            );
                        }
                        if (!valueFitsInAddressableUnit(contentEntry.getValue()))
                        {
                            throw new InvalidConfig(
                                label + " has content value " + contentEntry.getValue() + " at offset " + offset
                                + " which does not fit in a " + (addressableUnit * 8) + "-bit addressable unit"
                            );
                        }
                    }
                }
            }
        }

        // Validate pageTables: userId/page bounds, non-negative frames, and that multiple valid
        // descriptors don't reference the same frame
        if (pageTables != null && !pageTables.isEmpty())
        {
            Map<Long, String> frameReferences = new HashMap<>(); // block -> "userId:page"

            for (Map.Entry<Integer, Map<Long, PageTableDescriptorInit>> userEntry : pageTables.entrySet())
            {
                int userId = userEntry.getKey();
                if (userId < 0 || userId >= numberOfUsers)
                {
                    throw new InvalidConfig("pageTables contains userId " + userId + " out of range [0, " + numberOfUsers + ")");
                }

                Map<Long, PageTableDescriptorInit> pageMap = userEntry.getValue();

                for (Map.Entry<Long, PageTableDescriptorInit> pageEntry : pageMap.entrySet())
                {
                    long pageNum = pageEntry.getKey();
                    PageTableDescriptorInit descriptor = pageEntry.getValue();

                    if (pageNum < 0 || pageNum >= maxPages)
                    {
                        throw new InvalidConfig(
                            "Page table descriptor for user " + userId + " references page " + pageNum
                            + " out of range [0, " + maxPages + ")"
                        );
                    }

                    if (descriptor.valid())
                    {
                        long block = descriptor.block();
                        if (block < 0)
                        {
                            throw new InvalidConfig(
                                "Page table descriptor for user " + userId + ", page " + String.format("0x%X", pageNum)
                                + " references a negative frame " + block
                            );
                        }

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

        // Validate instructions: user id, virtual address width, and (for writes) that the value
        // fits within a single memory word
        if (instructions != null && !instructions.isEmpty())
        {
            long virtualMemorySize = getVirtualMemorySize();

            for (int i = 0; i < instructions.size(); i++)
            {
                Instruction instruction = instructions.get(i);
                String label = "Instruction[" + i + "]";

                if (instruction.getUser() < 0 || instruction.getUser() >= numberOfUsers)
                {
                    throw new InvalidConfig(
                        label + " has user " + instruction.getUser() + " out of range [0, " + numberOfUsers + ")"
                    );
                }
                if (instruction.getVirtualAddress() < 0 || instruction.getVirtualAddress() >= virtualMemorySize)
                {
                    throw new InvalidConfig(
                        label + " has virtual address " + String.format("0x%X", instruction.getVirtualAddress())
                        + " which exceeds the virtual address space size " + String.format("0x%X", virtualMemorySize)
                    );
                }
                if (instruction.getAccessType() == Instruction.AccessType.WR
                    && !valueFitsInAddressableUnit(instruction.getValue()))
                {
                    throw new InvalidConfig(
                        label + " has write value " + instruction.getValue()
                        + " which does not fit in a " + (addressableUnit * 8) + "-bit addressable unit"
                    );
                }
            }
        }

        // Validate that there is enough consecutive space for kernel structures (page tables)
        validatePageTableSpace();
    }

    /** Whether {@code value} fits in the {@code addressableUnit * 8}-bit width of one memory word. */
    private boolean valueFitsInAddressableUnit(long value)
    {
        int bits = addressableUnit * 8;
        if (bits >= 64)
        {
            return true; // a full long already fits in a word this wide
        }
        return value >= 0 && value < (1L << bits);
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
        // Track which descriptor (user, page) originally referenced each frame
        Map<Long, long[]> frameOrigins = new HashMap<>(); // frame -> {userId, pageNum}

        if (pageTables != null && !pageTables.isEmpty())
        {
            for (Map.Entry<Integer, Map<Long, PageTableDescriptorInit>> userEntry : pageTables.entrySet())
            {
                int userId = userEntry.getKey();
                Map<Long, PageTableDescriptorInit> pageMap = userEntry.getValue();

                for (Map.Entry<Long, PageTableDescriptorInit> pageEntry : pageMap.entrySet())
                {
                    PageTableDescriptorInit descriptor = pageEntry.getValue();

                    if (descriptor.valid())
                    {
                        long block = descriptor.block();
                        occupiedRanges.put(block, block);
                        frameOrigins.put(block, new long[] { userId, pageEntry.getKey() });
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
                long[] origin = frameOrigins.get(endFrame);
                throw new InvalidConfig(
                    "Page table descriptor for user " + origin[0] + ", page " + String.format("0x%X", origin[1])
                    + " references frame " + String.format("0x%X", endFrame) + " which exceeds physical memory"
                );
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
                "Need " + pluralize(totalKernelFrames, "frame") + " for " + pluralize(numberOfUsers, "page table")
                + ", but only " + pluralize(totalFrames, "frame") + " available. " +
                "Each page table requires " + pluralize(framesPerPageTable, "frame") + " " +
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
                "Insufficient space for all page tables. Need " + pluralize(totalPageTablesNeeded, "page table") +
                " but could only fit " + pageTablesFitted + ". " +
                "Each page table requires " + pluralize(framesPerPageTable, "consecutive frame") + " " +
                "(size: " + singlePageTableSize + " AU, descriptor size: " + descriptorSize + " AU, max pages: " + maxPages + ")"
            );
        }
    }

    /** {@code count} followed by {@code singular}, pluralized ("1 frame" vs. "2 frames"). */
    private static String pluralize(long count, String singular)
    {
        return count + " " + singular + (count == 1 ? "" : "s");
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
        return 1L << getVirtualMemoryBits();
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
