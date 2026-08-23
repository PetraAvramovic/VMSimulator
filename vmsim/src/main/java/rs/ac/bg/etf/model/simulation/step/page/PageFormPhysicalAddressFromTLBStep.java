package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.FormPhysicalAddressFromTLBStep;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public class PageFormPhysicalAddressFromTLBStep<T extends PageSimulationContext> extends  FormPhysicalAddressFromTLBStep<T>
{

    protected PageFormPhysicalAddressFromTLBStep(T context, TLBEntry entry) 
    {
        super(context, entry);
    }

    @Override
    public SimulationStep<T> execute() 
    {
        long physicalAddress = (entry.getBlock() << context.getWordBits()) | context.getWordBits();
        previousPhysicalAddress = context.getCurrentPhysicalAddress();
        
        context.setCurrentPhysicalAddress(physicalAddress);
        context.getCurrentInstruction().setPhysicalAddress(physicalAddress);

        return new PageMemoryAccessStep<T>(context);
    }

}
