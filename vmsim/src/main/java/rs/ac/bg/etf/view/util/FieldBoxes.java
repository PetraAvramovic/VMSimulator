package rs.ac.bg.etf.view.util;

import java.util.HashMap;
import java.util.Map;

import javafx.beans.binding.Bindings;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * Shared factory for the address-breakdown field boxes and their satellite labels used by the
 * paged MMU and TLB schematic tabs, so both tabs size and style them identically.
 *
 * <p>Every size here is a design-size constant passed through {@link UiScale} on use, so the boxes
 * follow the current UI scale; the accessors below are methods (not constants) for that reason.
 */
public final class FieldBoxes {
    private static final String FIELD_FONT_RESOURCE = "/rs/ac/bg/etf/fonts/IBMPlexMono-SemiBold.ttf";
    private static final double FIELD_FONT_SIZE = 21;

    private static final double FIELD_PADDING = 40;
    private static final double MIN_FIELD_WIDTH = 70;
    // Boxes are forced to this exact height (padding 10px*2 + border 2px*2 + title/value text) so the
    // fixed-coordinate connector lines below always line up with the real rendered box edges. This is
    // the "solo" height -- a standalone field like the Page Table Pointer / TLB's Process box -- kept
    // deliberately unshrunk (just scaled up a little with the field font) since its own proportions
    // are the ones to preserve; the shorter, flatter ADDRESS_BOX_HEIGHT below is for paired
    // left/right fields (Page|Word, Block|Word) instead.
    private static final double BOX_HEIGHT = 70;
    // Height for the paired VA/PA address-breakdown fields specifically (Page|Word, Block|Word) --
    // shorter than BOX_HEIGHT so they read as the wide, flat boxes the reference schematic uses,
    // rather than scaling up in lock-step with the "solo" fields.
    private static final double ADDRESS_BOX_HEIGHT = 46;
    // Much wider padding than FIELD_PADDING, for those same paired address fields: capping their
    // height short only reads as "the reference's wide, flat boxes" if the width grows to match --
    // otherwise they just look like the same box, slightly squashed. Solo fields (Page Table
    // Pointer / Process) keep the normal FIELD_PADDING and their own taller BOX_HEIGHT.
    private static final double ADDRESS_FIELD_PADDING = 90;
    private static final double TITLE_GAP = 20;

    // One Font per scaled size, loaded straight from the bundled .ttf -- see fieldFont().
    private static final Map<Double, Font> FIELD_FONTS = new HashMap<>();

    private FieldBoxes() {
    }

    /**
     * The value font of every field box, at the current UI scale. Loaded straight from the bundled
     * .ttf and applied via Label.setFont() -- see the comment on .field-box-value in
     * light-theme.css for why this isn't a CSS family/weight string: numeric -fx-font-weight against
     * the multi-weight "IBM Plex Mono" @font-face family wasn't reliably selecting anything between
     * Regular and Bold, so SemiBold is loaded directly instead.
     */
    public static Font fieldFont() {
        return FIELD_FONTS.computeIfAbsent(UiScale.font(FIELD_FONT_SIZE), FieldBoxes::sizedFieldFont);
    }

    // The .ttf is registered with the toolkit once (Font.loadFont copies the file out and registers
    // it every time it is called, which is slow to repeat for every UI scale); every other size is
    // then just another instance of the already-registered face.
    private static Font registeredFace;
    private static boolean faceLoadAttempted;

    private static Font sizedFieldFont(double size) {
        if (!faceLoadAttempted) {
            faceLoadAttempted = true;
            registeredFace = Font.loadFont(FieldBoxes.class.getResourceAsStream(FIELD_FONT_RESOURCE), size);
            if (registeredFace != null)
                return registeredFace;
        }
        return registeredFace != null
                ? new Font(registeredFace.getName(), size)
                : Font.font("IBM Plex Mono", javafx.scene.text.FontWeight.BOLD, size);
    }

    public static double fieldPadding() {
        return UiScale.px(FIELD_PADDING);
    }

    public static double minFieldWidth() {
        return UiScale.px(MIN_FIELD_WIDTH);
    }

    /** The "solo" field height (see BOX_HEIGHT). */
    public static double boxHeight() {
        return UiScale.px(BOX_HEIGHT);
    }

    /** The paired VA/PA address field height (see ADDRESS_BOX_HEIGHT). */
    public static double addressBoxHeight() {
        return UiScale.px(ADDRESS_BOX_HEIGHT);
    }

    public static double addressFieldPadding() {
        return UiScale.px(ADDRESS_FIELD_PADDING);
    }

    /** Gap between a field title's top and the field box it labels (the title sits above the box). */
    public static double titleGap() {
        return UiScale.px(TITLE_GAP);
    }

    // Cell for a field that sits directly adjacent to a neighboring field (e.g. Page|Word), sharing a
    // single divider border so the pair reads as one continuous box, with its title rendered separately above.
    // Defaults to the "solo" height/padding; paired address fields should call the explicit overload
    // below with addressBoxHeight()/addressFieldPadding() instead.
    public static Region valueCell(String edgeStyleClass, StringProperty valueProperty, int hexDigits) {
        return valueCell(edgeStyleClass, valueProperty, hexDigits, boxHeight(), fieldPadding());
    }

    public static Region valueCell(String edgeStyleClass, StringProperty valueProperty, int hexDigits, double height, double padding) {
        // Measured via a standalone Text node, not the Label itself -- Label.prefWidth() relies on
        // its Skin, which isn't reliably resolved before the node is ever attached to a live Scene
        // (as is the case here, mid-construction), and silently underestimates. Text.getLayoutBounds()
        // computes straight from font metrics and needs no Scene, the same technique WidthCalculator
        // already uses for the table columns.
        Text sample = new Text("0x" + "0".repeat(hexDigits));
        sample.setFont(fieldFont());
        double width = Math.max(minFieldWidth(), sample.getLayoutBounds().getWidth() + padding);

        Label valueLabel = new Label();
        valueLabel.getStyleClass().add("field-box-value");
        valueLabel.setFont(fieldFont());
        valueLabel.textProperty().bind(valueProperty);

        StackPane cell = new StackPane(valueLabel);
        cell.getStyleClass().addAll("field-box-cell", edgeStyleClass);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefSize(width, height);
        cell.setMinSize(width, height);
        cell.setMaxSize(width, height);
        return cell;
    }

    // Centred over the box it labels rather than left-aligned to its edge -- bound live off the
    // box's own layoutX/width (not just measured once), since PA-side boxes' layoutX is itself a
    // live binding (see PagedMMUTabView's own PA-follows-canvas-width comment) and would otherwise
    // leave the title behind as the box slides. Also re-centres itself if the label's own text ever
    // changes width after the fact.
    public static Label fieldTitle(String text, Region box, double y) {
        Label label = new Label(text);
        label.getStyleClass().add("field-box-title");
        label.setLayoutY(y);
        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> box.getLayoutX() + (box.getWidth() - label.getWidth()) / 2.0,
                box.layoutXProperty(), box.widthProperty(), label.widthProperty()));
        return label;
    }

    public static Label sectionLabel(String text, double x, double y) {
        Label label = new Label(text);
        label.getStyleClass().add("schematic-heading");
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
    }

    public static Label bitLabel(int bits, double x, double y) {
        Label label = new Label(bits + "b");
        label.getStyleClass().add("bit-width-label");
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
    }
}
