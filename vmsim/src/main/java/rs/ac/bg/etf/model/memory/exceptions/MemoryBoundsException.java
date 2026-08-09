package rs.ac.bg.etf.model.memory.exceptions;

public class MemoryBoundsException extends RuntimeException
{
    public MemoryBoundsException(long address, long memorySize) 
    {
        super("Address " + Long.toHexString(address) + "is greater than physical memory size: " + Long.toHexString(memorySize) + ".");
    }
    
}
