package rs.ac.bg.etf.view.util;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * Shared factory for the address-breakdown field boxes and their satellite labels used by the
 * paged MMU and TLB schematic tabs, so both tabs size and style them identically.
 */
public final class FieldBoxes {
    // Must match .va-breakdown-value's actual CSS weight/size, otherwise width estimates undershoot
    // the real rendered text and values get clipped to an ellipsis.
    public static final Font FIELD_FONT = Font.font("Consolas", FontWeight.BOLD, 15);
    public static final double FIELD_PADDING = 40;
    public static final double MIN_FIELD_WIDTH = 70;
    // Boxes are forced to this exact height (padding 10px*2 + border 2px*2 + title/value text) so the
    // fixed-coordinate connector lines below always line up with the real rendered box edges.
    public static final double BOX_HEIGHT = 62;

    private FieldBoxes() {
    }

    // Cell for a field that sits directly adjacent to a neighboring field (e.g. Page|Word), sharing a
    // single divider border so the pair reads as one continuous box, with its title rendered separately above.
    public static Region valueCell(String edgeStyleClass, StringProperty valueProperty, int hexDigits) {
        // Measured via a standalone Text node, not the Label itself -- Label.prefWidth() relies on
        // its Skin, which isn't reliably resolved before the node is ever attached to a live Scene
        // (as is the case here, mid-construction), and silently underestimates. Text.getLayoutBounds()
        // computes straight from font metrics and needs no Scene, the same technique WidthCalculator
        // already uses for the table columns.
        Text sample = new Text("0x" + "0".repeat(hexDigits));
        sample.setFont(FIELD_FONT);
        double width = Math.max(MIN_FIELD_WIDTH, sample.getLayoutBounds().getWidth() + FIELD_PADDING);

        Label valueLabel = new Label();
        valueLabel.getStyleClass().add("va-breakdown-value");
        valueLabel.setFont(FIELD_FONT);
        valueLabel.textProperty().bind(valueProperty);

        StackPane cell = new StackPane(valueLabel);
        cell.getStyleClass().addAll("va-breakdown-cell", edgeStyleClass);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefSize(width, BOX_HEIGHT);
        cell.setMinSize(width, BOX_HEIGHT);
        cell.setMaxSize(width, BOX_HEIGHT);
        return cell;
    }

    public static Label fieldTitle(String text, double x, double y) {
        Label label = new Label(text);
        label.getStyleClass().add("va-breakdown-title");
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
    }

    public static Label sectionLabel(String text, double x, double y) {
        Label label = new Label(text);
        label.getStyleClass().add("mmu-section-label");
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
    }

    public static Label bitLabel(int bits, double x, double y) {
        Label label = new Label(bits + "b");
        label.getStyleClass().add("mmu-bit-width");
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
    }
}
