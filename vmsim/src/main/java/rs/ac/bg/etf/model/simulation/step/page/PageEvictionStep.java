package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.os.EvictionPolicy;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.os.PageOSMemoryManager.FrameMapping;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.model.simulation.step.StepDescriptionKey;
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
        evictionPolicy.removeVictim();

        PageTableDescriptor victimDescriptor = victimFrameMapping.descriptor();
        victimDescriptor.setValid(false);
        memoryManager.free(victimFrame);

        long tag = tlb.calculateTag(victimFrameMapping.user(), victimFrameMapping.page());
        tlbEntry = tlb.invalidateEntry(tag);

        if (tlbEntry != null)
            victimDescriptor.setDirty(tlbEntry.isDirty());

        context.setPageEvictionVictim(victimFrameMapping.user(), victimFrameMapping.page(), victimFrame);
        context.setPageEvictionInvalidatedTlbEntry(tlbEntry != null,
                tlbEntry != null ? tlbEntry.getTag() : -1,
                tlbEntry != null ? tlbEntry.getBlock() : -1,
                tlbEntry != null && tlbEntry.isDirty());

        setAffectedComponents(tlbEntry != null
                ? new SimulationComponent[] { SimulationComponent.OS, SimulationComponent.MMU, SimulationComponent.TLB }
                : new SimulationComponent[] { SimulationComponent.OS, SimulationComponent.MMU });

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

    @Override
    public StepDescription getStepDescription()
    {
        PageTableDescriptor victimDescriptor = victimFrameMapping.descriptor();
        return victimDescriptor.isDirty()
                ? new StepDescription(StepDescriptionKey.FRAME_EVICTED_DIRTY,
                        victimFrame, victimFrameMapping.user(), victimFrameMapping.page())
                : new StepDescription(StepDescriptionKey.FRAME_EVICTED,
                        victimFrame, victimFrameMapping.user(), victimFrameMapping.page());
    }

    /** The frame chosen as the eviction victim; valid once {@link #execute()} has run. */
    public long getVictimFrame()
    {
        return victimFrame;
    }

    /** The user that owned the evicted frame; valid once {@link #execute()} has run. */
    public int getVictimUser()
    {
        return victimFrameMapping.user();
    }

    /** The page that occupied the evicted frame; valid once {@link #execute()} has run. */
    public long getVictimPage()
    {
        return victimFrameMapping.page();
    }

}
