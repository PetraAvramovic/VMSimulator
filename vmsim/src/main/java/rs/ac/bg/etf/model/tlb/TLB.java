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
    
    /**
     * Record of a replacement pointer advanced ahead of an insertion, for undo support.
     * evictForInsertion() never removes the entry it evicts from {@code entries} (the caller marks
     * it invalid in place instead, leaving its other fields inspectable), so there is nothing to
     * restore here except whichever hardware replacement pointer it advanced.
     */
    protected static class EvictionRecord
    {
        public final int setIndex;
        public final int pointerValue;

        public EvictionRecord(int setIndex, int pointerValue)
        {
            this.setIndex = setIndex;
            this.pointerValue = pointerValue;
        }
    }

    /**
     * Record of an insertion that caused eviction, for undo support.
     */
    protected static class InsertionRecord
    {
        public final TLBEntry evictedEntry; // null if no eviction occurred
        public final TLBEntry insertedEntry;
        public final int position; // position-specific data
        public final int secondaryPosition; // for set-associative shift operations
        
        public InsertionRecord(TLBEntry evictedEntry, TLBEntry insertedEntry, int position)
        {
            this(evictedEntry, insertedEntry, position, -1);
        }
        
        public InsertionRecord(TLBEntry evictedEntry, TLBEntry insertedEntry, int position, int secondaryPosition)
        {
            this.evictedEntry = evictedEntry;
            this.insertedEntry = insertedEntry;
            this.position = position;
            this.secondaryPosition = secondaryPosition;
        }
    }
    protected int size;
    protected ArrayList<TLBEntry> entries;
    protected int addressBits;
    protected int processBits;
    protected Stack<InvalidationRecord> invalidationStack;
    protected Stack<InsertionRecord> insertionStack;
    protected Stack<EvictionRecord> evictionStack;

    public TLB(int size, int addressBits, int processBits)
    {
        this.size = size;
        this.entries = new ArrayList<>();
        this.addressBits = addressBits;
        this.processBits = processBits;
        this.invalidationStack = new Stack<>();
        this.insertionStack = new Stack<>();
        this.evictionStack = new Stack<>();
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
     * @return The evicted entry if eviction occurred, null otherwise
     */
    public abstract TLBEntry insert(TLBEntry entry);
    
    /**
     * Invalidates the entry with the matching tag, if present.
     * @param tag The tag to invalidate
     * @return The entry that was invalidated, or null if no matching entry was found
     */
    public abstract TLBEntry invalidateEntry(long tag);

    /**
     * Checks, without mutating anything, whether inserting an entry for the given tag would have
     * to evict an existing entry (i.e. the relevant slot/set is currently full).
     * @param tag The tag that would be inserted
     * @return true if insertion would require an eviction first
     */
    public abstract boolean wouldEvict(long tag);

    /**
     * Evicts whichever entry currently occupies the slot an insertion for the given tag would
     * target, ahead of that insertion actually happening. Only meaningful to call when
     * {@link #wouldEvict} just returned true for the same tag. Marks nothing itself -- the caller
     * is expected to invalidate the returned entry (set its own valid/dirty fields) in place; the
     * entry stays in {@code entries} at its original slot the whole time, still inspectable (tag,
     * block, ...), and {@code insert} treats an invalid-but-present slot as free.
     * @param tag The tag the upcoming insertion targets
     * @return The entry occupying the target slot
     */
    public abstract TLBEntry evictForInsertion(long tag);

    /**
     * Restores whichever hardware replacement pointer evictForInsertion advanced. Called by
     * undoEviction to undo evictForInsertion; a no-op for a TLB flavour with no such pointer.
     * @param record The eviction record identifying which pointer to restore, and to what value
     */
    protected abstract void restoreEvictionPointer(EvictionRecord record);
    
    /**
     * Restores an invalidated entry to its original position.
     * Called by undoInvalidation to restore entries with position awareness.
     * @param record The invalidation record containing the entry and its original position
     */
    protected abstract void restoreInvalidatedEntry(InvalidationRecord record);
    
    /**
     * Removes an inserted entry and optionally restores an evicted entry.
     * Called by undoInsertion to undo insertions.
     * @param record The insertion record containing evicted and inserted entries with positions
     */
    protected abstract void undoInsertionInternal(InsertionRecord record);
    
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
     * Undoes the last insertion by popping from the insertion stack and reversing the operation.
     * @return true if an undo was performed, false if the stack was empty
     */
    public boolean undoInsertion()
    {
        if (insertionStack.isEmpty())
        {
            return false;
        }
        InsertionRecord record = insertionStack.pop();
        undoInsertionInternal(record);
        return true;
    }

    /**
     * Undoes the last evictForInsertion call by popping from the eviction stack and restoring.
     * @return true if an undo was performed, false if the stack was empty
     */
    public boolean undoEviction()
    {
        if (evictionStack.isEmpty())
        {
            return false;
        }
        EvictionRecord record = evictionStack.pop();
        restoreEvictionPointer(record);
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
     * Protected helper to push an insertion record with eviction info onto the undo stack.
     * @param evictedEntry The entry that was evicted (null if no eviction)
     * @param insertedEntry The entry that was inserted
     * @param position Position info for restoration
     */
    protected void pushInsertion(TLBEntry evictedEntry, TLBEntry insertedEntry, int position)
    {
        insertionStack.push(new InsertionRecord(evictedEntry, insertedEntry, position));
    }
    
    /**
     * Protected helper to push an insertion record with additional position info (for set-associative).
     * @param evictedEntry The entry that was evicted (null if no eviction)
     * @param insertedEntry The entry that was inserted
     * @param position Primary position info for restoration
     * @param secondaryPosition Secondary position info (e.g., set start for set-associative)
     */
    protected void pushInsertion(TLBEntry evictedEntry, TLBEntry insertedEntry, int position, int secondaryPosition)
    {
        insertionStack.push(new InsertionRecord(evictedEntry, insertedEntry, position, secondaryPosition));
    }

    /**
     * Protected helper to push an eviction-for-insertion pointer record onto the undo stack.
     * @param setIndex Which set's pointer advanced (unused / -1 for a TLB with a single, whole-table pointer)
     * @param pointerValue The pointer's pre-eviction value
     */
    protected void pushEviction(int setIndex, int pointerValue)
    {
        evictionStack.push(new EvictionRecord(setIndex, pointerValue));
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

    /**
     * Number of low-order bits of the full lookup key that select the row/set rather than being
     * stored in the tag. Zero for a fully-associative TLB, where the whole key is the tag.
     * @return the index/set bit count
     */
    public int getIndexComponentBits()
    {
        return 0;
    }

    /**
     * Reduces a full lookup key (as returned by {@link #calculateTag}) to the value actually stored
     * in and compared against {@link TLBEntry#getTag()}. Identity for a fully-associative TLB.
     * @param fullKey The full lookup key
     * @return The stored-tag value
     */
    protected long toStoredTag(long fullKey)
    {
        return fullKey >>> getIndexComponentBits();
    }

    /**
     * The single row a full lookup key deterministically maps to (direct-mapped TLB), or -1 when the
     * key does not resolve to exactly one row (fully- or set-associative).
     * @param fullKey The full lookup key
     * @return The mapped row index, or -1
     */
    public int mappedSlot(long fullKey)
    {
        return -1;
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
