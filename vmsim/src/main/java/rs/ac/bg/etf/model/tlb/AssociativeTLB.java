package rs.ac.bg.etf.model.tlb;

/**
 * Fully associative TLB: any entry can occupy any slot.
 * Lookup performs a linear search over all entries; insertion uses FIFO
 * replacement when the TLB is full.
 */
public class AssociativeTLB extends TLB
{
    public AssociativeTLB(int size, int addressBits, int processBits)
    {
        super(size, addressBits, processBits);
    }
    
    @Override
    public TLBEntry lookup(long tag)
    {
        for (TLBEntry entry : entries)
        {
            if (entry.isHit(tag))
            {
                return entry;
            }
        }
        return null;
    }
    
    @Override
    public void insert(TLBEntry entry)
    {
        if (entries.size() >= size)
        {
            entries.remove(0); // FIFO eviction of the oldest entry
        }
        entries.add(entry);
    }
    
    @Override
    public TLBEntry invalidateEntry(long tag)
    {
        for (int i = 0; i < entries.size(); i++)
        {
            TLBEntry entry = entries.get(i);
            if (entry.getTag() == tag)
            {
                entries.remove(i);
                pushInvalidatedEntry(entry, i);
                return entry;
            }
        }
        return null;
    }
    
    @Override
    protected void restoreInvalidatedEntry(InvalidationRecord record)
    {
        entries.add(record.position, record.entry);
    }
}
