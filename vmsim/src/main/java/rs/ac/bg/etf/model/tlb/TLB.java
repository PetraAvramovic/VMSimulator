package rs.ac.bg.etf.model.tlb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * Abstract base class for TLB implementations.
 * Address-structure-agnostic: works with a generic address component
 * (page number, segment number, or combined value) and a process id,
 * combined into a single tag: tag = (processId << addressBits) | addressComponent.
 */
public abstract class TLB
{
    /**
     * Record of an invalidated entry with its original position.
     */
    protected static class InvalidationRecord
    {
        public final TLBEntry entry;
        public final int position;
        
        public InvalidationRecord(TLBEntry entry, int position)
        {
            this.entry = entry;
            this.position = position;
        }
    }
    protected int size;
    protected ArrayList<TLBEntry> entries;
    protected int addressBits;
    protected int processBits;
    protected Stack<InvalidationRecord> invalidationStack;
    
    public TLB(int size, int addressBits, int processBits)
    {
        this.size = size;
        this.entries = new ArrayList<>();
        this.addressBits = addressBits;
        this.processBits = processBits;
        this.invalidationStack = new Stack<>();
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
     * @return The entry that was invalidated, or null if no matching entry was found
     */
    public abstract TLBEntry invalidateEntry(long tag);
    
    /**
     * Restores an invalidated entry to its original position.
     * Called by undoInvalidation to restore entries with position awareness.
     * @param record The invalidation record containing the entry and its original position
     */
    protected abstract void restoreInvalidatedEntry(InvalidationRecord record);
    
    /**
     * Undoes the last invalidation by popping from the invalidation stack and restoring.
     * @return true if an undo was performed, false if the stack was empty
     */
    public boolean undoInvalidation()
    {
        if (invalidationStack.isEmpty())
        {
            return false;
        }
        InvalidationRecord record = invalidationStack.pop();
        restoreInvalidatedEntry(record);
        return true;
    }
    
    /**
     * Protected helper to push an invalidated entry onto the undo stack with its position.
     * Called by subclasses in their invalidateEntry implementations.
     * @param entry The entry that was invalidated
     * @param position Position-specific data (meaning depends on subclass: index for associative, slot for direct, etc.)
     */
    protected void pushInvalidatedEntry(TLBEntry entry, int position)
    {
        if (entry != null)
        {
            invalidationStack.push(new InvalidationRecord(entry, position));
        }
    }
    
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
