package rs.ac.bg.etf.model.memory;

import rs.ac.bg.etf.model.memory.exceptions.*;
import rs.ac.bg.etf.model.simulation.SimulationConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

/**
 * MemoryPair
long address, long value */
record MemoryPair(long address, long value) {
}

public class Memory 
{
    private HashMap<Long, Long> memory = new HashMap<>();
    private Stack<MemoryPair> writes = new Stack<>();
    private long memorySize;

    public Memory(long memorySize) 
    {
        this.memorySize = memorySize;
    }

    public void init(ArrayList<SimulationConfig.MemoryInitializationBlock> memoryInit)
    {
        if (memoryInit == null)
            return;

        for (SimulationConfig.MemoryInitializationBlock block : memoryInit)
        {
            long address = block.startAddress();
            for (Long value : block.data())
            {
                memory.put(address, value);
                address++;
            }
        }
    }

    public void restart()
    {
        while (!writes.empty())
            undoWrite();
    }

    private void checkAddress(long address)
    {
        if (Long.compareUnsigned(address, memorySize) >= 0)
            throw new MemoryBoundsException(address, memorySize);
    }

    public void executeInstruction(Instruction instruction)
    {
        long address = instruction.getAddress();
        long value = instruction.getValue();
        Instruction.AccessType accessType = instruction.getAccessType();

        checkAddress(address);

        Long memoryValue = memory.get(address);
        if (memoryValue == null)
            memoryValue = 0L;

        if (accessType == Instruction.AccessType.RD)
        {
            instruction.setValue(memoryValue);
        }
        else if (accessType == Instruction.AccessType.WR)
        {
            writes.push(new MemoryPair(address, memoryValue));
            memory.put(address, value);
        }
    }

    public void undoWrite()
    {
        MemoryPair pair = writes.pop();
        checkAddress(pair.address());

        memory.put(pair.address(), pair.value());
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

        long memoryValue = memory.get(address);
        writes.push(new MemoryPair(address, memoryValue));
        memory.put(address, value);
    }

    public void execute(long address)
    {
        checkAddress(address);
    }
}
