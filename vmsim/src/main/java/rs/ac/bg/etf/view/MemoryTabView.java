package rs.ac.bg.etf.view;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Polyline;

import rs.ac.bg.etf.view.inspector.MemoryInspectorWindow;
import rs.ac.bg.etf.view.memory.MemoryTableView;
import rs.ac.bg.etf.view.shape.BitWidthLine;
import rs.ac.bg.etf.view.util.FieldBoxes;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.viewmodel.MemoryTabViewModel;

/**
 * Memory tab: a Physical Address field box wired straight down into a windowed schematic table of
 * physical memory (fogged until an address has actually been formed), mirroring the MMU tab's own
 * page-table schematic. Clicking the table opens a separate, independently-scrollable/seekable
 * full-memory browser window, seeded on whichever address the schematic is currently centred on.
 */
public class MemoryTabView extends StackPane
{
    private static final double CANVAS_HEIGHT = 700;
    private static final double MARGIN = 30;
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    private final Pane canvas = new Pane();

    public MemoryTabView(MemoryTabViewModel viewModel)
    {
        getStyleClass().add("mmu-tab-container");
        canvas.setPrefHeight(CANVAS_HEIGHT);

        double boxY = 50;
        double boxX = MARGIN;
        double tableY = boxY + FieldBoxes.BOX_HEIGHT + 90;

        Region paBox = FieldBoxes.valueCell(
                "va-breakdown-cell-solo", viewModel.physicalAddressHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getPhysicalAddressBits()));
        double paBoxWidth = paBox.getPrefWidth();
        paBox.setLayoutX(boxX);
        paBox.setLayoutY(boxY);

        // Left-aligned above its own field, matching every other tab's title placement -- the
        // accented section-header style (matching the MMU tab's "Virtual Address"/"Physical
        // Address" headers), not the small per-field title used for Page/Word/Block.
        Label paTitle = FieldBoxes.sectionLabel("Physical Address", boxX, boxY - 24);

        BitWidthLine addressWire = new BitWidthLine();
        addressWire.bitsProperty().set(viewModel.getPhysicalAddressBits());
        addressWire.labelOnLeftProperty().set(false);
        addressWire.arrowTipVisibleProperty().set(false);
        addressWire.startXProperty().set(boxX + paBoxWidth / 2);
        addressWire.startYProperty().set(boxY + FieldBoxes.BOX_HEIGHT);
        addressWire.endXProperty().bind(addressWire.startXProperty());
        addressWire.endYProperty().set(tableY - 20);

        // Only the table itself is centred horizontally in the (viewport-width-tracking) canvas --
        // the PA box and both titles stay left-anchored like every other tab's fields.
        MemoryTableView memoryTableView = new MemoryTableView(viewModel);
        memoryTableView.setLayoutY(tableY);
        memoryTableView.layoutXProperty().bind(canvas.widthProperty().subtract(memoryTableView.widthProperty()).divide(2));
        memoryTableView.getStyleClass().add("page-table-clickable");
        memoryTableView.setCursor(Cursor.HAND);

        Label tableTitle = FieldBoxes.sectionLabel("Memory", 0, tableY - 24);
        tableTitle.layoutXProperty().bind(memoryTableView.layoutXProperty());

        MemoryInspectorWindow inspector = new MemoryInspectorWindow(viewModel.getContext(), viewModel.currentStepNumberProperty());

        canvas.getChildren().addAll(paTitle, paBox, addressWire, tableTitle, memoryTableView);

        Polyline addressToRowLine = elbow();
        canvas.getChildren().add(addressToRowLine);

        Runnable updateConnector = () -> updateConnector(memoryTableView, addressWire, addressToRowLine);
        memoryTableView.currentEntryAnchorProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(updateConnector));
        memoryTableView.layoutXProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(updateConnector));
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(updateConnector));
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(updateConnector));
        Platform.runLater(updateConnector);

        bindActive(addressWire, viewModel.memoryAddressedProperty());
        bindActive(addressToRowLine, viewModel.memoryAddressedProperty());

        memoryTableView.setOnMouseClicked(e -> inspector.toggle(
                memoryTableView.getScene() != null ? memoryTableView.getScene().getWindow() : null,
                viewModel.getWindowCenterAddress()));

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.getStyleClass().addAll("mmu-scroll-pane", "slim-scroll");
        // The canvas's width tracks the actual viewport (not a fixed constant), so "centred in the
        // canvas" (the table's layoutX binding above) really means "centred in the visible tab".
        scrollPane.setFitToWidth(true);

        getChildren().add(scrollPane);
    }

    private void updateConnector(MemoryTableView memoryTableView, BitWidthLine addressWire, Polyline addressToRowLine)
    {
        Region rowAnchor = memoryTableView.currentEntryAnchorProperty().get();
        if (rowAnchor == null || rowAnchor.getScene() == null)
            return;

        double sourceX = addressWire.endXProperty().get();
        double sourceY = addressWire.endYProperty().get();

        Bounds rowBounds = canvas.sceneToLocal(rowAnchor.localToScene(rowAnchor.getBoundsInLocal()));
        double targetY = rowBounds.getCenterY();
        double tableLeftX = memoryTableView.getLayoutX();

        addressToRowLine.getPoints().setAll(
                sourceX, sourceY,
                sourceX, targetY,
                tableLeftX, targetY);
        addressToRowLine.toFront();
    }

    private Polyline elbow(double... points)
    {
        Polyline polyline = new Polyline(points);
        polyline.getStyleClass().add("connector-line");
        return polyline;
    }

    private void bindActive(javafx.scene.Node node, javafx.beans.property.BooleanProperty active)
    {
        node.pseudoClassStateChanged(ACTIVE, active.get());
        active.addListener((obs, oldVal, newVal) -> node.pseudoClassStateChanged(ACTIVE, newVal));
    }

    private void bindActive(BitWidthLine line, javafx.beans.property.BooleanProperty active)
    {
        bindActive(line.getWire(), active);
        bindActive(line.getArrowHead(), active);
        bindActive(line.getTick(), active);
        bindActive(line.getBitsLabel(), active);
    }
}
