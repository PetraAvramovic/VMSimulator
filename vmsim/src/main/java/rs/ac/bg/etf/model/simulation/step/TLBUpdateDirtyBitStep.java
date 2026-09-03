package rs.ac.bg.etf.model.simulation.step;

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

        return nexStep();
    }

    @Override
    public void undo() 
    {
        tlbEntry.setDirty(false);
    }

    @Override
    public String getDescription()
    {
        return "Updated dirty bit in existing TLB entry.";
    }

    
}
