package rs.ac.bg.etf.model.os;

import java.util.HashMap;
import java.util.HashSet;


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

    public long allocateAndLock()
    {
        if (allocatedFrames.size() == maxFrames)
            throw new RuntimeException("More frames needed to allocate kernel structures");
        
        long frame = -1;

        for (long i = 0; i < maxFrames; i++)
        {
            if (!allocatedFrames.containsKey(i))
            {
                frame = i;
                break;
            }
        }

        allocatedFrames.put(frame, null);
        lockedFrames.add(frame);

        return frame;
    }
}
