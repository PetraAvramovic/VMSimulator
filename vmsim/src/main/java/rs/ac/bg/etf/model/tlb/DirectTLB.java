package rs.ac.bg.etf.model.tlb;

/**
 * Direct-mapped TLB: each tag maps to exactly one slot, computed as (tag % size).
 * Inserting a new entry always overwrites whatever occupies that slot.
 * Empty slots are represented by null so the UI can render them as EMPTY.
 *
 * @param <E> the type of TLB entry stored
 */
public class DirectTLB<E extends TLBEntry> extends TLB<E>
{
    public DirectTLB(int size, int addressBits, int processBits)
    {
        super(size, addressBits, processBits);
        for (int i = 0; i < size; i++)
        {
            entries.add(null);
        }
    }
    
    private int indexFor(int tag)
    {
        return Math.floorMod(tag, size);
    }
    
    @Override
    public E lookup(int tag)
    {
        E entry = entries.get(indexFor(tag));
        if (entry != null && entry.isHit(tag))
        {
            return entry;
        }
        return null;
    }
    
    @Override
    public void insert(E entry)
    {
        entries.set(indexFor(entry.getTag()), entry);
    }
    
    @Override
    public void invalidateTag(int tag)
    {
        int index = indexFor(tag);
        E entry = entries.get(index);
        if (entry != null && entry.getTag() == tag)
        {
            entries.set(index, null);
        }
    }
    
    @Override
    public void flushProcessTag(int processId)
    {
        for (int i = 0; i < entries.size(); i++)
        {
            E entry = entries.get(i);
            if (entry != null && (entry.getTag() >>> addressBits) == processId)
            {
                entries.set(i, null);
            }
        }
    }
}
