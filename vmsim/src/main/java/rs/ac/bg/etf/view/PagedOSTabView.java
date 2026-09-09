package rs.ac.bg.etf.view;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polyline;
import rs.ac.bg.etf.view.os.DiskBlockPopup;
import rs.ac.bg.etf.view.os.FrameTableView;
import rs.ac.bg.etf.view.os.ReplacementQueueView;
import rs.ac.bg.etf.view.os.UserSummaryView;
import rs.ac.bg.etf.view.util.FieldBoxes;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.OsLine;

/**
 * Paged OS tab: a dashboard for the OS's physical-memory management. A scrollable frame
 * table (each row led by an occupancy swatch), the disk transfer that services a page
 * fault (two lit wires + a click-through block popup), the FIFO replacement queue, and a
 * per-user page-table summary. Rebuilt on every step by {@link PagedOSTabViewModel}.
 */
public class PagedOSTabView extends StackPane
{
    private static final double CANVAS_WIDTH = 980;
    private static final double CANVAS_HEIGHT = 760;
    private static final double MARGIN = 30;
    private static final double TABLE_Y = 72;
    private static final double TABLE_X = MARGIN;
    private static final double TABLE_W = 560;
    /** Frame rows visible before the table scrolls. */
    private static final int VISIBLE_ROWS = 15;
    private static final double DISK_BOX_W = 168;

    private static final javafx.css.PseudoClass ACTIVE = javafx.css.PseudoClass.getPseudoClass("active");

    private final Pane canvas = new Pane();

    public PagedOSTabView(PagedOSTabViewModel viewModel)
    {
        getStyleClass().add("os-tab-container");
        canvas.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);

        // ---- Frame table (scrollable; swatch column is the occupancy strip) ---------
        Label memHeader = FieldBoxes.sectionLabel("Physical Memory (frames)", TABLE_X, TABLE_Y - 26);

        // header row + N data rows + panel padding/border
        double tableHeight = FrameTableView.ROW_HEIGHT * (VISIBLE_ROWS + 1) + 22;

        FrameTableView table = new FrameTableView(viewModel);
        table.setLayoutX(TABLE_X);
        table.setLayoutY(TABLE_Y);
        table.setPrefSize(TABLE_W, tableHeight);
        table.setMinHeight(tableHeight);
        table.setMaxHeight(tableHeight);

        // ---- Disk box ---------------------------------------------------------------
        DiskBlockPopup popup = new DiskBlockPopup(viewModel);

        Label diskTitle = new Label("Disk");
        diskTitle.getStyleClass().add("mmu-section-label");
        Region diskAddressCell = FieldBoxes.valueCell(
                "va-breakdown-cell-solo", viewModel.diskAddressHexProperty(), viewModel.diskHexDigitsProperty().get());
        Label diskSummary = new Label();
        diskSummary.textProperty().bind(viewModel.diskBlockSummaryProperty());
        diskSummary.getStyleClass().add("os-disk-summary");
        Label diskHint = new Label("click for block contents");
        diskHint.getStyleClass().add("os-disk-summary");

        VBox diskBox = new VBox(6, diskTitle, diskAddressCell, diskSummary, diskHint);
        diskBox.getStyleClass().add("os-disk-box");
        diskBox.setAlignment(Pos.CENTER);
        diskBox.setPrefWidth(DISK_BOX_W);
        diskBox.setLayoutX(CANVAS_WIDTH - MARGIN - DISK_BOX_W);
        diskBox.setLayoutY(TABLE_Y + tableHeight / 2 - 70);
        diskBox.setCursor(javafx.scene.Cursor.HAND);
        diskBox.setOnMouseClicked(e -> popup.toggle(getScene() != null ? getScene().getWindow() : null));
        bindOpacity(diskBox, viewModel.diskEngagedProperty());

