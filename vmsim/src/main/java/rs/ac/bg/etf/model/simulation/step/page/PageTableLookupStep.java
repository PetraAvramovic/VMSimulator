package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.memory.Instruction.AccessType;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.table.PageTable;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageTableLookupStep<T extends PageSimulationContext> extends SimulationStep<T>
{

    protected PageTableLookupStep(T context) 
    {
        super(context);
    }

    @Override
    public SimulationStep<T> execute() 
    {
        Instruction currentInstruction = context.getCurrentInstruction();
        PageTable pageTable = context.getPageTable(currentInstruction.getUser());

        PageTableDescriptor desc = pageTable.getEntry(context.getPageComponent());

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

}
