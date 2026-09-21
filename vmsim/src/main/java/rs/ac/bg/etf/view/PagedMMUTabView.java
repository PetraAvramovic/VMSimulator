package rs.ac.bg.etf.view;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import rs.ac.bg.etf.view.inspector.PageTableInspectorWindow;
import rs.ac.bg.etf.view.shape.BitWidthLine;
import rs.ac.bg.etf.view.shape.CurlyBrace;
import rs.ac.bg.etf.view.util.FieldBoxes;
import rs.ac.bg.etf.view.util.PostLayoutTask;
import rs.ac.bg.etf.view.util.SchematicTab;
import rs.ac.bg.etf.view.util.UiScale;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel.MmuLine;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel.MmuSideNote;

import java.util.List;

/**
 * Paged MMU tab: address-computation schematic (VA/PA field boxes, page-table-pointer adder,
 * and the windowed page table) connected by lines, mirroring the paged-MMU hardware diagram.
 */
public class PagedMMUTabView extends SchematicTab {
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    // Every length below is a design-size (1080p) value passed through UiScale.px() -- and every font
    // through UiScale.font() -- at the scale this tab is built at. The screen is rebuilt when the UI
    // scale changes, so they are fixed for this instance's lifetime, hence instance fields (kept in
    // the constants' UPPER_CASE names so the many call sites below read as before).

    // Field-box font/padding/height live in FieldBoxes (shared with the TLB tab); aliased here so the
    // many BOX_HEIGHT/FIELD_FONT call sites below stay unchanged.
    private final Font FIELD_FONT = FieldBoxes.fieldFont();
    private final Font TITLE_FONT = Font.font("IBM Plex Sans", javafx.scene.text.FontWeight.BOLD, UiScale.font(11));
    private final double TITLE_PADDING = UiScale.px(40);
    private final double MIN_FIELD_WIDTH = FieldBoxes.minFieldWidth();
    private final double BOX_HEIGHT = FieldBoxes.boxHeight();
    // Shorter than BOX_HEIGHT -- used only by the paired VA/PA address fields (Page|Word, Block|Word),
    // not the solo Page Table Pointer box, which keeps the taller BOX_HEIGHT. See FieldBoxes' own doc
    // comment on ADDRESS_BOX_HEIGHT for why the two diverge.
    private final double ADDRESS_BOX_HEIGHT = FieldBoxes.addressBoxHeight();
    private final double ADDRESS_FIELD_PADDING = FieldBoxes.addressFieldPadding();
    // Gap between a field title and the box it labels.
    private final double TITLE_GAP = FieldBoxes.titleGap();
    // Starting estimates, not fixed sizes: the canvas grows to fill the tab's real viewport (see
    // SchematicTab.mountCanvas) and PA is bound to that live width, never a hardcoded pixel canvas
    // size. MIN_CANVAS_WIDTH is a floor under the width derived from the laid-out table (see the
    // designWidth binding below); CANVAS_HEIGHT only stands in until the block wire's real bottom
    // edge has been measured (see updateDynamicConnectors), so a wider config never ends up with
    // wires running through the table.
    private final double MIN_CANVAS_WIDTH = UiScale.px(920);
    private final double CANVAS_HEIGHT = UiScale.px(780);
    private final double MARGIN = UiScale.px(30);
    // How far the block wire's rising leg must stay clear of the table's right edge -- room for the
    // wire's own tick + "Nb" tag, which ride to that leg's side.
    private final double BLOCK_WIRE_CLEARANCE = UiScale.px(60);
    // How far below the table's bottom edge the block wire drops before turning toward PA Block.
    private final double BLOCK_DROP_LENGTH = UiScale.px(80);
    // Every field that drops straight down from the page table's bottom edge uses this wire length, so
    // that their bit-width indicators (each drawn at its wire's midpoint) sit on one horizontal row.
    private final double DROP_LENGTH = UiScale.px(40);
    private final double DROP_INDICATOR_Y = DROP_LENGTH / 2;
    // Vertical gap between the descriptor-size brace's ends and the table's own top border, and how
    // far its tip then bulges upward from there, toward the "2^N words" label above it.
    private final double DESCRIPTOR_BRACE_GAP = UiScale.px(3);
    private final double DESCRIPTOR_BRACE_DEPTH = UiScale.px(16);
    // Gap between the "Page Table" title's own bottom and the brace's left end, which it sits above.
    private final double TABLE_TITLE_GAP = UiScale.px(6);

    private PagedMMUTabViewModel viewModel;

