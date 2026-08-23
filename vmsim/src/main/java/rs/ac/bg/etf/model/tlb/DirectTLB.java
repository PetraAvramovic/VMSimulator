package rs.ac.bg.etf.model.tlb;

/**
 * Direct-mapped TLB: each tag maps to exactly one slot, computed as (tag % size).
 * Inserting a new entry always overwrites whatever occupies that slot.
 * Empty slots are represented by null so the UI can render them as EMPTY.
 */
public class DirectTLB extends TLB
{
    public DirectTLB(int size, int addressBits, int processBits)
    {
        super(size, addressBits, processBits);
        for (int i = 0; i < size; i++)
        {
            entries.add(null);
        }
    }
    
    private int indexFor(long tag)
    {
        return Math.floorMod(tag, size);
    }
    
    @Override
    public TLBEntry lookup(long tag)
    {
        TLBEntry entry = entries.get(indexFor(tag));
        if (entry != null && entry.isHit(tag))
        {
            return entry;
        }
        return null;
    }
    
    @Override
    public void insert(TLBEntry entry)
    {
        entries.set(indexFor(entry.getTag()), entry);
    }
    
    @Override
    public TLBEntry invalidateEntry(long tag)
    {
        int index = indexFor(tag);
        TLBEntry entry = entries.get(index);
        if (entry != null && entry.getTag() == tag)
        {
            entries.set(index, null);
            pushInvalidatedEntry(entry, index);
            return entry;
        }
        return null;
    }
    
    @Override
    protected void restoreInvalidatedEntry(InvalidationRecord record)
    {
        entries.set(record.position, record.entry);
    }
    
    @Override
    public void flushProcessTag(int processId)
    {
        for (int i = 0; i < entries.size(); i++)
        {
            TLBEntry entry = entries.get(i);
            if (entry != null && (entry.getTag() >>> addressBits) == processId)
            {
                entries.set(i, null);
            }
        }
    }
}
