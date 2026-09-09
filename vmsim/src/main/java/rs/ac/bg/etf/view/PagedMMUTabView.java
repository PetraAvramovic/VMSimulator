package rs.ac.bg.etf.view;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import rs.ac.bg.etf.view.shape.BitWidthLine;
import rs.ac.bg.etf.view.shape.CurlyBrace;
import rs.ac.bg.etf.view.util.FieldBoxes;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel.MmuLine;

import java.util.List;

/**
 * Paged MMU tab: address-computation schematic (VA/PA field boxes, page-table-pointer adder,
 * and the windowed page table) connected by lines, mirroring the paged-MMU hardware diagram.
 */
public class PagedMMUTabView extends StackPane {
    // Field-box font/padding/height live in FieldBoxes now (shared with the TLB tab); aliased here so
    // the many BOX_HEIGHT/FIELD_FONT call sites below stay unchanged.
    private static final Font FIELD_FONT = FieldBoxes.FIELD_FONT;
    private static final Font TITLE_FONT = Font.font("System", javafx.scene.text.FontWeight.BOLD, 11);
    private static final double FIELD_PADDING = FieldBoxes.FIELD_PADDING;
    private static final double TITLE_PADDING = 40;
    private static final double MIN_FIELD_WIDTH = FieldBoxes.MIN_FIELD_WIDTH;
    private static final double BOX_HEIGHT = FieldBoxes.BOX_HEIGHT;
    private static final double CANVAS_WIDTH = 920;
    private static final double CANVAS_HEIGHT = 780;
    private static final double MARGIN = 30;
    // Every field that drops straight down from the page table's bottom edge uses this wire length, so
    // that their bit-width indicators (each drawn at its wire's midpoint) sit on one horizontal row.
    private static final double DROP_LENGTH = 40;
    private static final double DROP_INDICATOR_Y = DROP_LENGTH / 2;
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    private final Pane canvas = new Pane();

    private PagedMMUTabViewModel viewModel;

    public PagedMMUTabView(PagedMMUTabViewModel viewModel) {
        this.viewModel = viewModel;
        getStyleClass().add("mmu-tab-container");
        canvas.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);

        // ---- Standalone field widths (unchanged: title stays inside these boxes) ----
        double pointerBoxW = titledFieldWidth("Page Table Pointer", ValueConverter.hexDigitsFor(viewModel.getPhysicalAddressBits()));
        double offsetBoxW = titledFieldWidth("Table Offset", ValueConverter.hexDigitsFor(viewModel.getOffsetBits()));

        double boxY = 50;

        // ---- Virtual Address: Page | Word, rendered as one divided box (no gap, shared border) ----
        Region pageBox = FieldBoxes.valueCell("va-breakdown-cell-left", viewModel.pageHexProperty(), ValueConverter.hexDigitsFor(viewModel.getPageBits()));
        double pageBoxW = pageBox.getPrefWidth();
        double pageBoxX = MARGIN;
        pageBox.setLayoutX(pageBoxX);
        pageBox.setLayoutY(boxY);

        Region wordBoxVA = FieldBoxes.valueCell("va-breakdown-cell-right", viewModel.wordHexProperty(), ValueConverter.hexDigitsFor(viewModel.getWordBits()));
        double wordBoxW = wordBoxVA.getPrefWidth();
        double wordBoxVAX = pageBoxX + pageBoxW;
        wordBoxVA.setLayoutX(wordBoxVAX);
        wordBoxVA.setLayoutY(boxY);

        Label pageTitle = FieldBoxes.fieldTitle("Page", pageBoxX, boxY - 20);
        Label wordTitleVA = FieldBoxes.fieldTitle("Word", wordBoxVAX, boxY - 20);

        // ---- Physical Address: Block | Word, mirrored on the right with the same adjacent-box treatment ----
        double wordBoxPAX = CANVAS_WIDTH - MARGIN - wordBoxW;
        Region wordBoxPA = FieldBoxes.valueCell("va-breakdown-cell-right", viewModel.paWordHexProperty(), ValueConverter.hexDigitsFor(viewModel.getWordBits()));
        wordBoxPA.setLayoutX(wordBoxPAX);
        wordBoxPA.setLayoutY(boxY);

