package rs.ac.bg.etf.model.tlb;

/**
 * Fully associative TLB: any entry can occupy any slot.
 * Lookup performs a linear search over all entries; insertion uses FIFO
 * replacement when the TLB is full.
 *
 * @param <E> the type of TLB entry stored
 */
public class AssociativeTLB<E extends TLBEntry> extends TLB<E>
{
    public AssociativeTLB(int size, int addressBits, int processBits)
    {
        super(size, addressBits, processBits);
    }
    
    @Override
    public E lookup(int tag)
    {
        for (E entry : entries)
        {
            if (entry.isHit(tag))
            {
                return entry;
            }
        }
        return null;
    }
    
    @Override
    public void insert(E entry)
    {
        if (entries.size() >= size)
        {
            entries.remove(0); // FIFO eviction of the oldest entry
        }
        entries.add(entry);
    }
    
    @Override
    public void invalidateTag(int tag)
    {
        entries.removeIf(entry -> entry.getTag() == tag);
    }
}
