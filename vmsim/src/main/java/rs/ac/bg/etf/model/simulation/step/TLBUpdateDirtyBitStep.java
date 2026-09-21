package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class TLBUpdateDirtyBitStep<T extends SimulationContext> extends SimulationStep<T> 
{
    protected TLBEntry tlbEntry;

    public TLBUpdateDirtyBitStep(T context, TLBEntry tlbEntry) 
    {
        super(context);
        this.tlbEntry = tlbEntry;
    }

    public abstract SimulationStep<T> nexStep();

    @Override
    public SimulationStep<T> execute() 
    {
        tlbEntry.setDirty(true);

        setAffectedComponents(SimulationComponent.TLB);
        return nexStep();
    }

    @Override
    public void undo() 
    {
        tlbEntry.setDirty(false);
    }

    @Override
    public StepDescription getStepDescription()
    {
        return new StepDescription(StepDescriptionKey.TLB_DIRTY_BIT_UPDATED, describeTlbEntry(tlbEntry));
    }

    
}
