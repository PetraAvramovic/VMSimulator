package rs.ac.bg.etf.model.os;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageOSMemoryManager extends OSMemoryManager
{
    public static record FrameMapping(int user, long page, PageTableDescriptor descriptor){}

    private HashMap<Long, FrameMapping> allocatedFrames = new HashMap<>();
    private HashSet<Long> lockedFrames = new HashSet<>();
    private long maxFrames;

    public PageOSMemoryManager(EvictionPolicy evictionPolicy, int numberOfUsers, long physicalAddressBits, long wordBits) {
        super(evictionPolicy, numberOfUsers);
        this.maxFrames = 1 << (physicalAddressBits - wordBits);
    }

    public void init(Map<PageTableDescriptor, Integer> descriptors)
    {
        allocatedFrames.clear();
        lockedFrames.clear();
        
        for (PageTableDescriptor descriptor: descriptors.keySet())
        {
            int user = descriptors.get(descriptor);

            allocatedFrames.put(descriptor.getBlock(), new FrameMapping(user, descriptor.getPage(), descriptor));
        }
    }

    public long getFreeFrame() 
    {
        if (lockedFrames.size() == maxFrames)
            throw new RuntimeException("All frames locked by kernel");

        if (allocatedFrames.size() == maxFrames)
            return -1;

       long frame = -1;

        for (long i = 0; i < maxFrames; i++)
        {
            if (!allocatedFrames.containsKey(i))
            {
                frame = i;
                break;
            }
        }

        return frame;
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
        
        for (long i = 0; i < maxFrames - frames; i++)
        {
            boolean found = true;

            for (long j = 0; j < frames; j++) 
            {
                if (allocatedFrames.containsKey(i + j)) 
                {
                    found = false;
                    i = i + j; 
                    break;
                }
            }

            if (found)
            {
                for (long j = 0; j < frames; j++) 
                {
                    long currentFrame = i + j;
                    allocatedFrames.put(currentFrame, null);
                    lockedFrames.add(currentFrame);
                }

                
                return i; 
            }
        }

        throw new RuntimeException("External Fragmentation Error: Not enough consecutive free frames available to allocate kernel structures.");
    }
}
