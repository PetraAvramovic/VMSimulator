package rs.ac.bg.etf.view.tlb;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.effect.BoxBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;

/**
 * Set-associative TLB body view: one windowed table per way ("Entry 0", "Entry 1", ...), each
 * behaving like {@link DirectTLBView} -- rows indexed by set number, recentred on the addressed
 * set, blurred until the lookup runs. A shared vertical set-index bus on the left taps every
 * table's selected row; the bus's left end is the address anchor.
 * <p>
 * Every way-table also has its own block-output line -- always drawn, dropping just below the
 * table's Block column and running right toward the Physical Address box (its far end X is set by
 * the owning tab via {@link #blockLineEndXProperty()}). Each line carries its own diagonal tick +
 * "Nb" width label, and a vertical bus joins all their right ends. Only the line belonging to the
 * way that resolved the lookup lights up (blue) and carries the frame-number value, when
 * {@link #blockActiveProperty()} is set.
 */
public class SetAssociativeTLBView extends StackPane implements TLBBodyView
{
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final double BUS_STUB_LENGTH = 18.0;
    private static final double WAY_GAP = 12.0;
    private static final double CAPTION_GAP = 4.0;
    // How far a way-table's block line drops below its bottom border before turning right, and the
    // fallback horizontal reach before the tab has told us where the PA Block box is.
    private static final double BLOCK_LINE_DROP = 24.0;
    private static final double BLOCK_LINE_FALLBACK_REACH = 160.0;

    private final VBox stack = new VBox(WAY_GAP);
    private final Pane overlay = new Pane();

    private final List<List<PagedTLBRowView>> wayRows;
    private final List<VBox> tableBoxes = new ArrayList<>();
    private final List<VBox> rowBodies = new ArrayList<>();
    private final List<Line> taps = new ArrayList<>();
    // One block-output line per way-table, always drawn; the resolved way's goes :active (blue).
    // Every line has its own diagonal tick + "Nb" width label. blockJoin is the always-grey bus
    // bridging all their right ends; blockBusLive is the blue overlay from the resolved way's line
    // up to the bus top (the output tap) -- only that stretch carries signal on a hit.
    private final List<Polyline> blockLines = new ArrayList<>();
    private final List<Line> blockTicks = new ArrayList<>();
    private final List<Label> blockBitsLabels = new ArrayList<>();
    private final Line blockJoin = connectorLine();
    private final Line blockBusLive = connectorLine();
    private final Label blockValueLabel = flowLabel("mmu-bit-value");
    private final Line busLine = searchLine();
    private final Region busAnchor = marker();
    private final Region blockAnchor = marker();

    private final ObservableValue<Number> selectedWindowRow;
    private final ObservableValue<Number> resolvedWay;

    private final BooleanProperty active = new SimpleBooleanProperty(false);
    private final BooleanProperty blockActive = new SimpleBooleanProperty(false);
    private final DoubleProperty blockLineEndX = new SimpleDoubleProperty(Double.NaN);
    private final ObjectProperty<Region> addressAnchor = new SimpleObjectProperty<>();
    private final ObjectProperty<Region> tableBottomAnchor = new SimpleObjectProperty<>();

