package rs.ac.bg.etf.view.util;

/**
 * How a memory row inside a kernel-locked frame is presented, shared by every memory table (the
 * Memory tab's preview and the full inspector window): greyed-out text (see the theme stylesheets'
 * {@code .data-table-row-locked}) and a tooltip naming why.
 */
public final class LockedRow
{
    public static final String STYLE_CLASS = "data-table-row-locked";
    public static final String TOOLTIP = "Locked";

    private LockedRow() {}
}
