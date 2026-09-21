package rs.ac.bg.etf.view.util;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

/**
 * An absolute-coordinate drawing surface for a schematic tab that states, as properties, the
 * smallest logical size its content needs to draw without anything running into anything else.
 *
 * <p>A plain {@code Pane}'s preferred size is the extent of its children -- which here is circular,
 * because the Physical Address boxes are bound to the canvas's own width. So a canvas's minimum and
 * preferred size are instead exactly this declared design size; the owning tab keeps it up to date
 * from what it actually laid out (table widths, wire drop lengths...) and the UI scale
 * ({@link ResponsiveHost}) takes care of fitting it to whatever window there is.
 *
 * <p>Design size must never depend on the canvas's own current width/height, or the two would chase
 * each other.
 */
public class SchematicCanvas extends Pane {
    private final DoubleProperty designWidth = new SimpleDoubleProperty(this, "designWidth", 0);
    private final DoubleProperty designHeight = new SimpleDoubleProperty(this, "designHeight", 0);

    // Region caches its min/pref sizes until asked to lay out again, and a layout request raised
    // while an ancestor is mid-layout is silently dropped by JavaFX rather than passed up -- which is
    // exactly when a design size tends to change (a table's width settling during the first layout
    // pass). Deferring to after the layout pass guarantees the request reaches every ancestor, so
    // none of them keeps a stale minimum. Coalesced: several changes in one pass cost one relayout.
    // Not skipped while hidden: a hidden tab's minimum still counts towards the workbench's.
    private final PostLayoutTask relayout = new PostLayoutTask(this, this::requestLayout, false);

    public SchematicCanvas() {
        designWidth.addListener(observable -> relayout.request());
        designHeight.addListener(observable -> relayout.request());
    }

    public DoubleProperty designWidthProperty() {
        return designWidth;
    }

    public DoubleProperty designHeightProperty() {
        return designHeight;
    }

    /**
     * Raises {@code nodes} above everything else, in the given order -- unless they already are the
     * topmost children in that order. {@code Node.toFront()} takes the node out of the children list
     * and puts it back even when the z-order it ends up with is the one it already had, and a wire
     * re-route (which asks for this) runs on every layout pass; doing that for a handful of wires
     * each time is a lot of list changes, and a scene-graph sync of the canvas, for no visible effect.
     */
    public void bringToFront(Node... nodes) {
        ObservableList<Node> children = getChildren();
        int first = children.size() - nodes.length;
        if (first >= 0) {
            int i = 0;
            while (i < nodes.length && children.get(first + i) == nodes[i])
                i++;
            if (i == nodes.length)
                return;
        }
        for (Node node : nodes)
            node.toFront();
    }

    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + designWidth.get() + snappedRightInset();
    }

    @Override
    protected double computePrefWidth(double height) {
        return computeMinWidth(height);
    }

    @Override
    protected double computeMinHeight(double width) {
        return snappedTopInset() + designHeight.get() + snappedBottomInset();
    }

    @Override
    protected double computePrefHeight(double width) {
        return computeMinHeight(width);
    }
}
