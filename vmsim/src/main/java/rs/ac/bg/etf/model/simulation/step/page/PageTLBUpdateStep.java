package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.TLBUpdateStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public class PageTLBUpdateStep<T extends PageSimulationContext> extends TLBUpdateStep<T> 
{
    private PageTableDescriptor descriptor;
    private PageTableDescriptor evictedDescriptor;
    private boolean descriptorWasDirty = false;

    public PageTLBUpdateStep(T context, PageTableDescriptor descriptor) 
    {
        super(context);
        this.descriptor = descriptor;
    }

    @Override
    public TLBEntry getTLBEntry() 
    {
        Instruction instruction = context.getCurrentInstruction();
        int user = instruction.getUser();
        long tag = context.getTLB().calculateTag(user, context.getPageComponent());
        TLBEntry entry = new TLBEntry(tag, true, descriptor.isDirty(), descriptor.getBlock());

        return entry;
    }

    @Override
    public void writebackDirty() 
    {
        PageOSMemoryManager memoryManager = context.getOSMemoryManager();
        evictedDescriptor = memoryManager.getFrameMapping(evicted.getBlock()).descriptor();

        descriptorWasDirty = evictedDescriptor.isDirty();
        evictedDescriptor.setDirty(true);
    }

    @Override
    public SimulationStep<T> nextStep() 
    {
        return new PageMemoryAccessStep<T>(context);
    }

    @Override
    public void undo()
    {
        super.undo();

        if (evicted != null)
        {
            evictedDescriptor.setDirty(descriptorWasDirty);
        }
    }

    
}
