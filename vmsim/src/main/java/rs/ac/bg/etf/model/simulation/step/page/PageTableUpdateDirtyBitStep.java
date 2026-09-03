package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageTableUpdateDirtyBitStep<T extends PageSimulationContext> extends SimulationStep<T> 
{
    private PageTableDescriptor descriptor;

    public PageTableUpdateDirtyBitStep(T context, PageTableDescriptor descriptor) 
    {
        super(context);
        this.descriptor = descriptor;
    }

    @Override
    public SimulationStep<T> execute() 
    {
        descriptor.setDirty(true);

        return new FormPhysicalAddressFromPageTableStep<T>(context, descriptor);
    }

    @Override
    public void undo() 
    {
        descriptor.setDirty(false);
    }

    @Override
    public String getDescription()
    {
        return String.format("Set dirty bit for page %d in page table.", descriptor.getPage());
    }

    
}
