package rs.ac.bg.etf.model.tlb;

/**
 * Fully associative TLB: any entry can occupy any slot.
 * Lookup performs a linear search over all entries; insertion uses strict hardware round-robin
 * replacement (dumb pointer that doesn't track validity). Empty slots are filled first,
 * but when full, the pointer simply increments regardless of entry age.
 * Empty slots are represented by null so the UI can render them as EMPTY.
 */
public class AssociativeTLB extends TLB
{
    private int fifoPointer; // Hardware round-robin pointer, increments on every eviction
    
    public AssociativeTLB(int size, int addressBits, int processBits)
    {
        super(size, addressBits, processBits);
        this.fifoPointer = 0;
        // Initialize array with nulls
        for (int i = 0; i < size; i++)
        {
            entries.add(null);
        }
    }
    
    @Override
    public TLBEntry lookup(long tag)
    {
        for (TLBEntry entry : entries)
        {
            if (entry != null && entry.isHit(tag))
            {
                return entry;
            }
        }
        return null;
    }
    
    @Override
    public TLBEntry insert(TLBEntry entry)
    {
        // First, look for any empty slot and fill it
        for (int i = 0; i < size; i++)
        {
            if (entries.get(i) == null)
            {
                entries.set(i, entry);
                pushInsertion(null, entry, i);
                return null;
            }
        }
        
        // No free slot: dumb pointer eviction (doesn't care about validity)
        int victimIndex = fifoPointer % size;
        TLBEntry evicted = entries.get(victimIndex);
        entries.set(victimIndex, entry);
        int oldPointer = fifoPointer;
        fifoPointer++;
        pushInsertion(evicted, entry, victimIndex, oldPointer);
        return evicted;
    }
    
    @Override
    protected void undoInsertionInternal(InsertionRecord record)
    {
        if (record.evictedEntry != null)
        {
            // Eviction occurred: restore evicted entry and restore pointer to pre-eviction state
            entries.set(record.position, record.evictedEntry);
            fifoPointer = record.secondaryPosition;
        }
        else
        {
            // No eviction: just clear the slot
            entries.set(record.position, null);
        }
    }
    
    @Override
    public TLBEntry invalidateEntry(long tag)
    {
        for (int i = 0; i < entries.size(); i++)
        {
            TLBEntry entry = entries.get(i);
            if (entry != null && entry.getTag() == tag)
            {
                entries.set(i, null);
                pushInvalidatedEntry(entry, i);
                return entry;
            }
        }
        return null;
    }
    
    @Override
    protected void restoreInvalidatedEntry(InvalidationRecord record)
    {
        entries.set(record.position, record.entry);
    }
}
