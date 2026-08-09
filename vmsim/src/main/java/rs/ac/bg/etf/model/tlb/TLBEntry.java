package rs.ac.bg.etf.model.tlb;

/**
 * Base class for TLB entries.
 * Contains common fields: valid, dirty, and tag
 */
public abstract class TLBEntry
{
    private boolean valid;
    private boolean dirty;
    private int tag;
    
    public TLBEntry()
    {
        this.valid = false;
        this.dirty = false;
        this.tag = 0;
    }
    
    public TLBEntry(int tag, boolean valid, boolean dirty)
    {
        this.tag = tag;
        this.valid = valid;
        this.dirty = dirty;
    }
    
    // Getters and setters
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
    
    public int getTag()
    {
        return tag;
    }
    
    public void setTag(int tag)
    {
        this.tag = tag;
    }
    
    /**
     * Checks if this entry matches the given tag.
     * @param tag The tag to compare
     * @return true if tags match and entry is valid
     */
    public boolean isHit(int tag)
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
        return String.format("TLBEntry[tag=0x%x, valid=%b, dirty=%b]", tag, valid, dirty);
    }
}
