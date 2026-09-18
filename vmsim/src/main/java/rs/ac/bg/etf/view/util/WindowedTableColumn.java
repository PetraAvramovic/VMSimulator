package rs.ac.bg.etf.view.util;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One column of a {@link WindowedTableView}: a header label, a fixed pixel width, how to render a
 * row's value for it, and an optional click action (e.g. a page's Disk cell opening that block's
 * own inspector window) -- null for a plain, non-interactive column.
 */
public record WindowedTableColumn<R>(String header, double width, Function<R, String> textFn, Consumer<R> onClick)
{
    public WindowedTableColumn(String header, double width, Function<R, String> textFn)
    {
        this(header, width, textFn, null);
    }
}