    public SetAssociativeTLBView(List<PagedTLBRowView> headers, List<List<PagedTLBRowView>> wayRows,
            ObservableValue<Number> selectedWindowRow, ObservableValue<Number> resolvedWay,
            ObservableValue<String> blockHex, int frameBits)
    {
        this.wayRows = wayRows;
        this.selectedWindowRow = selectedWindowRow;
        this.resolvedWay = resolvedWay;

        for (int w = 0; w < wayRows.size(); w++) {
            Label caption = new Label("Entry " + w);
            caption.getStyleClass().add("tlb-table-title");

            VBox rowsBody = new VBox();
            rowsBody.getChildren().addAll(wayRows.get(w));

            VBox tableBox = new VBox();
            tableBox.getStyleClass().add("tlb-table");
            tableBox.getChildren().addAll(headers.get(w), rowsBody);

            stack.getChildren().add(new VBox(CAPTION_GAP, caption, tableBox));
            tableBoxes.add(tableBox);
            rowBodies.add(rowsBody);
            taps.add(searchLine());
            blockLines.add(connectorPolyline());
            blockTicks.add(connectorLine());
            Label bits = flowLabel("mmu-bit-width");
            bits.setText(frameBits + "b");
            blockBitsLabels.add(bits);
        }

        blockValueLabel.textProperty().bind(blockHex);

        overlay.setMouseTransparent(true);
        overlay.getChildren().add(busLine);
        overlay.getChildren().addAll(taps);
        overlay.getChildren().addAll(blockLines);
        overlay.getChildren().addAll(blockTicks);
        overlay.getChildren().addAll(blockJoin, blockBusLive);
        overlay.getChildren().addAll(blockBitsLabels);
        overlay.getChildren().add(blockValueLabel);
        overlay.getChildren().addAll(busAnchor, blockAnchor);

        getChildren().addAll(stack, overlay);
        addressAnchor.set(busAnchor);
        tableBottomAnchor.set(blockAnchor);

        Runnable reposition = () -> Platform.runLater(this::repositionAnchors);
        sceneProperty().addListener((o, ov, nv) -> reposition.run());
        widthProperty().addListener((o, ov, nv) -> reposition.run());
        heightProperty().addListener((o, ov, nv) -> reposition.run());
        for (List<PagedTLBRowView> rows : wayRows)
            for (PagedTLBRowView row : rows)
                row.visibleProperty().addListener((o, ov, nv) -> reposition.run());
        selectedWindowRow.addListener((o, ov, nv) -> reposition.run());
        blockLineEndX.addListener((o, ov, nv) -> reposition.run());
        resolvedWay.addListener((o, ov, nv) -> {
            reposition.run();
            updateBlockLighting();
        });

        active.addListener((o, ov, nv) -> {
            busLine.pseudoClassStateChanged(ACTIVE, nv);
            for (Line tap : taps)
                tap.pseudoClassStateChanged(ACTIVE, nv);
            for (VBox body : rowBodies)
                setFogged(body, !nv);
            reposition.run();
        });
        blockActive.addListener((o, ov, nv) -> updateBlockLighting());
        for (VBox body : rowBodies)
            setFogged(body, !active.get());
        updateBlockLighting();

        reposition.run();
    }

    @Override
    public Region getNode() { return this; }

    @Override
    public ObjectProperty<Region> addressAnchorProperty() { return addressAnchor; }

    @Override
    public ObjectProperty<Region> tableBottomAnchorProperty() { return tableBottomAnchor; }

    @Override
    public BooleanProperty activeProperty() { return active; }

    /** Lit while the block-output step is running: turns the resolved way's line and readout blue. */
    public BooleanProperty blockActiveProperty() { return blockActive; }

    /** Far (right) end X of every way's block line, in this view's local coords; set by the tab. */
    public DoubleProperty blockLineEndXProperty() { return blockLineEndX; }

