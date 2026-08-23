package rs.ac.bg.etf.model.tlb;

/**
 * Set-associative TLB: entries are grouped into sets of a fixed associativity.
 * A tag maps to a set via (tag % numSets); within a set, lookup is linear
 * and insertion uses FIFO replacement when the set is full.
 * Empty slots are represented by null so the UI can render them as EMPTY.
 */
public class SetAssociativeTLB extends TLB
{
    private final int entriesPerSet;
    private final int numSets;
    
    public SetAssociativeTLB(int size, int addressBits, int processBits, int entriesPerSet)
    {
        super(size, addressBits, processBits);
        this.entriesPerSet = entriesPerSet;
        this.numSets = size / entriesPerSet;
        for (int i = 0; i < size; i++)
        {
            entries.add(null);
        }
    }
    
    private int setIndexFor(long tag)
    {
        return Math.floorMod(tag, numSets);
    }
    
    private int setStart(int setIndex)
    {
        return setIndex * entriesPerSet;
    }
    
    @Override
    public TLBEntry lookup(long tag)
    {
        int start = setStart(setIndexFor(tag));
        for (int i = start; i < start + entriesPerSet; i++)
        {
            TLBEntry entry = entries.get(i);
            if (entry != null && entry.isHit(tag))
            {
                return entry;
            }
        }
        return null;
    }
    
    @Override
    public void insert(TLBEntry entry)
    {
        int start = setStart(setIndexFor(entry.getTag()));
        for (int i = start; i < start + entriesPerSet; i++)
        {
            if (entries.get(i) == null)
            {
                entries.set(i, entry);
                return;
            }
        }
        // Set is full: FIFO eviction - shift left, insert as newest
        for (int i = start; i < start + entriesPerSet - 1; i++)
        {
            entries.set(i, entries.get(i + 1));
        }
        entries.set(start + entriesPerSet - 1, entry);
    }
    
    @Override
    public TLBEntry invalidateEntry(long tag)
    {
        int start = setStart(setIndexFor(tag));
        for (int i = start; i < start + entriesPerSet; i++)
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
    
    public int getEntriesPerSet()
    {
        return entriesPerSet;
    }
    
    public int getNumSets()
    {
        return numSets;
    }
}
