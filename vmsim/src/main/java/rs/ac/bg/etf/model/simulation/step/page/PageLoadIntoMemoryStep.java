package rs.ac.bg.etf.model.simulation.step.page;

import java.util.SortedMap;

import rs.ac.bg.etf.model.disk.Disk;
import rs.ac.bg.etf.model.memory.Memory;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;


public class PageLoadIntoMemoryStep<T extends PageSimulationContext> extends SimulationStep<T> 
{
    private long frame;
    private long previousFrame;
    private SortedMap<Long, Long> previousBlock;

    public PageLoadIntoMemoryStep(T context, long frame) 
    {
        super(context);
        this.frame = frame;
    }

    @Override
    public SimulationStep<T> execute() 
    {
        PageOSMemoryManager memoryManager = context.getOSMemoryManager();
        Disk disk = context.getDisk();
        Memory memory = context.getMemory();
        PageTableDescriptor descriptor = context.getCurrentDescriptor();

        long diskAddress = descriptor.getDisk();
        SortedMap<Long, Long> block = disk.readBlock(diskAddress);

        long memoryAddress = frame << context.getWordBits();
        previousBlock = memory.readBlock(memoryAddress, context.getPageSize());
        memory.writeBlock(memoryAddress, block);

        previousFrame = descriptor.getBlock();
        descriptor.setValid(true);
        descriptor.setBlock(frame);

        int user = context.getCurrentInstruction().getUser();
        long page = context.getPageComponent();

        memoryManager.allocate(frame, user, page, descriptor);

        return new FormPhysicalAddressFromPageTableStep<T>(context, descriptor);
    }

    @Override
    public void undo() 
    {
        PageOSMemoryManager memoryManager = context.getOSMemoryManager();
        Memory memory = context.getMemory();
        PageTableDescriptor descriptor = context.getCurrentDescriptor();

        long memoryAddress = frame << context.getWordBits();
        memory.writeBlock(memoryAddress, previousBlock);

        descriptor.setValid(false);
        descriptor.setBlock(previousFrame);
        
        memoryManager.undoAllocation(frame);
    }

    @Override
    public String getDescription()
    {
        PageTableDescriptor descriptor = context.getCurrentDescriptor();
        return String.format("Loaded page %d from disk into frame 0x%X.", descriptor.getPage(), frame);
    }

    /** The frame the faulting page is loaded into. */
    public long getFrame()
    {
        return frame;
    }

    
}
