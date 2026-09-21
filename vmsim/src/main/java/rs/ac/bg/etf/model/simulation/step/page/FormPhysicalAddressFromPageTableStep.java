package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.model.simulation.step.StepDescriptionKey;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class FormPhysicalAddressFromPageTableStep<T extends PageSimulationContext> extends SimulationStep<T>
{
    private PageTableDescriptor descriptor;
    private long previousPhysicalAddress;
    // Whose page table the block was read from, captured in execute() for the description.
    private int user;

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
        user = context.getCurrentInstruction().getUser();

        context.setCurrentPhysicalAddress(physicalAddress);
        context.getCurrentInstruction().setPhysicalAddress(physicalAddress);

        setAffectedComponents(SimulationComponent.MMU, SimulationComponent.MEMORY);
        return PageTLBUpdateStep.nextTlbStep(context, descriptor);
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
        // The block comes from the page table entry, the word straight from the virtual address.
        return new StepDescription(StepDescriptionKey.PHYSICAL_ADDRESS_FROM_PAGE_TABLE,
                context.getCurrentPhysicalAddress(), descriptor.getBlock(), descriptor.getPage(), user,
                context.getWordComponent());
    }
    
}
