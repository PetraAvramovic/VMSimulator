package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.TLB;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class TLBUpdateStep<T extends SimulationContext> extends SimulationStep<T> 
{
    protected TLBEntry evicted;
    private boolean wasDirty = false;

    protected TLBUpdateStep(T context) 
    {
        super(context);
    }
    
    public abstract TLBEntry getTLBEntry();
    public abstract void writebackDirty();
    public abstract SimulationStep<T> nextStep();

    @Override
    public SimulationStep<T> execute() 
    {
        TLBEntry entry = getTLBEntry();
        TLB tlb = context.getTLB();
       
        evicted = tlb.insert(entry);

        if (evicted != null)
        {
            evicted.setValid(false);
            if (evicted.isDirty())
            {
                wasDirty = true;
                evicted.setDirty(false);
                writebackDirty();
        
            }
        } 

        return nextStep();
    }

    @Override
    public void undo() 
    {
       TLB tlb = context.getTLB();

        if (evicted != null)
        {
            evicted.setValid(true);
            evicted.setDirty(wasDirty);
        }
            
       tlb.undoInsertion();
    }

    
}
