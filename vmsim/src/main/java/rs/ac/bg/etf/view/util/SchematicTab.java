package rs.ac.bg.etf.view.util;

import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.StackPane;

/**
 * Shared shell of the MMU / TLB / OS / Memory tabs: a {@link SchematicCanvas} inside a scroll pane,
 * plus the one thing a scroll pane can't do on its own -- report the canvas's design size upward as
 * this tab's own minimum, so the workbench's UI scale ({@link ResponsiveHost}) knows how much room
 * the schematic really needs.
 *
 * <p>Scrolling is only the safety net (e.g. a tab whose content grows past its design size); the
 * normal way a schematic adapts to a small window is the whole UI being rebuilt at a smaller scale.
 * Everything a subclass measures or lays out must go through {@link UiScale}.
 */
public abstract class SchematicTab extends StackPane {
    protected final SchematicCanvas canvas = new SchematicCanvas();

    private ScrollPane viewport;

    // Same deferral (and reason) as SchematicCanvas's own relayout: this tab's own minimum is what the
    // TabPane / SplitPane / scale-to-fit above it cache, so the invalidation has to travel up from
    // outside a layout pass or it is dropped on the way. Not skipped while hidden.
    private final PostLayoutTask relayout = new PostLayoutTask(this, this::requestLayoutUpTheTree, false);

    protected SchematicTab() {
        getStyleClass().add("schematic-tab");
        canvas.designWidthProperty().addListener(observable -> relayout.request());
        canvas.designHeightProperty().addListener(observable -> relayout.request());
    }

    /**
     * Wraps the canvas in the standard schematic scroll pane and mounts it as this tab's first
     * child. The canvas grows to fill the viewport in both directions (never below its design
     * size), so anything bound to the canvas's width/height -- the Physical Address boxes, say --
     * tracks the real viewport rather than a fixed pixel size.
     */
    protected ScrollPane mountCanvas() {
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.getStyleClass().addAll("mmu-scroll-pane", "slim-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        getChildren().add(scrollPane);
        viewport = scrollPane;
        return scrollPane;
    }

    // requestLayout() alone does not reach the TabPane: a layout request stops at the first
    // *unmanaged* ancestor (it is its own layout root and does not pass the request on), and the
    // region TabPaneSkin puts every tab's content in is unmanaged. The TabPane would then keep the
    // minimum height it cached before this tab's design size settled -- and the workbench, which sizes
    // the UI scale from that minimum, would never find out the tab needs more room (it stayed at
    // scale 1.0 with the tab stretched past the window and clipped, and the ScrollPane inside, being
    // as tall as the tab, had nothing to scroll). So every ancestor is asked explicitly.
    private void requestLayoutUpTheTree() {
        requestLayout();
        for (Parent ancestor = getParent(); ancestor != null; ancestor = ancestor.getParent())
            ancestor.requestLayout();
    }

    // The scroll pane's own border/padding sits between this tab and the canvas, so the canvas only
    // gets to be its full design size if the tab is at least that much larger.
    private double viewportChromeWidth() {
        return viewport != null ? viewport.getInsets().getLeft() + viewport.getInsets().getRight() : 0;
    }

    private double viewportChromeHeight() {
        return viewport != null ? viewport.getInsets().getTop() + viewport.getInsets().getBottom() : 0;
    }

    @Override
    protected double computeMinWidth(double height) {
        return canvas.minWidth(-1) + viewportChromeWidth() + snappedLeftInset() + snappedRightInset();
    }

    /**
     * The height the workbench is asked to make room for -- what its UI scale is fitted to -- given
     * the full height the schematic needs. By default all of it: a schematic that doesn't fit is drawn
     * at a smaller scale. A tab whose height grows with its configuration (a set-associative TLB
     * stacks one table per way) overrides this to ask for less, and the rest is reached by scrolling
     * instead of shrinking the whole UI -- possibly all the way to the minimum scale -- to fit a tall
     * stack. The canvas still reports its full design height, which is what makes it scroll.
     *
     * @return at most {@code schematicHeight}
     */
    protected double heightToFit(double schematicHeight) {
        return schematicHeight;
    }

    @Override
    protected double computeMinHeight(double width) {
        return heightToFit(canvas.minHeight(-1)) + viewportChromeHeight() + snappedTopInset() + snappedBottomInset();
    }
}
