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

    public void writeBlock(long address, SortedMap<Long, Long> block)
    {
        block = remap(address, block, true);
        memory.putAll(block);
    }
}
