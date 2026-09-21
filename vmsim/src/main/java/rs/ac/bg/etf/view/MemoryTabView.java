package rs.ac.bg.etf.view;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;

import rs.ac.bg.etf.view.inspector.MemoryInspectorWindow;
import rs.ac.bg.etf.view.memory.MemoryTableView;
import rs.ac.bg.etf.view.shape.BitWidthLine;
import rs.ac.bg.etf.view.util.FieldBoxes;
import rs.ac.bg.etf.view.util.PostLayoutTask;
import rs.ac.bg.etf.view.util.SchematicTab;
import rs.ac.bg.etf.view.util.UiScale;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.viewmodel.MemoryTabViewModel;

/**
 * Memory tab: a Physical Address field box wired straight down into a windowed schematic table of
 * physical memory (fogged until an address has actually been formed), mirroring the MMU tab's own
 * page-table schematic. Clicking the table opens a separate, independently-scrollable/seekable
 * full-memory browser window, seeded on whichever address the schematic is currently centred on.
 */
public class MemoryTabView extends SchematicTab
{
    private final double MARGIN = UiScale.px(30);
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    public MemoryTabView(MemoryTabViewModel viewModel)
    {
        // Matches the MMU tab's own boxY exactly: the header needs a 58px clearance above the box
        // (see paTitle below), and 50 was too small for that -- boxY - 58 went negative, shoving
        // the header up past the canvas's own top edge instead of sitting under the tab bar with a
        // sane margin the way every other tab's header does.
        double boxY = UiScale.px(78);
        double boxX = MARGIN;
        double tableY = boxY + FieldBoxes.addressBoxHeight() + UiScale.px(90);

        // Same wide/short proportions and padding as the MMU/TLB tabs' own Block|Word address
        // boxes (ADDRESS_BOX_HEIGHT/ADDRESS_FIELD_PADDING) -- not the taller, narrower "solo
        // field" proportions (the plain 3-arg valueCell() overload, BOX_HEIGHT/FIELD_PADDING)
        // meant for things like the Page Table Pointer box.
        Region paBox = FieldBoxes.valueCell(
                "field-box-cell-solo", viewModel.physicalAddressHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getPhysicalAddressBits()),
                FieldBoxes.addressBoxHeight(), FieldBoxes.addressFieldPadding());
        double paBoxWidth = paBox.getPrefWidth();
        paBox.setLayoutX(boxX);
        paBox.setLayoutY(boxY);

        // The same bold, left-aligned "Physical Address" header style, and the same 58px gap down
        // to the box, that the MMU tab uses above its own Block|Word pair -- this tab just has one
        // undivided box instead of two, so there's no second, smaller "Block"/"Word" caption
        // underneath the header filling part of that gap.
        Label paTitle = FieldBoxes.sectionLabel("Physical Address", boxX, boxY - UiScale.px(58));

        BitWidthLine addressWire = new BitWidthLine();
        addressWire.bitsProperty().set(viewModel.getPhysicalAddressBits());
        addressWire.labelOnLeftProperty().set(false);
        addressWire.arrowTipVisibleProperty().set(false);
        addressWire.startXProperty().set(boxX + paBoxWidth / 2);
        addressWire.startYProperty().set(boxY + FieldBoxes.addressBoxHeight());
        addressWire.endXProperty().bind(addressWire.startXProperty());
        // endY is only a sane pre-layout placeholder -- updateConnector() below moves it down to
        // whichever row is actually addressed, so this wire always spans the whole run from the
        // box down to the row (not just partway), and the "Nb" tag -- always at a BitWidthLine's
        // own midpoint -- reads as centred on that whole run rather than pinned near the box.
        addressWire.endYProperty().set(tableY - UiScale.px(20));

