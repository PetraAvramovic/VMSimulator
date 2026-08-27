package rs.ac.bg.etf.model.tlb;

/**
 * Unified TLB entry class containing all fields for paging, segmentation, and combined modes.
 */
public class TLBEntry
{
    private boolean valid;
    private boolean dirty;
    private long tag;
    private long block;        // Physical frame number (for paging and segment-paging modes)
    private int rwe;          // Read/Write/Execute permissions (for segmentation and segment-paging modes)
    private int length;       // Segment length (for segmentation mode)
    private int startAddr;    // Segment start address (for segmentation mode)
    
    public TLBEntry()
    {
        this.valid = false;
        this.dirty = false;
        this.tag = 0;
        this.block = 0;
        this.rwe = 0;
        this.length = 0;
        this.startAddr = 0;
    }
    
    public TLBEntry(long tag, boolean valid, boolean dirty)
    {
        this(tag, valid, dirty, 0, 0, 0, 0);
    }
    
    public TLBEntry(long tag, boolean valid, boolean dirty, long block)
    {
        this(tag, valid, dirty, 0, block, 0, 0);
    }
    
    public TLBEntry(long tag, boolean valid, boolean dirty, int rwe, int length, int startAddr)
    {
        this(tag, valid, dirty, 0, rwe, length, startAddr);
    }
    
    public TLBEntry(long tag, boolean valid, boolean dirty, int rwe, long block, int length, int startAddr)
    {
        this.tag = tag;
        this.valid = valid;
        this.dirty = dirty;
        this.block = block;
        this.rwe = rwe;
        this.length = length;
        this.startAddr = startAddr;
    }
    
    // Base field getters and setters
    public boolean isValid()
    {
        return valid;
    }
    
    public void setValid(boolean valid)
    {
        this.valid = valid;
    }
    
    public boolean isDirty()
    {
        return dirty;
    }
    
    public void setDirty(boolean dirty)
    {
        this.dirty = dirty;
    }
    
    public long getTag()
    {
        return tag;
    }
    
    public void setTag(long tag)
    {
        this.tag = tag;
    }
    
    // Paging mode getters and setters
    public long getBlock()
    {
        return block;
    }
    
    public void setBlock(long block)
    {
        this.block = block;
    }
    
    // Segmentation mode getters and setters
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
    
    // Permission checking methods (for segmentation modes)
    public boolean canRead()
    {
        return (rwe & 0b100) != 0;
    }
    
    public boolean canWrite()
    {
        return (rwe & 0b010) != 0;
    }
    
    public boolean canExecute()
    {
        return (rwe & 0b001) != 0;
    }
    
    /**
     * Checks if this entry matches the given tag.
     * @param tag The tag to compare
     * @return true if tags match and entry is valid
     */
    public boolean isHit(long tag)
    {
        return this.valid && this.tag == tag;
    }
    
    /**
     * Invalidates this entry.
     */
    public void invalidate()
    {
        this.valid = false;
    }
    
    @Override
    public String toString()
    {
        return String.format(
            "TLBEntry[tag=0x%x, valid=%b, dirty=%b, block=0x%x, rwe=%d, length=%d, startAddr=0x%x]",
            tag, valid, dirty, block, rwe, length, startAddr);
    }
}
