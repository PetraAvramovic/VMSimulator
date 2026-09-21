package rs.ac.bg.etf.model.simulation.step.page;

import java.util.SortedMap;

import rs.ac.bg.etf.model.disk.Disk;
import rs.ac.bg.etf.model.memory.Memory;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.model.simulation.step.StepDescriptionKey;
import rs.ac.bg.etf.model.table.PageTableDescriptor;


public class PageLoadIntoMemoryStep<T extends PageSimulationContext> extends SimulationStep<T>
{
    private long frame;
    private long previousFrame;
    private SortedMap<Long, Long> previousBlock;

    // Captured at execute() time: undo() / getStepDescription() must NOT re-read
    // context.getCurrentDescriptor(), which is shared scratch state: another instruction's
    // PageFaultStep replaces it, and only gives the old value back when that step is undone.
    private PageTableDescriptor descriptor;
    private int user;
    private long page;
    private long diskAddress;

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
        descriptor = context.getCurrentDescriptor();

        diskAddress = descriptor.getDisk();
        SortedMap<Long, Long> block = disk.readBlock(diskAddress);

        long memoryAddress = frame << context.getWordBits();
        previousBlock = memory.readBlock(memoryAddress, context.getPageSize());
        memory.writeBlock(memoryAddress, block);

        previousFrame = descriptor.getBlock();
        descriptor.setValid(true);
        descriptor.setBlock(frame);

        user = context.getCurrentInstruction().getUser();
        page = context.getPageComponent();

        memoryManager.allocate(frame, user, page, descriptor);

        context.setCurrentFrame(frame);
        context.setCurrentLoadDiskAddress(diskAddress);

        setAffectedComponents(SimulationComponent.OS, SimulationComponent.MMU, SimulationComponent.MEMORY);
        return new FormPhysicalAddressFromPageTableStep<T>(context, descriptor);
    }

    @Override
    public void undo()
    {
        PageOSMemoryManager memoryManager = context.getOSMemoryManager();
        Memory memory = context.getMemory();

        long memoryAddress = frame << context.getWordBits();
        memory.writeBlock(memoryAddress, previousBlock);

        descriptor.setValid(false);
        descriptor.setBlock(previousFrame);

        memoryManager.undoAllocation(frame);
    }

    @Override
    public StepDescription getStepDescription()
    {
        // The disk address is the Disk field of the page's page table entry.
        return new StepDescription(StepDescriptionKey.PAGE_LOADED_INTO_MEMORY, page, user, diskAddress, frame);
    }

}
