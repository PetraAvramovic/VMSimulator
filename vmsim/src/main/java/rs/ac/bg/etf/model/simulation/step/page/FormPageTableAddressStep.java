package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;

public class FormPageTableAddressStep<T extends PageSimulationContext> extends SimulationStep<T> 
{

    protected FormPageTableAddressStep(T context) 
    {
        super(context);
    }

    @Override
    public SimulationStep<T> execute() 
    {
        long ptp = context.getCurrentPTP();

        long offset = context.getCurrentDescriptorOffset();

        context.setCurrentDescriptorAddress(ptp + offset);

        return new PageTableLookupStep<T>(context);
    }

    @Override
    public void undo() 
    {
        
    }

    @Override
    public String getDescription()
    {
        return String.format("Computed page table entry address: 0x%X (pointer 0x%X + offset 0x%X).",
                context.getCurrentDescriptorAddress(), context.getCurrentPTP(), context.getCurrentDescriptorOffset());
    }

}