        Region blockBoxPA = FieldBoxes.valueCell("va-breakdown-cell-left", viewModel.blockHexProperty(), viewModel.blockHexDigitsProperty().get());
        double blockBoxW = blockBoxPA.getPrefWidth();
        double blockBoxPAX = wordBoxPAX - blockBoxW;
        blockBoxPA.setLayoutX(blockBoxPAX);
        blockBoxPA.setLayoutY(boxY);

        Label blockTitle = FieldBoxes.fieldTitle("Block", blockBoxPAX, boxY - 20);
        Label wordTitlePA = FieldBoxes.fieldTitle("Word", wordBoxPAX, boxY - 20);

        Label vaHeader = FieldBoxes.sectionLabel("Virtual Address", pageBoxX, 10);
        Label paHeader = FieldBoxes.sectionLabel("Physical Address", blockBoxPAX, 10);

        // ---- Word pass-through: VA Word flows straight across into PA Word, unchanged ----
        // Right-angle elbow (each segment changes only one axis) so it reads as a clean signal wire
        double passY = boxY + BOX_HEIGHT + 40;
        double wordVACenterX = wordBoxVAX + wordBoxW / 2;
        double wordPACenterX = wordBoxPAX + wordBoxW / 2;
        Polyline wordPassLine = elbow(
                wordVACenterX, boxY + BOX_HEIGHT,
                wordVACenterX, passY,
                wordPACenterX, passY,
                wordPACenterX, boxY + BOX_HEIGHT);
        Label wordBitsStart = FieldBoxes.bitLabel(viewModel.getWordBits(), wordVACenterX + 6, passY - 18);
        Label wordBitsEnd = FieldBoxes.bitLabel(viewModel.getWordBits(), wordPACenterX + 6, passY - 18);

        // ---- Page -> page-table offset (page bits concatenated with a fixed shift-bit zero fill) ----
        // There is no standalone box for this value anymore; it is shown as a label riding the wire
        // that carries it from the merge brace down into the adder.
        double offsetBoxY = 220;
        double offsetBoxX = pageBoxX;
        double offsetLineMidY = offsetBoxY + BOX_HEIGHT;

        // The page line and the zero-fill line merge into a single offset value via a curly brace;
        // its ears sit a fixed gap above the merge point so there's room for the brace curve + stub.
        // The feeding lines and the merge stub stop short of the brace, leaving a visible gap.
        double braceTopY = offsetBoxY - 10;
        double braceDepth = 20;
        double braceGap = 8;
        // The tip's true X depends on both ears (page-box center and the zero-fill line's X), so the
        // stub below the brace must target that same point exactly, or it reads as a slight bend.
        double mergeCenterX = ((pageBoxX + pageBoxW / 2.0) + (offsetBoxX + offsetBoxW * 0.7)) / 2.0;

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

        double shiftLineX = offsetBoxX + offsetBoxW * 0.7;
        BitWidthLine shiftDownLine = bitWidthWire(viewModel.getShiftBits());
        shiftDownLine.arrowTipVisibleProperty().set(false);
        shiftDownLine.startXProperty().set(shiftLineX);
        shiftDownLine.startYProperty().set(boxY + BOX_HEIGHT + 50);

        Label zeroFillLabel = new Label("0");
        zeroFillLabel.getStyleClass().add("mmu-bit-value");
        zeroFillLabel.setLayoutX(shiftLineX);
        zeroFillLabel.setLayoutY(boxY + BOX_HEIGHT + 30);

