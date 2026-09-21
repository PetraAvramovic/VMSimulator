package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class TLBUpdateStep<T extends SimulationContext> extends SimulationStep<T>
{
    // The entry this step put in the TLB, kept so the description can say where it went.
    private TLBEntry insertedEntry;

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
        insertedEntry = entry;

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
        // The new entry's block (and dirty bit) are copied from the page table entry the walk just read.
        return new StepDescription(StepDescriptionKey.TLB_INSERTED,
                context.getAddressComponent(), context.getCurrentInstruction().getUser(),
                describeTlbEntry(insertedEntry), insertedEntry.getBlock());
    }
}
