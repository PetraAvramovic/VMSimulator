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
    // Where the victim page's entry sat in the TLB before it was invalidated (-1 if it was not cached
    // there), and whether the victim was dirty -- both for the description. A cached page's dirty bit
    // is read from its TLB entry, which is the up-to-date one; an uncached page's from the page table.
    private int victimTlbSlot = -1;
    private boolean victimWasDirty;
    // The victim's page table entry's own dirty bit as it was before execute() overwrote it with the TLB
    // entry's (see below), so undo() can put it back.
    private boolean descriptorWasDirty;

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
        descriptorWasDirty = victimDescriptor.isDirty();
        victimDescriptor.setValid(false);
        memoryManager.free(victimFrame);

        long tag = tlb.calculateTag(victimFrameMapping.user(), victimFrameMapping.page());
        TLBEntry cached = tlb.lookup(tag);
        victimTlbSlot = cached != null ? tlb.getEntries().indexOf(cached) : -1;
        tlbEntry = tlb.invalidateEntry(tag);

        if (tlbEntry != null)
            victimDescriptor.setDirty(tlbEntry.isDirty());
        victimWasDirty = victimDescriptor.isDirty();

        context.setPageEvictionVictim(victimFrameMapping.user(), victimFrameMapping.page(), victimFrame);
        context.setPageEvictionInvalidatedTlbEntry(tlbEntry != null,
                tlbEntry != null ? tlbEntry.getTag() : -1,
                tlbEntry != null ? tlbEntry.getBlock() : -1,
                tlbEntry != null && tlbEntry.isDirty(),
                victimTlbSlot);

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
        victimDescriptor.setDirty(descriptorWasDirty);

        // The victim's TLB entry was removed by execute(); bring it back to the slot it was in.
        if (tlbEntry != null)
            context.getTLB().undoInvalidation();
    }

    @Override
    public StepDescription getStepDescription()
    {
        StepDescription dirtyBitSource = victimTlbSlot >= 0
                ? describeTlbSlot(victimTlbSlot)
                : new StepDescription(StepDescriptionKey.DIRTY_BIT_SOURCE_PAGE_TABLE);

        return new StepDescription(
                victimWasDirty ? StepDescriptionKey.FRAME_EVICTED_DIRTY : StepDescriptionKey.FRAME_EVICTED,
                victimFrame, victimFrameMapping.page(), victimFrameMapping.user(), dirtyBitSource);
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
