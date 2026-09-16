package rs.ac.bg.etf.view.inspector;

import rs.ac.bg.etf.model.table.PageTable;
import rs.ac.bg.etf.model.table.PageTableDescriptor;
import rs.ac.bg.etf.view.util.WindowedRowSource;

/**
 * Adapts one user's {@link PageTable} to the generic {@code WindowedTableView}. Read-only: always
 * uses {@link PageTable#getEntry(long)}, never {@code getEntryAndAdd}, so merely scrolling through
 * never-touched pages doesn't grow the table's backing map.
 */
public class PageTableRowSource implements WindowedRowSource<PageTableRow>
{
    private final PageTable pageTable;
    private final long entryCount;

    public PageTableRowSource(PageTable pageTable, long entryCount)
    {
        this.pageTable = pageTable;
        this.entryCount = entryCount;
    }

    @Override
    public long getEntryCount()
    {
        return entryCount;
    }

    @Override
    public PageTableRow rowAt(long index)
    {
        PageTableDescriptor descriptor = pageTable.getEntry(index);
        return new PageTableRow(index, descriptor.isValid(), descriptor.isDirty(),
                descriptor.getBlock(), descriptor.getDisk());
    }
}
