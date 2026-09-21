package rs.ac.bg.etf.view.tlb;

import java.util.ArrayList;
import java.util.List;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import rs.ac.bg.etf.model.simulation.SimulationConfig.TLBType;
import rs.ac.bg.etf.view.inspector.TLBInspectorWindow;
import rs.ac.bg.etf.view.shape.BitWidthLine;
import rs.ac.bg.etf.view.shape.CurlyBrace;
import rs.ac.bg.etf.view.util.FieldBoxes;
import rs.ac.bg.etf.view.util.PostLayoutTask;
import rs.ac.bg.etf.view.util.SchematicTab;
import rs.ac.bg.etf.view.util.UiScale;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel.LookupOutcome;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel.Row;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel.TlbLine;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel.TlbSideNote;

/**
 * Paged TLB tab: an address-formation schematic (Process / Virtual Address / Physical Address
 * field boxes) wired into the TLB body view. The body view ({@link AssociativeTLBView} today)
 * only exposes layout landmarks; this class owns the address-merge wiring and always routes the
 * block output off the bottom of the row table.
 */
public class PagedTLBTabView extends SchematicTab
{
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    // Starting estimates, not fixed sizes: the canvas grows to fill the tab's real viewport (see
    // SchematicTab.mountCanvas) and PA is bound to that live width, never a hardcoded pixel canvas
    // size -- matches PagedMMUTabView's treatment exactly. MIN_CANVAS_WIDTH is a floor under the
    // width derived from the laid-out table (see updateDesignWidth); CANVAS_HEIGHT only stands in
    // until the block wire's real bottom edge has been measured (see updateConnectors), so a wider
    // TLB never ends up with wires through its rows.
    private final double MIN_CANVAS_WIDTH = UiScale.px(900);
    private final double CANVAS_HEIGHT = UiScale.px(720);
    private final double MARGIN = UiScale.px(30);
    // How far the block wire's rising leg (or the set-associative output bus) must stay clear of the
    // table's right edge -- room for the wire's own tick, "Nb" tag and the frame-number readout.
    private final double BLOCK_WIRE_CLEARANCE = UiScale.px(60);
    // Room below the set-associative way-tables' stack for each way's block line (which drops just
    // under its table), the shared output bus's frame readout and the bottom margin.
    private final double SET_ASSOC_BLOCK_LINE_ALLOWANCE = UiScale.px(120);
    // Matches PagedMMUTabView's own boxY (78): the gap from the section-label title (y=20, set by
    // FieldBoxes.sectionLabel calls below) down to the field-box title (BOX_Y - 20) down to the box
    // itself must read the same on both schematic tabs.
    private final double BOX_Y = UiScale.px(78);
    // Shared by every field box in the Process/VA/PA row -- User, Page, Word, Block -- so they all
    // read as one consistent height (the MMU tab's taller "solo" BOX_HEIGHT is only for its own
    // standalone Page Table Pointer box, which has no counterpart here).
    private final double ADDRESS_BOX_HEIGHT = FieldBoxes.addressBoxHeight();
    private final double ADDRESS_FIELD_PADDING = FieldBoxes.addressFieldPadding();
    // Gap between a field title and the box it labels.
    private final double TITLE_GAP = FieldBoxes.titleGap();
    private final double PROCESS_GAP = UiScale.px(64);
    private final double TABLE_Y = UiScale.px(300);
    // How far right of the merge point the table sits -- past AssociativeTLBView's own bus-stub
    // length, purely for breathing room between the tag wiring and the table (the router recomputes
    // the live bus anchor either way, so this only steers the body node's initial placement).
    private final double BUS_STUB_APPROX = UiScale.px(150);
    private final double BRACE_EAR_Y = BOX_Y + ADDRESS_BOX_HEIGHT + UiScale.px(72);
    private final double BRACE_DEPTH = UiScale.px(16);
    private final double BRACE_GAP = UiScale.px(8);
    private final double BLOCK_DROP = UiScale.px(44);
    // Direct-mapped fork geometry: a long k@p stem from the brace tip to the fork bar, then two
    // parallel legs FORK_HALF_WIDTH px either side of it -- the tag leg drops SPLIT_LEG_LEN px to
    // its readout, the index leg drops to the selected row. The table is nudged down/right to fit.
    // FORK_HALF_WIDTH is wide enough that the tag leg's own labels (tick + value) never crowd the
    // index leg's, which sits the same distance out on the fork's other side.
    private final double KP_STEM_LEN = UiScale.px(72);
    private final double FORK_HALF_WIDTH = UiScale.px(40);
    // Long enough that the tag's own bit-width tick reads at a true, visually centred midpoint (not
    // cramped against the fork bar) with room left below it before the live value readout, which
    // sits under the wire's dead end -- see tagValueLabel's own gap below.
    private final double SPLIT_LEG_LEN = UiScale.px(110);
    // Gap between the tag leg's dead end and its live hex value readout, matching zeroFillLabel's
    // own gap treatment in PagedMMUTabView.
    private final double TAG_VALUE_GAP = UiScale.px(6);
    private final double DIRECT_TABLE_X = UiScale.px(220);
    private final double DIRECT_TABLE_Y = UiScale.px(84);
    // Set-associative stacks several tables, so it starts them well above the direct/assoc table
    // (the fork sits to their left, not above) -- but still clear of the word pass-through line and
    // the address boxes -- to give the 4-way case a chance of fitting with little/no scrolling.
    private final double SET_ASSOC_TABLE_Y = UiScale.px(210);
    // Gap between the "TLB" title's own measured bottom edge and the table's top border, matching
    // PagedMMUTabView's own TABLE_TITLE_GAP treatment for "Page Table" (live label height, not a
    // guessed pixel offset, so it holds regardless of font family/size changes down the line).
    private final double TABLE_TITLE_GAP = UiScale.px(8);

