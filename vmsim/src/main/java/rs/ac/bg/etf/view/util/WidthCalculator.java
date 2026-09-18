package rs.ac.bg.etf.view.util;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

public class WidthCalculator {
    private static final double DEFAULT_FONT_SIZE = 13;
    private static final double CELL_PADDING = 16;

    /** Font size the MMU schematic's PageTableView and the TLB schematic's row tables
     *  (AssociativeTLBView / DirectTLBView / SetAssociativeTLBView) are drawn at -- both scoped
     *  through the ".page-table-inline" CSS marker rather than the shared DEFAULT_FONT_SIZE every
     *  other table (OS/inspector) uses. Column widths at this size are computed by passing it to
     *  the overloads below; keep it in sync with light-theme.css's own
     *  ".page-table-inline .page-table-cell" font-size. */
    public static final double LARGE_CELL_FONT_SIZE = 19;
    /** Index/page-number column width at LARGE_CELL_FONT_SIZE -- hand-tuned rather than measured
     *  (the values these columns display aren't bounded by a fixed digit count), shared by both
     *  enlarged tables so they read as exactly the same size. */
    public static final double LARGE_INDEX_COL_WIDTH = 85;
    /** V/D bit-flag column width at LARGE_CELL_FONT_SIZE, shared the same way. */
    public static final double LARGE_BIT_COL_WIDTH = 44;

    /** Column of hex values rendered as "0x" + digits, e.g. "Frame"/"Disk" -- the +2 accounts for "0x". */
    public static double columnWidth(String header, int digits)
    {
        return columnWidth(header, digits, DEFAULT_FONT_SIZE);
    }

    /** Same, but measured at an explicit font size -- for a table drawn larger/smaller than the
     *  shared default (e.g. the MMU schematic's own enlarged PageTableView). */
    public static double columnWidth(String header, int digits, double fontSize)
    {
        return plainColumnWidth(header, digits + 2, fontSize);
    }

    /** Column of plain (no "0x" prefix) fixed-width text, e.g. a decimal count or a short code. */
    public static double plainColumnWidth(String header, int charCount)
    {
        return plainColumnWidth(header, charCount, DEFAULT_FONT_SIZE);
    }

    /** Same, but measured at an explicit font size -- for a table drawn larger/smaller than the
     *  shared default (e.g. the MMU schematic's own enlarged PageTableView). */
    public static double plainColumnWidth(String header, int charCount, double fontSize)
    {
        Text valueSample = new Text("F".repeat(Math.max(charCount, 1)));
        valueSample.setFont(Font.font("IBM Plex Mono", fontSize));
        Text headerSample = new Text(header);
        headerSample.setFont(Font.font("IBM Plex Mono", FontWeight.BOLD, fontSize));
        double widest = Math.max(valueSample.getLayoutBounds().getWidth(), headerSample.getLayoutBounds().getWidth());
        return widest + CELL_PADDING;
    }
}
