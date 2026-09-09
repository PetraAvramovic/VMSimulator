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
        this.getStyleClass().add("page-table-row");
        
        this.setPrefHeight(24.0);
        this.setMinHeight(24.0);
        this.setMaxHeight(24.0);

        indexLabel = cell("", 60);
        vLabel = cell("", 30);
        dLabel = cell("", 30);
        tagLabel = cell("", WidthCalculator.columnWidth("Tag", tagHexWidth));

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
        getStyleClass().add("page-table-header");
    }

    public void setShowIndex(boolean show) {
        showIndex.set(show);
    }

    public BooleanProperty showIndexProperty() {
        return showIndex;
    }

    protected Label cell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("page-table-cell");
        
        
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }
}