package rs.ac.bg.etf.model.tlb;

/**
 * Set-associative TLB: entries are grouped into sets of a fixed associativity.
 * A tag maps to a set via (tag % numSets); within a set, lookup is linear
 * and insertion uses strict hardware round-robin replacement (dumb pointer per set).
 * Empty slots are represented by null so the UI can render them as EMPTY.
 * Each set maintains its own hardware round-robin pointer that increments on eviction.
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
    public TLBEntry insert(TLBEntry entry)
    {
        int setIdx = setIndexFor(entry.getTag());
        int start = setStart(setIdx);
        int[] pointerForSet = fifoPointerPerSet;
        
        // First, look for any empty slot in this set
        for (int i = start; i < start + entriesPerSet; i++)
        {
            if (entries.get(i) == null)
            {
                entries.set(i, entry);
                pushInsertion(null, entry, i);
                return null;
            }
        }
        
        // Set is full: use dumb pointer eviction within the set
        int victimOffset = pointerForSet[setIdx] % entriesPerSet;
        int victimIndex = start + victimOffset;
        TLBEntry evicted = entries.get(victimIndex);
        entries.set(victimIndex, entry);
        int oldPointer = pointerForSet[setIdx];
        pointerForSet[setIdx]++;
        pushInsertion(evicted, entry, victimIndex, oldPointer);
        return evicted;
    }
    
    @Override
    protected void undoInsertionInternal(InsertionRecord record)
    {
        // Determine which set this position belongs to
        int setIdx = record.position / entriesPerSet;
        
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
        int start = setStart(setIdx);
        
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
