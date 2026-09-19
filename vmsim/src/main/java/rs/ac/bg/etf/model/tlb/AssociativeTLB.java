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
        // First, look for any free slot -- either genuinely empty, or holding an entry a prior
        // evictForInsertion() already invalidated in place -- and fill it
        for (int i = 0; i < size; i++)
        {
            TLBEntry existing = entries.get(i);
            if (existing == null || !existing.isValid())
            {
                entries.set(i, entry);
                pushInsertion(existing, entry, i);
                return existing;
            }
        }

        // No free slot: dumb pointer eviction (doesn't care about validity). Shouldn't happen once
        // callers always run evictForInsertion() first, but kept as a defensive fallback.
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
        entries.set(record.position, record.evictedEntry);
        if (record.secondaryPosition >= 0)
        {
            fifoPointer = record.secondaryPosition;
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

    @Override
    public boolean wouldEvict(long tag)
    {
        for (int i = 0; i < size; i++)
        {
            TLBEntry existing = entries.get(i);
            if (existing == null || !existing.isValid())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public TLBEntry evictForInsertion(long tag)
    {
        int victimIndex = fifoPointer % size;
        TLBEntry evicted = entries.get(victimIndex);
        int oldPointer = fifoPointer;
        fifoPointer++;
        pushEviction(-1, oldPointer);
        return evicted;
    }

    @Override
    protected void restoreEvictionPointer(EvictionRecord record)
    {
        fifoPointer = record.pointerValue;
    }
}
