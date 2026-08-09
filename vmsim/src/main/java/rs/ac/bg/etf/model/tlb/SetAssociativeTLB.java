package rs.ac.bg.etf.model.tlb;

/**
 * Set-associative TLB: entries are grouped into sets of a fixed associativity.
 * A tag maps to a set via (tag % numSets); within a set, lookup is linear
 * and insertion uses FIFO replacement when the set is full.
 * Empty slots are represented by null so the UI can render them as EMPTY.
 *
 * @param <E> the type of TLB entry stored
 */
public class SetAssociativeTLB<E extends TLBEntry> extends TLB<E>
{
    private final int associativity;
    private final int numSets;
    
    public SetAssociativeTLB(int size, int addressBits, int processBits, int associativity)
    {
        super(size, addressBits, processBits);
        this.associativity = associativity;
        this.numSets = size / associativity;
        for (int i = 0; i < size; i++)
        {
            entries.add(null);
        }
    }
    
    private int setIndexFor(int tag)
    {
        return Math.floorMod(tag, numSets);
    }
    
    private int setStart(int setIndex)
    {
        return setIndex * associativity;
    }
    
    @Override
    public E lookup(int tag)
    {
        int start = setStart(setIndexFor(tag));
        for (int i = start; i < start + associativity; i++)
        {
            E entry = entries.get(i);
            if (entry != null && entry.isHit(tag))
            {
                return entry;
            }
        }
        return null;
    }
    
    @Override
    public void insert(E entry)
    {
        int start = setStart(setIndexFor(entry.getTag()));
        for (int i = start; i < start + associativity; i++)
        {
            if (entries.get(i) == null)
            {
                entries.set(i, entry);
                return;
            }
        }
        // Set is full: FIFO eviction - shift left, insert as newest
        for (int i = start; i < start + associativity - 1; i++)
        {
            entries.set(i, entries.get(i + 1));
        }
        entries.set(start + associativity - 1, entry);
    }
    
    @Override
    public void invalidateTag(int tag)
    {
        int start = setStart(setIndexFor(tag));
        for (int i = start; i < start + associativity; i++)
        {
            E entry = entries.get(i);
            if (entry != null && entry.getTag() == tag)
            {
                entries.set(i, null);
                return;
            }
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
    
    public int getAssociativity()
    {
        return associativity;
    }
    
    public int getNumSets()
    {
        return numSets;
    }
}