    public PagedMMUTabView(PagedMMUTabViewModel viewModel) {
        this.viewModel = viewModel;
        canvas.designHeightProperty().set(CANVAS_HEIGHT);

        // ---- Standalone field width: Table Offset has no rendered box of its own (its value rides
        // a wire label instead -- see below), only this phantom width for layout math. ----
        double offsetBoxW = titledFieldWidth("Table Offset", ValueConverter.hexDigitsFor(viewModel.getOffsetBits()));

        double boxY = UiScale.px(78);

        // ---- Virtual Address: Page | Word, rendered as one divided box (no gap, shared border) ----
        Region pageBox = FieldBoxes.valueCell("field-box-cell-left", viewModel.pageHexProperty(), ValueConverter.hexDigitsFor(viewModel.getPageBits()), ADDRESS_BOX_HEIGHT, ADDRESS_FIELD_PADDING);
        double pageBoxW = pageBox.getPrefWidth();
        double pageBoxX = MARGIN;
        pageBox.setLayoutX(pageBoxX);
        pageBox.setLayoutY(boxY);

        Region wordBoxVA = FieldBoxes.valueCell("field-box-cell-right", viewModel.wordHexProperty(), ValueConverter.hexDigitsFor(viewModel.getWordBits()), ADDRESS_BOX_HEIGHT, ADDRESS_FIELD_PADDING);
        double wordBoxW = wordBoxVA.getPrefWidth();
        double wordBoxVAX = pageBoxX + pageBoxW;
        wordBoxVA.setLayoutX(wordBoxVAX);
        wordBoxVA.setLayoutY(boxY);

        Label pageTitle = FieldBoxes.fieldTitle("Page", pageBox, boxY - TITLE_GAP);
        Label wordTitleVA = FieldBoxes.fieldTitle("Word", wordBoxVA, boxY - TITLE_GAP);

        // ---- Physical Address: Block | Word, mirrored on the right with the same adjacent-box treatment.
        // Bound to the canvas's own live width (which itself grows to fill the tab -- see the
        // ScrollPane's setFitToWidth below), not a fixed pixel canvas size, so PA always sits flush
        // against the tab's real right edge instead of pinned at some fixed offset that leaves unused
        // space on a wider window. ----
        Region wordBoxPA = FieldBoxes.valueCell("field-box-cell-right", viewModel.paWordHexProperty(), ValueConverter.hexDigitsFor(viewModel.getWordBits()), ADDRESS_BOX_HEIGHT, ADDRESS_FIELD_PADDING);
        wordBoxPA.layoutXProperty().bind(canvas.widthProperty().subtract(MARGIN).subtract(wordBoxW));
        wordBoxPA.setLayoutY(boxY);

        Region blockBoxPA = FieldBoxes.valueCell("field-box-cell-left", viewModel.blockHexProperty(), viewModel.blockHexDigitsProperty().get(), ADDRESS_BOX_HEIGHT, ADDRESS_FIELD_PADDING);
        double blockBoxW = blockBoxPA.getPrefWidth();
        blockBoxPA.layoutXProperty().bind(wordBoxPA.layoutXProperty().subtract(blockBoxW));
        blockBoxPA.setLayoutY(boxY);

        Label blockTitle = FieldBoxes.fieldTitle("Block", blockBoxPA, boxY - TITLE_GAP);
        Label wordTitlePA = FieldBoxes.fieldTitle("Word", wordBoxPA, boxY - TITLE_GAP);

        Label vaHeader = FieldBoxes.sectionLabel("Virtual Address", pageBoxX, UiScale.px(20));
        Label paHeader = FieldBoxes.sectionLabel("Physical Address", 0, UiScale.px(20));
        paHeader.layoutXProperty().bind(blockBoxPA.layoutXProperty());

        // ---- Word pass-through: VA Word flows straight across into PA Word, unchanged ----
        // Right-angle elbow, each straight leg its own BitWidthLine (see that class's own doc comment
        // for why an elbow is a chain of segments rather than one multi-point shape). The bit-width
        // tag shows on both vertical legs, matching the original two-sided "Nb" labelling; the
        // horizontal leg in between carries none.
        double passY = boxY + ADDRESS_BOX_HEIGHT + UiScale.px(40);
        double wordVACenterX = wordBoxVAX + wordBoxW / 2;
        // PA Word's own centre moves whenever wordBoxPA's live-bound layoutX does, so this has to be
        // a binding too, not a one-time double, or the wire would stay put while PA slides away from it.
        var wordPACenterX = wordBoxPA.layoutXProperty().add(wordBoxW / 2.0);

        BitWidthLine wordDownVA = bitWidthWire(viewModel.getWordBits());
        wordDownVA.arrowTipVisibleProperty().set(false);
        wordDownVA.startXProperty().set(wordVACenterX);
        wordDownVA.startYProperty().set(boxY + ADDRESS_BOX_HEIGHT);
        wordDownVA.endXProperty().set(wordVACenterX);
        wordDownVA.endYProperty().set(passY);

        BitWidthLine wordAcross = bitWidthWire(viewModel.getWordBits());
        wordAcross.arrowTipVisibleProperty().set(false);
        wordAcross.bitWidthIndicatorVisibleProperty().set(false);
        wordAcross.startXProperty().set(wordVACenterX);
        wordAcross.startYProperty().set(passY);
        wordAcross.endXProperty().bind(wordPACenterX);
        wordAcross.endYProperty().set(passY);
        // Flipped so the value label lands above the wire, matching blockAcross's own treatment.
        wordAcross.labelOnLeftProperty().set(true);
        Label wordFlowLabel = wordAcross.getValueLabel();
        wordFlowLabel.textProperty().bind(hideWhenInactive(viewModel.wordHexProperty(), viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH)));
        wordAcross.valueLabelVisibleProperty().set(true);

        // Runs bottom-to-top (start at passY, end at the box) so its arrow lands pointing into the
        // PA Word box, not away from it. Flipped labelOnLeft compensates so the tag still lands on
        // the right, the same side wordDownVA's tag reads on.
        BitWidthLine wordUpPA = bitWidthWire(viewModel.getWordBits());
        wordUpPA.labelOnLeftProperty().set(true);
        wordUpPA.startXProperty().bind(wordPACenterX);
        wordUpPA.startYProperty().set(passY);
        wordUpPA.endXProperty().bind(wordPACenterX);
        wordUpPA.endYProperty().set(boxY + ADDRESS_BOX_HEIGHT);

        // ---- Page -> page-table offset (page bits concatenated with a fixed shift-bit zero fill) ----
        // There is no standalone box for this value anymore; it is shown as a label riding the wire
        // that carries it from the merge brace down into the adder.
        double offsetBoxY = UiScale.px(220);
        double offsetBoxX = pageBoxX;

        // The page line and the zero-fill line merge into a single offset value via a curly brace;
        // its ears sit a fixed gap above the merge point so there's room for the brace curve + stub.
        // The feeding lines and the merge stub stop short of the brace, leaving a visible gap.
        double braceTopY = offsetBoxY - UiScale.px(10);
        double braceDepth = UiScale.px(20);
        double braceGap = UiScale.px(8);
        // The tip's true X depends on both ears (page-box center and the zero-fill line's X), so the
        // stub below the brace must target that same point exactly, or it reads as a slight bend.
        // The multiplier widens the brace's span past the page box's own width -- there's plenty of
        // free canvas to its right before the Page Table Pointer box.
        double mergeCenterX = ((pageBoxX + pageBoxW / 2.0) + (offsetBoxX + offsetBoxW * 1.0)) / 2.0;

        CurlyBrace offsetBrace = new CurlyBrace();
        offsetBrace.depthProperty().set(braceDepth);

        // Page field -> table offset: a plain wire (no arrow) that carries its own mid-span bit-width tag.
        BitWidthLine pageDownLine = bitWidthWire(viewModel.getPageBits());
        pageDownLine.arrowTipVisibleProperty().set(false);

