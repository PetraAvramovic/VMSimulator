package rs.ac.bg.etf.model.simulation;

import rs.ac.bg.etf.model.simulation.exceptions.InvalidConfig;

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

    private long memorySize = -1;
    private int numberOfUsers = -1;

    private int wordBitsWidth = -1;
    private int pageBitsWidth = -1;
    private int segmentBitsWidth = -1;

    private int tlbSize = -1;
    private int tlbEntriesPerSet = -1;

    public void validateConfig() throws InvalidConfig
    {
        if (!isPowerOfTwo(memorySize))
        {
            throw new InvalidConfig("memorySize is required and must be a power of 2");
        }
        if (!isPowerOfTwo(numberOfUsers))
        {
            throw new InvalidConfig("numberOfUsers is required and must be a power of 2");
        }
        if (wordBitsWidth <= 0)
        {
            throw new InvalidConfig("wordBitsWidth is required");
        }
        if (!isPowerOfTwo(tlbSize))
        {
            throw new InvalidConfig("tlbSize is required and must be a power of 2");
        }

        if (translationType == TranslationType.PAGED || translationType == TranslationType.SEGMENT_PAGED)
        {
            if (pageBitsWidth <= 0)
            {
                throw new InvalidConfig("pageBitsWidth is required for " + translationType);
            }
        }
        if (translationType == TranslationType.SEGMENT || translationType == TranslationType.SEGMENT_PAGED)
        {
            if (segmentBitsWidth <= 0)
            {
                throw new InvalidConfig("segmentBitsWidth is required for " + translationType);
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

    public long getVirtualMemorySize()
    {
        int bitsWidth = wordBitsWidth;

        switch (translationType) {
            case TranslationType.PAGED:
                bitsWidth += pageBitsWidth;
                break;
            case TranslationType.SEGMENT:
                bitsWidth += segmentBitsWidth;
                break;
            case TranslationType.SEGMENT_PAGED:
                bitsWidth += pageBitsWidth + segmentBitsWidth;
            default:
                break;
        }
        return 1 << bitsWidth;
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
        return memorySize;
    }

    public void setMemorySize(long memorySize) {
        this.memorySize = memorySize;
    }

    public int getNumberOfUsers() {
        return numberOfUsers;
    }

    public void setNumberOfUsers(int numberOfUsers) {
        this.numberOfUsers = numberOfUsers;
    }

    public int getWordBitsWidth() {
        return wordBitsWidth;
    }

    public void setWordBitsWidth(int wordBitsWidth) {
        this.wordBitsWidth = wordBitsWidth;
    }

    public int getPageBitsWidth() {
        return pageBitsWidth;
    }

    public void setPageBitsWidth(int pageBitsWidth) {
        this.pageBitsWidth = pageBitsWidth;
    }

    public int getSegmentBitsWidth() {
        return segmentBitsWidth;
    }

    public void setSegmentBitsWidth(int segmentBitsWidth) {
        this.segmentBitsWidth = segmentBitsWidth;
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

    
}