        // ---- Disk <-> active frame row wires ---------------------------------------
        Polyline loadWire = connector();
        Polyline loadArrow = connector();
        Label loadCaption = wireLabel("load page");
        Label loadValue = boundWireLabel(viewModel.loadPageValueHexProperty());

        Polyline writeWire = connector();
        Polyline writeArrow = connector();
        Label writeCaption = wireLabel("write back");
        Label writeValue = boundWireLabel(viewModel.writeBackValueHexProperty());

        bindVisible(viewModel.lineActiveProperty(OsLine.LOAD_PAGE), loadWire, loadArrow, loadCaption, loadValue);
        bindVisible(viewModel.lineActiveProperty(OsLine.WRITE_BACK), writeWire, writeArrow, writeCaption, writeValue);
        bindActive(loadWire, viewModel.lineActiveProperty(OsLine.LOAD_PAGE));
        bindActive(loadArrow, viewModel.lineActiveProperty(OsLine.LOAD_PAGE));
        bindActive(loadCaption, viewModel.lineActiveProperty(OsLine.LOAD_PAGE));
        bindActive(loadValue, viewModel.lineActiveProperty(OsLine.LOAD_PAGE));
        bindActive(writeWire, viewModel.lineActiveProperty(OsLine.WRITE_BACK));
        bindActive(writeArrow, viewModel.lineActiveProperty(OsLine.WRITE_BACK));
        bindActive(writeCaption, viewModel.lineActiveProperty(OsLine.WRITE_BACK));
        bindActive(writeValue, viewModel.lineActiveProperty(OsLine.WRITE_BACK));

        // ---- FIFO replacement queue ----------------------------------------------
        double fifoY = TABLE_Y + tableHeight + 34;
        Label fifoHeader = FieldBoxes.sectionLabel("Replacement queue (FIFO)  —  oldest → newest", TABLE_X, fifoY - 22);
        ReplacementQueueView fifo = new ReplacementQueueView(viewModel);
        fifo.setLayoutX(TABLE_X);
        fifo.setLayoutY(fifoY);

        Label nextVictimLabel = new Label();
        nextVictimLabel.getStyleClass().add("os-disk-summary");
        nextVictimLabel.textProperty().bind(viewModel.nextVictimHexProperty()
                .map(hex -> "/".equals(hex) ? "" : "next victim: frame " + hex));
        nextVictimLabel.setLayoutX(TABLE_X);
        nextVictimLabel.setLayoutY(fifoY + 32);

        // ---- Per-user page-table summary ---------------------------------------
        UserSummaryView summary = new UserSummaryView(viewModel);
        ScrollPane summaryScroll = new ScrollPane(summary);
        summaryScroll.setFitToWidth(true);
        summaryScroll.getStyleClass().add("os-user-summary-scroll");
        summaryScroll.setLayoutX(TABLE_X);
        summaryScroll.setLayoutY(fifoY + 64);
        summaryScroll.setPrefViewportHeight(150);
        summaryScroll.setMaxHeight(220);
        summaryScroll.setPrefWidth(460);

        canvas.getChildren().addAll(
                memHeader, table, diskBox,
                loadWire, loadArrow, loadCaption, loadValue,
                writeWire, writeArrow, writeCaption, writeValue,
                fifoHeader, fifo, nextVictimLabel, summaryScroll);

        Runnable updateWires = () -> updateWires(
                table, diskBox,
                loadWire, loadArrow, loadCaption, loadValue,
                writeWire, writeArrow, writeCaption, writeValue);

        table.activeRowAnchorProperty().addListener((o, ov, nv) -> Platform.runLater(updateWires));
        diskBox.boundsInParentProperty().addListener((o, ov, nv) -> Platform.runLater(updateWires));
        canvas.widthProperty().addListener((o, ov, nv) -> Platform.runLater(updateWires));
        canvas.heightProperty().addListener((o, ov, nv) -> Platform.runLater(updateWires));
        sceneProperty().addListener((o, ov, nv) -> Platform.runLater(updateWires));
        Platform.runLater(updateWires);

