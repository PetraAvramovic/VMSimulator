package rs.ac.bg.etf.view.tlb;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import rs.ac.bg.etf.model.simulation.SimulationConfig.TLBType;
import rs.ac.bg.etf.view.shape.BitWidthLine;
import rs.ac.bg.etf.view.shape.CurlyBrace;
import rs.ac.bg.etf.view.util.FieldBoxes;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel.LookupOutcome;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel.Row;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel.TlbLine;

/**
 * Paged TLB tab: an address-formation schematic (Process / Virtual Address / Physical Address
 * field boxes) wired into the TLB body view. The body view ({@link AssociativeTLBView} today)
 * only exposes layout landmarks; this class owns the address-merge wiring and always routes the
 * block output off the bottom of the row table.
 */
public class PagedTLBTabView extends StackPane
{
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final double CANVAS_WIDTH = 900;
    private static final double CANVAS_HEIGHT = 720;
    private static final double MARGIN = 30;
    private static final double BOX_Y = 60;
    private static final double BOX_HEIGHT = FieldBoxes.BOX_HEIGHT;
    private static final double PROCESS_GAP = 64;
    private static final double TABLE_Y = 300;
    // Roughly AssociativeTLBView's bus-stub length plus the .tlb-table panel padding; only used to
    // pre-align the body view so the tag line drops near-vertically. The router handles any residual.
    private static final double BUS_STUB_APPROX = 28;
    private static final double BRACE_EAR_Y = BOX_Y + BOX_HEIGHT + 72;
    private static final double BRACE_DEPTH = 16;
    private static final double BRACE_GAP = 8;
    private static final double BLOCK_DROP = 44;
    // Direct-mapped fork geometry: a long k@p stem from the brace tip to the fork bar, then two
    // parallel legs FORK_HALF_WIDTH px either side of it -- the tag leg drops SPLIT_LEG_LEN px to
    // its readout, the index leg drops to the selected row. The table is nudged down/right to fit.
    private static final double KP_STEM_LEN = 72;
    private static final double FORK_HALF_WIDTH = 20;
    private static final double SPLIT_LEG_LEN = 70;
    private static final double DIRECT_TABLE_X = 124;
    private static final double DIRECT_TABLE_Y = 84;
    // Set-associative stacks several tables, so it starts them well above the direct/assoc table
    // (the fork sits to their left, not above) -- but still clear of the word pass-through line and
    // the address boxes -- to give the 4-way case a chance of fitting with little/no scrolling.
    private static final double SET_ASSOC_TABLE_Y = 210;

    private final PagedTLBTabViewModel viewModel;
    private final Pane canvas = new Pane();
    // Flat list of every row view in the body (one table's worth for direct / associative; all
    // way-tables concatenated for set-associative) -- used for the connector re-route listeners.
    private final List<PagedTLBRowView> rowViews = new ArrayList<>();
    // Set-associative only: the row views grouped by way-table, for updateRowData().
    private final List<List<PagedTLBRowView>> wayRowViews;

    private final TLBBodyView bodyView;
    // Same instance as bodyView when the TLB is set-associative, else null -- lets the tab bind the
    // per-way block-output lighting the interface does not expose.
    private final SetAssociativeTLBView setAssocBody;
    private final Region bodyNode;
    private double mergeX;
    // True for a direct-mapped or set-associative TLB: the key forks into a dead-end tag readout
    // and an index/set wire into the selected row(s), instead of one line into the search bus.
    private final boolean split;

    private final CurlyBrace tagBrace = new CurlyBrace();
    private final Polyline tagLine = elbow();
    private final Line tagTick = tick();
    private final Label tagBitsLabel;
    private final Label tagValueLabel = flowLabel();

