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
import javafx.scene.shape.Rectangle;
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
    private static final double CANVAS_WIDTH = 880;
    private static final double CANVAS_HEIGHT = 480;
    private static final double MARGIN = 30;
    private static final double TABLE_Y = 72;
    private static final double TABLE_X = MARGIN;
    /** Initial gap between the frame table and the right-hand column (before responsive layout). */
    private static final double COLUMN_GAP = 200;
    /** Minimum table-to-disk-box gap, so the wire captions always fit. */
    private static final double WIRE_GAP = 200;
    /** The disk box's "platter stack" motif: a few short cylinders behind the address readout. */
    private static final int DISK_PLATTER_COUNT = 3;
    private static final double DISK_PLATTER_HEIGHT = 22;
    private static final double DISK_PLATTER_GAP = 8;

    private static final javafx.css.PseudoClass ACTIVE = javafx.css.PseudoClass.getPseudoClass("active");

    private final Pane canvas = new Pane();

    public PagedOSTabView(PagedOSTabViewModel viewModel)
    {
        getStyleClass().add("os-tab-container");
        canvas.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);

        // ---- Frame table (scrollable; swatch column is the occupancy strip) ---------
        Label memHeader = FieldBoxes.sectionLabel("Frame table", TABLE_X, TABLE_Y - 26);

        FrameTableView table = new FrameTableView(viewModel);
        table.setLayoutX(TABLE_X);
        table.setLayoutY(TABLE_Y);
        double tableHeight = table.panelHeight();

        // Everything else stacks in a column immediately to the right of the table.
        double rightX = TABLE_X + table.panelWidth() + COLUMN_GAP;

        // ---- Disk box (right column, top) -----------------------------------------
        DiskBlockPopup popup = new DiskBlockPopup(viewModel);

        Label diskTitle = new Label("Disk");
        diskTitle.getStyleClass().add("mmu-section-label");

        Label addressTitle = new Label("Address");
        addressTitle.getStyleClass().add("va-breakdown-title");

        int diskDigits = Math.max(1, viewModel.diskHexDigitsProperty().get());
        Region diskAddressCell = FieldBoxes.valueCell(
                "va-breakdown-cell-solo", viewModel.diskAddressHexProperty(), diskDigits);
        // FieldBoxes sizes the cell from a detached Label.prefWidth, which under-measures a wide
        // (8-hex-digit) disk address; re-size it from a real Text measurement so it never clips.
        double addrCellW = textWidth("0x" + "F".repeat(diskDigits), FieldBoxes.FIELD_FONT) + FieldBoxes.FIELD_PADDING;
        diskAddressCell.setMinWidth(addrCellW);
        diskAddressCell.setPrefWidth(addrCellW);
        diskAddressCell.setMaxWidth(addrCellW);

        // The platter stack is a bit wider than the address cell it frames, so it reads as the
        // disk "body" the reading sits inside rather than a same-size box behind an identical box.
        double platterWidth = addrCellW + 40;
        double platterStackHeight = DISK_PLATTER_COUNT * DISK_PLATTER_HEIGHT
                + (DISK_PLATTER_COUNT - 1) * DISK_PLATTER_GAP;
        Node platterStack = diskPlatterStack(platterWidth);

        // "Address" sits above this StackPane (like the Page/Word titles above their value cells
        // elsewhere) rather than inside it, so it never lands on top of the topmost platter.
        StackPane diskGraphic = new StackPane(platterStack, diskAddressCell);
        diskGraphic.setPrefSize(platterWidth, Math.max(platterStackHeight, diskAddressCell.prefHeight(-1)));

        Label diskSummary = new Label();
        diskSummary.textProperty().bind(viewModel.diskBlockSummaryProperty());
        diskSummary.getStyleClass().add("os-disk-summary");

        VBox diskBox = new VBox(6, diskTitle, addressTitle, diskGraphic, diskSummary);
        diskBox.getStyleClass().add("os-disk-box");
        diskBox.setAlignment(Pos.CENTER);
        // Size to the widest of the platter stack and the summary text, so nothing clips.
        double diskBoxW = Math.max(platterWidth, textWidth("no non-zero data present")) + 32;
        diskBox.setPrefWidth(diskBoxW);
        diskBox.setMinWidth(diskBoxW);
        diskBox.setLayoutX(rightX);
        diskBox.setLayoutY(TABLE_Y);
        diskBox.setCursor(javafx.scene.Cursor.HAND);
        diskBox.setOnMouseClicked(e -> popup.toggle(getScene() != null ? getScene().getWindow() : null));
        // Clickability now reads through the :hover style (see light-theme.css) instead of a
        // permanent "click for..." caption competing with the disk-block summary for attention.
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

        // ---- Eviction policy (right column, below the disk box) ------------------
        Label fifoHeader = FieldBoxes.sectionLabel("Eviction policy (FIFO)", rightX, TABLE_Y);
        double rightColW = Math.max(diskBoxW, 300);
        ReplacementQueueView fifo = new ReplacementQueueView(viewModel);
        fifo.setPrefWrapLength(rightColW);
        fifo.setMaxWidth(rightColW);

        Label nextVictimLabel = new Label();
        nextVictimLabel.getStyleClass().add("os-disk-summary");
        nextVictimLabel.textProperty().bind(viewModel.nextVictimHexProperty()
                .map(hex -> "/".equals(hex) ? "" : "next victim: frame " + hex));

        // ---- Per-user page-table summary (right column, bottom) -----------------
        // User count is small (power of two, validated) so the panel just renders inline;
        // the whole tab already scrolls via the outer ScrollPane.
        UserSummaryView summary = new UserSummaryView(viewModel);

        canvas.getChildren().addAll(
                memHeader, table, diskBox,
                loadWire, loadArrow, loadCaption, loadValue,
                writeWire, writeArrow, writeCaption, writeValue,
                fifoHeader, fifo, nextVictimLabel, summary);

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.getStyleClass().addAll("mmu-scroll-pane", "slim-scroll");
        getChildren().add(scrollPane);

        Runnable updateWires = () -> updateWires(
                table, diskBox,
                loadWire, loadArrow, loadCaption, loadValue,
                writeWire, writeArrow, writeCaption, writeValue);

        // Responsive layout: the right column is pushed toward the right edge of the
        // viewport (so the tab isn't a narrow strip with a big void), but never closer
        // than WIRE_GAP to the table, so the wire captions always have room.
        double tableRight = TABLE_X + table.panelWidth();
        Runnable relayout = () -> {
            double viewportW = scrollPane.getViewportBounds() != null
                    ? scrollPane.getViewportBounds().getWidth() : CANVAS_WIDTH;
            double colX = Math.max(tableRight + WIRE_GAP, viewportW - MARGIN - rightColW);

            for (javafx.scene.Node n : new javafx.scene.Node[] { diskBox, fifoHeader, fifo, nextVictimLabel, summary })
                n.setLayoutX(colX);

            // layoutBounds, not boundsInParent: effect-immune, so the disk box's :hover drop-shadow
            // never nudges the sections stacked below it.
            double y = diskBox.getLayoutY() + diskBox.getLayoutBounds().getHeight() + 30;
            fifoHeader.setLayoutY(y);
            y += 22;
            fifo.setLayoutY(y);
            y += Math.max(fifo.getLayoutBounds().getHeight(), 26) + 8;
            nextVictimLabel.setLayoutY(y);
            y += 26;
            summary.setLayoutY(y);

            double contentBottom = Math.max(table.getLayoutY() + tableHeight,
                    summary.getLayoutY() + summary.getLayoutBounds().getHeight());
            double contentRight = colX + rightColW;
            canvas.setPrefWidth(Math.max(viewportW, contentRight + MARGIN));
            canvas.setPrefHeight(Math.max(scrollPane.getViewportBounds() != null
                    ? scrollPane.getViewportBounds().getHeight() : CANVAS_HEIGHT, contentBottom + MARGIN));
            updateWires.run();
        };

        // layoutBounds, not boundsInParent: only a real size change (content, not a hover effect)
        // should trigger a relayout.
        table.activeRowAnchorProperty().addListener((o, ov, nv) -> Platform.runLater(updateWires));
        diskBox.layoutBoundsProperty().addListener((o, ov, nv) -> Platform.runLater(relayout));
        fifo.layoutBoundsProperty().addListener((o, ov, nv) -> Platform.runLater(relayout));
        summary.layoutBoundsProperty().addListener((o, ov, nv) -> Platform.runLater(relayout));
        scrollPane.viewportBoundsProperty().addListener((o, ov, nv) -> Platform.runLater(relayout));
        sceneProperty().addListener((o, ov, nv) -> Platform.runLater(relayout));
        Platform.runLater(relayout);
    }

    private void updateWires(
            FrameTableView table, Region diskBox,
            Polyline loadWire, Polyline loadArrow, Label loadCaption, Label loadValue,
            Polyline writeWire, Polyline writeArrow, Label writeCaption, Label writeValue)
    {
        Region rowAnchor = table.activeRowAnchorProperty().get();
        if (rowAnchor == null || rowAnchor.getScene() == null || diskBox.getScene() == null)
            return;

        // layoutBounds (not boundsInLocal/boundsInParent): the disk box's :hover drop-shadow
        // would otherwise inflate its effective bounds asymmetrically (the shadow's Y offset),
        // shifting the computed anchor and visibly nudging the wire on every hover.
        Bounds row = canvas.sceneToLocal(rowAnchor.localToScene(rowAnchor.getLayoutBounds()));
        Bounds tableBox = canvas.sceneToLocal(table.localToScene(table.getLayoutBounds()));
        Bounds disk = canvas.sceneToLocal(diskBox.localToScene(diskBox.getLayoutBounds()));

        // X terminates on the table panel's outer edge (arrowhead against the border); Y
        // tracks the active row's centre. A knee near each end keeps the long horizontal
        // run -- where the caption rides -- clear of both boxes.
        double rowX = tableBox.getMaxX();
        double rowY = row.getCenterY();
        double diskX = disk.getMinX();
        double diskY = disk.getCenterY();
        double span = diskX - rowX;
        double loadKneeX = rowX + span * 0.34;
        double writeKneeX = rowX + span * 0.66;

        loadWire.getPoints().setAll(diskX, diskY - 7, loadKneeX, diskY - 7, loadKneeX, rowY - 7, rowX, rowY - 7);
        loadArrow.getPoints().setAll(rowX + 9, rowY - 12, rowX, rowY - 7, rowX + 9, rowY - 2);

        writeWire.getPoints().setAll(rowX, rowY + 7, writeKneeX, rowY + 7, writeKneeX, diskY + 7, diskX, diskY + 7);
        writeArrow.getPoints().setAll(diskX - 9, diskY + 2, diskX, diskY + 7, diskX - 9, diskY + 12);

        // load caption + value stack, centred on the run near the disk box, fully above it
        double loadMid = (loadKneeX + diskX) / 2;
        centre(loadCaption, loadMid, diskY - 45);
        centre(loadValue, loadMid, diskY - 29);
        // write caption + value stack, centred on the run near the table, fully below it
        double writeMid = (rowX + writeKneeX) / 2;
        centre(writeCaption, writeMid, rowY + 13);
        centre(writeValue, writeMid, rowY + 29);

        for (Node n : new Node[] { loadWire, loadArrow, loadCaption, loadValue, writeWire, writeArrow, writeCaption, writeValue })
            n.toFront();
    }

    private static void centre(Label label, double cx, double y)
    {
        label.setLayoutX(cx - label.prefWidth(-1) / 2);
        label.setLayoutY(y);
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

    /**
     * A few short "platters" stacked with small gaps -- a classic disk-drum motif standing in
     * for the disk box's plain rectangle. Purely decorative background behind the address
     * readout (see the disk box's StackPane); fill/stroke live in .os-disk-platter (light-theme.css).
     */
    private static Node diskPlatterStack(double width)
    {
        VBox stack = new VBox(DISK_PLATTER_GAP);
        stack.setAlignment(Pos.CENTER);
        for (int i = 0; i < DISK_PLATTER_COUNT; i++)
        {
            Rectangle platter = new Rectangle(width, DISK_PLATTER_HEIGHT);
            platter.setArcWidth(DISK_PLATTER_HEIGHT);
            platter.setArcHeight(DISK_PLATTER_HEIGHT);
            platter.getStyleClass().add("os-disk-platter");
            stack.getChildren().add(platter);
        }
        return stack;
    }

    // Rendered width of an .os-disk-summary label (Consolas 11), for sizing the disk box to its content.
    private static double textWidth(String text)
    {
        return textWidth(text, javafx.scene.text.Font.font("Consolas", 11));
    }

    private static double textWidth(String text, javafx.scene.text.Font font)
    {
        javafx.scene.text.Text sample = new javafx.scene.text.Text(text);
        sample.setFont(font);
        return sample.getLayoutBounds().getWidth();
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
        node.setOpacity(engaged.get() ? 1.0 : 0.5);
        engaged.addListener((o, ov, nv) -> node.setOpacity(nv ? 1.0 : 0.5));
    }

    private void bindActive(Node node, BooleanProperty active)
    {
        node.pseudoClassStateChanged(ACTIVE, active.get());
        active.addListener((o, ov, nv) -> node.pseudoClassStateChanged(ACTIVE, nv));
    }
}
