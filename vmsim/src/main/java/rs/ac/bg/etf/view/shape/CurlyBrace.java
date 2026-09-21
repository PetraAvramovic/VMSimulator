package rs.ac.bg.etf.view.shape;

import javafx.beans.InvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Path;
import rs.ac.bg.etf.view.util.UiScale;

/**
 * A schematic curly brace ("}") that visually combines two incoming wires (its left/right ends) into a
 * single point (its tip), used wherever two signals concatenate into one (e.g. page + shift bits into
 * a table offset). Left/right end coordinates are plain bindable properties so callers can wire them
 * directly to the boxes/lines they connect instead of hardcoding positions; the tip is derived from
 * them so a further line can stem from it.
 */
public class CurlyBrace extends Path {
    /** Fraction of the depth reached at the two ends, before the mid-sections sweep to full depth. */
    private static final double END_CURVE_FRACTION = 0.5;
    /** Design-size default depth; scaled to the UI scale the brace is built at. */
    private static final double DEFAULT_DEPTH = 12;

    private final DoubleProperty leftX = new SimpleDoubleProperty();
    private final DoubleProperty leftY = new SimpleDoubleProperty();
    private final DoubleProperty rightX = new SimpleDoubleProperty();
    private final DoubleProperty rightY = new SimpleDoubleProperty();
    private final DoubleProperty depth = new SimpleDoubleProperty(UiScale.px(DEFAULT_DEPTH));

    private final ReadOnlyDoubleWrapper tipX = new ReadOnlyDoubleWrapper();
    private final ReadOnlyDoubleWrapper tipY = new ReadOnlyDoubleWrapper();

    private final MoveTo leftStart = new MoveTo();
    private final QuadCurveTo leftOuter = new QuadCurveTo();
    private final QuadCurveTo leftInner = new QuadCurveTo();
    private final MoveTo rightStart = new MoveTo();
    private final QuadCurveTo rightOuter = new QuadCurveTo();
    private final QuadCurveTo rightInner = new QuadCurveTo();

    public CurlyBrace() {
        getStyleClass().add("connector-line");

        getElements().addAll(leftStart, leftOuter, leftInner, rightStart, rightOuter, rightInner);

        InvalidationListener onChange = obs -> updateGeometry();
        leftX.addListener(onChange);
        leftY.addListener(onChange);
        rightX.addListener(onChange);
        rightY.addListener(onChange);
        depth.addListener(onChange);
        updateGeometry();
    }

    private void updateGeometry() {
        double x1 = leftX.get();
        double y1 = leftY.get();
        double x2 = rightX.get();
        double y2 = rightY.get();
        double w = depth.get();
        double q = END_CURVE_FRACTION;

        double dx = x1 - x2;
        double dy = y1 - y2;
        double len = Math.hypot(dx, dy);
        if (len == 0) {
            len = 1;
        }
        dx /= len;
        dy /= len;

        double outerLeftX = x1 + q * w * dy;
        double outerLeftY = y1 - q * w * dx;
        double innerLeftX = (x1 - 0.25 * len * dx) + (1 - q) * w * dy;
        double innerLeftY = (y1 - 0.25 * len * dy) - (1 - q) * w * dx;
        double tipPointX = (x1 - 0.5 * len * dx) + w * dy;
        double tipPointY = (y1 - 0.5 * len * dy) - w * dx;
        double outerRightX = x2 + q * w * dy;
        double outerRightY = y2 - q * w * dx;
        double innerRightX = (x1 - 0.75 * len * dx) + (1 - q) * w * dy;
        double innerRightY = (y1 - 0.75 * len * dy) - (1 - q) * w * dx;

        leftStart.setX(x1);
        leftStart.setY(y1);
        leftOuter.setControlX(outerLeftX);
        leftOuter.setControlY(outerLeftY);
        leftOuter.setX(innerLeftX);
        leftOuter.setY(innerLeftY);
        leftInner.setControlX(2 * innerLeftX - outerLeftX);
        leftInner.setControlY(2 * innerLeftY - outerLeftY);
        leftInner.setX(tipPointX);
        leftInner.setY(tipPointY);

        rightStart.setX(x2);
        rightStart.setY(y2);
        rightOuter.setControlX(outerRightX);
        rightOuter.setControlY(outerRightY);
        rightOuter.setX(innerRightX);
        rightOuter.setY(innerRightY);
        rightInner.setControlX(2 * innerRightX - outerRightX);
        rightInner.setControlY(2 * innerRightY - outerRightY);
        rightInner.setX(tipPointX);
        rightInner.setY(tipPointY);

        tipX.set(tipPointX);
        tipY.set(tipPointY);
    }

    public DoubleProperty leftXProperty() {
        return leftX;
    }

    public DoubleProperty leftYProperty() {
        return leftY;
    }

    public DoubleProperty rightXProperty() {
        return rightX;
    }

    public DoubleProperty rightYProperty() {
        return rightY;
    }

    /** How far the brace's tip protrudes beyond the line joining its two ends; defaults to 12. */
    public DoubleProperty depthProperty() {
        return depth;
    }

    public ReadOnlyDoubleProperty tipXProperty() {
        return tipX.getReadOnlyProperty();
    }

    public ReadOnlyDoubleProperty tipYProperty() {
        return tipY.getReadOnlyProperty();
    }
}
