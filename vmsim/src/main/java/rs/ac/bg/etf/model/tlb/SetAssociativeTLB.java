package rs.ac.bg.etf.model.tlb;

/**
 * Set-associative TLB: entries are grouped into sets of a fixed associativity.
 * A full lookup key maps to a set via its low {@code log2(numSets)} bits ({@code key % numSets}); the
 * remaining high {@code k@p - m} bits are the value stored in and compared against the entry's tag.
 * Within a set, lookup is linear and insertion uses strict hardware round-robin replacement (dumb
 * pointer per set). Empty slots are represented by null so the UI can render them as EMPTY.
 * Each set maintains its own hardware round-robin pointer that increments on eviction.
 * <p>
 * Storage is <b>way-major</b>: way {@code w} of set {@code s} lives at flat index
 * {@code w * numSets + s} in {@code entries}. So the first {@code numSets} slots are every set's
 * way 0, the next {@code numSets} slots every set's way 1, and so on -- one contiguous column per
 * way, which is how the schematic view draws it (one table per way, indexed by set number).
 */
public class SetAssociativeTLB extends TLB
{
    private final int entriesPerSet;
    private final int numSets;
    private final int[] fifoPointerPerSet; // Hardware round-robin pointer per set

    public SetAssociativeTLB(int size, int addressBits, int processBits, int entriesPerSet)
    {
        super(size, addressBits, processBits);
        this.entriesPerSet = entriesPerSet;
        this.numSets = size / entriesPerSet;
        this.fifoPointerPerSet = new int[numSets];
        for (int i = 0; i < size; i++)
        {
            entries.add(null);
        }
    }

    private int setIndexFor(long fullKey)
    {
        return Math.floorMod(fullKey, numSets);
    }

    @Override
    public int getIndexComponentBits()
    {
        return Integer.numberOfTrailingZeros(numSets);
    }

    /** Flat {@code entries} index of way {@code way} within set {@code setIndex} (way-major layout). */
    public int slotFor(int setIndex, int way)
    {
        return way * numSets + setIndex;
    }

    /** Set a full lookup key maps to ({@code key % numSets}). */
    public int setIndexOf(long fullKey)
    {
        return setIndexFor(fullKey);
    }

    /**
     * Index of the way in {@code key}'s set that currently holds it as a valid entry, or {@code -1}
     * if the key is not cached (a miss). Used by the view to colour the hit/insert row.
     */
    public int wayHolding(long fullKey)
    {
        int set = setIndexFor(fullKey);
        long storedTag = toStoredTag(fullKey);
        for (int w = 0; w < entriesPerSet; w++)
        {
            TLBEntry entry = entries.get(slotFor(set, w));
            if (entry != null && entry.isValid() && entry.getTag() == storedTag)
            {
                return w;
            }
        }
        return -1;
    }

    @Override
    public TLBEntry lookup(long tag)
    {
        int set = setIndexFor(tag);
        long storedTag = toStoredTag(tag);
        for (int w = 0; w < entriesPerSet; w++)
        {
            TLBEntry entry = entries.get(slotFor(set, w));
            if (entry != null && entry.isValid() && entry.getTag() == storedTag)
            {
                return entry;
            }
        }
        return null;
    }

    @Override
    public TLBEntry insert(TLBEntry entry)
    {
        int setIdx = setIndexFor(entry.getTag());
        entry.setTag(toStoredTag(entry.getTag()));

        // First, look for any empty slot in this set
        for (int w = 0; w < entriesPerSet; w++)
        {
            int idx = slotFor(setIdx, w);
            if (entries.get(idx) == null)
            {
                entries.set(idx, entry);
                pushInsertion(null, entry, idx);
                return null;
            }
        }

        // Set is full: use dumb pointer eviction within the set
        int victimWay = fifoPointerPerSet[setIdx] % entriesPerSet;
        int victimIndex = slotFor(setIdx, victimWay);
        TLBEntry evicted = entries.get(victimIndex);
        entries.set(victimIndex, entry);
        int oldPointer = fifoPointerPerSet[setIdx];
        fifoPointerPerSet[setIdx]++;
        pushInsertion(evicted, entry, victimIndex, oldPointer);
        return evicted;
    }

    @Override
    protected void undoInsertionInternal(InsertionRecord record)
    {
        // Determine which set this position belongs to (way-major: set = position % numSets)
        int setIdx = record.position % numSets;

        if (record.evictedEntry != null)
        {
            // Eviction occurred: restore evicted entry and restore pointer to pre-eviction state
            entries.set(record.position, record.evictedEntry);
            fifoPointerPerSet[setIdx] = record.secondaryPosition;
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
        int setIdx = setIndexFor(tag);
        long storedTag = toStoredTag(tag);
        for (int w = 0; w < entriesPerSet; w++)
        {
            int idx = slotFor(setIdx, w);
            TLBEntry entry = entries.get(idx);
            if (entry != null && entry.getTag() == storedTag)
            {
                entries.set(idx, null);
                pushInvalidatedEntry(entry, idx);
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

    public int getEntriesPerSet()
    {
        return entriesPerSet;
    }

    public int getNumSets()
    {
        return numSets;
    }
}