    private final PagedTLBTabViewModel viewModel;
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

    // Associative / set-associative (non-split): the whole user@page tag rides one line, brace tip
    // straight down then across into the body's search-bus anchor -- each straight leg its own
    // BitWidthLine segment (see BitWidthLine's own doc comment for why an elbow is a chain of
    // segments rather than one multi-point shape), the same treatment PagedMMUTabView's own elbowed
    // wires get.
    private final BitWidthLine tagDown;
    private final BitWidthLine tagAcross;

    // Direct-mapped / set-associative (split): the key forks at the brace tip -- a k@p stem down to
    // the fork bar, then two parallel legs. The tag leg dead-ends in a hex readout coloured by the
    // lookup outcome; the index leg carries on into the selected row. The tag leg's own "Nb" bit tag
    // rides its true midpoint (BitWidthLine's usual self-labelling); its live value is a separate,
    // manually-placed label sitting below the wire's dead end (see tagValueLabel) rather than
    // BitWidthLine's own mirrored-at-midpoint value label, which read too cramped against the tick.
    private final BitWidthLine kpDown;
    private final BitWidthLine tagStub;
    private final BitWidthLine tagLeg;
    // The tag leg's live hex value, positioned below its dead end -- see tagLeg's own doc comment.
    private final Label tagValueLabel;
    private final BitWidthLine indexStub;
    private final BitWidthLine indexLeg;
    private final BitWidthLine indexAcross;

    // Block output: always leaves the bottom of the table, into PA Block. Non-set-associative routes
    // its own drop/across/rise; set-associative only needs the riser from the body view's own shared
    // output bus up into the box (the body view draws its own per-way block lines). Both rises are
    // split into a far segment (no tag) + a near segment whose fixed short span puts its bit-width
    // tag at the same height as wordUpPA's own tag, instead of at the true midpoint of the whole
    // (much longer, and for set-associative variably long) drop/tap-to-box run.
    private final BitWidthLine blockDown;
    private final BitWidthLine blockAcross;
    private final BitWidthLine blockUpFar;
    private final BitWidthLine blockUpNear;
    private final BitWidthLine blockRiserFar;
    private final BitWidthLine blockRiserNear;
    private final Region blockBoxPA;
    // Width of PA's Word box, kept so updateDesignWidth knows how much room the PA pair needs.
    private double paWordBoxWidth;

    private final PostLayoutTask reposition;
    // Wires a re-route wants above the body view, in the order it asked for them (see updateConnectors).
    private final List<Node> raiseOrder = new ArrayList<>();

    public PagedTLBTabView(PagedTLBTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        canvas.designWidthProperty().set(MIN_CANVAS_WIDTH);
        canvas.designHeightProperty().set(CANVAS_HEIGHT);

        int tagDigits = viewModel.tagHexDigitsProperty().get();
        int blockDigits = viewModel.blockHexDigitsProperty().get();

        // ---- Process / Virtual Address / Physical Address field boxes -------------------------
        // Process's User box shares ADDRESS_BOX_HEIGHT with the VA/PA address boxes (not the taller
        // "solo" BOX_HEIGHT the MMU tab's standalone Page Table Pointer box uses) so all three field
        // boxes in this row read as one consistent height, only FIELD_PADDING keeps its narrower width.
        Region userBox = FieldBoxes.valueCell("field-box-cell-solo", viewModel.userHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getProcessIdBits()), ADDRESS_BOX_HEIGHT, FieldBoxes.fieldPadding());
        double userBoxW = userBox.getPrefWidth();
        double userBoxX = MARGIN;
        place(userBox, userBoxX, BOX_Y);

