package rs.ac.bg.etf.view.inspector;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.view.util.WindowedRowSource;

/**
 * Adapts one disk block to the generic {@code WindowedTableView}: every word offset from 0 to the
 * page size (one block backs exactly one page -- see PageStoreToDiskStep/PageLoadIntoMemoryStep),
 * read on demand straight from the live {@link rs.ac.bg.etf.model.disk.Disk} so a write-back while
 * the window is open shows up immediately. An offset nothing has ever written reads as 0, the same
 * convention {@code Disk} itself uses for a word it never materialised.
 */
public class DiskBlockRowSource implements WindowedRowSource<DiskWord>
{
    private final PageSimulationContext context;
    private final long diskAddress;
    private final long entryCount;

    public DiskBlockRowSource(PageSimulationContext context, long diskAddress)
    {
        this.context = context;
        this.diskAddress = diskAddress;
        this.entryCount = context.getPageSize();
    }

    @Override
    public long getEntryCount()
    {
        return entryCount;
    }

    @Override
    public DiskWord rowAt(long index)
    {
        return new DiskWord(index, context.getDisk().readBlock(diskAddress).getOrDefault(index, 0L));
    }
}
