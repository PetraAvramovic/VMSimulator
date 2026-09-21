package rs.ac.bg.etf.view.shape;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineJoin;
import rs.ac.bg.etf.view.util.UiScale;

/**
 * A straight schematic wire with an optional arrow tip at its end point, an optional bit-width
 * indicator (the diagonal "slash" tick plus an "Nb" label) sitting at its midpoint, and an optional
 * value label on the wire's *other* side, matching the signal-wire notation used across the MMU/TLB
 * diagrams. This is the only way wires should be built in these schematics -- never a bare
 * {@code Line}/{@code Polyline} with hand-placed tick/label nodes next to it (see git history for
 * how easily those drift out of sync with each other); an elbow (multi-bend) connector is a chain of
 * several {@code BitWidthLine} segments, one per straight leg, rather than a single multi-point path.
 *
 * <p>Everything is bindable: the four end-point coordinates, the bit count, the toggles, and the
 * geometry tunables are all JavaFX properties, so the wire re-routes itself whenever any input
 * changes. The midpoint is exposed read-only so a further wire can branch from it. Nothing about the
 * rendered geometry is fixed at a call site &mdash; callers bind {@link #startXProperty()} … to the
 * boxes they connect and let this class recompute.
 */
public class BitWidthLine extends Group {
    /** Distance from the end point back along the wire to the arrow head's base. */
    private static final double DEFAULT_ARROW_LENGTH = 8;
    /** Half the span of the arrow head, measured perpendicular to the wire. */
    private static final double DEFAULT_ARROW_HALF_WIDTH = 5;
    /** Full length of the diagonal bit-width tick that crosses the wire. */
    private static final double DEFAULT_TICK_LENGTH = 14;
    /** Gap between the tick and the nearest bounding-box edge of the "Nb" label. */
    private static final double DEFAULT_LABEL_GAP = 6;
    /** Default suffix appended to the bit count in the indicator label. */
    private static final String DEFAULT_SUFFIX = "b";

    private static final double SIN_45 = Math.sqrt(2) / 2;
    private static final double COS_45 = Math.sqrt(2) / 2;

    private final DoubleProperty startX = new SimpleDoubleProperty();
    private final DoubleProperty startY = new SimpleDoubleProperty();
    private final DoubleProperty endX = new SimpleDoubleProperty();
    private final DoubleProperty endY = new SimpleDoubleProperty();

    private final IntegerProperty bits = new SimpleIntegerProperty();
    private final StringProperty suffix = new SimpleStringProperty(DEFAULT_SUFFIX);

    private final BooleanProperty arrowTipVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty bitWidthIndicatorVisible = new SimpleBooleanProperty(true);
    /** When true the label sits on the wire's left-hand side (relative to its start&rarr;end direction). */
    private final BooleanProperty labelOnLeft = new SimpleBooleanProperty(true);

    // The DEFAULT_* values are design-size lengths; each wire scales them to the UI scale it is built at.
    private final DoubleProperty arrowLength = new SimpleDoubleProperty(UiScale.px(DEFAULT_ARROW_LENGTH));
    private final DoubleProperty arrowHalfWidth = new SimpleDoubleProperty(UiScale.px(DEFAULT_ARROW_HALF_WIDTH));
    private final DoubleProperty tickLength = new SimpleDoubleProperty(UiScale.px(DEFAULT_TICK_LENGTH));
    private final DoubleProperty labelGap = new SimpleDoubleProperty(UiScale.px(DEFAULT_LABEL_GAP));

    private final ReadOnlyDoubleWrapper midX = new ReadOnlyDoubleWrapper();
    private final ReadOnlyDoubleWrapper midY = new ReadOnlyDoubleWrapper();

    private final Line wire = new Line();
    private final Polyline arrowHead = new Polyline();
    private final Line tick = new Line();
    private final Label bitsLabel = new Label();
    private final Label valueLabel = new Label();
    /** Whether the value label (opposite the bit-width tag) is shown; defaults to hidden. */
    private final BooleanProperty valueLabelVisible = new SimpleBooleanProperty(false);

