package rs.ac.bg.etf.model.simulation.step.page;

import java.util.SortedMap;

import rs.ac.bg.etf.model.disk.Disk;
import rs.ac.bg.etf.model.memory.Memory;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

public class PageStoreToDiskStep<T extends PageSimulationContext> extends SimulationStep<T> 
{
    private PageTableDescriptor descriptor;
    private SortedMap<Long, Long> previousDiskBlock;

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

        long frame = descriptor.getBlock();
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
    public String getDescription()
    {
        return String.format("Wrote dirty page %d back to disk address 0x%X.", descriptor.getPage(), descriptor.getDisk());
    }

    
}
