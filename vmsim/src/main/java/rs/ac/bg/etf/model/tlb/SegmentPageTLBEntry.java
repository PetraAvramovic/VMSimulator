package rs.ac.bg.etf.model.tlb;

/**
 * TLB entry specialization for combined page-segment translation.
 * Extends TLBEntry with rwe (read/write/execute) and block (physical frame) fields.
 */
public class SegmentPageTLBEntry extends TLBEntry
{
    private int rwe;   // Read/Write/Execute permissions (3 bits: RWE)
    private int block; // Physical frame number (from page table)
    
    public SegmentPageTLBEntry()
    {
        super();
        this.rwe = 0;
        this.block = 0;
    }
    
    public SegmentPageTLBEntry(int tag, boolean valid, boolean dirty, int rwe, int block)
    {
        super(tag, valid, dirty);
        this.rwe = rwe;
        this.block = block;
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
    
    public int getBlock()
    {
        return block;
    }
    
    public void setBlock(int block)
    {
        this.block = block;
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
        return String.format("SegmentPageTLBEntry[tag=0x%x, valid=%b, dirty=%b, rwe=%d, block=0x%x]", 
                             getTag(), isValid(), isDirty(), rwe, block);
    }
}
