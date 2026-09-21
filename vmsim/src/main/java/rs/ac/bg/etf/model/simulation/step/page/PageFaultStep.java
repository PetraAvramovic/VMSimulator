package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.model.simulation.step.StepDescriptionKey;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageFaultStep<T extends PageSimulationContext> extends SimulationStep<T>
{
    private PageTableDescriptor descriptor;
    private long frame;
    // Whatever the context's current descriptor was before this step made the faulting page's the
    // current one. The context field is shared scratch state, so undo() puts the old value back rather
    // than clearing it: a later fault's undo must not blank the descriptor an earlier fault, and the
    // load step redone after it, still depends on.
    private PageTableDescriptor previousDescriptor;

    protected PageFaultStep(T context, PageTableDescriptor descriptor)
    {
        super(context);
        this.descriptor = descriptor;
    }

    @Override
    public SimulationStep<T> execute()
    {
        PageOSMemoryManager memoryManager = context.getOSMemoryManager();
        frame = memoryManager.getFreeFrame();
        // -1 (memory full, about to evict) is the same sentinel context starts with, so a fault that
        // finds nothing never leaves a stale frame from an earlier instruction's load looking "active".
        context.setCurrentFrame(frame);
        previousDescriptor = context.getCurrentDescriptor();
        context.setCurrentDescriptor(descriptor);

        setAffectedComponents(SimulationComponent.OS);
        if (frame == -1)
            return new PageEvictionStep<T>(context);
        else
            return new PageLoadIntoMemoryStep<T>(context, frame);
    }

    @Override
    public void undo() 
    {
        context.setCurrentDescriptor(previousDescriptor);
    }

    @Override
    public StepDescription getStepDescription()
    {
        // Only the frame search: the lookup step before this one already said the page is not valid.
        return frame == -1
                ? new StepDescription(StepDescriptionKey.PAGE_FAULT_NO_FRAME)
                : new StepDescription(StepDescriptionKey.PAGE_FAULT_LOADING, frame);
    }

    
    
}