    private void repositionAnchors()
    {
        if (getScene() == null)
            return;

        int selIndex = intValue(selectedWindowRow, -1);

        // Pass 1: resolve each way-table's selected-row Y and panel bounds.
        double[] selY = new double[wayRows.size()];
        double[] botY = new double[wayRows.size()];
        boolean[] present = new boolean[wayRows.size()];
        double spineX = Double.MAX_VALUE;
        double panelRight = 0;
        double blockColX = Double.NaN;
        Double firstY = null;
        Double lastY = null;

        for (int w = 0; w < wayRows.size(); w++) {
            PagedTLBRowView target = targetRow(w, selIndex);
            if (target == null)
                continue;

            Bounds panel = toOverlay(tableBoxes.get(w));
            Bounds row = toOverlay(target);
            selY[w] = row.getCenterY();
            botY[w] = panel.getMaxY();
            present[w] = true;
            spineX = Math.min(spineX, panel.getMinX() - BUS_STUB_LENGTH);
            panelRight = Math.max(panelRight, panel.getMaxX());
            if (Double.isNaN(blockColX)) {
                Region blockCell = target.blockCellAnchorProperty().get();
                blockColX = blockCell != null && blockCell.getScene() != null
                        ? toOverlay(blockCell).getCenterX()
                        : panel.getCenterX();
            }
            if (firstY == null)
                firstY = selY[w];
            lastY = selY[w];
        }

        if (firstY == null) {
            busLine.setVisible(false);
            busAnchor.setVisible(false);
            blockAnchor.setVisible(false);
            blockJoin.setVisible(false);
            blockBusLive.setVisible(false);
            for (Line tap : taps)
                tap.setVisible(false);
            for (Polyline bl : blockLines)
                bl.setVisible(false);
            for (Line t : blockTicks)
                t.setVisible(false);
            for (Label b : blockBitsLabels)
                b.setVisible(false);
            blockValueLabel.setVisible(false);
            return;
        }

        // Pass 2: the vertical set-index bus and one horizontal tap into each way-table's left edge.
        for (int w = 0; w < wayRows.size(); w++) {
            Line tap = taps.get(w);
            if (!present[w]) {
                tap.setVisible(false);
                continue;
            }
            Bounds panel = toOverlay(tableBoxes.get(w));
            tap.setVisible(true);
            tap.setStartX(spineX);
            tap.setStartY(selY[w]);
            tap.setEndX(panel.getMinX());
            tap.setEndY(selY[w]);
        }

        busLine.setVisible(true);
        busLine.setStartX(spineX);
        busLine.setStartY(firstY);
        busLine.setEndX(spineX);
        busLine.setEndY(lastY);

        busAnchor.setVisible(true);
        busAnchor.setLayoutX(spineX);
        busAnchor.setLayoutY((firstY + lastY) / 2);

        // Pass 3: each way-table's own block line -- drop below the Block column, then run right to
        // a shared vertical join bus. Every line gets a diagonal tick + "Nb" width label beneath
        // it; the resolved way's line also shows the frame value above it.
        double endX = Double.isNaN(blockLineEndX.get())
                ? panelRight + BLOCK_LINE_FALLBACK_REACH
                : blockLineEndX.get();
        int rw = intValue(resolvedWay, -1);
        boolean haveResolved = rw >= 0 && rw < wayRows.size() && present[rw];
        double tickX = blockColX + (endX - blockColX) * 0.5;

        Double firstLineY = null;
        Double lastLineY = null;
        for (int w = 0; w < wayRows.size(); w++) {
            Polyline bl = blockLines.get(w);
            Line tk = blockTicks.get(w);
            Label bits = blockBitsLabels.get(w);
            if (!present[w]) {
                bl.setVisible(false);
                tk.setVisible(false);
                bits.setVisible(false);
                continue;
            }
            double lineY = botY[w] + BLOCK_LINE_DROP;
            bl.setVisible(true);
            bl.getPoints().setAll(blockColX, botY[w], blockColX, lineY, endX, lineY);
            positionTick(tk, tickX, lineY);
            bits.autosize();
            bits.setVisible(true);
            bits.setLayoutX(tickX - bits.getWidth() / 2);
            bits.setLayoutY(lineY + 5);
            if (firstLineY == null)
                firstLineY = lineY;
            lastLineY = lineY;
        }

        // Grey bus joining every way's line end.
        blockJoin.setVisible(!firstLineY.equals(lastLineY));
        blockJoin.setStartX(endX);
        blockJoin.setStartY(firstLineY);
        blockJoin.setEndX(endX);
        blockJoin.setEndY(lastLineY);

        // Output tap at the top of the bus -- the tab runs the wire on to PA Block from here.
        blockAnchor.setLayoutX(endX);
        blockAnchor.setLayoutY(firstLineY);
        blockAnchor.setVisible(true);

        // Blue overlay: only the stretch from the resolved way's line up to the tap carries signal.
        if (haveResolved) {
            blockBusLive.setVisible(true);
            blockBusLive.setStartX(endX);
            blockBusLive.setStartY(firstLineY);
            blockBusLive.setEndX(endX);
            blockBusLive.setEndY(botY[rw] + BLOCK_LINE_DROP);

            double lineY = botY[rw] + BLOCK_LINE_DROP;
            blockValueLabel.autosize();
            blockValueLabel.setVisible(true);
            blockValueLabel.setLayoutX(tickX - blockValueLabel.getWidth() / 2);
            blockValueLabel.setLayoutY(lineY - blockValueLabel.getHeight() - 5);
        } else {
            blockBusLive.setVisible(false);
            blockValueLabel.setVisible(false);
        }
    }

