package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.os.PageOSMemoryManager.FrameMapping;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.TLBEvictionStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageTLBEvictionStep<T extends PageSimulationContext> extends TLBEvictionStep<T>
{
    private PageTableDescriptor descriptor;
    private PageTableDescriptor evictedDescriptor;
    private boolean descriptorWasDirty = false;

    public PageTLBEvictionStep(T context, PageTableDescriptor descriptor)
    {
        super(context);
        this.descriptor = descriptor;
    }

    @Override
    public long getTag()
    {
        int user = context.getCurrentInstruction().getUser();
        return context.getTLB().calculateTag(user, context.getPageComponent());
    }

    @Override
    public void writebackDirty()
    {
        PageOSMemoryManager memoryManager = context.getOSMemoryManager();
        FrameMapping evictedMapping = memoryManager.getFrameMapping(evicted.getBlock());
        evictedDescriptor = evictedMapping.descriptor();

        descriptorWasDirty = evictedDescriptor.isDirty();
        evictedDescriptor.setDirty(true);

        context.setTlbWritebackVictim(evictedMapping.user(), evictedMapping.page());
    }

    @Override
    public void undoWritebackDirty()
    {
        evictedDescriptor.setDirty(descriptorWasDirty);
    }

    @Override
    public SimulationStep<T> nextStep()
    {
        return new PageTLBUpdateStep<T>(context, descriptor);
    }
}
