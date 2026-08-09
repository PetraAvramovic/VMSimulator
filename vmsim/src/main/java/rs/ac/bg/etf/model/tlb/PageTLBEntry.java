package rs.ac.bg.etf.model.tlb;

/**
 * TLB entry specialization for paging.
 * Extends TLBEntry with a block field (physical frame number).
 */
public class PageTLBEntry extends TLBEntry
{
    private int block; // Physical frame number
    
    public PageTLBEntry()
    {
        super();
        this.block = 0;
    }
    
    public PageTLBEntry(int tag, boolean valid, boolean dirty, int block)
    {
        super(tag, valid, dirty);
        this.block = block;
    }
    
    public int getBlock()
    {
        return block;
    }
    
    public void setBlock(int block)
    {
        this.block = block;
    }
    
    @Override
    public String toString()
    {
        return String.format("PageTLBEntry[tag=0x%x, valid=%b, dirty=%b, block=0x%x]", 
                             getTag(), isValid(), isDirty(), block);
    }
}
