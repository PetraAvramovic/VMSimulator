package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.model.simulation.step.StepDescriptionKey;

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

        setAffectedComponents(SimulationComponent.MMU);
        return new PageTableLookupStep<T>(context);
    }

    @Override
    public void undo() 
    {
        
    }

    @Override
    public StepDescription getStepDescription()
    {
        // Address = the user's page table pointer + an offset that is the page number times the size of
        // one entry (the page number shifted left by log2 of it).
        return new StepDescription(StepDescriptionKey.PAGE_TABLE_ADDRESS_FORMED,
                context.getCurrentDescriptorAddress(), context.getCurrentInstruction().getUser(),
                context.getCurrentPTP(), context.getCurrentDescriptorOffset());
    }

}
