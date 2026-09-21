package rs.ac.bg.etf.view.util;

import javafx.scene.control.Button;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * Shared factory for the small circular "back" button (a chevron icon) used in the top-left
 * corner of every screen that offers a back-navigation action, so its geometry and styling stay
 * in one place instead of being re-typed per view.
 *
 * <p>The chevron is drawn as a {@link Polyline} rather than a text glyph ("&larr;") -- a font's
 * glyph metrics (ascent/descent, side bearings) are rarely symmetric, so a text-based arrow reads
 * as visually off-centre inside a circular button no matter how the label is aligned. A shape's
 * layout bounds are exactly the points drawn, so a chevron built symmetrically around its own
 * origin centres perfectly regardless of font or platform.
 */
public final class BackButton {
    // Chevron half-span in each direction from its centre point, in local (icon) coordinates
    // (design-size lengths, scaled with the UI in create()).
    private static final double CHEVRON_HALF_WIDTH = 4;
    private static final double CHEVRON_HALF_HEIGHT = 5;

    private BackButton() {
    }

    public static Button create(Runnable onAction) {
        double halfWidth = UiScale.px(CHEVRON_HALF_WIDTH);
        double halfHeight = UiScale.px(CHEVRON_HALF_HEIGHT);
        Polyline chevron = new Polyline(
                halfWidth, -halfHeight,
                -halfWidth, 0,
                halfWidth, halfHeight);
        chevron.getStyleClass().add("back-button-icon");
        chevron.setStrokeLineCap(StrokeLineCap.ROUND);
        chevron.setStrokeLineJoin(StrokeLineJoin.ROUND);

        Button backButton = new Button();
        backButton.setGraphic(chevron);
        backButton.getStyleClass().add("back-button");
        backButton.setFocusTraversable(false);
        backButton.setOnAction(e -> onAction.run());
        return backButton;
    }
}
