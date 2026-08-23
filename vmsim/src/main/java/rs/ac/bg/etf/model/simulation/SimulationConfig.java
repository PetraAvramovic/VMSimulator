package rs.ac.bg.etf.model.simulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        SEGMENT,
        SEGMENT_PAGED
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
    private ArrayList<MemoryInitializationBlock> memoryInit;
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

    public void validateConfig() throws InvalidConfig
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

        if (translationType == TranslationType.PAGED || translationType == TranslationType.SEGMENT_PAGED)
        {
            if (pageBits <= 0)
            {
                throw new InvalidConfig("pageBits is required for " + translationType);
            }
        }
        if (translationType == TranslationType.SEGMENT || translationType == TranslationType.SEGMENT_PAGED)
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
            case TranslationType.SEGMENT:
                bitsWidth += segmentBits;
                break;
            case TranslationType.SEGMENT_PAGED:
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

        int blockCount = memoryInit == null ? 0 : memoryInit.size();
        sb.append("\nMemory init blocks (").append(blockCount).append("):");
        for (int i = 0; i < blockCount; i++)
        {
            MemoryInitializationBlock block = memoryInit.get(i);
            sb.append("\n  [").append(i).append("] startAddress=0x")
              .append(Long.toHexString(block.startAddress()))
              .append(", data=").append(block.data());
        }

        return sb.toString();
    }

    /*public ArrayList<MemoryInitializationBlock> getMemoryInit() {
        return memoryInit;
    }

    public void setMemoryInit(ArrayList<MemoryInitializationBlock> memoryInit) {
        this.memoryInit = memoryInit;
    }*/

    public Map<Integer, Map<Long, PageTableDescriptorInit>> getPageTables() {
        return pageTables;
    }

    public void setPageTables(Map<Integer, Map<Long, PageTableDescriptorInit>> pageTables) {
        this.pageTables = pageTables;
    }

    
}
