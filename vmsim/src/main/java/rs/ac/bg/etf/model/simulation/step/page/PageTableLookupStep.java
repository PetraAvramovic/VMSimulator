package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.memory.Instruction.AccessType;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.table.PageTable;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageTableLookupStep<T extends PageSimulationContext> extends SimulationStep<T>
{
    private PageTableDescriptor lookupResult;

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
    public String getDescription()
    {
        if (!lookupResult.isValid())
            return String.format("Page table lookup: page %d not valid (page fault).", lookupResult.getPage());

        return String.format("Page table lookup: page %d -> frame 0x%X%s.",
                lookupResult.getPage(), lookupResult.getBlock(), lookupResult.isDirty() ? " (dirty)" : "");
    }

}
