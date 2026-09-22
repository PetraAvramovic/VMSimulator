package rs.ac.bg.etf.model.memory;

import rs.ac.bg.etf.model.memory.exceptions.*;

import java.util.SortedMap;
import java.util.TreeMap;


public class Memory 
{
    private TreeMap<Long, Long> memory = new TreeMap<>();
    private long memorySize;

    public Memory(long memorySize) 
    {
        this.memorySize = memorySize;
    }

    public void init(SortedMap<Long,Long> memoryInit)
    {
        if (memoryInit == null)
            return;

        memory.clear();
        memory.putAll(memoryInit);
    }

    private void checkAddress(long address)
    {
        if (Long.compareUnsigned(address, memorySize) >= 0)
            throw new MemoryBoundsException(address, memorySize);
    }

    public long read(long address)
    {
        checkAddress(address);

        if (!memory.containsKey(address))
            return 0L;
        else
            return memory.get(address);
    }

    public void write(long address, long value)
    {
        checkAddress(address);

        if (!memory.containsKey(address))
            memory.put(address, 0L);

        memory.put(address, value);
    }

    public void execute(long address)
    {
        checkAddress(address);
    }

    private TreeMap<Long, Long> remap(long startAddress, SortedMap<Long, Long> block, boolean absolute)
    {
        TreeMap<Long, Long> remapped = new TreeMap<>();

        for (Long address: block.keySet())
        {
            long newAddress;
            if (absolute)
                newAddress = startAddress + address;
            else
                newAddress = address - startAddress;

            remapped.put(newAddress, block.get(address));
        }

        return remapped;
    }

    public SortedMap<Long, Long> readBlock(long address, long size)
    {
        return remap(address, memory.subMap(address, address + size), false);
    }

    /**
     * Replaces the whole {@code [address, address + size)} range with {@code block}: every
     * existing entry in that range is cleared first, not merged into. {@code block} is sparse
     * (an absent offset reads as 0, see {@link #read}), so a plain {@code putAll} would only ever
     * overwrite the offsets {@code block} happens to list and leave whatever was already there at
     * every other offset in the range untouched -- e.g. a page loaded into a frame that previously
     * held different (or more) data, or {@code previousBlock} on
     * {@link rs.ac.bg.etf.model.simulation.step.page.PageLoadIntoMemoryStep#undo()} restoring a
     * frame to a state with fewer entries than what is currently written there, would both leave
     * stale values behind instead of actually reverting.
     */
    public void writeBlock(long address, SortedMap<Long, Long> block, long size)
    {
        memory.subMap(address, address + size).clear();
        memory.putAll(remap(address, block, true));
    }
}
