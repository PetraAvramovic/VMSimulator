package rs.ac.bg.etf.model.os;

public abstract class EvictionPolicy 
{
    public abstract void logAllocation(long address);
    public abstract long getVictim();
    public abstract void undoVictim(long address);
    public abstract void undoAllocation(long address);
}
