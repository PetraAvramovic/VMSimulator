package rs.ac.bg.etf.model.simulation.step.page;

import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageFaultStep<T extends PageSimulationContext> extends SimulationStep<T>
{
    private PageTableDescriptor descriptor;
    private long frame;

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
        context.setCurrentDescriptor(descriptor);

        if (frame == -1)
            return new PageEvictionStep<T>(context);
        else
            return new PageLoadIntoMemoryStep<T>(context, frame);
    }

    @Override
    public void undo() 
    {
        context.setCurrentDescriptor(null);
    }

    
    
}
