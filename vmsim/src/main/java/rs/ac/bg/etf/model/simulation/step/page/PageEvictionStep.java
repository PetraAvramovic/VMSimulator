package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.os.EvictionPolicy;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.os.PageOSMemoryManager.FrameMapping;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;
import rs.ac.bg.etf.model.tlb.TLB;
import rs.ac.bg.etf.model.tlb.TLBEntry;

public class PageEvictionStep<T extends PageSimulationContext> extends SimulationStep<T> 
{
    private long victimFrame;
    private FrameMapping victimFrameMapping;
    private TLBEntry tlbEntry;

    protected PageEvictionStep(T context) 
    {
        super(context);
    }

    @Override
    public SimulationStep<T> execute() 
    {
        PageOSMemoryManager memoryManager = context.getOSMemoryManager();
        EvictionPolicy evictionPolicy = memoryManager.getEvictionPolicy();
        TLB tlb = context.getTLB();

        victimFrame = evictionPolicy.getVictim();
        
        victimFrameMapping = memoryManager.getFrameMapping(victimFrame);
        PageTableDescriptor victimDescriptor = victimFrameMapping.descriptor();
        victimDescriptor.setValid(false);
        memoryManager.free(victimFrame);

        long tag = tlb.calculateTag(victimFrameMapping.user(), victimFrameMapping.page());
        tlbEntry = tlb.invalidateEntry(tag);

        if (tlbEntry != null)
            victimDescriptor.setDirty(tlbEntry.isDirty());

        if (victimDescriptor.isDirty())
            return new PageStoreToDiskStep<T>(context, victimDescriptor);
        else
            return new PageLoadIntoMemoryStep<T>(context, victimFrame);

    }

    @Override
    public void undo() 
    {
        PageOSMemoryManager memoryManager = context.getOSMemoryManager();
        EvictionPolicy evictionPolicy = memoryManager.getEvictionPolicy();
        PageTableDescriptor victimDescriptor = victimFrameMapping.descriptor();

        evictionPolicy.undoVictim(victimFrame);
        memoryManager.undoFree(victimFrame, victimFrameMapping);
        victimDescriptor.setValid(true);
    }

}