        pageDownLine.startXProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            return pageBox.getLayoutX() + (pageBox.getWidth() / 2.0);
        }, pageBox.layoutXProperty(), pageBox.widthProperty())); // Re-calculates if box moves or stretches

            // Bind Start Y to the exact bottom edge of the pageBox
        pageDownLine.startYProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            return pageBox.getLayoutY() + pageBox.getHeight();
        }, pageBox.layoutYProperty(), pageBox.heightProperty()));

        // The brace's left ear tracks the page line's own start X, so the line stays perfectly vertical
        offsetBrace.leftXProperty().bind(pageDownLine.startXProperty());
        offsetBrace.leftYProperty().set(braceTopY);
        pageDownLine.endXProperty().bind(offsetBrace.leftXProperty());
        pageDownLine.endYProperty().bind(offsetBrace.leftYProperty().subtract(braceGap));

        double shiftLineX = offsetBoxX + offsetBoxW * 1.0;
        BitWidthLine shiftDownLine = bitWidthWire(viewModel.getShiftBits());
        shiftDownLine.arrowTipVisibleProperty().set(false);
        shiftDownLine.startXProperty().set(shiftLineX);
        shiftDownLine.startYProperty().set(boxY + ADDRESS_BOX_HEIGHT + UiScale.px(50));

        Label zeroFillLabel = new Label("0");
        zeroFillLabel.getStyleClass().add("wire-value-label");
        zeroFillLabel.setLayoutX(shiftLineX);
        // Sits a fixed gap above the wire's own start point, but pinned there via the label's own
        // live height -- not a hand-tuned Y offset -- so it stays clear of the wire regardless of
        // how big .wire-value-label's font is; no more re-tuning this by hand each time that changes.
        double zeroFillGap = UiScale.px(6);
        zeroFillLabel.layoutYProperty().bind(
                shiftDownLine.startYProperty().subtract(zeroFillLabel.heightProperty()).subtract(zeroFillGap));

        zeroFillLabel.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double labelWidth = newWidth.doubleValue();
            zeroFillLabel.setLayoutX(shiftLineX - (labelWidth / 2.0));
        });

        // The brace's right ear tracks the zero-fill line's own X, so that line stays vertical too
        offsetBrace.rightXProperty().set(shiftLineX);
        offsetBrace.rightYProperty().set(braceTopY);
        shiftDownLine.endXProperty().bind(offsetBrace.rightXProperty());
        shiftDownLine.endYProperty().bind(offsetBrace.rightYProperty().subtract(braceGap));

        // ---- Page table pointer (base address of the current user's page table) ----
        double pointerBoxX = offsetBoxX + offsetBoxW + UiScale.px(90);
        double pointerBoxY = offsetBoxY;
        // Title sits outside/above the box, like every other field box in this schematic -- not
        // stacked inside it -- so the box itself is sized to the value alone, not the (wider) title.
        Region pointerBox = FieldBoxes.valueCell(
                "field-box-cell-solo", viewModel.pageTablePointerHexProperty(),
                ValueConverter.hexDigitsFor(viewModel.getPhysicalAddressBits()));
        pointerBox.setLayoutX(pointerBoxX);
        pointerBox.setLayoutY(pointerBoxY);
        double pointerBoxW = pointerBox.getPrefWidth();
        Label pointerTitle = FieldBoxes.fieldTitle("Page Table Pointer", pointerBox, pointerBoxY - TITLE_GAP);

        // ---- Adder: page table pointer + table offset = descriptor's physical address ----
        double adderCenterX = mergeCenterX;
        double adderCenterY = offsetBoxY + BOX_HEIGHT + UiScale.px(70);
        double adderRadius = UiScale.px(18);

        // A StackPane centers its children by bounds, so the "+" sits exactly in the middle of the
        // circle regardless of font metrics (unlike hardcoded pixel offsets).
        StackPane adderNode = new StackPane();
        adderNode.setLayoutX(adderCenterX - adderRadius);
        adderNode.setLayoutY(adderCenterY - adderRadius);
        // Pref alone isn't enough: a Region's effective size is its pref clamped up to its (auto-
        // computed, from children) min, so the "+" label's own min width/height at this font size can
        // silently grow this pane past 2r -- with the extra room tacked onto the bottom-right, since
        // layoutX/Y anchors the top-left corner. That drags the circle's real centre away from
        // (adderCenterX, adderCenterY), throwing off every wire routed to that point. Pinning min and
        // max to the same 2r forces the box -- and so the circle's centre -- to stay exactly put.
        adderNode.setPrefSize(adderRadius * 2, adderRadius * 2);
        adderNode.setMinSize(adderRadius * 2, adderRadius * 2);
        adderNode.setMaxSize(adderRadius * 2, adderRadius * 2);
        // Circle's default centerX/centerY (0,0) puts its layout bounds at [-r,-r, 2r,2r] -- letting
        // the StackPane's shape-centering math re-derive the offset back to the middle. Pinning the
        // centre to (r,r) instead makes the circle's own bounds exactly [0,0, 2r,2r], flush with the
        // pane's, so its rendered centre is deterministically adderNode's (layoutX+r, layoutY+r) --
        // i.e. exactly (adderCenterX, adderCenterY) -- with no auto-centering rounding involved.
        Circle adderCircle = new Circle(adderRadius, adderRadius, adderRadius);
        adderCircle.getStyleClass().add("mmu-adder-circle");
        // A "+" glyph, like the back button's chevron (see BackButton's own doc comment), reads as
        // visibly off-centre no matter how the Label is aligned -- font line-height reserves
        // asymmetric space above/below the glyph itself. Two lines built symmetrically around their
        // own origin have no such bias, so StackPane's bounds-based centering lands them exactly on
        // the circle's centre.
        double plusHalfSpan = adderRadius * 0.55;
        Line plusHorizontal = new Line(-plusHalfSpan, 0, plusHalfSpan, 0);
        Line plusVertical = new Line(0, -plusHalfSpan, 0, plusHalfSpan);
        plusHorizontal.getStyleClass().add("mmu-adder-label");
        plusVertical.getStyleClass().add("mmu-adder-label");
        Group adderPlus = new Group(plusHorizontal, plusVertical);
        adderNode.getChildren().addAll(adderCircle, adderPlus);

        // One wire, brace tip to adder centre -- not split partway down -- since nothing here
        // actually bends; splitting it only pushed the "Nb" tag up onto a short stub while the value
        // sat on a separate, longer segment far below it. Its own midpoint (now the true midpoint of
        // the whole run) carries the tag; the value label rides the opposite side. adderNode (opaque
        // fill) is added after this in z-order, so the portion inside the circle is simply covered,
        // guaranteeing a flush join regardless of any sub-pixel rounding at the circle's boundary.
        BitWidthLine offsetToAdder = bitWidthWire(viewModel.getOffsetBits());
        offsetToAdder.arrowTipVisibleProperty().set(false);
        // Flipped from bitWidthWire()'s own default so the tag sits on the left and the value on the
        // right, matching where the value already read before this wire carried a tag of its own.
        offsetToAdder.labelOnLeftProperty().set(true);
        offsetToAdder.startXProperty().bind(offsetBrace.tipXProperty());
        offsetToAdder.startYProperty().bind(offsetBrace.tipYProperty().add(braceGap));
        offsetToAdder.endXProperty().set(adderCenterX);
        offsetToAdder.endYProperty().set(adderCenterY);
        Label offsetValueLabel = offsetToAdder.getValueLabel();
        offsetValueLabel.textProperty().bind(hideWhenInactive(viewModel.descriptorOffsetHexProperty(), viewModel.lineActiveProperty(MmuLine.OFFSET_TO_ADDER)));
        offsetToAdder.valueLabelVisibleProperty().set(true);

        double pointerCenterX = pointerBoxX + pointerBoxW / 2;
        BitWidthLine pointerDown = bitWidthWire(viewModel.getPhysicalAddressBits());
        pointerDown.arrowTipVisibleProperty().set(false);
        pointerDown.startXProperty().set(pointerCenterX);
        pointerDown.startYProperty().set(pointerBoxY + BOX_HEIGHT);
        pointerDown.endXProperty().set(pointerCenterX);
        pointerDown.endYProperty().set(adderCenterY);

        BitWidthLine pointerAcross = bitWidthWire(viewModel.getPhysicalAddressBits());
        pointerAcross.arrowTipVisibleProperty().set(false);
        pointerAcross.bitWidthIndicatorVisibleProperty().set(false);
        pointerAcross.startXProperty().set(pointerCenterX);
        pointerAcross.startYProperty().set(adderCenterY);
        pointerAcross.endXProperty().set(adderCenterX);
        pointerAcross.endYProperty().set(adderCenterY);

        // ---- Adder output descends toward the page table (the highlighted row is the dynamic target) ----
        // Shifted right of the adder's own column (rather than tucked close behind it) so the
        // addressDown wire's value label -- which rides the gap between the wire and the table --
        // has room to grow for a wide physical address without its text reaching the table's border.
        double tableX = pointerBoxX + UiScale.px(30);
        double tableY = adderCenterY + UiScale.px(90);

        // (tableX, tableY - 24) is only a sane pre-layout placeholder; updateDynamicConnectors
        // repositions it live to sit just above the descriptor-size brace's own left end.
        Label tableHeaderLabel = FieldBoxes.sectionLabel("Page Table", tableX, tableY - UiScale.px(24));
        // "Page Table" and the TLB tab's "TLB" title share .schematic-heading (see PagedTLBTabView),
        // distinct from the plain .schematic-heading the VA/PA/Process headers keep.
        tableHeaderLabel.getStyleClass().remove("schematic-heading");
        tableHeaderLabel.getStyleClass().add("schematic-heading");

        // Curly brace spanning just the descriptor's own columns (V/D/Block/Disk -- Index is a
        // display aid, not part of the stored entry), so it's visually obvious why the page number
        // gets shifted left by exactly this many bits before becoming the table offset: each
        // descriptor occupies 2^shiftBits words, so multiplying the index by that size (a left
        // shift) is what yields the word offset. Endpoints are bound live (see updateDynamicConnectors)
        // to the V column's left edge and the table's own right edge, not guessed pixel offsets;
        // negative depth bulges the brace upward, away from the table, tip pointing at the label.
        CurlyBrace descriptorSizeBrace = new CurlyBrace();
        descriptorSizeBrace.depthProperty().set(-DESCRIPTOR_BRACE_DEPTH);
        Label descriptorSizeLabel = new Label("2" + toSuperscript(viewModel.getShiftBits()) + " words");
        descriptorSizeLabel.getStyleClass().addAll("bit-width-label", "mmu-descriptor-size-label");

        PageTableView pageTableView = new PageTableView(viewModel);
        pageTableView.setLayoutX(tableX);
        pageTableView.setLayoutY(tableY);

        // The narrowest the schematic can be drawn without the block wire's rising leg (which runs
        // up beside PA Block's centre) cutting through the table: table's right edge, a run-out for
        // the wire's own tag, then PA Block's half-width and PA Word flush to the margin. Derived
        // from the table's real laid-out width -- which grows with the configured Block/Disk hex
        // digits -- so a wider configuration widens the design size instead of overlapping. Only the
        // table's own width is read, never the canvas's, so this can't chase its own tail. The
        // table's preferred width (fixed cell widths, known at construction) backs up the live
        // width, so the minimum is already right before this tab has ever been laid out.
        canvas.designWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(MIN_CANVAS_WIDTH,
                        tableX + Math.max(pageTableView.prefWidth(-1), pageTableView.getWidth())
                                + BLOCK_WIRE_CLEARANCE + blockBoxW / 2 + wordBoxW + MARGIN),
                pageTableView.widthProperty()));

        // Clicking the schematic's small (7-row) preview opens a separate, resizable window that
        // browses the full table (up to 2^pageBits entries) for any user, not just the current one.
        pageTableView.getStyleClass().add("data-table-clickable");
        pageTableView.setCursor(javafx.scene.Cursor.HAND);
        PageTableInspectorWindow pageTableInspector =
                new PageTableInspectorWindow(viewModel.getContext(), viewModel.currentStepNumberProperty());
        pageTableView.setOnMouseClicked(e ->
                pageTableInspector.toggle(pageTableView.getScene() != null ? pageTableView.getScene().getWindow() : null));

        canvas.getChildren().addAll(
                wordDownVA, wordAcross, wordUpPA, pageDownLine, shiftDownLine, offsetBrace,
                offsetToAdder, pointerDown, pointerAcross,
                zeroFillLabel,
                vaHeader, paHeader, pageTitle, wordTitleVA, blockTitle, wordTitlePA, pointerTitle,
                pageBox, wordBoxVA, blockBoxPA, wordBoxPA, pointerBox,
                adderNode, tableHeaderLabel, descriptorSizeBrace, descriptorSizeLabel, pageTableView);

        // ---- Dynamic connectors: the highlighted row moves within the window as pages change, so
        // addressDown's end (and addressAcross entirely) are re-routed every recompute -- see
        // updateDynamicConnectors(). One wire from the adder's own edge, not split at a fixed
        // "stub" length -- its own midpoint (the true midpoint of the whole adder-to-row run) always
        // carries the physical-address tag, with the resolved value opposite it. ----
        BitWidthLine addressDown = bitWidthWire(viewModel.getPhysicalAddressBits());
        addressDown.arrowTipVisibleProperty().set(false);
        // Flipped so the tag sits on the left and the value on the right, matching where the value
        // already read before this wire carried a tag of its own.
        addressDown.labelOnLeftProperty().set(true);
        addressDown.startXProperty().set(adderCenterX);
        addressDown.startYProperty().set(adderCenterY + adderRadius);
        Label addressLabel = addressDown.getValueLabel();
        addressLabel.textProperty().bind(hideWhenInactive(viewModel.descriptorAddressHexProperty(), viewModel.lineActiveProperty(MmuLine.ADDER_TO_TABLE)));
        addressDown.valueLabelVisibleProperty().set(true);

        BitWidthLine addressAcross = bitWidthWire(viewModel.getPhysicalAddressBits());
        addressAcross.bitWidthIndicatorVisibleProperty().set(false);

        // ---- Below the table: V/D/Disk fields of the highlighted row drop straight down, and
        // Block's own drop continues on into the PA Block box. Split into two vertical segments so
        // the first one's own midpoint lands exactly on the shared V/D/Disk indicator row -- its bit
        // tag rides there instead of a hand-placed tick/label pair -- then a third (horizontal) leg
        // carries the resolved value, and a fourth runs the rest of the way up into the box. ----
        BitWidthLine blockDownNear = bitWidthWire(viewModel.getFrameBits());
        blockDownNear.arrowTipVisibleProperty().set(false);
        BitWidthLine blockDownFar = bitWidthWire(viewModel.getFrameBits());
        blockDownFar.arrowTipVisibleProperty().set(false);
        blockDownFar.bitWidthIndicatorVisibleProperty().set(false);
        BitWidthLine blockAcross = bitWidthWire(viewModel.getFrameBits());
        blockAcross.arrowTipVisibleProperty().set(false);
        blockAcross.bitWidthIndicatorVisibleProperty().set(false);
        // Flipped so the value label lands above the wire, matching before.
        blockAcross.labelOnLeftProperty().set(true);
        Label blockFlowLabel = blockAcross.getValueLabel();
        blockFlowLabel.textProperty().bind(hideWhenInactive(viewModel.blockHexProperty(), viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK)));
        blockAcross.valueLabelVisibleProperty().set(true);
        // Split the same way as blockDownNear/blockDownFar below the table: blockUpNear's own span is
        // sized (in updateDynamicConnectors) so its midpoint lands at the same height as wordUpPA's
        // own tag, and -- already running bottom-to-top, so its arrow already lands on the box end --
        // it keeps its arrow enabled, pointing into the PA Block box.
        BitWidthLine blockUpFar = bitWidthWire(viewModel.getFrameBits());
        blockUpFar.arrowTipVisibleProperty().set(false);
        blockUpFar.bitWidthIndicatorVisibleProperty().set(false);
        BitWidthLine blockUpNear = bitWidthWire(viewModel.getFrameBits());
        // Flipped from the default so the tag lands on the right, matching wordUpPA (also running
        // bottom-to-top, also flipped for the same reason).
        blockUpNear.labelOnLeftProperty().set(true);

        ColumnDrop vDrop = columnDrop(1, viewModel.currentVBitProperty(), viewModel.pageTableAccessedProperty(), pageTableView.validColumnAnchorProperty().get());
        ColumnDrop dDrop = columnDrop(1, viewModel.currentDBitProperty(), viewModel.pageTableAccessedProperty(), pageTableView.dirtyColumnAnchorProperty().get());
        ColumnDrop diskDrop = columnDrop(viewModel.getDiskBits(), viewModel.currentDiskHexProperty(), viewModel.pageTableAccessedProperty(), pageTableView.diskColumnAnchorProperty().get());

        canvas.getChildren().addAll(addressDown, addressAcross, blockDownNear, blockDownFar, blockAcross, blockUpFar, blockUpNear);
        // V/D/Disk column drops disabled for now (not added to the scene at all) -- only the block
        // wire stays visible.

        Runnable updateDynamicConnectors = () -> updateDynamicConnectors(
                pageTableView, adderCenterX, adderCenterY + adderRadius, addressDown, addressAcross,
                blockBoxPA, blockDownNear, blockDownFar, blockAcross, blockUpFar, blockUpNear, vDrop, dDrop, diskDrop,
                descriptorSizeBrace, descriptorSizeLabel, tableHeaderLabel);

        // Once per pulse, right after layout (see PostLayoutTask) -- not one runLater per change, which
        // re-routed every wire several times per resize pass and always a frame behind the boxes.
        PostLayoutTask reroute = new PostLayoutTask(this, updateDynamicConnectors);

        pageTableView.currentEntryAnchorProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        // The table's own height can still settle a pass or two after the initial layout (e.g. once
        // header/row label fonts finish measuring), which would otherwise leave the drop wires below
        // pinned to a stale, taller tableBottom -- so re-route whenever it actually changes too.
        pageTableView.heightProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        // currentEntryAnchor is a recycled row node (PageTableView's fixed 7-row pool), so it often
        // comes back to the exact same Region reference across different steps/pages (e.g. the
        // addressed page landing in the pool's middle slot every time it isn't clamped near either
        // end of the table) -- an ObjectProperty.set() to an unchanged reference never fires its
        // listener, so the anchor-based re-route above can silently go stale for that slot. Stepping
        // always changes this property, so listening to it too guarantees a fresh recompute every step.
        viewModel.currentStepNumberProperty().addListener((obs, oldVal, newVal) -> reroute.request());
        reroute.request();

        // ---- Wires/labels only light up once the step that uses them has actually executed ----
        bindActive(pageDownLine, viewModel.lineActiveProperty(MmuLine.PAGE_TO_OFFSET));
        bindActive(offsetBrace, viewModel.lineActiveProperty(MmuLine.PAGE_TO_OFFSET));

        bindActive(shiftDownLine, viewModel.lineActiveProperty(MmuLine.ZERO_FILL_TO_OFFSET));
        bindActive(zeroFillLabel, viewModel.lineActiveProperty(MmuLine.ZERO_FILL_TO_OFFSET));

        // offsetToAdder now runs brace-tip to adder (see its own construction comment), so
        // OFFSET_TO_ADDER alone is enough -- FormPageTableAddressStep sets PAGE_TO_OFFSET and
        // OFFSET_TO_ADDER together, in the same union, so the two were always synchronized anyway.
        bindActive(offsetToAdder, viewModel.lineActiveProperty(MmuLine.OFFSET_TO_ADDER));
        bindActive(pointerDown, viewModel.lineActiveProperty(MmuLine.POINTER_TO_ADDER));
        bindActive(pointerAcross, viewModel.lineActiveProperty(MmuLine.POINTER_TO_ADDER));

        bindActive(addressDown, viewModel.lineActiveProperty(MmuLine.ADDER_TO_TABLE));
        bindActive(addressAcross, viewModel.lineActiveProperty(MmuLine.ADDER_TO_TABLE));
        bindActive(adderCircle, viewModel.lineActiveProperty(MmuLine.ADDER_TO_TABLE));

        bindActive(wordDownVA, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));
        bindActive(wordAcross, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));
        bindActive(wordUpPA, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));

        bindActive(blockDownNear, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockDownFar, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockAcross, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockUpFar, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockUpNear, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));

        for (ColumnDrop drop : List.of(vDrop, dDrop, diskDrop)) {
            bindActive(drop.line(), viewModel.pageTableAccessedProperty());
            bindActive(drop.valueLabel(), viewModel.pageTableAccessedProperty());
        }

        // Grows the canvas to fill the viewport (never below its design size), which is what lets
        // Physical Address's live binding actually reach the tab's true right edge instead of a
        // fixed pixel canvas size.
        mountCanvas();
        getChildren().add(buildSideNoteOverlay(viewModel));
    }

    // Some steps mutate a page-table descriptor that belongs to an evicted/victim page, not the one
    // currently addressed -- the windowed table above has no row to show that on (see CLAUDE.md's
    // MMU tab notes). Rather than thread this through the schematic's own wire geometry, it's an
    // overlay pinned to this StackPane directly (a sibling of the ScrollPane, not inside the
    // canvas), so it can never collide with the schematic's own layout math and stays visible
    // regardless of scroll position. The entry itself is rendered as an actual little page-table
    // (reusing .data-table/.data-table-header/.data-table-row/.data-table-cell verbatim) so
    // it reads as "here is that row," not another line of step-description prose.
    private Node buildSideNoteOverlay(PagedMMUTabViewModel viewModel) {
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

        // Widths mirror PageTableView.cell()'s own approach (a fixed width per column, so text
        // centers within a real box) -- without them every cell collapses to its own bare text
        // width with no gap between columns at all.
        double indexWidth = WidthCalculator.plainColumnWidth("Index", 4);
        double bitWidth = WidthCalculator.plainColumnWidth("V", 1);
        double blockWidth = WidthCalculator.columnWidth("Block", viewModel.blockHexDigitsProperty().get());
        double diskWidth = WidthCalculator.columnWidth("Disk", viewModel.diskHexDigitsProperty().get());

        Label indexCell = tableCell("", indexWidth);
        Label vCell = tableCell("", bitWidth);
        Label dCell = tableCell("", bitWidth);
        Label blockCell = tableCell("", blockWidth);
        Label diskCell = tableCell("", diskWidth);
        HBox headerRow = new HBox(
                tableCell("Index", indexWidth), tableCell("V", bitWidth), tableCell("D", bitWidth),
                tableCell("Block", blockWidth), tableCell("Disk", diskWidth));
        headerRow.getStyleClass().add("data-table-header");
        HBox dataRow = new HBox(indexCell, vCell, dCell, blockCell, diskCell);
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
            MmuSideNote note = viewModel.sideNoteProperty().get();
            boolean visible = note != null;
            card.setVisible(visible);
            card.setManaged(visible);
            if (!visible)
                return;

            headline.setText(note.headline());
            userLabel.setText("user " + note.user());
            indexCell.setText(Long.toString(note.page()));
            vCell.setText(note.valid() ? "1" : "0");
            dCell.setText(note.dirty() ? "1" : "0");
            blockCell.setText(ValueConverter.toHex(note.block(), viewModel.blockHexDigitsProperty().get()));
            diskCell.setText(ValueConverter.toHex(note.disk(), viewModel.diskHexDigitsProperty().get()));
        };
        viewModel.sideNoteProperty().addListener((o, ov, nv) -> rebuild.run());
        rebuild.run();

        return card;
    }

    private Label tableCell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("data-table-cell");
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private void updateDynamicConnectors(
            PageTableView pageTableView, double sourceX, double sourceY,
            BitWidthLine addressDown, BitWidthLine addressAcross,
            Region blockBoxPA, BitWidthLine blockDownNear, BitWidthLine blockDownFar,
            BitWidthLine blockAcross, BitWidthLine blockUpFar, BitWidthLine blockUpNear,
            ColumnDrop vDrop, ColumnDrop dDrop, ColumnDrop diskDrop,
            CurlyBrace descriptorSizeBrace, Label descriptorSizeLabel, Label tableHeaderLabel) {

        Region rowAnchor = pageTableView.currentEntryAnchorProperty().get();

        // Establish a stable horizontal entry coordinate straight from the table's left border
        double stableTableLeftEdgeX = pageTableView.getLayoutX();
        double targetWireCenterY = 0;

        // The table's own full bounds (header through the last row) -- needed by the idle fallback
        // wire-centering below, and by the block-passthrough / V-D-Disk drop sections further down.
        // layoutBounds, not boundsInLocal: the unaccessed rows carry a BoxBlur effect (see
        // PageTableView.updateFog), and boundsInLocal would inflate by the blur radius, pushing
        // tableBottom (and so every drop wire anchored to it) below the table's actual border.
        Bounds tableBounds = pageTableView.getScene() != null
                ? canvas.sceneToLocal(pageTableView.localToScene(pageTableView.getLayoutBounds()))
                : null;

        // Descriptor-size brace: left end on the V column's own left edge, right end on the table's
        // own right edge, both riding the table's top -- so it always spans exactly the descriptor's
        // stored columns regardless of how wide Block/Disk end up being for this configuration.
        Region validColumnAnchor = pageTableView.validColumnAnchorProperty().get();
        if (validColumnAnchor != null && validColumnAnchor.getScene() != null && tableBounds != null) {
            Bounds vBounds = canvas.sceneToLocal(validColumnAnchor.localToScene(validColumnAnchor.getBoundsInLocal()));
            double braceY = tableBounds.getMinY() - DESCRIPTOR_BRACE_GAP;
            descriptorSizeBrace.setVisible(true);
            descriptorSizeBrace.leftXProperty().set(vBounds.getMinX());
            descriptorSizeBrace.leftYProperty().set(braceY);
            descriptorSizeBrace.rightXProperty().set(tableBounds.getMaxX());
            descriptorSizeBrace.rightYProperty().set(braceY);

            descriptorSizeLabel.autosize();
            descriptorSizeLabel.setLayoutX(descriptorSizeBrace.tipXProperty().get() - descriptorSizeLabel.getWidth() / 2.0);
            descriptorSizeLabel.setLayoutY(descriptorSizeBrace.tipYProperty().get() - descriptorSizeLabel.getHeight() - UiScale.px(4));

            // "Page Table" sits at the table's own left edge, level with the brace above it.
            tableHeaderLabel.autosize();
            tableHeaderLabel.setLayoutX(tableBounds.getMinX());
            tableHeaderLabel.setLayoutY(braceY - tableHeaderLabel.getHeight() - TABLE_TITLE_GAP);
        } else {
            descriptorSizeBrace.setVisible(false);
        }

        // =========================================================================
        // SOLID HARDWARE SIGNAL ROUTER SWITCH
        // =========================================================================
        // PageTableView.currentEntryAnchor already resolves to the right row on its own: the
        // addressed row once it's been looked up, or the pool's middle row as a fallback while idle
        // (see updateRowData()) -- so it, not the table's own overall bounds, is always the correct
        // target. The two used to disagree: the idle fallback here averaged the *whole table's*
        // bounds (header included), which sits a bit higher than the middle row's own centre once
        // the header's height is folded in, so the wire visibly jumped when a lookup resolved.
        if (rowAnchor != null && rowAnchor.getScene() != null) {
            // Use the absolute, changing vertical midpoint of the anchored row strip [^*]
            Bounds rowBounds = canvas.sceneToLocal(rowAnchor.localToScene(rowAnchor.getBoundsInLocal()));
            targetWireCenterY = rowBounds.getCenterY();
        } else if (tableBounds != null) {
            // Anchor not yet attached to a live scene (first layout pass) -- fall back to the whole
            // table's own centre just so the wire has somewhere sane to point meanwhile.
            targetWireCenterY = tableBounds.getMinY() + (tableBounds.getHeight() / 2.0);
        }

        // Apply vector coordinates updates cleanly if layout geometry is valid
        if (targetWireCenterY > 0) {
            addressDown.startXProperty().set(sourceX);
            addressDown.startYProperty().set(sourceY);
            addressDown.endXProperty().set(sourceX);
            addressDown.endYProperty().set(targetWireCenterY);

            addressAcross.startXProperty().set(sourceX);
            addressAcross.startYProperty().set(targetWireCenterY);
            addressAcross.endXProperty().set(stableTableLeftEdgeX);
            addressAcross.endYProperty().set(targetWireCenterY);
        }

        // =========================================================================
        // BLOCK PASSTHROUGH WIRE CONTROLLER (Keep your existing working block line logic)
        // =========================================================================
        Region blockColumnAnchor = pageTableView.blockColumnAnchorProperty().get();
        boolean routeBlock = blockColumnAnchor != null && blockColumnAnchor.getScene() != null
                && blockBoxPA.getScene() != null && tableBounds != null;
        if (routeBlock) {
            Bounds columnBounds = canvas.sceneToLocal(blockColumnAnchor.localToScene(blockColumnAnchor.getBoundsInLocal()));
            Bounds blockBoxBounds = canvas.sceneToLocal(blockBoxPA.localToScene(blockBoxPA.getBoundsInLocal()));

            double columnX = columnBounds.getCenterX();
            double tableBottom = tableBounds.getMaxY();
            double dropY = tableBottom + BLOCK_DROP_LENGTH;
            double boxCenterX = blockBoxBounds.getCenterX();

            // The wire's lowest point is the schematic's bottom edge, so the design height is exactly
            // that plus a margin -- CANVAS_HEIGHT is only the estimate used until this first runs.
            // Measured from the table's own geometry (not the viewport), so it can't depend on the
            // canvas's current size.
            canvas.designHeightProperty().set(dropY + MARGIN);

            // Block's bit-width tag rides blockDownNear at its own midpoint -- pinned to the same row
            // as the V/D/Disk drops below by choosing that segment's far end so its midpoint lands
            // exactly on indicatorY, as far past it as tableBottom sits before it.
            double indicatorY = tableBottom + DROP_INDICATOR_Y;
            double blockDownNearEndY = 2 * indicatorY - tableBottom;

            blockDownNear.startXProperty().set(columnX);
            blockDownNear.startYProperty().set(tableBottom);
            blockDownNear.endXProperty().set(columnX);
            blockDownNear.endYProperty().set(blockDownNearEndY);

            blockDownFar.startXProperty().set(columnX);
            blockDownFar.startYProperty().set(blockDownNearEndY);
            blockDownFar.endXProperty().set(columnX);
            blockDownFar.endYProperty().set(dropY);

            blockAcross.startXProperty().set(columnX);
            blockAcross.startYProperty().set(dropY);
            blockAcross.endXProperty().set(boxCenterX);
            blockAcross.endYProperty().set(dropY);

            // blockUpNear's own span is 40 (matching wordUpPA's own box-to-passY span exactly), so
            // its own midpoint -- where its tag lands -- sits the same 20px below the box edge that
            // wordUpPA's tag does, reading as "the same height" even though the two boxes' bottoms
            // aren't necessarily at the same canvas Y.
            double blockUpNearStartY = blockBoxBounds.getMaxY() + UiScale.px(40);

            blockUpFar.startXProperty().set(boxCenterX);
            blockUpFar.startYProperty().set(dropY);
            blockUpFar.endXProperty().set(boxCenterX);
            blockUpFar.endYProperty().set(blockUpNearStartY);

            blockUpNear.startXProperty().set(boxCenterX);
            blockUpNear.startYProperty().set(blockUpNearStartY);
            blockUpNear.endXProperty().set(boxCenterX);
            blockUpNear.endYProperty().set(blockBoxBounds.getMaxY());
        }

        // Pull the wires to the front to prevent Z-order overlapping visibility clips: the address
        // pair first, the block wires above them once those are routed. One call for both groups --
        // two separate ones would each find the other's wires on top of theirs and reshuffle every run.
        if (routeBlock)
            canvas.bringToFront(addressDown, addressAcross,
                    blockDownNear, blockDownFar, blockAcross, blockUpFar, blockUpNear);
        else
            canvas.bringToFront(addressDown, addressAcross);

        // =========================================================================
        // V / D / DISK DROP LINES: straight down from the highlighted row's columns
        // =========================================================================
        if (tableBounds != null) {
            double tableBottom = tableBounds.getMaxY();

            positionColumnDrop(pageTableView.validColumnAnchorProperty().get(), tableBottom, vDrop);
            positionColumnDrop(pageTableView.dirtyColumnAnchorProperty().get(), tableBottom, dDrop);
            positionColumnDrop(pageTableView.diskColumnAnchorProperty().get(), tableBottom, diskDrop);
        }
    }

    // Places a drop wire straight down from a table column's header; the wire renders its own
    // bit-width tick and the arrowhead where it meets the value readout below.
    private void positionColumnDrop(Region columnAnchor, double tableBottom, ColumnDrop drop) {
        if (columnAnchor == null || columnAnchor.getScene() == null)
            return;

        Bounds columnBounds = canvas.sceneToLocal(columnAnchor.localToScene(columnAnchor.getBoundsInLocal()));
        double columnX = columnBounds.getCenterX();
        double dropBottom = tableBottom + DROP_LENGTH;

        drop.line().startXProperty().set(columnX);
        drop.line().startYProperty().set(tableBottom);
        drop.line().endXProperty().set(columnX);
        drop.line().endYProperty().set(dropBottom);

        drop.valueLabel().setLayoutY(dropBottom + UiScale.px(6));

        for (Node node : drop.nodes())
            node.toFront();
    }

    // A column's drop from the table down to its resolved value: a self-labelling bit-width wire
    // (line + diagonal tick + "Nb" tag + arrowhead) plus the hex value readout below it.
    private record ColumnDrop(BitWidthLine line, Label valueLabel) {
        Node[] nodes() {
            return new Node[] { line, valueLabel };
        }
    }

    private ColumnDrop columnDrop(int bits, StringProperty valueProperty, BooleanProperty activeProperty, Region columnAnchor) {
        BitWidthLine line = bitWidthWire(bits);
        // BitWidthLine's own default tick/gap push a label's near edge 13px past its wire (reach 7 +
        // gap 6) -- plenty of room on the schematic's wider wires, but these V/D/Block/Disk columns
        // sit only ~30px apart, so that default reach put each label within a few px of the *next*
        // column's own wire. Tightened so the label clears its own tick with real margin to spare
        // before reaching the neighbour.
        line.tickLengthProperty().set(UiScale.px(8));
        line.labelGapProperty().set(UiScale.px(2));
        line.arrowTipVisibleProperty().set(false);

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(hideWhenInactive(valueProperty, activeProperty));
        valueLabel.getStyleClass().add("wire-value-label");

        valueLabel.layoutXProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(() -> {
            if (columnAnchor != null && columnAnchor.getScene() != null) {
                // Fetch the live, absolute horizontal center of the table column column grid [^*]
                Bounds b = canvas.sceneToLocal(columnAnchor.localToScene(columnAnchor.getBoundsInLocal()));
                double currentColumnCenterX = b.getCenterX();
                
                // Perfect mathematical center anchoring [^*]
                return currentColumnCenterX - (valueLabel.getWidth() / 2.0);
            }
            return 0.0;
        }, 
        valueLabel.widthProperty()            // Dependency A: Triggers when hex value string length text shifts
         // Dependency B: Triggers when the parent grid column stretches or slides
        ));

        return new ColumnDrop(line, valueLabel);
    }

    // Unicode superscript digits for "2^N" read as an actual exponent instead of a caret. 1/2/3 live
    // in the Latin-1 Supplement block (superscript two/three predate Unicode's own block and were
    // never duplicated there); 0 and 4-9 are in the Superscripts and Subscripts block -- there is no
    // single contiguous run of code points to offset into, hence the explicit lookup table.
    private static final String[] SUPERSCRIPT_DIGITS = {
            "⁰", "¹", "²", "³", "⁴", "⁵", "⁶", "⁷", "⁸", "⁹"
    };

    private static String toSuperscript(int n) {
        StringBuilder result = new StringBuilder();
        for (char digit : Integer.toString(n).toCharArray()) {
            result.append(SUPERSCRIPT_DIGITS[digit - '0']);
        }
        return result.toString();
    }

    // A straight signal wire that carries its own mid-span bit-width tag (diagonal tick + "Nb" label),
    // replacing the old hand-placed line + tick + bitLabel trios. The tag rides on the wire's right side.
    private BitWidthLine bitWidthWire(int bits) {
        BitWidthLine wire = new BitWidthLine();
        wire.bitsProperty().set(bits);
        wire.labelOnLeftProperty().set(false);
        return wire;
    }

    // Wire-borne value readouts only make sense once their step has actually run; blank them out rather
    // than showing a "/" placeholder, unlike the VA/PA field boxes which keep "/" as an empty-state cue.
    private javafx.beans.binding.StringExpression hideWhenInactive(StringProperty valueProperty, BooleanProperty active) {
        return javafx.beans.binding.Bindings.when(active).then(valueProperty).otherwise("");
    }

    // Toggles the ":active" pseudo-class so CSS can style a connector/label differently once its step has run
    private void bindActive(Node node, BooleanProperty active) {
        node.pseudoClassStateChanged(ACTIVE, active.get());
        active.addListener((obs, oldVal, newVal) -> node.pseudoClassStateChanged(ACTIVE, newVal));
    }

    // Same, but reaches into a BitWidthLine so its wire, arrow head, tick and both labels light up together
    private void bindActive(BitWidthLine line, BooleanProperty active) {
        bindActive(line.getWire(), active);
        bindActive(line.getArrowHead(), active);
        bindActive(line.getTick(), active);
        bindActive(line.getBitsLabel(), active);
        bindActive(line.getValueLabel(), active);
    }

    // Only ever used for the "Table Offset" phantom width (offsetBoxW), which the merge-brace
    // geometry positions relative to pageBox's own centre -- so it has to grow with the same
    // ADDRESS_FIELD_PADDING pageBox itself now uses, or that assumed-comparable width goes stale
    // and the brace's two ears end up far out of proportion with each other.
    private double fieldWidth(int digits) {
        Text sample = new Text("0x" + "F".repeat(Math.max(digits, 1)));
        sample.setFont(FIELD_FONT);
        return Math.max(MIN_FIELD_WIDTH, sample.getLayoutBounds().getWidth() + ADDRESS_FIELD_PADDING);
    }

    // Wide enough to fit either the widest possible hex value or the field's title text, whichever is larger
    private double titledFieldWidth(String title, int digits) {
        return Math.max(fieldWidth(digits), textWidth(title, TITLE_FONT) + TITLE_PADDING);
    }

    private static double textWidth(String text, Font font) {
        Text sample = new Text(text);
        sample.setFont(font);
        return sample.getLayoutBounds().getWidth();
    }

}
