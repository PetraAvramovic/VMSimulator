package rs.ac.bg.etf.model.tlb;

/**
 * Direct-mapped TLB: each full lookup key maps to exactly one slot, computed from its low
 * {@code log2(size)} bits ({@code key % size}). The remaining high {@code k@p - m} bits are the
 * value stored in and compared against the entry's tag. Inserting a new entry always overwrites
 * whatever occupies that slot. Empty slots are represented by null so the UI can render them as EMPTY.
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

    private int indexFor(long fullKey)
    {
        return Math.floorMod(fullKey, size);
    }

    @Override
    public int getIndexComponentBits()
    {
        return Integer.numberOfTrailingZeros(size);
    }

    @Override
    public int mappedSlot(long fullKey)
    {
        return indexFor(fullKey);
    }

    @Override
    public TLBEntry lookup(long tag)
    {
        TLBEntry entry = entries.get(indexFor(tag));
        if (entry != null && entry.isValid() && entry.getTag() == toStoredTag(tag))
        {
            return entry;
        }
        return null;
    }

    @Override
    public TLBEntry insert(TLBEntry entry)
    {
        int index = indexFor(entry.getTag());
        entry.setTag(toStoredTag(entry.getTag()));
        TLBEntry evicted = entries.get(index);
        entries.set(index, entry);
        pushInsertion(evicted, entry, index);
        return evicted;
    }
    
    @Override
    protected void undoInsertionInternal(InsertionRecord record)
    {
        // Restore the evicted entry (might be null for empty slot)
        entries.set(record.position, record.evictedEntry);
    }
    
    @Override
    public TLBEntry invalidateEntry(long tag)
    {
        int index = indexFor(tag);
        TLBEntry entry = entries.get(index);
        if (entry != null && entry.getTag() == toStoredTag(tag))
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
    public boolean wouldEvict(long tag)
    {
        TLBEntry existing = entries.get(indexFor(tag));
        return existing != null && existing.isValid();
    }

    @Override
    public TLBEntry evictForInsertion(long tag)
    {
        // A direct-mapped TLB has no replacement policy choosing among candidates -- the target
        // slot is fixed by the tag alone -- so there is no pointer to advance, and this is a pure
        // peek: insert() overwrites whatever the caller leaves marked invalid here.
        return entries.get(indexFor(tag));
    }

    @Override
    protected void restoreEvictionPointer(EvictionRecord record)
    {
    }

    @Override
    public void flushProcessTag(int processId)
    {
        int pidShift = Math.max(0, addressBits - getIndexComponentBits());
        for (int i = 0; i < entries.size(); i++)
        {
            TLBEntry entry = entries.get(i);
            if (entry != null && (entry.getTag() >>> pidShift) == processId)
            {
                entries.set(i, null);
            }
        }
    }
}
