package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public abstract class FormPhysicalAddressFromTLBStep<T extends SimulationContext> extends SimulationStep<T>
{
    protected TLBEntry entry;
    protected long previousPhysicalAddress;

    protected FormPhysicalAddressFromTLBStep(T context, TLBEntry entry) 
    {
        super(context);
        this.entry = entry;
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
        // The block comes from the TLB entry, the word straight from the virtual address.
        return new StepDescription(StepDescriptionKey.PHYSICAL_ADDRESS_FROM_TLB,
                context.getCurrentPhysicalAddress(), entry.getBlock(), describeTlbEntry(entry),
                context.getWordComponent());
    }
}
