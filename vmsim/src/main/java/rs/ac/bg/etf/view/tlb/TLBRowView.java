package rs.ac.bg.etf.view.tlb;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import rs.ac.bg.etf.view.util.WidthCalculator;

public abstract class TLBRowView extends HBox
{
    protected final Label indexLabel;
    protected final Label vLabel;
    protected final Label dLabel;
    protected final Label tagLabel;
    protected final BooleanProperty showIndex = new SimpleBooleanProperty(true);


    public TLBRowView(int tagHexWidth)
    {
        super();
        this.setAlignment(Pos.CENTER_LEFT);
        this.getStyleClass().add("data-table-row");

        // No fixed pixel height: the row sizes itself from font metrics + the CSS row padding (see
        // light-theme.css's ".data-table-schematic > .tlb-table-rows > .data-table-row"), the same way
        // PageTableView's own rows do -- matches the MMU schematic's table exactly, including when
        // the enlarged 19px font is applied.
        indexLabel = cell("", WidthCalculator.largeIndexColumnWidth());
        vLabel = cell("", WidthCalculator.largeBitColumnWidth());
        dLabel = cell("", WidthCalculator.largeBitColumnWidth());
        tagLabel = cell("", WidthCalculator.columnWidth("Tag", tagHexWidth, WidthCalculator.LARGE_CELL_FONT_SIZE));

        indexLabel.visibleProperty().bind(showIndex);
        indexLabel.managedProperty().bind(showIndex);

        this.getChildren().addAll(indexLabel, vLabel, dLabel, tagLabel);

    }


    protected void updateCommonFields(int index, boolean valid, boolean dirty, String tagHex) {
        this.indexLabel.setText(String.valueOf(index));
        this.vLabel.setText(valid ? "1" : "0");
        this.dLabel.setText(dirty ? "1" : "0");
        this.tagLabel.setText(tagHex);
    }

    protected void setCommonHeaderLabels(String indexText, String vText, String dText, String tagText) {
        this.indexLabel.setText(indexText);
        this.vLabel.setText(vText);
        this.dLabel.setText(dText);
        this.tagLabel.setText(tagText);
        // A header row isn't a body row -- drop "data-table-row" so its lighter grey underline
        // and padding don't fight .data-table-header's own (darker, differently padded) styling.
        getStyleClass().remove("data-table-row");
        getStyleClass().add("data-table-header");
    }

    public void setShowIndex(boolean show) {
        showIndex.set(show);
    }

    public BooleanProperty showIndexProperty() {
        return showIndex;
    }

    protected Label cell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("data-table-cell");
        
        
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }
}