    public BitWidthLine() {
        getStyleClass().add("bit-width-line");
        wire.getStyleClass().add("connector-line");
        arrowHead.getStyleClass().addAll("connector-line", "bit-width-line__arrow");
        // Outlined (unfilled, see .connector-line), not solid -- the default MITER join at its sharp
        // tip vertex would otherwise stroke visibly past the true tip point (the miter extends
        // further the sharper the angle), reading as an overshoot into whatever the wire targets.
        // Same fix BackButton's own chevron already uses for its own sharp vertex.
        arrowHead.setStrokeLineJoin(StrokeLineJoin.ROUND);
        tick.getStyleClass().addAll("connector-line", "bit-width-line__tick");
        bitsLabel.getStyleClass().addAll("bit-width-label", "bit-width-line__label");
        valueLabel.getStyleClass().addAll("wire-value-label", "bit-width-line__value");

        bitsLabel.textProperty().bind(Bindings.concat(bits.asString(), suffix));

        // The arrow head follows one toggle; the tick/label and value label each follow their own.
        // Unmanaging the hidden pieces keeps them out of this Group's bounds so nothing branches off
        // a stale point.
        arrowHead.visibleProperty().bind(arrowTipVisible);
        arrowHead.managedProperty().bind(arrowTipVisible);
        tick.visibleProperty().bind(bitWidthIndicatorVisible);
        tick.managedProperty().bind(bitWidthIndicatorVisible);
        bitsLabel.visibleProperty().bind(bitWidthIndicatorVisible);
        bitsLabel.managedProperty().bind(bitWidthIndicatorVisible);
        valueLabel.visibleProperty().bind(valueLabelVisible);
        valueLabel.managedProperty().bind(valueLabelVisible);

        getChildren().addAll(wire, arrowHead, tick, bitsLabel, valueLabel);

        InvalidationListener onChange = obs -> updateGeometry();
        startX.addListener(onChange);
        startY.addListener(onChange);
        endX.addListener(onChange);
        endY.addListener(onChange);
        arrowTipVisible.addListener(onChange);
        bitWidthIndicatorVisible.addListener(onChange);
        valueLabelVisible.addListener(onChange);
        labelOnLeft.addListener(onChange);
        arrowLength.addListener(onChange);
        arrowHalfWidth.addListener(onChange);
        tickLength.addListener(onChange);
        labelGap.addListener(onChange);
        // The labels can only be centred once they've been measured, so recompute when their size settles.
        bitsLabel.widthProperty().addListener(onChange);
        bitsLabel.heightProperty().addListener(onChange);
        valueLabel.widthProperty().addListener(onChange);
        valueLabel.heightProperty().addListener(onChange);

        updateGeometry();
    }

    private void updateGeometry() {
        double x1 = startX.get();
        double y1 = startY.get();
        double x2 = endX.get();
        double y2 = endY.get();

        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        if (len == 0) {
            len = 1;
        }
        double ux = dx / len;
        double uy = dy / len;
        // Perpendicular pointing to the wire's left-hand side (start->end direction rotated +90°).
        double perpSign = labelOnLeft.get() ? 1 : -1;
        double px = -uy * perpSign;
        double py = ux * perpSign;

        boolean withArrow = arrowTipVisible.get();
        double arrow = arrowLength.get();
        double half = arrowHalfWidth.get();

        // The arrow's tip always lands exactly at (x2,y2) -- the same reach a plain wire to that
        // point would have, so turning the arrow on never extends any further. Only the wire itself
        // is trimmed back to the arrow head's base, so its stroke never pokes through the tip.
        double wireEndX = withArrow ? x2 - ux * arrow : x2;
        double wireEndY = withArrow ? y2 - uy * arrow : y2;
        wire.setStartX(x1);
        wire.setStartY(y1);
        wire.setEndX(wireEndX);
        wire.setEndY(wireEndY);

        if (withArrow) {
            arrowHead.getPoints().setAll(
                    wireEndX - uy * half, wireEndY + ux * half,
                    x2, y2,
                    wireEndX + uy * half, wireEndY - ux * half);
        } else {
            arrowHead.getPoints().clear();
        }

        double mx = (x1 + x2) / 2;
        double my = (y1 + y2) / 2;
        midX.set(mx);
        midY.set(my);

        if (bitWidthIndicatorVisible.get()) {
            // Diagonal slash: the wire direction rotated by 45°, centred on the midpoint.
            double rx = ux * COS_45 - uy * SIN_45;
            double ry = ux * SIN_45 + uy * COS_45;
            double reach = tickLength.get() / 2;
            tick.setStartX(mx - rx * reach);
            tick.setStartY(my - ry * reach);
            tick.setEndX(mx + rx * reach);
            tick.setEndY(my + ry * reach);

            // Push the label out until its nearest bounding-box edge clears the tick by labelGap,
            // whatever the wire's angle: for a vertical wire the perpendicular is horizontal, so the
            // label's half-width is what would intrude; for a horizontal wire it is the half-height.
            double w = bitsLabel.getWidth();
            double h = bitsLabel.getHeight();
            double labelHalfExtent = Math.abs(px) * w / 2 + Math.abs(py) * h / 2;
            double offset = reach + labelGap.get() + labelHalfExtent;
            bitsLabel.setLayoutX(mx + px * offset - w / 2);
            bitsLabel.setLayoutY(my + py * offset - h / 2);
        }

        if (valueLabelVisible.get()) {
            // Same placement as the bit-width label, mirrored to the wire's other side -- including
            // clearing the diagonal tick's reach even when the tick itself is hidden, so toggling
            // bitWidthIndicatorVisible later never suddenly shifts this label.
            double reach = tickLength.get() / 2;
            double vw = valueLabel.getWidth();
            double vh = valueLabel.getHeight();
            double valueHalfExtent = Math.abs(px) * vw / 2 + Math.abs(py) * vh / 2;
            double valueOffset = reach + labelGap.get() + valueHalfExtent;
            valueLabel.setLayoutX(mx - px * valueOffset - vw / 2);
            valueLabel.setLayoutY(my - py * valueOffset - vh / 2);
        }
    }

