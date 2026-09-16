package rs.ac.bg.etf.model.os;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageOSMemoryManager extends OSMemoryManager
{
    public static record FrameMapping(int user, long page, PageTableDescriptor descriptor){}

    // Sorted (not a HashMap) so getFreeFrame()/allocateAndLock() can walk occupied frames in
    // ascending order to find gaps, rather than scanning every one of the (up to 2^24) frames.
    private TreeMap<Long, FrameMapping> allocatedFrames = new TreeMap<>();
    private HashSet<Long> lockedFrames = new HashSet<>();
    private long maxFrames;

    public PageOSMemoryManager(EvictionPolicy evictionPolicy, int numberOfUsers, long physicalAddressBits, long wordBits) {
        super(evictionPolicy, numberOfUsers);
        // 1L (not 1): an int shift is masked mod 32, so >= 31 frame bits would overflow / go negative.
        this.maxFrames = 1L << (physicalAddressBits - wordBits);
    }

    public void init(Map<PageTableDescriptor, Integer> descriptors)
    {
        allocatedFrames.clear();
        lockedFrames.clear();

        // Log allocations in a deterministic (ascending frame number) order so the
        // initial FIFO eviction order is well defined, rather than whatever order
        // the HashMap happens to iterate in.
        ArrayList<PageTableDescriptor> orderedDescriptors = new ArrayList<>(descriptors.keySet());
        orderedDescriptors.sort(Comparator.comparingLong(PageTableDescriptor::getBlock));

        for (PageTableDescriptor descriptor: orderedDescriptors)
        {
            int user = descriptors.get(descriptor);

            allocatedFrames.put(descriptor.getBlock(), new FrameMapping(user, descriptor.getPage(), descriptor));
            evictionPolicy.logAllocation(descriptor.getBlock());
        }
    }

    public long getFreeFrame()
    {
        if (lockedFrames.size() == maxFrames)
            throw new RuntimeException("All frames locked by kernel");

        if (allocatedFrames.size() == maxFrames)
            return -1;

        // Walk occupied frames in ascending order and return the first gap, rather than testing
        // every one of the (up to 2^24) frame indices individually.
        long expected = 0;
        for (long occupied : allocatedFrames.keySet())
        {
            if (occupied > expected)
                break;

            expected = occupied + 1;
        }

        return expected < maxFrames ? expected : -1;
    }

    public void allocate(long frame, int user, long page, PageTableDescriptor descriptor)
    {
        allocatedFrames.put(frame, new FrameMapping(user, page, descriptor));
        evictionPolicy.logAllocation(frame);
    }

    public void undoAllocation(long frame)
    {
        allocatedFrames.remove(frame);
        evictionPolicy.undoAllocation(frame);
    }

    public void free(long frame)
    {
        allocatedFrames.remove(frame);
    }

    public void undoFree(long frame, FrameMapping frameMapping)
    {
        allocatedFrames.put(frame, frameMapping);
    }

    public FrameMapping getFrameMapping(long frame)
    {
        return allocatedFrames.get(frame);
    }

    public long getMaxFrames()
    {
        return maxFrames;
    }

    /** True when the frame is pinned by the kernel (page-table storage) rather than free or user-allocated. */
    public boolean isLocked(long frame)
    {
        return lockedFrames.contains(frame);
    }

    /** Unmodifiable snapshot of the eviction order, oldest first (index 0 = next victim). */
    public List<Long> getReplacementOrder()
    {
        return evictionPolicy.getOrder();
    }

    public long allocateAndLock(long frames)
    {
        if (allocatedFrames.size() == maxFrames)
            throw new RuntimeException("More frames needed to allocate kernel structures");

        // Walk occupied frames in ascending order, looking for the first gap (before, between, or
        // after them) at least `frames` wide, instead of probing every one of the (up to 2^24)
        // candidate starting frames individually.
        long candidateStart = 0;
        for (long occupied : allocatedFrames.keySet())
        {
            if (occupied - candidateStart >= frames)
                break;

            candidateStart = occupied + 1;
        }

        if (candidateStart + frames > maxFrames)
        {
            throw new RuntimeException("External Fragmentation Error: Not enough consecutive free frames available to allocate kernel structures.");
        }

        for (long j = 0; j < frames; j++)
        {
            long currentFrame = candidateStart + j;
            allocatedFrames.put(currentFrame, null);
            lockedFrames.add(currentFrame);
        }

        return candidateStart;
    }
}
