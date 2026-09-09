package rs.ac.bg.etf.model.os;

import java.util.List;

public abstract class EvictionPolicy
{
    public abstract void logAllocation(long address);

    /** Peeks the frame that would be evicted next, without removing it. */
    public abstract long getVictim();

    /** Removes the frame last returned by {@link #getVictim()} from the policy's bookkeeping. */
    public abstract void removeVictim();

    public abstract void undoVictim(long address);
    public abstract void undoAllocation(long address);

    /** Unmodifiable snapshot of the replacement order, oldest first (index 0 = next victim). */
    public abstract List<Long> getOrder();
}
