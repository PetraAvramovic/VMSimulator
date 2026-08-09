package rs.ac.bg.etf.model.tlb;

/**
 * TLB entry specialization for segmentation.
 * Extends TLBEntry with rwe (read/write/execute), length, and startAddr fields.
 */
public class SegmentTLBEntry extends TLBEntry
{
    private int rwe;       // Read/Write/Execute permissions (3 bits: RWE)
    private int length;    // Segment length
    private int startAddr; // Segment start address
    
    public SegmentTLBEntry()
    {
        super();
        this.rwe = 0;
        this.length = 0;
        this.startAddr = 0;
    }
    
    public SegmentTLBEntry(int tag, boolean valid, boolean dirty, int rwe, int length, int startAddr)
    {
        super(tag, valid, dirty);
        this.rwe = rwe;
        this.length = length;
        this.startAddr = startAddr;
    }
    
    // Getters and setters
    public int getRWE()
    {
        return rwe;
    }
    
    public void setRWE(int rwe)
    {
        this.rwe = rwe;
    }
    
    public int getLength()
    {
        return length;
    }
    
    public void setLength(int length)
    {
        this.length = length;
    }
    
    public int getStartAddr()
    {
        return startAddr;
    }
    
    public void setStartAddr(int startAddr)
    {
        this.startAddr = startAddr;
    }
    
    /**
     * Checks if read permission is set.
     */
    public boolean canRead()
    {
        return (rwe & 0b100) != 0;
    }
    
    /**
     * Checks if write permission is set.
     */
    public boolean canWrite()
    {
        return (rwe & 0b010) != 0;
    }
    
    /**
     * Checks if execute permission is set.
     */
    public boolean canExecute()
    {
        return (rwe & 0b001) != 0;
    }
    
    @Override
    public String toString()
    {
        return String.format("SegmentTLBEntry[tag=0x%x, valid=%b, dirty=%b, rwe=%d, length=%d, startAddr=0x%x]", 
                             getTag(), isValid(), isDirty(), rwe, length, startAddr);
    }
}
