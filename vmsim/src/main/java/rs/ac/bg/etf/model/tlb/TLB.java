package rs.ac.bg.etf.model.tlb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for TLB implementations.
 * Address-structure-agnostic: works with a generic address component
 * (page number, segment number, or combined value) and a process id,
 * combined into a single tag: tag = (processId << addressBits) | addressComponent.
 */
public abstract class TLB
{
    protected int size;
    protected ArrayList<TLBEntry> entries;
    protected int addressBits;
    protected int processBits;
    
    public TLB(int size, int addressBits, int processBits)
    {
        this.size = size;
        this.entries = new ArrayList<>();
        this.addressBits = addressBits;
        this.processBits = processBits;
    }
    
    /**
     * Looks up an entry by tag.
     * @param tag The tag to search for
     * @return The matching entry, or null on miss
     */
    public abstract TLBEntry lookup(long tag);
    
    /**
     * Inserts a new entry, replacing an existing one if the TLB (or relevant slot/set) is full.
     * @param entry The entry to insert
     */
    public abstract void insert(TLBEntry entry);
    
    /**
     * Invalidates the entry with the matching tag, if present.
     * @param tag The tag to invalidate
     */
    public abstract void invalidateTag(long tag);
    
    /**
     * Checks if an entry with the given tag exists in the TLB.
     * @param tag The tag to search for
     * @return true if an entry with the tag exists, false otherwise
     */
    public boolean isHit(long tag)
    {
        return lookup(tag) != null;
    }
    public void flushProcessTag(int processId)
    {
        entries.removeIf(entry -> entry != null && (entry.getTag() >>> addressBits) == processId);
    }
    
    /**
     * Combines a process id and an address component into a single tag.
     * @param processId The process id
     * @param addressComponent The address component (page number, segment number, etc.)
     * @return The combined tag
     */
    public long calculateTag(int processId, long addressComponent)
    {
        return (processId << addressBits) | addressComponent;
    }
    
    public List<TLBEntry> getEntries()
    {
        return Collections.unmodifiableList(entries);
    }
    
    public int getSize()
    {
        return size;
    }
    
    public int getAddressComponentBits()
    {
        return addressBits;
    }
    
    public int getProcessIdBits()
    {
        return processBits;
    }
}
