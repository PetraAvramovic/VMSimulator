package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class TLBUpdateStep<T extends SimulationContext> extends SimulationStep<T>
{
    protected TLBUpdateStep(T context)
    {
        super(context);
    }

    public abstract TLBEntry getTLBEntry();
    public abstract SimulationStep<T> nextStep();

    @Override
    public SimulationStep<T> execute()
    {
        TLBEntry entry = getTLBEntry();
        context.getTLB().insert(entry);

        setAffectedComponents(SimulationComponent.TLB);
        return nextStep();
    }

    @Override
    public void undo()
    {
        context.getTLB().undoInsertion();
    }

    @Override
    public StepDescription getStepDescription()
    {
        return new StepDescription(StepDescriptionKey.TLB_INSERTED);
    }
}
