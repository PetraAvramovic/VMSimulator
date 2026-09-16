package rs.ac.bg.etf.model.simulation.step.page;

import java.util.SortedMap;

import rs.ac.bg.etf.model.disk.Disk;
import rs.ac.bg.etf.model.memory.Memory;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.model.simulation.step.StepDescriptionKey;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageStoreToDiskStep<T extends PageSimulationContext> extends SimulationStep<T> 
{
    private PageTableDescriptor descriptor;
    private SortedMap<Long, Long> previousDiskBlock;
    private long frame;

    public PageStoreToDiskStep(T context, PageTableDescriptor descriptor) {
        super(context);
        this.descriptor = descriptor;
    }

    @Override
    public SimulationStep<T> execute() 
    {
        Disk disk = context.getDisk();
        Memory memory = context.getMemory();

        long diskAddress = descriptor.getDisk();
        previousDiskBlock = disk.readBlock(diskAddress);

        frame = descriptor.getBlock();
        SortedMap<Long, Long> block = memory.readBlock(frame, context.getPageSize());
        disk.writeBlock(diskAddress, block);
        descriptor.setDirty(false);

        return new PageLoadIntoMemoryStep<T>(context, frame);
    }

    @Override
    public void undo() 
    {
        Disk disk = context.getDisk();
        long diskAddress = descriptor.getDisk();
        disk.writeBlock(diskAddress, previousDiskBlock);    
        descriptor.setDirty(true);
    }

    @Override
    public StepDescription getStepDescription()
    {
        return new StepDescription(StepDescriptionKey.PAGE_STORED_TO_DISK, descriptor.getPage(), descriptor.getDisk());
    }

    /** The frame being written back (and then reused for the incoming page); valid once {@link #execute()} has run. */
    public long getFrame()
    {
        return frame;
    }

    /** Disk address the dirty victim page is written to. */
    public long getDiskAddress()
    {
        return descriptor.getDisk();
    }

    /** The victim page being written back. */
    public long getVictimPage()
    {
        return descriptor.getPage();
    }

    
}