    public DoubleProperty startXProperty() {
        return startX;
    }

    public DoubleProperty startYProperty() {
        return startY;
    }

    public DoubleProperty endXProperty() {
        return endX;
    }

    public DoubleProperty endYProperty() {
        return endY;
    }

    /** Bit count shown in the indicator label (rendered as the count followed by {@link #suffixProperty()}). */
    public IntegerProperty bitsProperty() {
        return bits;
    }

    /** Text appended after the bit count in the indicator label; defaults to {@code "b"}. */
    public StringProperty suffixProperty() {
        return suffix;
    }

    /** Whether the arrow head at the end point is drawn; defaults to {@code true}. */
    public BooleanProperty arrowTipVisibleProperty() {
        return arrowTipVisible;
    }

    /** Whether the mid-wire bit-width tick and its label are drawn; defaults to {@code true}. */
    public BooleanProperty bitWidthIndicatorVisibleProperty() {
        return bitWidthIndicatorVisible;
    }

    /** Side the label sits on relative to the start&rarr;end direction; {@code true} (default) = left. */
    public BooleanProperty labelOnLeftProperty() {
        return labelOnLeft;
    }

    /** Distance from the end point back to the arrow head's base; defaults to 8. */
    public DoubleProperty arrowLengthProperty() {
        return arrowLength;
    }

    /** Half the arrow head's span, perpendicular to the wire; defaults to 5. */
    public DoubleProperty arrowHalfWidthProperty() {
        return arrowHalfWidth;
    }

    /** Full length of the diagonal bit-width tick; defaults to 14. */
    public DoubleProperty tickLengthProperty() {
        return tickLength;
    }

    /** Gap between the tick and the near edge of the label; defaults to 4. */
    public DoubleProperty labelGapProperty() {
        return labelGap;
    }

    public ReadOnlyDoubleProperty midXProperty() {
        return midX.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty midYProperty() {
        return midY.getReadOnlyProperty();
    }

    /** The wire stroke, exposed so callers can toggle its {@code :active} pseudo-class. */
    public Line getWire() {
        return wire;
    }

    /** The arrow head, exposed so callers can toggle its {@code :active} pseudo-class. */
    public Polyline getArrowHead() {
        return arrowHead;
    }

    /** The bit-width tick, exposed so callers can toggle its {@code :active} pseudo-class. */
    public Line getTick() {
        return tick;
    }

    /** The "Nb" label, exposed so callers can toggle its {@code :active} pseudo-class. */
    public Label getBitsLabel() {
        return bitsLabel;
    }

    /** Whether the value label (opposite the bit-width tag) is drawn; defaults to {@code false}. */
    public BooleanProperty valueLabelVisibleProperty() {
        return valueLabelVisible;
    }

    /** The value label sitting opposite the bit-width tag; callers bind its text to the readout it
     *  carries. Hidden ({@link #valueLabelVisibleProperty()}) until a caller opts in. */
    public Label getValueLabel() {
        return valueLabel;
    }
}
