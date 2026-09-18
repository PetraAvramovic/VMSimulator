package rs.ac.bg.etf.view;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
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
import rs.ac.bg.etf.view.inspector.DiskBlockInspectorWindow;
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
        DiskBlockInspectorWindow diskInspector =
                new DiskBlockInspectorWindow(viewModel.getContext(), viewModel.currentStepNumberProperty());

        // "Disk" sits above the card, left-aligned, the same treatment "Frame table" and
        // "Eviction policy (FIFO)" get for their own panels below -- not stacked inside the card
        // itself, which read as part of the card's own data rather than a heading for it.
        Label diskHeader = FieldBoxes.sectionLabel("Disk", rightX, TABLE_Y - 26);

        Label addressTitle = new Label("Address");
        addressTitle.getStyleClass().add("va-breakdown-title");

        // Plain value text, not a bordered cell nested inside the disk card -- a box-within-a-box
        // read as if the address had some separate identity from the disk itself. The card's own
        // border (.os-disk-box, the same flat framed-card style as the MMU tab's standalone value
        // boxes) is the only box now; the address is just its value, like Current VA/PA in the
        // sidebar.
        int diskDigits = Math.max(1, viewModel.diskHexDigitsProperty().get());
        Label diskAddressValue = new Label();
        diskAddressValue.getStyleClass().add("va-breakdown-value");
        diskAddressValue.setFont(FieldBoxes.FIELD_FONT);
        diskAddressValue.textProperty().bind(viewModel.diskAddressHexProperty());

        VBox diskBox = new VBox(6, addressTitle, diskAddressValue);
        diskBox.getStyleClass().add("os-disk-box");
        diskBox.setAlignment(Pos.CENTER);
        // Sized to the address value alone (measured the same way FieldBoxes' own boxes are, so a
        // wide 8-hex-digit address never clips).
        double addrTextW = textWidth("0x" + "F".repeat(diskDigits), FieldBoxes.FIELD_FONT);
        double diskBoxW = addrTextW + 32;
        diskBox.setPrefWidth(diskBoxW);
        diskBox.setMinWidth(diskBoxW);
        diskBox.setLayoutY(TABLE_Y);
        // Only while a transfer is actually in flight is there a real block to open. The cursor
        // and the CSS :hover cue (see .os-disk-box:active:hover in light-theme.css) both only
        // engage while diskEngaged is true too, so an inactive box gives no "this is clickable"
        // signal at all -- hovering it and having nothing happen on click read as broken.
        diskBox.cursorProperty().bind(Bindings.when(viewModel.diskEngagedProperty())
                .then(javafx.scene.Cursor.HAND).otherwise(javafx.scene.Cursor.DEFAULT));
        diskBox.setOnMouseClicked(e -> {
            long address = viewModel.getCurrentDiskAddress();
            if (address >= 0)
                diskInspector.toggle(getScene() != null ? getScene().getWindow() : null, address);
        });
        // Lit (border turns blue) while a load/write step is the current step, the same :active
        // convention every wire/value in the schematic tabs uses -- not dimmed the rest of the
        // time, which read as "disabled" rather than "nothing to show right now".
        bindActive(diskBox, viewModel.diskEngagedProperty());

        // ---- Disk <-> active frame row wires ---------------------------------------
        Polyline loadWire = connector();
        Polyline loadArrow = connector();
        Label loadCaption = wireLabel("load page");
        Label loadValue = boundWireLabel(viewModel.loadPageValueHexProperty());

        Polyline writeWire = connector();
        Polyline writeArrow = connector();
        Label writeCaption = wireLabel("write back");
        Label writeValue = boundWireLabel(viewModel.writeBackValueHexProperty());

        // Caption/value stacks are centred on their own run and follow it via a live binding on
        // each label's own widthProperty() -- not a one-shot prefWidth() snapshot recomputed
        // imperatively on every wire update, which could measure a label's width before its bound,
        // frequently-changing text (loadValue/writeValue) had actually been laid out with its real
        // CSS font, silently leaving the centred text shifted off from its caption above/below it.
        // updateWires() below only ever sets these four properties; the labels re-centre themselves.
        DoubleProperty loadStackX = new SimpleDoubleProperty();
        DoubleProperty loadStackBaseY = new SimpleDoubleProperty();
        DoubleProperty writeStackX = new SimpleDoubleProperty();
        DoubleProperty writeStackBaseY = new SimpleDoubleProperty();
        bindCenteredX(loadCaption, loadStackX);
        bindCenteredX(loadValue, loadStackX);
        loadCaption.layoutYProperty().bind(loadStackBaseY.subtract(45));
        loadValue.layoutYProperty().bind(loadStackBaseY.subtract(29));
        bindCenteredX(writeCaption, writeStackX);
        bindCenteredX(writeValue, writeStackX);
        writeCaption.layoutYProperty().bind(writeStackBaseY.add(13));
        writeValue.layoutYProperty().bind(writeStackBaseY.add(29));

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

        // ---- Eviction policy + per-user summary (below the frame table) ----------
        // Only the disk stays beside the table in its own right-hand column; everything else
        // that used to share that column now stacks under the (now taller) table instead, using
        // the width that frees up below it rather than being squeezed into the disk's own width.
        Label fifoHeader = FieldBoxes.sectionLabel("Eviction policy (FIFO)", TABLE_X, TABLE_Y);
        ReplacementQueueView fifo = new ReplacementQueueView(viewModel);

        Label nextVictimLabel = new Label();
        nextVictimLabel.getStyleClass().add("os-disk-summary");
        nextVictimLabel.textProperty().bind(viewModel.nextVictimHexProperty()
                .map(hex -> "/".equals(hex) ? "" : "next victim: frame " + hex));

        // ---- Per-user page-table summary (below the eviction policy) ------------
        // User count is small (power of two, validated) so the panel just renders inline;
        // the whole tab already scrolls via the outer ScrollPane.
        UserSummaryView summary = new UserSummaryView(viewModel);

        for (Node n : new Node[] { fifoHeader, fifo, nextVictimLabel, summary })
            n.setLayoutX(TABLE_X);

        // The disk box is the only thing left in the right-hand column, so it alone drives both
        // the binding and the floor below -- flush against the tab's real right edge, exactly like
        // PagedMMUTabView/PagedTLBTabView bind their own Physical Address box, instead of
        // recomputing setLayoutX imperatively on every relayout pass. canvas.setMinWidth is the
        // floor that guarantees room for the table + wire captions + disk box even when the
        // viewport is narrower than that (the ScrollPane scrolls horizontally past it, same as
        // MIN_CANVAS_WIDTH does on the other schematic tabs).
        double tableRight = TABLE_X + table.panelWidth();
        canvas.setMinWidth(tableRight + WIRE_GAP + diskBoxW + MARGIN);
        var colX = canvas.widthProperty().subtract(MARGIN).subtract(diskBoxW);
        diskHeader.layoutXProperty().bind(colX);
        diskBox.layoutXProperty().bind(colX);

        canvas.getChildren().addAll(
                memHeader, table, diskHeader, diskBox,
                loadWire, loadArrow, loadCaption, loadValue,
                writeWire, writeArrow, writeCaption, writeValue,
                fifoHeader, fifo, nextVictimLabel, summary);

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.getStyleClass().addAll("mmu-scroll-pane", "slim-scroll");
        // Grows canvas to fill the viewport's real width when it's wider than canvas's own
        // minWidth (never narrower -- that floor still applies below it) -- matches
        // PagedMMUTabView/PagedTLBTabView's own ScrollPane treatment exactly.
        scrollPane.setFitToWidth(true);
        getChildren().add(scrollPane);

        Runnable updateWires = () -> updateWires(
                table, diskBox,
                loadWire, loadArrow, loadCaption, loadValue,
                writeWire, writeArrow, writeCaption, writeValue,
                loadStackX, loadStackBaseY, writeStackX, writeStackBaseY);

        // Vertical stacking still has to happen imperatively (each section's Y depends on the
        // real, dynamically-changing height of the one above it), so it stays a recomputed
        // Runnable; only the canvas's own height (to fill a tall viewport) is derived alongside it.
        Runnable relayout = () -> {
            double y = table.getLayoutY() + tableHeight + 30;
            fifoHeader.setLayoutY(y);
            y += 22;
            fifo.setLayoutY(y);
            y += Math.max(fifo.getLayoutBounds().getHeight(), 26) + 8;
            nextVictimLabel.setLayoutY(y);
            y += 26;
            summary.setLayoutY(y);

            // layoutBounds, not boundsInParent: effect-immune, so the disk box's :hover drop-shadow
            // never affects the computed content height.
            double contentBottom = Math.max(diskBox.getLayoutY() + diskBox.getLayoutBounds().getHeight(),
                    summary.getLayoutY() + summary.getLayoutBounds().getHeight());
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
            Polyline writeWire, Polyline writeArrow, Label writeCaption, Label writeValue,
            DoubleProperty loadStackX, DoubleProperty loadStackBaseY,
            DoubleProperty writeStackX, DoubleProperty writeStackBaseY)
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

        // load caption + value stack, centred on the run near the disk box, fully above it.
        // write caption + value stack, centred on the run near the table, fully below it. Only the
        // target properties are set here -- the labels' own bindCenteredX/layoutYProperty bindings
        // (set up once in the constructor) do the actual positioning.
        loadStackX.set((loadKneeX + diskX) / 2);
        loadStackBaseY.set(diskY);
        writeStackX.set((rowX + writeKneeX) / 2);
        writeStackBaseY.set(rowY);

        for (Node n : new Node[] { loadWire, loadArrow, loadCaption, loadValue, writeWire, writeArrow, writeCaption, writeValue })
            n.toFront();
    }

    // Keeps a label centred on a live target X as a standing binding, driven by the label's own
    // widthProperty() rather than a one-shot prefWidth() snapshot taken whenever the wire happens
    // to be recomputed. loadValue/writeValue's text rebinds on every step, and widthProperty only
    // reports the real (CSS-resolved) width once layout has actually measured the new text -- so
    // this re-centres itself exactly when that settles, instead of risking a stale/pre-layout
    // width that would silently shift the text off from its caption above/below it.
    private static void bindCenteredX(Label label, DoubleProperty centerX)
    {
        label.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> centerX.get() - label.getWidth() / 2.0,
                centerX, label.widthProperty()));
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


    private void bindActive(Node node, BooleanProperty active)
    {
        node.pseudoClassStateChanged(ACTIVE, active.get());
        active.addListener((o, ov, nv) -> node.pseudoClassStateChanged(ACTIVE, nv));
    }
}