        // Grow the canvas so the ScrollPane can reach the summary if there are many users.
        summaryScroll.boundsInParentProperty().addListener((o, ov, nv) ->
                canvas.setPrefHeight(Math.max(CANVAS_HEIGHT, nv.getMaxY() + 30)));

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.getStyleClass().add("mmu-scroll-pane");
        getChildren().add(scrollPane);
    }

    private void updateWires(
            FrameTableView table, Region diskBox,
            Polyline loadWire, Polyline loadArrow, Label loadCaption, Label loadValue,
            Polyline writeWire, Polyline writeArrow, Label writeCaption, Label writeValue)
    {
        Region rowAnchor = table.activeRowAnchorProperty().get();
        if (rowAnchor == null || rowAnchor.getScene() == null || diskBox.getScene() == null)
            return;

        Bounds row = canvas.sceneToLocal(rowAnchor.localToScene(rowAnchor.getBoundsInLocal()));
        Bounds disk = canvas.sceneToLocal(diskBox.localToScene(diskBox.getBoundsInLocal()));

        double rowX = row.getMaxX();
        double rowY = row.getCenterY();
        double diskX = disk.getMinX();
        double diskY = disk.getCenterY();
        double midX = (rowX + diskX) / 2;

        loadWire.getPoints().setAll(diskX, diskY - 6, midX, diskY - 6, midX, rowY - 6, rowX, rowY - 6);
        loadArrow.getPoints().setAll(rowX + 9, rowY - 11, rowX, rowY - 6, rowX + 9, rowY - 1);

        writeWire.getPoints().setAll(rowX, rowY + 6, midX, rowY + 6, midX, diskY + 6, diskX, diskY + 6);
        writeArrow.getPoints().setAll(diskX - 9, diskY + 1, diskX, diskY + 6, diskX - 9, diskY + 11);

        loadCaption.setLayoutX(midX - loadCaption.prefWidth(-1) - 6);
        loadCaption.setLayoutY((diskY + rowY) / 2 - 24);
        loadValue.setLayoutX(midX + 6);
        loadValue.setLayoutY((diskY + rowY) / 2 - 24);

        writeCaption.setLayoutX(midX - writeCaption.prefWidth(-1) - 6);
        writeCaption.setLayoutY((diskY + rowY) / 2 + 8);
        writeValue.setLayoutX(midX + 6);
        writeValue.setLayoutY((diskY + rowY) / 2 + 8);

        for (Node n : new Node[] { loadWire, loadArrow, loadCaption, loadValue, writeWire, writeArrow, writeCaption, writeValue })
            n.toFront();
    }

    private Polyline connector()
    {
        Polyline p = new Polyline();
        p.getStyleClass().add("connector-line");
        return p;
    }

    private Label wireLabel(String text)
    {
        Label label = new Label(text);
        label.getStyleClass().add("os-wire-label");
        return label;
    }

    private Label boundWireLabel(javafx.beans.value.ObservableValue<String> value)
    {
        Label label = new Label();
        label.textProperty().bind(value);
        label.getStyleClass().add("mmu-bit-value");
        return label;
    }

    private void bindVisible(BooleanProperty source, Node... nodes)
    {
        for (Node n : nodes)
        {
            n.visibleProperty().bind(source);
            n.managedProperty().bind(source);
        }
    }

    private void bindOpacity(Node node, BooleanProperty engaged)
    {
        node.setOpacity(engaged.get() ? 1.0 : 0.35);
        engaged.addListener((o, ov, nv) -> node.setOpacity(nv ? 1.0 : 0.35));
    }

    private void bindActive(Node node, BooleanProperty active)
    {
        node.pseudoClassStateChanged(ACTIVE, active.get());
        active.addListener((o, ov, nv) -> node.pseudoClassStateChanged(ACTIVE, nv));
    }
}
