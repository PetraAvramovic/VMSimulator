package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.TLBLookupStep;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public class PageTLBLookupStep<T extends PageSimulationContext> extends TLBLookupStep<T> 
{

    protected PageTLBLookupStep(T context) 
    {
        super(context);
        
    }

    @Override
    public SimulationStep<T> nextStep(TLBEntry entry)
    {
        if (entry != null)
            return new PageFormPhysicalAddressFromTLBStep<T>(context, entry);
        else
            return new PageTableLookupStep<T>(context);
    }
    
}