        Region pageBox = FieldBoxes.valueCell("field-box-cell-left", viewModel.pageHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getPageBits()), ADDRESS_BOX_HEIGHT, ADDRESS_FIELD_PADDING);
        double pageBoxW = pageBox.getPrefWidth();
        double pageBoxX = userBoxX + userBoxW + PROCESS_GAP;
        place(pageBox, pageBoxX, BOX_Y);

        Region wordBoxVA = FieldBoxes.valueCell("field-box-cell-right", viewModel.wordHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getWordBits()), ADDRESS_BOX_HEIGHT, ADDRESS_FIELD_PADDING);
        double wordBoxW = wordBoxVA.getPrefWidth();
        double wordBoxVAX = pageBoxX + pageBoxW;
        place(wordBoxVA, wordBoxVAX, BOX_Y);

        // Bound to the canvas's own live width (which itself grows to fill the tab -- see the
        // ScrollPane's setFitToWidth below), not a fixed pixel canvas size, so PA always sits flush
        // against the tab's real right edge instead of pinned at some fixed offset that leaves unused
        // space on a wider window -- mirrors PagedMMUTabView's wordBoxPA/blockBoxPA treatment exactly.
        Region wordBoxPA = FieldBoxes.valueCell("field-box-cell-right", viewModel.paWordHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getWordBits()), ADDRESS_BOX_HEIGHT, ADDRESS_FIELD_PADDING);
        wordBoxPA.layoutXProperty().bind(canvas.widthProperty().subtract(MARGIN).subtract(wordBoxW));
        wordBoxPA.setLayoutY(BOX_Y);
        paWordBoxWidth = wordBoxW;

        blockBoxPA = FieldBoxes.valueCell("field-box-cell-left", viewModel.blockHexProperty(), blockDigits, ADDRESS_BOX_HEIGHT, ADDRESS_FIELD_PADDING);
        double blockBoxW = blockBoxPA.getPrefWidth();
        blockBoxPA.layoutXProperty().bind(wordBoxPA.layoutXProperty().subtract(blockBoxW));
        blockBoxPA.setLayoutY(BOX_Y);

        Label userTitle = FieldBoxes.fieldTitle("User", userBox, BOX_Y - TITLE_GAP);
        Label pageTitle = FieldBoxes.fieldTitle("Page", pageBox, BOX_Y - TITLE_GAP);
        Label wordTitleVA = FieldBoxes.fieldTitle("Word", wordBoxVA, BOX_Y - TITLE_GAP);
        Label blockTitle = FieldBoxes.fieldTitle("Block", blockBoxPA, BOX_Y - TITLE_GAP);
        Label wordTitlePA = FieldBoxes.fieldTitle("Word", wordBoxPA, BOX_Y - TITLE_GAP);

        Label processHeader = FieldBoxes.sectionLabel("Process", userBoxX, UiScale.px(20));
        Label vaHeader = FieldBoxes.sectionLabel("Virtual Address", pageBoxX, UiScale.px(20));
        Label paHeader = FieldBoxes.sectionLabel("Physical Address", 0, UiScale.px(20));
        paHeader.layoutXProperty().bind(blockBoxPA.layoutXProperty());

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

        // Clicking the schematic's small windowed preview opens a separate, resizable window that
        // browses the full TLB (up to tlbSize entries), same affordance as the MMU tab's page table.
        bodyNode.getStyleClass().add("tlb-body-clickable");
        bodyNode.setCursor(javafx.scene.Cursor.HAND);
        TLBInspectorWindow tlbInspector =
                new TLBInspectorWindow(viewModel.getContext(), viewModel.currentStepNumberProperty());
        bodyNode.setOnMouseClicked(e ->
                tlbInspector.toggle(bodyNode.getScene() != null ? bodyNode.getScene().getWindow() : null));

        // Sits above the whole schematic table -- for set-associative that means above "Entry 0"'s
        // own (much quieter) per-way caption, distinguishing this shared title from those.
        Label tableHeader = FieldBoxes.sectionLabel("TLB", tableX, 0);
        // Shares .schematic-heading with PagedMMUTabView's "Page Table" title -- the schematic's own
        // table-name caption, styled like .schematic-heading but under its own name since it's
        // specifically these two tables' titles, not every schematic-block heading.
        tableHeader.getStyleClass().remove("schematic-heading");
        tableHeader.getStyleClass().add("schematic-heading");
        // Bound to the label's own measured height (not a guessed pixel offset), so its bottom edge
        // always sits TABLE_TITLE_GAP above the table's top border regardless of font changes.
        tableHeader.layoutYProperty().bind(Bindings.createDoubleBinding(
                () -> tableY - tableHeader.getHeight() - TABLE_TITLE_GAP, tableHeader.heightProperty()));

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

        // ---- Associative / set-associative full-tag path: brace tip -> straight down -> across
        // into the search bus. tagDown carries the tag's own bit-width tag + live hex value
        // (mirrored either side of its midpoint, same as PagedMMUTabView's offsetToAdder); tagAcross
        // is a plain passthrough leg into the search bus's own midpoint -- not a specific row -- which
        // then fans out via its own arrowed per-row stubs (AssociativeTLBView), so it carries no
        // arrow of its own; an arrow landing mid-bus would misread as pointing at one destination. ----
        tagDown = bitWidthWire(viewModel.getTagBits());
        tagDown.arrowTipVisibleProperty().set(false);
        tagDown.labelOnLeftProperty().set(true);
        tagDown.valueLabelVisibleProperty().set(true);
        tagDown.getValueLabel().textProperty().bind(hideWhenInactive(viewModel.tagHexProperty(), viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB)));

        tagAcross = bitWidthWire(viewModel.getTagBits());
        tagAcross.arrowTipVisibleProperty().set(false);
        tagAcross.bitWidthIndicatorVisibleProperty().set(false);

        // ---- Direct-mapped / set-associative fork: brace tip -> k@p stem -> fork bar -> tag leg
        // (dead-ends in a hex readout coloured by the lookup outcome) + index leg (carries on into
        // the selected row). kpDown carries its own bit-width tag + live value the same way tagDown
        // does above; the tag leg keeps its "Nb" tag at its own true midpoint (self-labelling, as
        // usual) but its live value is a separate manually-placed label below the wire's dead end
        // (tagValueLabel) rather than BitWidthLine's own mirrored-at-midpoint value label. ----
        kpDown = bitWidthWire(viewModel.getFullKeyBits());
        kpDown.arrowTipVisibleProperty().set(false);
        kpDown.valueLabelVisibleProperty().set(true);
        kpDown.getValueLabel().textProperty().bind(hideWhenInactive(viewModel.fullTagHexProperty(), viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB)));

        tagStub = bitWidthWire(viewModel.getTagBits());
        tagStub.arrowTipVisibleProperty().set(false);
        tagStub.bitWidthIndicatorVisibleProperty().set(false);

        tagLeg = bitWidthWire(viewModel.getTagBits());
        tagLeg.arrowTipVisibleProperty().set(false);
        tagLeg.labelOnLeftProperty().set(true);

        tagValueLabel = new Label();
        tagValueLabel.getStyleClass().add("wire-value-label");
        tagValueLabel.textProperty().bind(hideWhenInactive(viewModel.tagHexProperty(), viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB)));

        indexStub = bitWidthWire(viewModel.getIndexBits());
        indexStub.arrowTipVisibleProperty().set(false);
        indexStub.bitWidthIndicatorVisibleProperty().set(false);

        indexLeg = bitWidthWire(viewModel.getIndexBits());
        indexLeg.arrowTipVisibleProperty().set(false);
        // A one-slot direct-mapped TLB has no index bits -- a diagonal tick with no accompanying
        // bit count would read oddly, so hide the whole indicator (tick + "0b" label together --
        // BitWidthLine only exposes that toggle as one pair, unlike the tick-only hide the old
        // hand-placed label allowed) rather than just the text.
        indexLeg.bitWidthIndicatorVisibleProperty().set(viewModel.getIndexBits() > 0);

        indexAcross = bitWidthWire(viewModel.getIndexBits());
        // Direct-mapped: this leg lands on the one selected row itself, so the arrow reads as the
        // address wire's actual terminus. Set-associative: it lands on the shared set-index bus's own
        // midpoint (like tagAcross above), which then fans out via its own arrowed per-way taps -- so
        // it carries no arrow here, same reasoning as tagAcross.
        indexAcross.arrowTipVisibleProperty().set(!setAssociative);
        indexAcross.bitWidthIndicatorVisibleProperty().set(false);
        indexAcross.labelOnLeftProperty().set(true);
        indexAcross.valueLabelVisibleProperty().set(true);
        indexAcross.getValueLabel().textProperty().bind(hideWhenInactive(viewModel.indexValueProperty(), viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB)));

        // Associative / set-associative: the whole user@page tag rides one line into the body view's
        // search-bus anchor. Direct-mapped: the key forks here -- the high k@p-m tag bits dead-end
        // in a hex readout coloured by the lookup outcome, the low m index bits carry on into the
        // selected row (the body view's address anchor tracks it).
        wireAddressIntoBody(split);

        // ---- Word pass-through: VA Word flows straight across into PA Word, unchanged --------
        // Right-angle elbow, each straight leg its own BitWidthLine (mirrors PagedMMUTabView's own
        // wordDownVA/wordAcross/wordUpPA treatment exactly, including the property bindings that let
        // it track wordBoxPA's own live-bound layoutX with no manual re-routing).
        double passY = BOX_Y + ADDRESS_BOX_HEIGHT + UiScale.px(40);
        double wordVACenterX = wordBoxVAX + wordBoxW / 2;
        var wordPACenterX = wordBoxPA.layoutXProperty().add(wordBoxW / 2.0);

        BitWidthLine wordDownVA = bitWidthWire(viewModel.getWordBits());
        wordDownVA.arrowTipVisibleProperty().set(false);
        wordDownVA.startXProperty().set(wordVACenterX);
        wordDownVA.startYProperty().set(BOX_Y + ADDRESS_BOX_HEIGHT);
        wordDownVA.endXProperty().set(wordVACenterX);
        wordDownVA.endYProperty().set(passY);

        BitWidthLine wordAcross = bitWidthWire(viewModel.getWordBits());
        wordAcross.arrowTipVisibleProperty().set(false);
        wordAcross.bitWidthIndicatorVisibleProperty().set(false);
        wordAcross.startXProperty().set(wordVACenterX);
        wordAcross.startYProperty().set(passY);
        wordAcross.endXProperty().bind(wordPACenterX);
        wordAcross.endYProperty().set(passY);
        // Flipped so the value label lands above the wire, matching blockAcross's own treatment
        // (mirrors PagedMMUTabView's own wordAcross value label exactly).
        wordAcross.labelOnLeftProperty().set(true);
        Label wordFlowLabel = wordAcross.getValueLabel();
        wordFlowLabel.textProperty().bind(hideWhenInactive(viewModel.wordHexProperty(), viewModel.lineActiveProperty(TlbLine.WORD_PASSTHROUGH)));
        wordAcross.valueLabelVisibleProperty().set(true);

        // Runs bottom-to-top (start at passY, end at the box) so its arrow lands pointing into the
        // PA Word box, not away from it. Flipped labelOnLeft compensates so the tag still lands on
        // the right, the same side wordDownVA's tag reads on.
        BitWidthLine wordUpPA = bitWidthWire(viewModel.getWordBits());
        wordUpPA.labelOnLeftProperty().set(true);
        wordUpPA.startXProperty().bind(wordPACenterX);
        wordUpPA.startYProperty().set(passY);
        wordUpPA.endXProperty().bind(wordPACenterX);
        wordUpPA.endYProperty().set(BOX_Y + ADDRESS_BOX_HEIGHT);

        // ---- Block output: always leaves the bottom of the table, into PA Block -------------
        // Mutually exclusive with blockRiserFar/blockRiserNear below: updateConnectors() only ever
        // routes one of the two sets per instance (never both), so whichever this TLB isn't is
        // hidden here -- left visible (and so still sitting at its unrouted, un-positioned default
        // geometry) is exactly how a stray "Nb" label ends up rendered at the canvas origin.
        blockDown = bitWidthWire(viewModel.getFrameBits());
        blockDown.setVisible(!setAssociative);
        blockDown.arrowTipVisibleProperty().set(false);
        blockDown.labelOnLeftProperty().set(true);

        blockAcross = bitWidthWire(viewModel.getFrameBits());
        blockAcross.setVisible(!setAssociative);
        blockAcross.arrowTipVisibleProperty().set(false);
        blockAcross.bitWidthIndicatorVisibleProperty().set(false);
        blockAcross.labelOnLeftProperty().set(true);
        blockAcross.valueLabelVisibleProperty().set(true);
        blockAcross.getValueLabel().textProperty().bind(hideWhenInactive(viewModel.blockHexProperty(), viewModel.lineActiveProperty(TlbLine.BLOCK_OUT)));

        blockUpFar = bitWidthWire(viewModel.getFrameBits());
        blockUpFar.setVisible(!setAssociative);
        blockUpFar.arrowTipVisibleProperty().set(false);
        blockUpFar.bitWidthIndicatorVisibleProperty().set(false);

        // Carries the rise's own bit-width tag. Flipped from the default (unlike blockDown) so the
        // tag lands on the wire's right, matching wordUpPA's own tag on the Word wire beside it. Its
        // own short, fixed span (set in updateConnectors) puts that tag at the same height as
        // wordUpPA's own tag, mirroring PagedMMUTabView's blockUpNear treatment exactly.
        blockUpNear = bitWidthWire(viewModel.getFrameBits());
        blockUpNear.setVisible(!setAssociative);
        blockUpNear.labelOnLeftProperty().set(true);

        // Set-associative draws its own per-way block lines + frame value inside the body view; the
        // tab routes the riser from the body's output tap into PA Block, with no value readout of
        // its own. Split into far (no tag) + near (fixed short span) exactly like blockUpFar/
        // blockUpNear above, so its own "Nb" tag lands at the same height as wordUpPA's, instead of
        // at the true midpoint of the whole tap-to-box run (which varies with the number of ways).
        blockRiserFar = bitWidthWire(viewModel.getFrameBits());
        blockRiserFar.setVisible(setAssociative);
        blockRiserFar.arrowTipVisibleProperty().set(false);
        blockRiserFar.bitWidthIndicatorVisibleProperty().set(false);

        blockRiserNear = bitWidthWire(viewModel.getFrameBits());
        blockRiserNear.setVisible(setAssociative);
        blockRiserNear.labelOnLeftProperty().set(true);

        canvas.getChildren().addAll(
                processHeader, vaHeader, paHeader, tableHeader,
                userTitle, pageTitle, wordTitleVA, blockTitle, wordTitlePA,
                userDown, pageDown, tagBrace, tagDown, tagAcross,
                kpDown, tagStub, tagLeg, tagValueLabel, indexStub, indexLeg, indexAcross,
                wordDownVA, wordAcross, wordUpPA,
                blockDown, blockAcross, blockUpFar, blockUpNear, blockRiserFar, blockRiserNear,
                userBox, pageBox, wordBoxVA, blockBoxPA, wordBoxPA,
                bodyNode);

        // ---- Dynamic connectors: the anchors are marker Regions the body view positions during
        // its own layout pass, so re-run whenever that layout (or ours) changes -- once per pulse,
        // right after layout (see PostLayoutTask), not once per change and a frame behind. ------
        reposition = new PostLayoutTask(this, this::updateConnectors);
        bodyView.addressAnchorProperty().addListener((o, ov, nv) -> reposition.request());
        bodyView.tableBottomAnchorProperty().addListener((o, ov, nv) -> reposition.request());
        bodyNode.layoutBoundsProperty().addListener((o, ov, nv) -> reposition.request());
        bodyNode.boundsInParentProperty().addListener((o, ov, nv) -> reposition.request());
        for (PagedTLBRowView row : rowViews) {
            row.blockCellAnchorProperty().addListener((o, ov, nv) -> reposition.request());
            row.layoutBoundsProperty().addListener((o, ov, nv) -> reposition.request());
        }
        canvas.widthProperty().addListener((o, ov, nv) -> reposition.request());
        canvas.heightProperty().addListener((o, ov, nv) -> reposition.request());
        sceneProperty().addListener((o, ov, nv) -> reposition.request());
        // The direct-mapped / set-associative body view moves its address marker in place (no
        // property swap), so the index wire must be re-routed when the selected row -- or, for
        // set-associative, the resolved way the block output leaves from -- changes.
        viewModel.selectedWindowRowProperty().addListener((o, ov, nv) -> reposition.request());
        viewModel.resolvedWayProperty().addListener((o, ov, nv) -> reposition.request());
        // Several labels are positioned relative to their own measured width; re-route once the
        // layout pass has actually sized them (getWidth() is 0 on the first connector pass).
        for (Label label : List.of(kpDown.getValueLabel(), kpDown.getBitsLabel(), tagValueLabel,
                tagLeg.getBitsLabel(), indexLeg.getBitsLabel(), indexAcross.getValueLabel()))
            label.widthProperty().addListener((o, ov, nv) -> reposition.request());
        reposition.request();

        // ---- Highlight wires as their simulation step runs ---------------------------------
        bindActive(bodyView.activeProperty(), viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(userDown, viewModel.lineActiveProperty(TlbLine.USER_TO_TAG));
        bindActive(pageDown, viewModel.lineActiveProperty(TlbLine.PAGE_TO_TAG));
        bindActive(tagBrace, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(tagDown, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(tagAcross, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
        bindActive(wordDownVA, viewModel.lineActiveProperty(TlbLine.WORD_PASSTHROUGH));
        bindActive(wordAcross, viewModel.lineActiveProperty(TlbLine.WORD_PASSTHROUGH));
        bindActive(wordUpPA, viewModel.lineActiveProperty(TlbLine.WORD_PASSTHROUGH));
        bindActive(blockDown, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        bindActive(blockAcross, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        bindActive(blockUpFar, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        bindActive(blockUpNear, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        bindActive(blockRiserFar, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        bindActive(blockRiserNear, viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
        // Set-associative: each way-table draws its own block line; the body view lights the
        // resolved way's line + frame readout from this.
        if (setAssocBody != null) {
            setAssocBody.blockActiveProperty().bind(viewModel.lineActiveProperty(TlbLine.BLOCK_OUT));
            // The stacked way-tables can run past the base design height -- let it grow so the
            // whole stack (and so the workbench's scale-to-fit) accounts for the lower tables and
            // their block lines. layoutBounds, not boundsInParent: the rows' fog BoxBlur inflates
            // boundsInParent by a different amount depending on whether a lookup has run yet, which
            // would make this minimum -- and with it the whole UI's scale -- flicker between steps.
            bodyNode.layoutBoundsProperty().addListener((o, ov, nv) ->
                    canvas.designHeightProperty().set(bodyNode.getLayoutY() + nv.getHeight() + SET_ASSOC_BLOCK_LINE_ALLOWANCE));
        }

        if (split) {
            // bindActive's BitWidthLine overload reaches into each wire's own wire/tick/label
            // children (see that overload's own doc comment) -- looping over a plain Node list here
            // would only toggle the outer Group's pseudo-class, leaving the children (and so the
            // wire's rendered colour) stuck grey.
            for (BitWidthLine line : List.of(kpDown, tagStub, tagLeg, indexStub, indexLeg, indexAcross))
                bindActive(line, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
            bindActive(tagValueLabel, viewModel.lineActiveProperty(TlbLine.ADDRESS_TO_TLB));
            bindOutcome(tagValueLabel, viewModel.lookupOutcomeProperty());
        }

        if (viewModel.isSetAssociative())
            for (int w = 0; w < viewModel.getWayCount(); w++)
                viewModel.getWayRows(w).addListener((ListChangeListener<Row>) change -> updateRowData());
        else
            viewModel.getVisibleRows().addListener((ListChangeListener<Row>) change -> updateRowData());
        updateRowData();

        // Grows the canvas to fill the viewport (never below its design size), which is what lets
        // Physical Address's live binding actually reach the tab's true right edge instead of a
        // fixed pixel canvas size -- matches PagedMMUTabView.
        mountCanvas();
        getChildren().add(buildSideNoteOverlay(viewModel));
    }

    // An eviction can invalidate a TLB entry belonging to a victim key that isn't the one currently
    // addressed -- the visible window above has no row to show that on. Rather than thread this
    // through the schematic's own connector-routing math, it's an overlay pinned to this StackPane
    // directly (a sibling of the ScrollPane, not inside the canvas), matching PagedMMUTabView's own
    // side-note treatment exactly, so it can never collide with the wiring above and stays visible
    // regardless of scroll position or which TLB body type (associative/direct/set-associative) is
    // mounted. The entry itself is rendered as an actual little table (reusing
    // .data-table/.data-table-header/.data-table-row/.data-table-cell verbatim, the same way
    // every real TLB row in this tab is built) so it reads as "here is that entry," not another
    // line of step-description prose.
    private Node buildSideNoteOverlay(PagedTLBTabViewModel viewModel)
    {
        Label headline = new Label();
        headline.getStyleClass().add("side-effect-note-headline");

        Label userLabel = new Label();
        userLabel.getStyleClass().add("side-effect-note-user");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button dismissButton = new Button("×");
        dismissButton.getStyleClass().add("side-effect-note-dismiss");
        dismissButton.setOnAction(e -> viewModel.dismissSideNote());

        HBox header = new HBox(UiScale.px(6), headline, userLabel, spacer, dismissButton);
        header.getStyleClass().add("side-effect-note-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // Widths mirror TLBRowView.cell()'s own approach (a fixed width per column, so text
        // centers within a real box) -- without them every cell collapses to its own bare text
        // width with no gap between columns at all.
        double indexWidth = WidthCalculator.plainColumnWidth("Index", 4);
        double bitWidth = WidthCalculator.plainColumnWidth("V", 1);
        double tagWidth = WidthCalculator.columnWidth("Tag", viewModel.tagHexDigitsProperty().get());
        double blockWidth = WidthCalculator.columnWidth("Block", viewModel.blockHexDigitsProperty().get());

        Label indexCell = tableCell("", indexWidth);
        Label vCell = tableCell("", bitWidth);
        Label dCell = tableCell("", bitWidth);
        Label tagCell = tableCell("", tagWidth);
        Label blockCell = tableCell("", blockWidth);
        HBox headerRow = new HBox(
                tableCell("Index", indexWidth), tableCell("V", bitWidth), tableCell("D", bitWidth),
                tableCell("Tag", tagWidth), tableCell("Block", blockWidth));
        headerRow.getStyleClass().add("data-table-header");
        HBox dataRow = new HBox(indexCell, vCell, dCell, tagCell, blockCell);
        dataRow.getStyleClass().add("data-table-row");
        VBox miniTable = new VBox(headerRow, dataRow);
        miniTable.getStyleClass().add("data-table");

        VBox card = new VBox(UiScale.px(6), header, miniTable);
        card.getStyleClass().add("side-effect-note-card");
        // A Region's default max width/height is unbounded, so without capping both, StackPane
        // stretches this to fill the whole tab (exactly what happened before this fix).
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.BOTTOM_LEFT);
        StackPane.setMargin(card, new Insets(0, 0, MARGIN, MARGIN));

        Runnable rebuild = () -> {
            TlbSideNote note = viewModel.sideNoteProperty().get();
            boolean visible = note != null;
            card.setVisible(visible);
            card.setManaged(visible);
            if (!visible)
                return;

            headline.setText(note.headline());
            userLabel.setText(note.user() >= 0 ? "user " + note.user() + ", page " + note.page() : "");
            indexCell.setText(note.index() >= 0 ? Integer.toString(note.index()) : "-");
            vCell.setText("0");
            dCell.setText(note.dirty() ? "1" : "0");
            tagCell.setText(ValueConverter.toHex(note.tag(), viewModel.tagHexDigitsProperty().get()));
            blockCell.setText(ValueConverter.toHex(note.block(), viewModel.blockHexDigitsProperty().get()));
        };
        viewModel.sideNoteProperty().addListener((o, ov, nv) -> rebuild.run());
        rebuild.run();

        return card;
    }

    private Label tableCell(String text, double width)
    {
        Label label = new Label(text);
        label.getStyleClass().add("data-table-cell");
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    // Both TLB families feed bodyView.addressAnchorProperty(); only how much of the address enters
    // differs. updateConnectors() draws either one full-tag line (fully-associative) or the fork
    // (direct-mapped / set-associative); here we only toggle which set of nodes is shown.
    private void wireAddressIntoBody(boolean splitAddress)
    {
        tagBrace.tipXProperty().addListener((o, ov, nv) -> updateConnectors());
        tagBrace.tipYProperty().addListener((o, ov, nv) -> updateConnectors());

        tagDown.setVisible(!splitAddress);
        tagAcross.setVisible(!splitAddress);
        kpDown.setVisible(splitAddress);
        tagStub.setVisible(splitAddress);
        tagLeg.setVisible(splitAddress);
        tagValueLabel.setVisible(splitAddress);
        indexStub.setVisible(splitAddress);
        indexLeg.setVisible(splitAddress);
        // Fully-associative (non-split) never routes an index leg -- routeFullTagLine() never
        // positions or activates indexAcross, so without this it's stuck showing indexValueProperty()
        // at its unpositioned (0,0) construction default in default (inactive/grey) styling.
        indexAcross.setVisible(splitAddress);
    }

    // The narrowest the schematic can be drawn without the block output (the rising leg into PA
    // Block, or the set-associative output bus beneath it) cutting through the table: the table's
    // right edge, a run-out for the wire's own labels, PA Block's half-width, then PA Word flush to
    // the margin. Measured from the first data row's real laid-out bounds -- which grow with the
    // configured tag/block hex digits -- in canvas coordinates. Deliberately not from the body
    // view's own width: the set-associative body's overlay reaches out to wherever PA Block sits,
    // which would make this depend on the canvas's own width and chase its own tail.
    //
    // The row's preferred width (fixed cell widths, known at construction) is the fallback for a tab
    // that hasn't been laid out yet -- e.g. one that has never been selected -- so the minimum is
    // right from the start instead of jumping the whole UI's scale the first time it's opened. The
    // table's left edge is the body node's own X in that case (the search-bus stubs live in the
    // body's unmanaged overlay, outside its layout).
    private void updateDesignWidth()
    {
        if (rowViews.isEmpty())
            return;

        PagedTLBRowView row = rowViews.get(0);
        double tableRight = bodyNode.getLayoutX() + row.prefWidth(-1);
        if (row.getScene() != null)
        {
            Bounds rowBounds = canvas.sceneToLocal(row.localToScene(row.getLayoutBounds()));
            tableRight = Math.max(tableRight, rowBounds.getMaxX());
        }
        double needed = tableRight + BLOCK_WIRE_CLEARANCE + blockBoxPA.getPrefWidth() / 2.0
                + paWordBoxWidth + MARGIN;
        canvas.designWidthProperty().set(Math.max(MIN_CANVAS_WIDTH, needed));
    }

    // A set-associative TLB stacks one table per way, so the schematic's height grows with the way
    // count. Asking the workbench to make room for all of them would shrink the whole UI (down to the
    // minimum scale) to fit a tall stack, so it is asked for the top of the schematic plus a single
    // way -- the same height the other TLB types need -- and the rest of the stack is scrolled to.
    @Override
    protected double heightToFit(double schematicHeight)
    {
        if (setAssocBody == null || bodyNode == null)
            return schematicHeight;
        return Math.min(schematicHeight,
                bodyNode.getLayoutY() + setAssocBody.wayHeight() + SET_ASSOC_BLOCK_LINE_ALLOWANCE);
    }

    private void raise(Node... nodes)
    {
        for (Node node : nodes)
            raiseOrder.add(node);
    }

    private void updateConnectors()
    {
        // Every wire the pass routes is lifted in one call at the end, not one raise per group: the
        // address wires and the block wires would each find the other's on top of theirs and
        // reshuffle the canvas's children on every pass (see SchematicCanvas.bringToFront).
        raiseOrder.clear();
        try {
            routeConnectors();
        } finally {
            canvas.bringToFront(raiseOrder.toArray(Node[]::new));
        }
    }

    private void routeConnectors()
    {
        // The wires below are routed to the body view's marker nodes; bring those up to date first
        // rather than relying on the body's own job having run already (no guaranteed order).
        bodyView.syncAnchors();
        updateDesignWidth();

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
            // PA Block box so the riser runs straight from the tap into the box.
            if (blockBoxPA.getScene() != null && bodyNode.getScene() != null) {
                Bounds box = canvas.sceneToLocal(blockBoxPA.localToScene(blockBoxPA.getBoundsInLocal()));
                // Bus + riser align to the bottom-middle of the PA Block box.
                double busSceneX = canvas.localToScene(box.getCenterX(), 0).getX();
                setAssocBody.blockLineEndXProperty().set(bodyNode.sceneToLocal(busSceneX, 0).getX());
                // The output tap below moves with that end X, so let the body place it before it is read.
                bodyView.syncAnchors();

                Region tap = bodyView.tableBottomAnchorProperty().get();
                if (tap != null && tap.getScene() != null) {
                    Bounds t = canvas.sceneToLocal(tap.localToScene(tap.getBoundsInLocal()));
                    double riserX = t.getCenterX();
                    double tapY = t.getCenterY();
                    double intoY = box.getMaxY();

                    // blockRiserNear's own span is 40 (matching wordUpPA's own box-to-passY span and
                    // blockUpNear above), so its own midpoint -- where its tag lands -- sits the same
                    // 20px below the box edge that wordUpPA's tag does, regardless of how far below
                    // the tap itself sits (which grows with the number of ways stacked).
                    double riserNearStartY = intoY + UiScale.px(40);

                    blockRiserFar.startXProperty().set(riserX);
                    blockRiserFar.startYProperty().set(tapY);
                    blockRiserFar.endXProperty().set(riserX);
                    blockRiserFar.endYProperty().set(riserNearStartY);

                    blockRiserNear.startXProperty().set(riserX);
                    blockRiserNear.startYProperty().set(riserNearStartY);
                    blockRiserNear.endXProperty().set(riserX);
                    blockRiserNear.endYProperty().set(intoY);

                    raise(blockRiserFar, blockRiserNear);
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

            // The wire's lowest point is the schematic's bottom edge, so the design height is
            // exactly that plus a margin -- CANVAS_HEIGHT is only the estimate used until this first
            // runs.
            canvas.designHeightProperty().set(dropY + MARGIN);

            blockDown.startXProperty().set(colX);
            blockDown.startYProperty().set(tableBottomY);
            blockDown.endXProperty().set(colX);
            blockDown.endYProperty().set(dropY);

            blockAcross.startXProperty().set(colX);
            blockAcross.startYProperty().set(dropY);
            blockAcross.endXProperty().set(boxX);
            blockAcross.endYProperty().set(dropY);

            // blockUpNear's own span is 40 (matching wordUpPA's own box-to-passY span exactly -- see
            // its own construction above), so its own midpoint -- where its tag lands -- sits the
            // same 20px below the box edge that wordUpPA's tag does, reading as "the same height"
            // even though the block wire's overall drop is much longer than the word wire's.
            double blockUpNearStartY = box.getMaxY() + UiScale.px(40);

            blockUpFar.startXProperty().set(boxX);
            blockUpFar.startYProperty().set(dropY);
            blockUpFar.endXProperty().set(boxX);
            blockUpFar.endYProperty().set(blockUpNearStartY);

            blockUpNear.startXProperty().set(boxX);
            blockUpNear.startYProperty().set(blockUpNearStartY);
            blockUpNear.endXProperty().set(boxX);
            blockUpNear.endYProperty().set(box.getMaxY());

            raise(blockDown, blockAcross, blockUpFar, blockUpNear);
        }
    }

    // Associative / set-associative: brace tip -> straight down at mergeX -> across to the search bus.
    private void routeFullTagLine(double busX, double busY, double tipY)
    {
        tagDown.startXProperty().set(mergeX);
        tagDown.startYProperty().set(tipY);
        tagDown.endXProperty().set(mergeX);
        tagDown.endYProperty().set(busY);

        tagAcross.startXProperty().set(mergeX);
        tagAcross.startYProperty().set(busY);
        tagAcross.endXProperty().set(busX);
        tagAcross.endYProperty().set(busY);

        raise(tagDown, tagAcross);
    }

    // Direct-mapped fork: brace tip -> a long k@p stem -> a fork bar with two parallel legs. The
    // tag leg (left) drops to a dead-end hex readout coloured by the lookup outcome; the index leg
    // (right) drops to the selected row and elbows into its left edge.
    private void routeAddressSplit(double anchorX, double anchorY, double tipY)
    {
        double stemX = mergeX;
        double forkY = tipY + KP_STEM_LEN;

        kpDown.startXProperty().set(stemX);
        kpDown.startYProperty().set(tipY);
        kpDown.endXProperty().set(stemX);
        kpDown.endYProperty().set(forkY);

        double tagLegX = stemX - FORK_HALF_WIDTH;
        double tagLegEndY = forkY + SPLIT_LEG_LEN;

        tagStub.startXProperty().set(stemX);
        tagStub.startYProperty().set(forkY);
        tagStub.endXProperty().set(tagLegX);
        tagStub.endYProperty().set(forkY);

        tagLeg.startXProperty().set(tagLegX);
        tagLeg.startYProperty().set(forkY);
        tagLeg.endXProperty().set(tagLegX);
        tagLeg.endYProperty().set(tagLegEndY);

        // Live tag value sits below the leg's dead end, centred on it -- not mirrored at the tick's
        // own midpoint (see tagLeg's own doc comment).
        tagValueLabel.autosize();
        tagValueLabel.setLayoutX(tagLegX - tagValueLabel.getWidth() / 2.0);
        tagValueLabel.setLayoutY(tagLegEndY + TAG_VALUE_GAP);

        double idxLegX = stemX + FORK_HALF_WIDTH;

        indexStub.startXProperty().set(stemX);
        indexStub.startYProperty().set(forkY);
        indexStub.endXProperty().set(idxLegX);
        indexStub.endYProperty().set(forkY);

        indexLeg.startXProperty().set(idxLegX);
        indexLeg.startYProperty().set(forkY);
        indexLeg.endXProperty().set(idxLegX);
        indexLeg.endYProperty().set(anchorY);

        indexAcross.startXProperty().set(idxLegX);
        indexAcross.startYProperty().set(anchorY);
        indexAcross.endXProperty().set(anchorX);
        indexAcross.endYProperty().set(anchorY);

        raise(kpDown, tagStub, tagLeg, tagValueLabel, indexStub, indexLeg, indexAcross);
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

    // A straight signal wire that carries its own mid-span bit-width tag (diagonal tick + "Nb" label),
    // replacing the old hand-placed line + tick + bitLabel trios. The tag rides on the wire's right
    // side by default; callers flip labelOnLeftProperty() where the reference schematic reads
    // differently.
    private static BitWidthLine bitWidthWire(int bits)
    {
        BitWidthLine wire = new BitWidthLine();
        wire.bitsProperty().set(bits);
        wire.labelOnLeftProperty().set(false);
        return wire;
    }

    // Wire-borne value readouts only make sense once their step has actually run; blank them out
    // rather than showing a "/" placeholder, unlike the Process/VA/PA field boxes which keep "/"
    // as an empty-state cue (see viewModel's userHex/pageHex/... "/" defaults).
    private static javafx.beans.binding.StringExpression hideWhenInactive(
            javafx.beans.property.StringProperty valueProperty, BooleanProperty active)
    {
        return Bindings.when(active).then(valueProperty).otherwise("");
    }

    private void bindActive(Node node, BooleanProperty active)
    {
        node.pseudoClassStateChanged(ACTIVE, active.get());
        active.addListener((o, ov, nv) -> node.pseudoClassStateChanged(ACTIVE, nv));
    }

    // Reaches into a BitWidthLine so its wire, arrow head, tick, bit-width label and value label all
    // light up together -- matches PagedMMUTabView's own BitWidthLine overload.
    private void bindActive(BitWidthLine line, BooleanProperty active)
    {
        bindActive(line.getWire(), active);
        bindActive(line.getArrowHead(), active);
        bindActive(line.getTick(), active);
        bindActive(line.getBitsLabel(), active);
        bindActive(line.getValueLabel(), active);
    }

    private void bindActive(BooleanProperty target, BooleanProperty source)
    {
        target.bind(source);
    }
}
