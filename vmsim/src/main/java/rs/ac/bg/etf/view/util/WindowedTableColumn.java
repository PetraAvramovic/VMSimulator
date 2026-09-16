package rs.ac.bg.etf.view.util;

import java.util.function.Function;

/**
 * One column of a {@link WindowedTableView}: a header label, a fixed pixel width, and how to
 * render a row's value for it.
 */
public record WindowedTableColumn<R>(String header, double width, Function<R, String> textFn) {}