        zeroFillLabel.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double labelWidth = newWidth.doubleValue();
            zeroFillLabel.setLayoutX(shiftLineX - (labelWidth / 2.0));
        });

        // The brace's right ear tracks the zero-fill line's own X, so that line stays vertical too
        offsetBrace.rightXProperty().set(shiftLineX);
        offsetBrace.rightYProperty().set(braceTopY);
        shiftDownLine.endXProperty().bind(offsetBrace.rightXProperty());
        shiftDownLine.endYProperty().bind(offsetBrace.rightYProperty().subtract(braceGap));

        // Merged offset value stub: descends from the brace's tip toward the adder, leaving a gap
        // between the brace's point and the stub so they don't visually touch.
        BitWidthLine offsetMergeLine = bitWidthWire(viewModel.getOffsetBits());
        offsetMergeLine.arrowTipVisibleProperty().set(false);
        offsetMergeLine.endXProperty().set(mergeCenterX);
        offsetMergeLine.endYProperty().set(offsetLineMidY);
        offsetMergeLine.startXProperty().bind(offsetBrace.tipXProperty());
        offsetMergeLine.startYProperty().bind(offsetBrace.tipYProperty().add(braceGap));

        Label offsetValueLabel = new Label();
        offsetValueLabel.textProperty().bind(viewModel.descriptorOffsetHexProperty());
        offsetValueLabel.getStyleClass().add("mmu-bit-value");
        offsetValueLabel.setLayoutX(mergeCenterX + 10);
        offsetValueLabel.setLayoutY(offsetLineMidY - 8);

        // ---- Page table pointer (base address of the current user's page table) ----
        double pointerBoxX = offsetBoxX + offsetBoxW + 90;
        double pointerBoxY = offsetBoxY;
        Region pointerBox = valueBox("Page Table Pointer", viewModel.pageTablePointerHexProperty(), pointerBoxW, pointerBoxX, pointerBoxY);

        // ---- Adder: page table pointer + table offset = descriptor's physical address ----
        double adderCenterX = mergeCenterX;
        double adderCenterY = offsetBoxY + BOX_HEIGHT + 70;
        double adderRadius = 18;

        // A StackPane centers its children by bounds, so the "+" sits exactly in the middle of the
        // circle regardless of font metrics (unlike hardcoded pixel offsets).
        StackPane adderNode = new StackPane();
        adderNode.setLayoutX(adderCenterX - adderRadius);
        adderNode.setLayoutY(adderCenterY - adderRadius);
        adderNode.setPrefSize(adderRadius * 2, adderRadius * 2);
        Circle adderCircle = new Circle(adderRadius);
        adderCircle.getStyleClass().add("mmu-adder-circle");
        Label adderPlus = new Label("+");
        adderPlus.getStyleClass().add("mmu-adder-label");
        adderNode.getChildren().addAll(adderCircle, adderPlus);

        Line offsetToAdder = line(mergeCenterX, offsetLineMidY, adderCenterX, adderCenterY - adderRadius);
        Polyline pointerToAdder = elbow(
                pointerBoxX + pointerBoxW / 2, pointerBoxY + BOX_HEIGHT,
                pointerBoxX + pointerBoxW / 2, adderCenterY,
                adderCenterX + 18, adderCenterY);

        // ---- Adder output descends toward the page table (the highlighted row is the dynamic target) ----
        double tableX = pointerBoxX - 40;
        double tableY = adderCenterY + 90;

        BitWidthLine adderDownStub = bitWidthWire(viewModel.getPhysicalAddressBits());
        adderDownStub.arrowTipVisibleProperty().set(false);
        adderDownStub.startXProperty().set(adderCenterX);
        adderDownStub.startYProperty().set(adderCenterY + 18);
        adderDownStub.endXProperty().set(adderCenterX);
        adderDownStub.endYProperty().set(tableY - 20);

        Label tableHeaderLabel = FieldBoxes.sectionLabel("Page Table", tableX, tableY - 24);
        Label tableSizeLabel = new Label("2^" + viewModel.getPageBits() + " entries");
        tableSizeLabel.getStyleClass().add("mmu-bit-value");
        tableSizeLabel.setLayoutX(tableX + 260);
        tableSizeLabel.setLayoutY(tableY - 24);

        PageTableView pageTableView = new PageTableView(viewModel);
        pageTableView.setLayoutX(tableX);
        pageTableView.setLayoutY(tableY);

        canvas.getChildren().addAll(
                wordPassLine, pageDownLine, shiftDownLine, offsetBrace, offsetMergeLine,
                offsetValueLabel, offsetToAdder, pointerToAdder, adderDownStub,
                wordBitsStart, wordBitsEnd, zeroFillLabel,
                vaHeader, paHeader, pageTitle, wordTitleVA, blockTitle, wordTitlePA,
                pageBox, wordBoxVA, blockBoxPA, wordBoxPA, pointerBox,
                adderNode, tableHeaderLabel, tableSizeLabel, pageTableView);

        // ---- Dynamic connectors: the highlighted row moves within the window as pages change ----
        Polyline addressToRowLine = elbow();
        Label addressLabel = new Label();
        addressLabel.textProperty().bind(viewModel.descriptorAddressHexProperty());
        addressLabel.getStyleClass().add("mmu-bit-value");

        Polyline blockToBoxLine = elbow();
        Label blockFlowLabel = new Label();
        blockFlowLabel.textProperty().bind(viewModel.blockHexProperty());
        blockFlowLabel.getStyleClass().add("mmu-bit-value");

        // ---- Below the table: V/D/Disk fields of the highlighted row drop straight down, Block's
        // bit-width tag rides along the existing block-flow wire instead of a line of its own ----
        Line blockBitsTick = tick();
        Label blockBitsLabel = FieldBoxes.bitLabel(viewModel.getFrameBits(), 0, 0);

        ColumnDrop vDrop = columnDrop(1, viewModel.currentVBitProperty(), pageTableView.validColumnAnchorProperty().get());
        ColumnDrop dDrop = columnDrop(1, viewModel.currentDBitProperty(), pageTableView.dirtyColumnAnchorProperty().get());
        ColumnDrop diskDrop = columnDrop(viewModel.getDiskBits(), viewModel.currentDiskHexProperty(), pageTableView.diskColumnAnchorProperty().get());

        canvas.getChildren().addAll(addressToRowLine, addressLabel, blockToBoxLine, blockFlowLabel, blockBitsTick, blockBitsLabel);
        canvas.getChildren().addAll(vDrop.nodes());
        canvas.getChildren().addAll(dDrop.nodes());
        canvas.getChildren().addAll(diskDrop.nodes());

        Runnable updateDynamicConnectors = () -> updateDynamicConnectors(
                pageTableView, adderCenterX, tableY - 20, addressToRowLine, addressLabel,
                blockBoxPA, blockToBoxLine, blockFlowLabel, blockBitsTick, blockBitsLabel, vDrop, dDrop, diskDrop);

        pageTableView.currentEntryAnchorProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(updateDynamicConnectors));
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(updateDynamicConnectors));
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> Platform.runLater(updateDynamicConnectors));
        Platform.runLater(updateDynamicConnectors);

        // ---- Wires/labels only light up once the step that uses them has actually executed ----
        bindActive(pageDownLine, viewModel.lineActiveProperty(MmuLine.PAGE_TO_OFFSET));
        bindActive(offsetBrace, viewModel.lineActiveProperty(MmuLine.PAGE_TO_OFFSET));
        bindActive(offsetMergeLine, viewModel.lineActiveProperty(MmuLine.PAGE_TO_OFFSET));

        bindActive(shiftDownLine, viewModel.lineActiveProperty(MmuLine.ZERO_FILL_TO_OFFSET));
        bindActive(zeroFillLabel, viewModel.lineActiveProperty(MmuLine.ZERO_FILL_TO_OFFSET));

        bindActive(offsetToAdder, viewModel.lineActiveProperty(MmuLine.OFFSET_TO_ADDER));
        bindActive(offsetValueLabel, viewModel.lineActiveProperty(MmuLine.OFFSET_TO_ADDER));
        bindActive(pointerToAdder, viewModel.lineActiveProperty(MmuLine.POINTER_TO_ADDER));

        bindActive(adderDownStub, viewModel.lineActiveProperty(MmuLine.ADDER_TO_ROW));
        bindActive(addressToRowLine, viewModel.lineActiveProperty(MmuLine.ADDER_TO_ROW));
        bindActive(addressLabel, viewModel.lineActiveProperty(MmuLine.ADDER_TO_ROW));

        bindActive(wordPassLine, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));
        bindActive(wordBitsStart, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));
        bindActive(wordBitsEnd, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));

        bindActive(blockToBoxLine, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockFlowLabel, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockBitsTick, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockBitsLabel, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));

        for (ColumnDrop drop : List.of(vDrop, dDrop, diskDrop)) {
            bindActive(drop.line(), viewModel.pageTableAccessedProperty());
            bindActive(drop.valueLabel(), viewModel.pageTableAccessedProperty());
        }

        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.getStyleClass().add("mmu-scroll-pane");

        getChildren().add(scrollPane);
    }

    private void updateDynamicConnectors(
            PageTableView pageTableView, double sourceX, double sourceY,
            Polyline addressToRowLine, Label addressLabel,
            Region blockBoxPA, Polyline blockToBoxLine, Label blockFlowLabel,
            Line blockBitsTick, Label blockBitsLabel,
            ColumnDrop vDrop, ColumnDrop dDrop, ColumnDrop diskDrop) {

        // 1. Read the true hardware step access state straight from the ViewModel
        boolean isTableCurrentlyAccessed = viewModel.pageTableAccessedProperty().get();
        Region rowAnchor = pageTableView.currentEntryAnchorProperty().get();

        addressToRowLine.setVisible(true);
        
        // Establish a stable horizontal entry coordinate straight from the table's left border
        double stableTableLeftEdgeX = pageTableView.getLayoutX();
        double targetWireCenterY = 0;

        // =========================================================================
        // SOLID HARDWARE SIGNAL ROUTER SWITCH
        // =========================================================================
        if (isTableCurrentlyAccessed && rowAnchor != null && rowAnchor.getScene() != null) {
            // ---- ACTIVE ACCESS TRACK ----
            // Use the absolute, changing vertical midpoint of the active row strip [^*]
            Bounds rowBounds = canvas.sceneToLocal(rowAnchor.localToScene(rowAnchor.getBoundsInLocal()));
            targetWireCenterY = rowBounds.getCenterY();
        } else {
            // ---- INACTIVE / IDLE GRACEFUL FALLBACK TRACK ----
            // Completely ignore the uninitialized child rows. Center the wire using
            // the stable vertical midpoint of the parent Page Table node itself [^*]!
            if (pageTableView.getScene() != null) {
                Bounds tableBounds = canvas.sceneToLocal(pageTableView.localToScene(pageTableView.getBoundsInLocal()));
                
                // Pinpoint the perfect vertical center of the table block graphics frame [^*]
                targetWireCenterY = tableBounds.getMinY() + (tableBounds.getHeight() / 2.0);
            }
        }

        // Apply vector coordinates updates cleanly if layout geometry is valid
        if (targetWireCenterY > 0) {
            addressToRowLine.getPoints().setAll(
                    sourceX, sourceY,
                    sourceX, targetWireCenterY,
                    stableTableLeftEdgeX, targetWireCenterY);

            addressLabel.setLayoutX(sourceX + 6);
            addressLabel.setLayoutY((sourceY + targetWireCenterY) / 2 - 14);
        }

        // Pull vector components to front to prevent Z-order overlapping visibility clips
        addressToRowLine.toFront();
        addressLabel.toFront();

        // =========================================================================
        // BLOCK PASSTHROUGH WIRE CONTROLLER (Keep your existing working block line logic)
        // =========================================================================
        Region blockColumnAnchor = pageTableView.blockColumnAnchorProperty().get();
        if (blockColumnAnchor != null && blockColumnAnchor.getScene() != null && blockBoxPA.getScene() != null) {
            Bounds columnBounds = canvas.sceneToLocal(blockColumnAnchor.localToScene(blockColumnAnchor.getBoundsInLocal()));
            Bounds tableBounds = canvas.sceneToLocal(pageTableView.localToScene(pageTableView.getBoundsInLocal()));
            Bounds blockBoxBounds = canvas.sceneToLocal(blockBoxPA.localToScene(blockBoxPA.getBoundsInLocal()));

            double columnX = columnBounds.getCenterX();
            double tableBottom = tableBounds.getMaxY();
            double dropY = tableBottom + 80;
            double boxCenterX = blockBoxBounds.getCenterX();

            blockToBoxLine.setVisible(true);
            blockToBoxLine.getPoints().setAll(
                    columnX, tableBottom,
                    columnX, dropY,
                    boxCenterX, dropY,
                    boxCenterX, blockBoxBounds.getMaxY());

            blockFlowLabel.setLayoutX((columnX + boxCenterX) / 2 - 20);
            blockFlowLabel.setLayoutY(dropY - 18);
            
            blockToBoxLine.toFront();
            blockFlowLabel.toFront();

            // Block's bit-width tag rides that wire, pinned to the same row as the V/D/Disk drops below
            double indicatorY = tableBottom + DROP_INDICATOR_Y;
            positionTick(blockBitsTick, columnX, indicatorY);
            blockBitsLabel.setLayoutX(columnX + 13);
            blockBitsLabel.setLayoutY(indicatorY - blockBitsLabel.getHeight() / 2);
        }

        // =========================================================================
        // V / D / DISK DROP LINES: straight down from the highlighted row's columns
        // =========================================================================
        if (pageTableView.getScene() != null) {
            Bounds tableBounds = canvas.sceneToLocal(pageTableView.localToScene(pageTableView.getBoundsInLocal()));
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

        drop.valueLabel().setLayoutY(dropBottom + 6);

        for (Node node : drop.nodes())
            node.toFront();
    }

    // Short diagonal stroke crossing a wire to denote its bit width, matching the reference schematic
    private void positionTick(Line tickMark, double centerX, double centerY) {
        tickMark.setStartX(centerX - 6);
        tickMark.setStartY(centerY + 6);
        tickMark.setEndX(centerX + 6);
        tickMark.setEndY(centerY - 6);
    }

    // A column's drop from the table down to its resolved value: a self-labelling bit-width wire
    // (line + diagonal tick + "Nb" tag + arrowhead) plus the hex value readout below it.
    private record ColumnDrop(BitWidthLine line, Label valueLabel) {
        Node[] nodes() {
            return new Node[] { line, valueLabel };
        }
    }

    private ColumnDrop columnDrop(int bits, StringProperty valueProperty, Region columnAnchor) {
        BitWidthLine line = bitWidthWire(bits);

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(valueProperty);
        valueLabel.getStyleClass().add("mmu-bit-value");

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

    private Line tick() {
        Line tickMark = new Line();
        tickMark.getStyleClass().add("connector-line");
        return tickMark;
    }

    private Region valueBox(String title, StringProperty valueProperty, double width, double x, double y) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("va-breakdown-title");

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(valueProperty);
        valueLabel.getStyleClass().add("va-breakdown-value");

        VBox box = new VBox(4, titleLabel, valueLabel);
        box.getStyleClass().add("va-breakdown-box");
        box.setAlignment(Pos.CENTER);
        box.setPrefWidth(width);
        box.setMinWidth(width);
        box.setMaxWidth(width);
        box.setPrefHeight(BOX_HEIGHT);
        box.setMinHeight(BOX_HEIGHT);
        box.setMaxHeight(BOX_HEIGHT);
        box.setLayoutX(x);
        box.setLayoutY(y);
        return box;
    }

    // A straight signal wire that carries its own mid-span bit-width tag (diagonal tick + "Nb" label),
    // replacing the old hand-placed line + tick + bitLabel trios. The tag rides on the wire's right side.
    private BitWidthLine bitWidthWire(int bits) {
        BitWidthLine wire = new BitWidthLine();
        wire.bitsProperty().set(bits);
        wire.labelOnLeftProperty().set(false);
        return wire;
    }

    private Line line(double x1, double y1, double x2, double y2) {
        Line line = new Line(x1, y1, x2, y2);
        line.getStyleClass().add("connector-line");
        return line;
    }

    private Polyline elbow(double... points) {
        Polyline polyline = new Polyline(points);
        polyline.getStyleClass().add("connector-line");
        return polyline;
    }

    // Toggles the ":active" pseudo-class so CSS can style a connector/label differently once its step has run
    private void bindActive(Node node, BooleanProperty active) {
        node.pseudoClassStateChanged(ACTIVE, active.get());
        active.addListener((obs, oldVal, newVal) -> node.pseudoClassStateChanged(ACTIVE, newVal));
    }

    // Same, but reaches into a BitWidthLine so its wire, arrow head, tick and label all light up together
    private void bindActive(BitWidthLine line, BooleanProperty active) {
        bindActive(line.getWire(), active);
        bindActive(line.getArrowHead(), active);
        bindActive(line.getTick(), active);
        bindActive(line.getBitsLabel(), active);
    }

    private static double fieldWidth(int digits) {
        Text sample = new Text("0x" + "F".repeat(Math.max(digits, 1)));
        sample.setFont(FIELD_FONT);
        return Math.max(MIN_FIELD_WIDTH, sample.getLayoutBounds().getWidth() + FIELD_PADDING);
    }

    // Wide enough to fit either the widest possible hex value or the field's title text, whichever is larger
    private static double titledFieldWidth(String title, int digits) {
        return Math.max(fieldWidth(digits), textWidth(title, TITLE_FONT) + TITLE_PADDING);
    }

    private static double textWidth(String text, Font font) {
        Text sample = new Text(text);
        sample.setFont(font);
        return sample.getLayoutBounds().getWidth();
    }

}
