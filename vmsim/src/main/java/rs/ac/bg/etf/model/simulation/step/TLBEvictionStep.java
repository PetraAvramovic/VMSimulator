package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.DirectTLB;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class TLBEvictionStep<T extends SimulationContext> extends SimulationStep<T>
{
    protected TLBEntry evicted;
    private boolean wasDirty = false;

    protected TLBEvictionStep(T context)
    {
        super(context);
    }

    public abstract long getTag();
    public abstract void writebackDirty();
    public abstract void undoWritebackDirty();
    public abstract SimulationStep<T> nextStep();

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

        if (wasDirty)
            return new StepDescription(
                    direct ? StepDescriptionKey.TLB_REPLACED_DIRTY : StepDescriptionKey.TLB_EVICTED_DIRTY,
                    evicted.getBlock());

        return new StepDescription(
                direct ? StepDescriptionKey.TLB_REPLACED : StepDescriptionKey.TLB_EVICTED,
                evicted.getBlock());
    }
}
