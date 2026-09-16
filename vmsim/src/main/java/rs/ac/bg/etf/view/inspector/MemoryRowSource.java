package rs.ac.bg.etf.view.inspector;

import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.view.util.WindowedRowSource;

/**
 * Adapts the whole of physical memory to the generic {@code WindowedTableView}: up to
 * {@code 2^physicalAddressBits} addressable words, read on demand via
 * {@link PageSimulationContext#getValueAtAddress}, never materialised as a whole.
 */
public class MemoryRowSource implements WindowedRowSource<MemoryRow>
{
    private final PageSimulationContext context;
    private final PageOSMemoryManager osManager;
    private final int wordBits;
    private final long entryCount;

    public MemoryRowSource(PageSimulationContext context)
    {
        this.context = context;
        this.osManager = context.getOSMemoryManager();
        this.wordBits = context.getWordBits();
        this.entryCount = context.getPhysicalMemorySize();
    }

    @Override
    public long getEntryCount()
    {
        return entryCount;
    }

    @Override
    public MemoryRow rowAt(long index)
    {
        long frame = index >>> wordBits;
        return new MemoryRow(index, context.getValueAtAddress(index), osManager.isLocked(frame));
    }
}