        // Only the table itself is centred horizontally in the (viewport-width-tracking) canvas --
        // the PA box and both titles stay left-anchored like every other tab's fields.
        MemoryTableView memoryTableView = new MemoryTableView(viewModel);
        memoryTableView.setLayoutY(tableY);
        memoryTableView.layoutXProperty().bind(canvas.widthProperty().subtract(memoryTableView.widthProperty()).divide(2));
        memoryTableView.getStyleClass().add("data-table-clickable");
        memoryTableView.setCursor(Cursor.HAND);

        // Design size from what's actually drawn -- never the canvas's own size: wide enough for
        // the (centred) table and the PA box each with their margins, tall enough for the table's
        // bottom edge plus a margin -- so a config with wider addresses widens the design size
        // (and the workbench scales to it) instead of the table running under the PA box. The
        // table's preferred size (known at construction) backs up its live size, so the minimum is
        // already right before this tab has ever been laid out.
        canvas.designWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(2 * MARGIN + paBoxWidth,
                        2 * MARGIN + Math.max(memoryTableView.prefWidth(-1), memoryTableView.getWidth())),
                memoryTableView.widthProperty()));
        canvas.designHeightProperty().bind(Bindings.createDoubleBinding(
                () -> tableY + Math.max(memoryTableView.prefHeight(-1), memoryTableView.getHeight()) + MARGIN,
                memoryTableView.heightProperty()));

        Label tableTitle = FieldBoxes.sectionLabel("Memory", 0, tableY - UiScale.px(24));
        tableTitle.layoutXProperty().bind(memoryTableView.layoutXProperty());

        MemoryInspectorWindow inspector = new MemoryInspectorWindow(viewModel.getContext(), viewModel.currentStepNumberProperty());

        // The final leg into the addressed row -- a BitWidthLine like addressWire above (see its
        // own class doc), not a raw Polyline. No bit-width tag of its own (addressWire's is the
        // one tag for this whole run); it does end in an arrow, since it's the leg that actually
        // lands on the row.
        BitWidthLine addressAcross = new BitWidthLine();
        addressAcross.bitWidthIndicatorVisibleProperty().set(false);

        canvas.getChildren().addAll(paTitle, paBox, addressWire, tableTitle, memoryTableView, addressAcross);

        // Once per pulse, right after layout (see PostLayoutTask), so the wire is drawn with the boxes.
        PostLayoutTask reroute = new PostLayoutTask(this, () -> updateConnector(memoryTableView, addressWire, addressAcross));
        memoryTableView.currentEntryAnchorProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        memoryTableView.layoutXProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        reroute.request();

        bindActive(addressWire, viewModel.memoryAddressedProperty());
        bindActive(addressAcross, viewModel.memoryAddressedProperty());

        memoryTableView.setOnMouseClicked(e -> inspector.toggle(
                memoryTableView.getScene() != null ? memoryTableView.getScene().getWindow() : null,
                viewModel.getWindowCenterAddress()));

        // The canvas's width tracks the actual viewport (not a fixed constant), so "centred in the
        // canvas" (the table's layoutX binding above) really means "centred in the visible tab".
        mountCanvas();
    }

    private void updateConnector(MemoryTableView memoryTableView, BitWidthLine addressWire, BitWidthLine addressAcross)
    {
        Region rowAnchor = memoryTableView.currentEntryAnchorProperty().get();
        if (rowAnchor == null || rowAnchor.getScene() == null)
            return;

        double sourceX = addressWire.startXProperty().get();

        Bounds rowBounds = canvas.sceneToLocal(rowAnchor.localToScene(rowAnchor.getBoundsInLocal()));
        double targetY = rowBounds.getCenterY();
        double tableLeftX = memoryTableView.getLayoutX();

        // addressWire now runs the whole way down to the addressed row itself, so its own
        // midpoint -- where the bit-width tag always sits -- is centred on that whole run.
        addressWire.endYProperty().set(targetY);

        addressAcross.startXProperty().set(sourceX);
        addressAcross.startYProperty().set(targetY);
        addressAcross.endXProperty().set(tableLeftX);
        addressAcross.endYProperty().set(targetY);

        canvas.bringToFront(addressWire, addressAcross);
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
