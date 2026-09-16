package rs.ac.bg.etf.view.util;

/**
 * On-demand data source for a {@link WindowedTableView}: rows are pulled one at a time for
 * whichever window is currently visible, never materialised as a whole list -- the same
 * "windowed" idiom {@code FrameTableView}/{@code PagedOSTabViewModel} established for physical
 * memory, generalised so any other address-scale table (a page table today, a future segment
 * table, ...) can reuse the same view by supplying its own implementation.
 */
public interface WindowedRowSource<R>
{
    /** Total number of logical entries (e.g. {@code 2^pageBits}) -- may be far larger than will ever be pooled. */
    long getEntryCount();

    /** Builds the row for entry {@code index} on demand; called only for entries actually on screen. */
    R rowAt(long index);
}
