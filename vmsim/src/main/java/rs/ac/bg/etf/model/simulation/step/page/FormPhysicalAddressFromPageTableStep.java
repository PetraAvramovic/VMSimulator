package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.model.simulation.step.StepDescriptionKey;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class FormPhysicalAddressFromPageTableStep<T extends PageSimulationContext> extends SimulationStep<T>
{
    private PageTableDescriptor descriptor;
    private long previousPhysicalAddress;

    protected FormPhysicalAddressFromPageTableStep(T context, PageTableDescriptor descriptor) 
    {
        super(context);
        this.descriptor = descriptor;
    }
    @Override
    public SimulationStep<T> execute() 
    {
        int wordBits = context.getWordBits();
        long word = context.getWordComponent();
        long block = descriptor.getBlock();
        long physicalAddress = (block << wordBits) | word;

        previousPhysicalAddress = context.getCurrentPhysicalAddress();

        context.setCurrentPhysicalAddress(physicalAddress);
        context.getCurrentInstruction().setPhysicalAddress(physicalAddress);
        
        return new PageTLBUpdateStep<T>(context, descriptor);
    }
    @Override
    public void undo() 
    {
        context.setCurrentPhysicalAddress(previousPhysicalAddress);
        context.getCurrentInstruction().setPhysicalAddress(-1);
    }

    @Override
    public StepDescription getStepDescription()
    {
        return new StepDescription(StepDescriptionKey.PHYSICAL_ADDRESS_FROM_PAGE_TABLE,
                context.getCurrentPhysicalAddress(), descriptor.getBlock());
    }
    
}
