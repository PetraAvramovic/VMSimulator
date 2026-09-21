package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.DirectTLB;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class TLBEvictionStep<T extends SimulationContext> extends SimulationStep<T>
{
    protected TLBEntry evicted;
    private boolean wasDirty = false;
    // Where the evicted entry sits (flat index into the TLB's entries), for the description.
    private int evictedSlot;

    protected TLBEvictionStep(T context)
    {
        super(context);
    }

    public abstract long getTag();
    public abstract void writebackDirty();
    public abstract void undoWritebackDirty();
    public abstract SimulationStep<T> nextStep();

    /** The user whose page table entry received the evicted entry's dirty bit; valid once {@link #writebackDirty()} has run. */
    public abstract int getWritebackUser();

    /** The page whose page table entry received the evicted entry's dirty bit; valid once {@link #writebackDirty()} has run. */
    public abstract long getWritebackPage();

    @Override
    public SimulationStep<T> execute()
    {
        evicted = context.getTLB().evictForInsertion(getTag());
        evicted.setValid(false);

        if (evicted.isDirty())
        {
            wasDirty = true;
            evicted.setDirty(false);
            writebackDirty();
        }

        int index = context.getTLB().getEntries().indexOf(evicted);
        evictedSlot = index;
        context.setEvictedTlbEntry(evicted.getTag(), evicted.getBlock(), wasDirty, index);

        setAffectedComponents(wasDirty
                ? new SimulationComponent[] { SimulationComponent.TLB, SimulationComponent.MMU }
                : new SimulationComponent[] { SimulationComponent.TLB });
        return nextStep();
    }

    @Override
    public void undo()
    {
        evicted.setValid(true);
        evicted.setDirty(wasDirty);

        if (wasDirty)
            undoWritebackDirty();

        context.getTLB().undoEviction();
    }

    @Override
    public StepDescription getStepDescription()
    {
        boolean direct = context.getTLB() instanceof DirectTLB;

        // Which entry was given up, what it held, and the page (of which user) it makes room for.
        StepDescription where = describeTlbSlot(evictedSlot);
        long page = context.getAddressComponent();
        int user = context.getCurrentInstruction().getUser();

        if (wasDirty)
            return new StepDescription(
                    direct ? StepDescriptionKey.TLB_REPLACED_DIRTY : StepDescriptionKey.TLB_EVICTED_DIRTY,
                    where, evicted.getBlock(), page, user, getWritebackPage(), getWritebackUser());

        return new StepDescription(
                direct ? StepDescriptionKey.TLB_REPLACED : StepDescriptionKey.TLB_EVICTED,
                where, evicted.getBlock(), page, user);
    }
}