    // Direct-mapped split: k@p stem from the brace tip to the fork (its own tick + bit width + value
    // readout), then the tag leg (reuses tagTick / tagBitsLabel / tagValueLabel) and the index leg
    // into the body's address anchor.
    private final Polyline kpStub = elbow();
    private final Line kpTick = tick();
    private final Label kpBitsLabel;
    private final Label kpValueLabel = flowLabel();
    private final Polyline leftBranch = elbow();
    private final Polyline indexBranch = elbow();
    private final Line indexTick = tick();
    private final Label indexBitsLabel;

    private final Polyline blockOut = elbow();
    private final Line blockTick = tick();
    private final Label blockBitsLabel;
    private final Label blockValueLabel = flowLabel();
    private final Region blockBoxPA;

    private final Runnable reposition;

    public PagedTLBTabView(PagedTLBTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        getStyleClass().add("tlb-tab-view");
        canvas.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);

        int tagDigits = viewModel.tagHexDigitsProperty().get();
        int blockDigits = viewModel.blockHexDigitsProperty().get();

        // ---- Process / Virtual Address / Physical Address field boxes -------------------------
        Region userBox = FieldBoxes.valueCell("va-breakdown-cell-solo", viewModel.userHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getProcessIdBits()));
        double userBoxW = userBox.getPrefWidth();
        double userBoxX = MARGIN;
        place(userBox, userBoxX, BOX_Y);

        Region pageBox = FieldBoxes.valueCell("va-breakdown-cell-left", viewModel.pageHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getPageBits()));
        double pageBoxW = pageBox.getPrefWidth();
        double pageBoxX = userBoxX + userBoxW + PROCESS_GAP;
        place(pageBox, pageBoxX, BOX_Y);

        Region wordBoxVA = FieldBoxes.valueCell("va-breakdown-cell-right", viewModel.wordHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getWordBits()));
        double wordBoxW = wordBoxVA.getPrefWidth();
        double wordBoxVAX = pageBoxX + pageBoxW;
        place(wordBoxVA, wordBoxVAX, BOX_Y);

        double wordBoxPAX = CANVAS_WIDTH - MARGIN - wordBoxW;
        Region wordBoxPA = FieldBoxes.valueCell("va-breakdown-cell-right", viewModel.paWordHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getWordBits()));
        place(wordBoxPA, wordBoxPAX, BOX_Y);

        blockBoxPA = FieldBoxes.valueCell("va-breakdown-cell-left", viewModel.blockHexProperty(), blockDigits);
        double blockBoxW = blockBoxPA.getPrefWidth();
        double blockBoxPAX = wordBoxPAX - blockBoxW;
        place(blockBoxPA, blockBoxPAX, BOX_Y);

        Label userTitle = FieldBoxes.fieldTitle("User", userBoxX, BOX_Y - 20);
        Label pageTitle = FieldBoxes.fieldTitle("Page", pageBoxX, BOX_Y - 20);
        Label wordTitleVA = FieldBoxes.fieldTitle("Word", wordBoxVAX, BOX_Y - 20);
        Label blockTitle = FieldBoxes.fieldTitle("Block", blockBoxPAX, BOX_Y - 20);
        Label wordTitlePA = FieldBoxes.fieldTitle("Word", wordBoxPAX, BOX_Y - 20);

        Label processHeader = FieldBoxes.sectionLabel("Process", userBoxX, 14);
        Label vaHeader = FieldBoxes.sectionLabel("Virtual Address", pageBoxX, 14);
        Label paHeader = FieldBoxes.sectionLabel("Physical Address", blockBoxPAX, 14);

        // ---- Tag formation geometry: user@page merge point ---------------------------------
        double userCenterX = userBoxX + userBoxW / 2.0;
        double pageCenterX = pageBoxX + pageBoxW / 2.0;
        mergeX = (userCenterX + pageCenterX) / 2.0;

        // ---- TLB body view ------------------------------------------------------------------
        TLBType tlbType = viewModel.tlbTypeProperty().get();
        boolean setAssociative = tlbType == TLBType.SET_ASSOCIATIVE;
        split = setAssociative || tlbType == TLBType.DIRECT;

        // One "table" for direct / associative; one per way ("Entry k") for set-associative. Every
        // table's leftmost column is the set number for set-associative, the raw slot otherwise.
        String indexHeader = setAssociative ? "Set" : "Index";
        int wayCount = setAssociative ? viewModel.getWayCount() : 1;
        List<PagedTLBRowView> headers = new ArrayList<>();
        List<List<PagedTLBRowView>> wayRowViewLists = new ArrayList<>();
        for (int w = 0; w < wayCount; w++) {
            PagedTLBRowView tableHeaderRow = new PagedTLBRowView(tagDigits, blockDigits);
            tableHeaderRow.setHeaderLabels(indexHeader, "V", "D", "Tag", "Block");
            headers.add(tableHeaderRow);
            List<PagedTLBRowView> rows = new ArrayList<>();
            for (int i = 0; i < viewModel.getMaxVisibleRows(); i++) {
                PagedTLBRowView row = new PagedTLBRowView(tagDigits, blockDigits);
                rows.add(row);
                rowViews.add(row);
            }
            wayRowViewLists.add(rows);
        }
        this.wayRowViews = wayRowViewLists;

        if (setAssociative) {
            setAssocBody = new SetAssociativeTLBView(headers, wayRowViewLists,
                    viewModel.selectedWindowRowProperty(), viewModel.resolvedWayProperty(),
                    viewModel.blockHexProperty(), viewModel.getFrameBits());
            bodyView = setAssocBody;
        } else if (split) {
            setAssocBody = null;
            bodyView = new DirectTLBView(headers.get(0), new ArrayList<>(rowViews),
                    viewModel.selectedWindowRowProperty());
        } else {
            setAssocBody = null;
            bodyView = new AssociativeTLBView(headers.get(0), new ArrayList<>(rowViews));
        }
        bodyNode = bodyView.getNode();
        // Associative: line the search bus (~BUS_STUB_APPROX px left of the rows) up under the merge
        // point so the tag line drops near-vertically. Direct-mapped: push the table down and right
        // so the k@p stem + fork + tag readout all have room to its left.
        double tableX = mergeX + (split ? DIRECT_TABLE_X : BUS_STUB_APPROX);
        double tableY = setAssociative ? SET_ASSOC_TABLE_Y : TABLE_Y + (split ? DIRECT_TABLE_Y : 0);
        bodyNode.setLayoutX(tableX);
        bodyNode.setLayoutY(tableY);

        // The set-associative view carries its own per-way "Entry N" captions, and it sits high
        // enough that a shared "TLB" caption would collide with the address boxes.
        Label tableHeader = FieldBoxes.sectionLabel("TLB", tableX, tableY - 24);
        tableHeader.getStyleClass().add("tlb-table-title");
        tableHeader.setVisible(!setAssociative);

        tagBrace.depthProperty().set(BRACE_DEPTH);
        tagBrace.leftYProperty().set(BRACE_EAR_Y);
        tagBrace.rightYProperty().set(BRACE_EAR_Y);

        BitWidthLine userDown = bitWidthWire(viewModel.getProcessIdBits());
        bindBottomCenter(userDown, userBox);
        tagBrace.leftXProperty().bind(userDown.startXProperty());
        userDown.endXProperty().bind(tagBrace.leftXProperty());
        userDown.endYProperty().bind(tagBrace.leftYProperty().subtract(BRACE_GAP));

        BitWidthLine pageDown = bitWidthWire(viewModel.getPageBits());
        bindBottomCenter(pageDown, pageBox);
        tagBrace.rightXProperty().bind(pageDown.startXProperty());
        pageDown.endXProperty().bind(tagBrace.rightXProperty());
        pageDown.endYProperty().bind(tagBrace.rightYProperty().subtract(BRACE_GAP));

        tagBitsLabel = bitLabel(viewModel.getTagBits(), 0, 0);
        tagValueLabel.textProperty().bind(viewModel.tagHexProperty());
        kpBitsLabel = bitLabel(viewModel.getFullKeyBits(), 0, 0);
        kpValueLabel.textProperty().bind(viewModel.fullTagHexProperty());
        indexBitsLabel = bitLabel(viewModel.getIndexBits(), 0, 0);

        // Associative / set-associative: the whole user@page tag rides one line into the body view's
        // search-bus anchor. Direct-mapped: the key forks here -- the high k@p-m tag bits dead-end
        // in a hex readout coloured by the lookup outcome, the low m index bits carry on into the
        // selected row (the body view's address anchor tracks it).
        wireAddressIntoBody(split);

        // ---- Word pass-through: VA Word flows straight across into PA Word -------------------
        double passY = BOX_Y + BOX_HEIGHT + 40;
        double wordVACenterX = wordBoxVAX + wordBoxW / 2;
        double wordPACenterX = wordBoxPAX + wordBoxW / 2;
        Polyline wordPassLine = elbow(
                wordVACenterX, BOX_Y + BOX_HEIGHT,
                wordVACenterX, passY,
                wordPACenterX, passY,
                wordPACenterX, BOX_Y + BOX_HEIGHT);
        Label wordBitsStart = bitLabel(viewModel.getWordBits(), wordVACenterX + 6, passY - 18);
        Label wordBitsEnd = bitLabel(viewModel.getWordBits(), wordPACenterX + 6, passY - 18);

        // ---- Block output: always leaves the bottom of the table, into PA Block -------------
        blockBitsLabel = bitLabel(viewModel.getFrameBits(), 0, 0);
        blockValueLabel.textProperty().bind(viewModel.blockHexProperty());
        // Set-associative draws its own per-way block lines + frame value inside the body view; the
        // tab keeps blockOut/blockTick/blockBitsLabel as the riser from the body's output tap into
        // PA Block, but not its own value readout.
        if (setAssociative)
            blockValueLabel.setVisible(false);

        canvas.getChildren().addAll(
                processHeader, vaHeader, paHeader, tableHeader,
                userTitle, pageTitle, wordTitleVA, blockTitle, wordTitlePA,
                userDown, pageDown, tagBrace, tagLine, tagTick, tagBitsLabel, tagValueLabel,
                kpStub, kpTick, kpBitsLabel, kpValueLabel, leftBranch, indexBranch, indexTick, indexBitsLabel,
                wordPassLine, wordBitsStart, wordBitsEnd,
                blockOut, blockTick, blockBitsLabel, blockValueLabel,
                userBox, pageBox, wordBoxVA, blockBoxPA, wordBoxPA,
                bodyNode);

        // ---- Dynamic connectors: the anchors are marker Regions the body view positions during
        // its own layout pass, so re-run whenever that layout (or ours) changes. -------------
        reposition = () -> Platform.runLater(this::updateConnectors);
        bodyView.addressAnchorProperty().addListener((o, ov, nv) -> reposition.run());
        bodyView.tableBottomAnchorProperty().addListener((o, ov, nv) -> reposition.run());
        bodyNode.layoutBoundsProperty().addListener((o, ov, nv) -> reposition.run());
        bodyNode.boundsInParentProperty().addListener((o, ov, nv) -> reposition.run());
        for (PagedTLBRowView row : rowViews) {
            row.blockCellAnchorProperty().addListener((o, ov, nv) -> reposition.run());
            row.layoutBoundsProperty().addListener((o, ov, nv) -> reposition.run());
        }
        canvas.widthProperty().addListener((o, ov, nv) -> reposition.run());
        canvas.heightProperty().addListener((o, ov, nv) -> reposition.run());
        sceneProperty().addListener((o, ov, nv) -> reposition.run());
        // The direct-mapped / set-associative body view moves its address marker in place (no
        // property swap), so the index wire must be re-routed when the selected row -- or, for
        // set-associative, the resolved way the block output leaves from -- changes.
        viewModel.selectedWindowRowProperty().addListener((o, ov, nv) -> reposition.run());
        viewModel.resolvedWayProperty().addListener((o, ov, nv) -> reposition.run());
        // Several labels are positioned relative to their own measured width; re-route once the
        // layout pass has actually sized them (getWidth() is 0 on the first connector pass).
        for (Label label : List.of(kpValueLabel, kpBitsLabel, tagValueLabel, tagBitsLabel, indexBitsLabel))
            label.widthProperty().addListener((o, ov, nv) -> reposition.run());
        reposition.run();

        // ---- Highlight wires as their simulation step runs ---------------------------------
        bindActive(bodyView.activeProperty(), viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(userDown, viewModel.lineActiveProperty(TlbLine.USER_TO_TAG));
        bindActive(pageDown, viewModel.lineActiveProperty(TlbLine.PAGE_TO_TAG));
        bindActive(tagBrace, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(tagLine, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(tagTick, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(tagBitsLabel, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(tagValueLabel, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(wordPassLine, viewModel.lineActiveProperty(TlbLine.WORD_PASSTHROUGH));
        bindActive(wordBitsStart, viewModel.lineActiveProperty(TlbLine.WORD_PASSTHROUGH));
        bindActive(wordBitsEnd, viewModel.lineActiveProperty(TlbLine.WORD_PASSTHROUGH));
        bindActive(blockOut, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        bindActive(blockTick, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        bindActive(blockBitsLabel, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        bindActive(blockValueLabel, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        // Set-associative: each way-table draws its own block line; the body view lights the
        // resolved way's line + frame readout from this.
        if (setAssocBody != null) {
            setAssocBody.blockActiveProperty().bind(viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
            // The stacked way-tables can run past the fixed canvas height -- let it grow so the
            // ScrollPane can reach the lower tables and their block lines.
            bodyNode.boundsInParentProperty().addListener((o, ov, nv) ->
                    canvas.setPrefHeight(Math.max(CANVAS_HEIGHT, nv.getMaxY() + 120)));
        }

        if (split) {
            for (Node n : List.of(kpStub, kpTick, kpBitsLabel, kpValueLabel,
                    leftBranch, indexBranch, indexTick, indexBitsLabel))
                bindActive(n, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
            bindOutcome(tagValueLabel, viewModel.lookupOutcomeProperty());
        }

        if (viewModel.isSetAssociative())
            for (int w = 0; w < viewModel.getWayCount(); w++)
                viewModel.getWayRows(w).addListener((ListChangeListener<Row>) change -> updateRowData());
        else
            viewModel.getVisibleRows().addListener((ListChangeListener<Row>) change -> updateRowData());
        updateRowData();

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.getStyleClass().add("mmu-scroll-pane");
        getChildren().add(scrollPane);
    }

    // Both TLB families feed bodyView.addressAnchorProperty(); only how much of the address enters
    // differs. updateConnectors() draws either one full-tag line (fully-associative) or the fork
    // (direct-mapped / set-associative); here we only toggle which set of nodes is shown.
    private void wireAddressIntoBody(boolean splitAddress)
    {
        tagBrace.tipXProperty().addListener((o, ov, nv) -> updateConnectors());
        tagBrace.tipYProperty().addListener((o, ov, nv) -> updateConnectors());

        // tagTick / tagBitsLabel / tagValueLabel are shared: on tagLine when fully-associative, on
        // the fork's tag leg when direct-mapped / set-associative -- so only tagLine itself is
        // hidden by the split.
        tagLine.setVisible(!splitAddress);
        kpStub.setVisible(splitAddress);
        kpTick.setVisible(splitAddress);
        kpBitsLabel.setVisible(splitAddress);
        kpValueLabel.setVisible(splitAddress);
        leftBranch.setVisible(splitAddress);
        indexBranch.setVisible(splitAddress);
        indexTick.setVisible(splitAddress);
        // A one-slot direct-mapped TLB has no index bits -- keep the branch line, drop the "0b" tag.
        indexBitsLabel.setVisible(splitAddress && viewModel.getIndexBits() > 0);
    }

    private void updateConnectors()
    {
        if (canvas.getScene() == null)
            return;

        Region busAnchor = bodyView.addressAnchorProperty().get();

        // ---- Address wiring: one full-tag line, or the direct-mapped fork ------------------
        if (busAnchor != null && busAnchor.getScene() != null) {
            Bounds b = canvas.sceneToLocal(busAnchor.localToScene(busAnchor.getBoundsInLocal()));
            double busX = b.getCenterX();
            double busY = b.getCenterY();
            double tipY = tagBrace.tipYProperty().get() + BRACE_GAP;

            if (split)
                routeAddressSplit(busX, busY, tipY);
            else
                routeFullTagLine(busX, busY, tipY);
        }

        // ---- Block output ------------------------------------------------------------------
        if (viewModel.isSetAssociative()) {
            // The body view draws one block line per way-table + its own readout, joined by a
            // vertical bus whose top is the output tap. The tab puts that bus directly under the
            // PA Block box so blockOut is one straight riser from the tap into the box.
            if (blockBoxPA.getScene() != null && bodyNode.getScene() != null) {
                Bounds box = canvas.sceneToLocal(blockBoxPA.localToScene(blockBoxPA.getBoundsInLocal()));
                // Bus + riser align to the bottom-middle of the PA Block box.
                double busSceneX = canvas.localToScene(box.getCenterX(), 0).getX();
                setAssocBody.blockLineEndXProperty().set(bodyNode.sceneToLocal(busSceneX, 0).getX());

                Region tap = bodyView.tableBottomAnchorProperty().get();
                if (tap != null && tap.getScene() != null) {
                    Bounds t = canvas.sceneToLocal(tap.localToScene(tap.getBoundsInLocal()));
                    double riserX = t.getCenterX();
                    double tapY = t.getCenterY();
                    double intoY = box.getMaxY();
                    blockOut.getPoints().setAll(riserX, tapY, riserX, intoY);
                    positionTick(blockTick, riserX, (tapY + intoY) / 2);
                    blockBitsLabel.setLayoutX(riserX + 8);
                    blockBitsLabel.setLayoutY((tapY + intoY) / 2 - blockBitsLabel.getHeight() / 2);
                    toFront(blockOut, blockTick, blockBitsLabel);
                }
            }
            return;
        }

        Region bottomAnchor = bodyView.tableBottomAnchorProperty().get();
        Region blockCell = rowViews.isEmpty() ? null : rowViews.get(0).blockCellAnchorProperty().get();
        // ---- Block output: X from the row's Block cell, Y from the table bottom -------------
        if (blockCell != null && blockCell.getScene() != null
                && bottomAnchor != null && bottomAnchor.getScene() != null
                && blockBoxPA.getScene() != null) {
            Bounds cell = canvas.sceneToLocal(blockCell.localToScene(blockCell.getBoundsInLocal()));
            Bounds bottom = canvas.sceneToLocal(bottomAnchor.localToScene(bottomAnchor.getBoundsInLocal()));
            Bounds box = canvas.sceneToLocal(blockBoxPA.localToScene(blockBoxPA.getBoundsInLocal()));

            double colX = cell.getCenterX();
            double tableBottomY = bottom.getMaxY();
            double dropY = tableBottomY + BLOCK_DROP;
            double boxX = box.getCenterX();

            blockOut.getPoints().setAll(
                    colX, tableBottomY,
                    colX, dropY,
                    boxX, dropY,
                    boxX, box.getMaxY());
            positionTick(blockTick, colX, tableBottomY + BLOCK_DROP / 2);
            blockBitsLabel.setLayoutX(colX - blockBitsLabel.getWidth() - 10);
            blockBitsLabel.setLayoutY(tableBottomY + BLOCK_DROP / 2 - blockBitsLabel.getHeight() / 2);
            blockValueLabel.setLayoutX((colX + boxX) / 2 - blockValueLabel.getWidth() / 2);
            blockValueLabel.setLayoutY(dropY - 20);
            toFront(blockOut, blockTick, blockBitsLabel, blockValueLabel);
        }
    }

    // Associative / set-associative: brace tip -> straight down at mergeX -> across to the search bus.
    private void routeFullTagLine(double busX, double busY, double tipY)
    {
        tagLine.getPoints().setAll(mergeX, tipY, mergeX, busY, busX, busY);
        double legMidY = (tipY + busY) / 2;
        positionTick(tagTick, mergeX, legMidY);
        // Labels sit to the LEFT of the vertical leg; the table hugs its right side.
        tagBitsLabel.setLayoutX(mergeX - tagBitsLabel.getWidth() - 10);
        tagBitsLabel.setLayoutY(legMidY - tagBitsLabel.getHeight() / 2);
        tagValueLabel.setLayoutX(mergeX - tagValueLabel.getWidth() - 10);
        tagValueLabel.setLayoutY(busY - 24);
        toFront(tagLine, tagTick, tagBitsLabel, tagValueLabel);
    }

    // Direct-mapped fork: brace tip -> a long k@p stem -> a fork bar with two parallel legs. The
    // tag leg (left) drops to a dead-end hex readout coloured by the lookup outcome; the index leg
    // (right) drops to the selected row and elbows into its left edge.
    private void routeAddressSplit(double anchorX, double anchorY, double tipY)
    {
        double stemX = mergeX;
        double forkY = tipY + KP_STEM_LEN;
        double stemMidY = (tipY + forkY) / 2;

        kpStub.getPoints().setAll(stemX, tipY, stemX, forkY);
        positionTick(kpTick, stemX, stemMidY);
        kpBitsLabel.setLayoutX(stemX + 10);
        kpBitsLabel.setLayoutY(stemMidY - kpBitsLabel.getHeight() / 2);
        kpValueLabel.setLayoutX(stemX - kpValueLabel.getWidth() - 10);
        kpValueLabel.setLayoutY(stemMidY - kpValueLabel.getHeight() / 2);

        double tagLegX = stemX - FORK_HALF_WIDTH;
        double tagLegEndY = forkY + SPLIT_LEG_LEN;
        double tagLegMidY = (forkY + tagLegEndY) / 2;
        leftBranch.getPoints().setAll(stemX, forkY, tagLegX, forkY, tagLegX, tagLegEndY);
        positionTick(tagTick, tagLegX, tagLegMidY);
        tagBitsLabel.setLayoutX(tagLegX - tagBitsLabel.getWidth() - 8);
        tagBitsLabel.setLayoutY(tagLegMidY - tagBitsLabel.getHeight() / 2);
        tagValueLabel.setLayoutX(tagLegX - tagValueLabel.getWidth() / 2);
        tagValueLabel.setLayoutY(tagLegEndY + 4);

        double idxLegX = stemX + FORK_HALF_WIDTH;
        double idxLegMidY = (forkY + anchorY) / 2;
        indexBranch.getPoints().setAll(stemX, forkY, idxLegX, forkY, idxLegX, anchorY, anchorX, anchorY);
        positionTick(indexTick, idxLegX, idxLegMidY);
        indexBitsLabel.setLayoutX(idxLegX + 8);
        indexBitsLabel.setLayoutY(idxLegMidY - indexBitsLabel.getHeight() / 2);

        toFront(kpStub, kpTick, kpBitsLabel, kpValueLabel, leftBranch, tagTick, tagBitsLabel,
                tagValueLabel, indexBranch, indexTick, indexBitsLabel);
    }

    // Toggle the green/red hit-miss style classes on a node from the lookup outcome. Independent of
    // bindActive's :active pseudo-class, which several of these nodes also carry.
    private void bindOutcome(Node node, ObjectProperty<LookupOutcome> outcome)
    {
        Runnable apply = () -> {
            node.getStyleClass().removeAll("tlb-tag-hit", "tlb-tag-miss");
            switch (outcome.get()) {
                case HIT -> node.getStyleClass().add("tlb-tag-hit");
                case MISS, INSERT -> node.getStyleClass().add("tlb-tag-miss");
                case PENDING -> { }
            }
        };
        apply.run();
        outcome.addListener((o, ov, nv) -> apply.run());
    }

    private void updateRowData()
    {
        if (viewModel.isSetAssociative()) {
            for (int w = 0; w < wayRowViews.size(); w++)
                applyRows(wayRowViews.get(w), viewModel.getWayRows(w));
            return;
        }
        applyRows(rowViews, viewModel.getVisibleRows());
    }

    private void applyRows(List<PagedTLBRowView> views, List<Row> rows)
    {
        for (int i = 0; i < views.size(); i++) {
            PagedTLBRowView rowView = views.get(i);
            if (i >= rows.size()) {
                rowView.setVisible(false);
                continue;
            }
            Row row = rows.get(i);
            rowView.setVisible(true);
            rowView.updateFields(
                    row.index(),
                    row.valid(),
                    row.dirty(),
                    ValueConverter.toHex(row.tag(), viewModel.tagHexDigitsProperty().get()),
                    ValueConverter.toHex(row.block(), viewModel.blockHexDigitsProperty().get()));
            rowView.setHighlight(row.highlight());
        }
    }

    // ---- small view helpers ---------------------------------------------------------------

    private static void place(Region box, double x, double y)
    {
        box.setLayoutX(x);
        box.setLayoutY(y);
    }

    private static void bindBottomCenter(BitWidthLine wire, Region box)
    {
        wire.arrowTipVisibleProperty().set(false);
        wire.startXProperty().bind(Bindings.createDoubleBinding(
                () -> box.getLayoutX() + box.getWidth() / 2.0, box.layoutXProperty(), box.widthProperty()));
        wire.startYProperty().bind(Bindings.createDoubleBinding(
                () -> box.getLayoutY() + box.getHeight(), box.layoutYProperty(), box.heightProperty()));
    }

    private static BitWidthLine bitWidthWire(int bits)
    {
        BitWidthLine wire = new BitWidthLine();
        wire.bitsProperty().set(bits);
        wire.labelOnLeftProperty().set(false);
        return wire;
    }

    private static Polyline elbow(double... points)
    {
        Polyline polyline = new Polyline(points);
        polyline.setManaged(false);
        polyline.getStyleClass().add("connector-line");
        return polyline;
    }

    private static Line tick()
    {
        Line mark = new Line();
        mark.setManaged(false);
        mark.getStyleClass().add("connector-line");
        return mark;
    }

    private static void positionTick(Line mark, double centerX, double centerY)
    {
        mark.setStartX(centerX - 6);
        mark.setStartY(centerY + 6);
        mark.setEndX(centerX + 6);
        mark.setEndY(centerY - 6);
    }

    // Managed so the enclosing Pane autosizes it to its text; an unmanaged Label is never resized
    // and renders at 0x0. (The decorative Polyline/Line shapes stay unmanaged -- they carry their
    // own geometry and must not inflate the canvas bounds.)
    private static Label flowLabel()
    {
        Label label = new Label();
        label.getStyleClass().add("mmu-bit-value");
        return label;
    }

    private static Label bitLabel(int bits, double x, double y)
    {
        return FieldBoxes.bitLabel(bits, x, y);
    }

    private static void toFront(Node... nodes)
    {
        for (Node n : nodes)
            n.toFront();
    }

    private void bindActive(Node node, BooleanProperty active)
    {
        node.pseudoClassStateChanged(ACTIVE, active.get());
        active.addListener((o, ov, nv) -> node.pseudoClassStateChanged(ACTIVE, nv));
    }

    private void bindActive(BitWidthLine line, BooleanProperty active)
    {
        bindActive(line.getWire(), active);
        bindActive(line.getArrowHead(), active);
        bindActive(line.getTick(), active);
        bindActive(line.getBitsLabel(), active);
    }

    private void bindActive(BooleanProperty target, BooleanProperty source)
    {
        target.bind(source);
    }
}