    // Only the way that resolved the lookup lights (line + tick + width label), plus the bus stretch
    // from it up to the output tap (blockBusLive). The grey blockJoin base never lights, so a hit in
    // way 0 shows just that line and the tap, not the whole vertical bus.
    private void updateBlockLighting()
    {
        boolean on = blockActive.get();
        int rw = intValue(resolvedWay, -1);
        for (int w = 0; w < blockLines.size(); w++) {
            boolean lit = on && w == rw;
            blockLines.get(w).pseudoClassStateChanged(ACTIVE, lit);
            blockTicks.get(w).pseudoClassStateChanged(ACTIVE, lit);
            blockBitsLabels.get(w).pseudoClassStateChanged(ACTIVE, lit);
        }
        blockBusLive.pseudoClassStateChanged(ACTIVE, on);
        blockValueLabel.pseudoClassStateChanged(ACTIVE, on);
    }

    // The row a wire should point at in way-table w: the selected one once the lookup is live,
    // otherwise the middle of the window (mirrors DirectTLBView / PageTableView).
    private PagedTLBRowView targetRow(int way, int selIndex)
    {
        List<PagedTLBRowView> visible = wayRows.get(way).stream()
                .filter(r -> r.isVisible() && r.getScene() != null)
                .toList();
        if (visible.isEmpty())
            return null;
        return (active.get() && selIndex >= 0 && selIndex < visible.size())
                ? visible.get(selIndex)
                : visible.get(visible.size() / 2);
    }

    private Bounds toOverlay(Region node)
    {
        return overlay.sceneToLocal(node.localToScene(node.getBoundsInLocal()));
    }

    private static int intValue(ObservableValue<Number> value, int fallback)
    {
        Number n = value.getValue();
        return n == null ? fallback : n.intValue();
    }

    private static void setFogged(VBox body, boolean fogged)
    {
        body.setEffect(fogged ? new BoxBlur(6, 6, 3) : null);
        body.setOpacity(fogged ? 0.45 : 1.0);
    }

    private static void positionTick(Line mark, double centerX, double centerY)
    {
        mark.setVisible(true);
        mark.setStartX(centerX - 6);
        mark.setStartY(centerY + 6);
        mark.setEndX(centerX + 6);
        mark.setEndY(centerY - 6);
    }

    private static Region marker()
    {
        Region region = new Region();
        region.setManaged(false);
        region.setMouseTransparent(true);
        region.setPrefSize(1, 1);
        return region;
    }

    // Unmanaged so it never inflates the overlay Pane (which would throw the StackPane's layout of
    // the tables off); repositionAnchors() calls autosize() before placing it.
    private static Label flowLabel(String styleClass)
    {
        Label label = new Label();
        label.setManaged(false);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static Line searchLine()
    {
        Line line = new Line();
        // Unmanaged: the hand-positioned bus/tap geometry reaches left of the tables into negative
        // overlay coords and must not inflate the overlay's bounds.
        line.setManaged(false);
        line.getStyleClass().add("tlb-search-line");
        return line;
    }

    private static Line connectorLine()
    {
        Line line = new Line();
        line.setManaged(false);
        line.getStyleClass().add("connector-line");
        return line;
    }

    private static Polyline connectorPolyline()
    {
        Polyline polyline = new Polyline();
        polyline.setManaged(false);
        polyline.getStyleClass().add("connector-line");
        return polyline;
    }
}
