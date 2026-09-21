package rs.ac.bg.etf.view.util;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * A tooltip a pooled table row can switch on and off as it is reused for different entries: the
 * row node stays the same while the window scrolls, so the tooltip is installed on it only while
 * the entry it currently shows has something to say, and removed again otherwise (an installed
 * tooltip with empty text would still pop up as a small blank box).
 */
public final class RowTooltip
{
    private static final String STYLE_CLASS = "data-table-tooltip";
    // JavaFX's default is a full second, which reads as "nothing happens" for a one-word label.
    private static final Duration SHOW_DELAY = Duration.millis(200);

    private final Node node;
    private final Tooltip tooltip = new Tooltip();
    private boolean installed;

    public RowTooltip(Node node)
    {
        this.node = node;
        tooltip.getStyleClass().add(STYLE_CLASS);
        tooltip.setShowDelay(SHOW_DELAY);
    }

    /** Shows {@code text} on hover over the row, or -- for null -- no tooltip at all. */
    public void set(String text)
    {
        if (text == null)
        {
            if (installed)
            {
                Tooltip.uninstall(node, tooltip);
                installed = false;
            }
            return;
        }
        tooltip.setText(text);
        if (!installed)
        {
            Tooltip.install(node, tooltip);
            installed = true;
        }
    }
}
