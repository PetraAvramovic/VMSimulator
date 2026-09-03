package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
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
    public String getDescription()
    {
        return String.format("Formed physical address 0x%X from page table (frame 0x%X).",
                context.getCurrentPhysicalAddress(), descriptor.getBlock());
    }
    
}
