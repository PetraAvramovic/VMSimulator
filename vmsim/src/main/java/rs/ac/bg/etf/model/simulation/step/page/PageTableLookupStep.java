package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.memory.Instruction.AccessType;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.model.simulation.step.StepDescriptionKey;
import rs.ac.bg.etf.model.table.PageTable;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageTableLookupStep<T extends PageSimulationContext> extends SimulationStep<T>
{
    private PageTableDescriptor lookupResult;
    // Whose page table the entry was read from, captured in execute() for the description.
    private int lookupUser;

    protected PageTableLookupStep(T context)
    {
        super(context);
    }

    @Override
    public SimulationStep<T> execute()
    {
        Instruction currentInstruction = context.getCurrentInstruction();
        PageTable pageTable = context.getPageTable(currentInstruction.getUser());

        PageTableDescriptor desc = pageTable.getEntryAndAdd(context.getPageComponent());
        lookupResult = desc;
        lookupUser = currentInstruction.getUser();

        setAffectedComponents(SimulationComponent.MMU);
        if (desc.isValid())
            if (!desc.isDirty() && currentInstruction.getAccessType() == AccessType.WR)
                return new PageTableUpdateDirtyBitStep<T>(context, desc);
            else
                return new FormPhysicalAddressFromPageTableStep<T>(context, desc);
        else
            return new PageFaultStep<T>(context, desc);
    }

    @Override
    public void undo() 
    {
        
    }

    @Override
    public StepDescription getStepDescription()
    {
        if (!lookupResult.isValid())
            return new StepDescription(StepDescriptionKey.PAGE_TABLE_LOOKUP_FAULT,
                    lookupResult.getPage(), lookupUser);

        return lookupResult.isDirty()
                ? new StepDescription(StepDescriptionKey.PAGE_TABLE_LOOKUP_HIT_DIRTY,
                        lookupResult.getBlock(), lookupResult.getPage(), lookupUser)
                : new StepDescription(StepDescriptionKey.PAGE_TABLE_LOOKUP_HIT,
                        lookupResult.getBlock(), lookupResult.getPage(), lookupUser);
    }

}
