package rs.ac.bg.etf.view.util;

import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Measures table column widths from font metrics. Every font size passed in here is a design-size
 * one (the sizes the CSS is written at); it is measured at {@link UiScale#font(double)} -- exactly
 * the size the scaled stylesheet draws it at -- and the padding follows the UI scale too.
 */
public class WidthCalculator {
    private static final double DEFAULT_FONT_SIZE = 13;
    private static final double CELL_PADDING = 16;

    /** Design-size font of the MMU schematic's PageTableView and the TLB schematic's row tables
     *  (AssociativeTLBView / DirectTLBView / SetAssociativeTLBView) -- both scoped through the
     *  ".data-table-schematic" CSS marker rather than the shared DEFAULT_FONT_SIZE every other table
     *  (OS/inspector) uses. Column widths at this size are computed by passing it to the overloads
     *  below; keep it in sync with the ".data-table-schematic .data-table-cell" font-size in
     *  light-theme.css and dark-theme.css alike. */
    public static final double LARGE_CELL_FONT_SIZE = 19;
    // Hand-tuned rather than measured (the values these columns display aren't bounded by a fixed
    // digit count), shared by both enlarged tables so they read as exactly the same size.
    private static final double LARGE_INDEX_COL_WIDTH = 85;
    private static final double LARGE_BIT_COL_WIDTH = 44;

    /** Index/page-number column width at LARGE_CELL_FONT_SIZE, at the current UI scale. */
    public static double largeIndexColumnWidth()
    {
        return UiScale.px(LARGE_INDEX_COL_WIDTH);
    }

    /** V/D bit-flag column width at LARGE_CELL_FONT_SIZE, shared the same way. */
    public static double largeBitColumnWidth()
    {
        return UiScale.px(LARGE_BIT_COL_WIDTH);
    }

    /** Column of hex values rendered as "0x" + digits, e.g. "Frame"/"Disk" -- the +2 accounts for "0x". */
    public static double columnWidth(String header, int digits)
    {
        return columnWidth(header, digits, DEFAULT_FONT_SIZE);
    }

    /** Same, but measured at an explicit (design-size) font size -- for a table drawn larger/smaller
     *  than the shared default (e.g. the MMU schematic's own enlarged PageTableView). */
    public static double columnWidth(String header, int digits, double fontSize)
    {
        return plainColumnWidth(header, digits + 2, fontSize);
    }

    /** Column of plain (no "0x" prefix) fixed-width text, e.g. a decimal count or a short code. */
    public static double plainColumnWidth(String header, int charCount)
    {
        return plainColumnWidth(header, charCount, DEFAULT_FONT_SIZE);
    }

    /** Same, but measured at an explicit (design-size) font size -- for a table drawn larger/smaller
     *  than the shared default (e.g. the MMU schematic's own enlarged PageTableView). */
    public static double plainColumnWidth(String header, int charCount, double fontSize)
    {
        double size = UiScale.font(fontSize);
        Text valueSample = new Text("F".repeat(Math.max(charCount, 1)));
        valueSample.setFont(Font.font("IBM Plex Mono", size));
        Text headerSample = new Text(header);
        headerSample.setFont(Font.font("IBM Plex Mono", FontWeight.BOLD, size));
        double widest = Math.max(valueSample.getLayoutBounds().getWidth(), headerSample.getLayoutBounds().getWidth());
        return widest + UiScale.px(CELL_PADDING);
    }
}
