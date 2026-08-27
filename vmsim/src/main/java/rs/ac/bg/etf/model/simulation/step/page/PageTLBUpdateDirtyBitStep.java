package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.TLBUpdateDirtyBitStep;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public class PageTLBUpdateDirtyBitStep<T extends PageSimulationContext> extends TLBUpdateDirtyBitStep<T> 
{

    public PageTLBUpdateDirtyBitStep(T context, TLBEntry tlbEntry) 
    {
        super(context, tlbEntry);
    }

    @Override
    public SimulationStep<T> nexStep() 
    {
        return new PageFormPhysicalAddressFromTLBStep<T>(context, tlbEntry);
    }
    
}
