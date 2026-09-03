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
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel.MmuLine;

import java.util.List;

/**
 * Paged MMU tab: address-computation schematic (VA/PA field boxes, page-table-pointer adder,
 * and the windowed page table) connected by lines, mirroring the paged-MMU hardware diagram.
 */
public class PagedMMUTabView extends StackPane {
    // Must match .va-breakdown-value's actual CSS weight/size, otherwise width estimates undershoot
    // the real rendered text and values get clipped to an ellipsis.
    private static final Font FIELD_FONT = Font.font("Consolas", javafx.scene.text.FontWeight.BOLD, 15);
    private static final Font TITLE_FONT = Font.font("System", javafx.scene.text.FontWeight.BOLD, 11);
    private static final double FIELD_PADDING = 36;
    private static final double TITLE_PADDING = 40;
    private static final double MIN_FIELD_WIDTH = 70;
    // Boxes are forced to this exact height (padding 10px*2 + border 2px*2 + title/value text) so the
    // fixed-coordinate connector lines below always line up with the real rendered box edges.
    private static final double BOX_HEIGHT = 62;
    private static final double CANVAS_WIDTH = 920;
    private static final double CANVAS_HEIGHT = 780;
    private static final double MARGIN = 30;
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");

    private final Pane canvas = new Pane();

    private PagedMMUTabViewModel viewModel;

    public PagedMMUTabView(PagedMMUTabViewModel viewModel) {
        this.viewModel = viewModel;
        getStyleClass().add("mmu-tab-container");
        canvas.setPrefSize(CANVAS_WIDTH, CANVAS_HEIGHT);

        // ---- Standalone field widths (unchanged: title stays inside these boxes) ----
        double pointerBoxW = titledFieldWidth("Page Table Pointer", hexDigits(viewModel.getPhysicalAddressBits()));
        double offsetBoxW = titledFieldWidth("Table Offset", hexDigits(viewModel.getOffsetBits()));

        double boxY = 50;

        // ---- Virtual Address: Page | Word, rendered as one divided box (no gap, shared border) ----
        Region pageBox = valueCell("va-breakdown-cell-left", viewModel.pageHexProperty(), exactHexDigits(viewModel.getPageBits()));
        double pageBoxW = pageBox.getPrefWidth();
        double pageBoxX = MARGIN;
        pageBox.setLayoutX(pageBoxX);
        pageBox.setLayoutY(boxY);

        Region wordBoxVA = valueCell("va-breakdown-cell-right", viewModel.wordHexProperty(), exactHexDigits(viewModel.getWordBits()));
        double wordBoxW = wordBoxVA.getPrefWidth();
        double wordBoxVAX = pageBoxX + pageBoxW;
        wordBoxVA.setLayoutX(wordBoxVAX);
        wordBoxVA.setLayoutY(boxY);

        Label pageTitle = fieldTitle("Page", pageBoxX, boxY - 20);
        Label wordTitleVA = fieldTitle("Word", wordBoxVAX, boxY - 20);

        // ---- Physical Address: Block | Word, mirrored on the right with the same adjacent-box treatment ----
        double wordBoxPAX = CANVAS_WIDTH - MARGIN - wordBoxW;
        Region wordBoxPA = valueCell("va-breakdown-cell-right", viewModel.paWordHexProperty(), exactHexDigits(viewModel.getWordBits()));
        wordBoxPA.setLayoutX(wordBoxPAX);
        wordBoxPA.setLayoutY(boxY);

        Region blockBoxPA = valueCell("va-breakdown-cell-left", viewModel.blockHexProperty(), viewModel.blockHexDigitsProperty().get());
        double blockBoxW = blockBoxPA.getPrefWidth();
        double blockBoxPAX = wordBoxPAX - blockBoxW;
        blockBoxPA.setLayoutX(blockBoxPAX);
        blockBoxPA.setLayoutY(boxY);

        Label blockTitle = fieldTitle("Block", blockBoxPAX, boxY - 20);
        Label wordTitlePA = fieldTitle("Word", wordBoxPAX, boxY - 20);

        Label vaHeader = sectionLabel("Virtual Address", pageBoxX, 10);
        Label paHeader = sectionLabel("Physical Address", blockBoxPAX, 10);

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
        Label wordBitsStart = bitLabel(viewModel.getWordBits(), wordVACenterX + 6, passY - 18);
        Label wordBitsEnd = bitLabel(viewModel.getWordBits(), wordPACenterX + 6, passY - 18);

        // ---- Page -> page-table offset (page bits concatenated with a fixed shift-bit zero fill) ----
        double offsetBoxY = 220;
        double offsetBoxX = pageBoxX;
        Region offsetBox = valueBox("Table Offset", viewModel.descriptorOffsetHexProperty(), offsetBoxW, offsetBoxX, offsetBoxY);

        Line pageDownLine = line(pageBoxX + pageBoxW / 2, boxY + BOX_HEIGHT, offsetBoxX + offsetBoxW * 0.3, offsetBoxY);
        Label pageBitsLabel = bitLabel(viewModel.getPageBits(), pageBoxX + pageBoxW / 2 + 6, (boxY + BOX_HEIGHT + offsetBoxY) / 2);

        Line shiftDownLine = line(offsetBoxX + offsetBoxW * 0.7, boxY + BOX_HEIGHT + 50, offsetBoxX + offsetBoxW * 0.7, offsetBoxY);
        Label zeroFillLabel = new Label("0");
        zeroFillLabel.getStyleClass().add("mmu-bit-value");
        zeroFillLabel.setLayoutX(shiftDownLine.getStartX());
        zeroFillLabel.setLayoutY(boxY + BOX_HEIGHT + 30);

        zeroFillLabel.widthProperty().addListener((obs, oldWidth, newWidth) -> {
            double labelWidth = newWidth.doubleValue();
            zeroFillLabel.setLayoutX(offsetBoxX + offsetBoxW * 0.7 - (labelWidth / 2.0));
        });

        Label shiftBitsLabel = bitLabel(viewModel.getShiftBits(), offsetBoxX + offsetBoxW * 0.7 + 6, (boxY + BOX_HEIGHT + offsetBoxY) / 2 + 24);

        // ---- Page table pointer (base address of the current user's page table) ----
        double pointerBoxX = offsetBoxX + offsetBoxW + 90;
        double pointerBoxY = offsetBoxY;
        Region pointerBox = valueBox("Page Table Pointer", viewModel.pageTablePointerHexProperty(), pointerBoxW, pointerBoxX, pointerBoxY);

        // ---- Adder: page table pointer + table offset = descriptor's physical address ----
        double adderCenterX = offsetBoxX + offsetBoxW / 2;
        double adderCenterY = offsetBoxY + BOX_HEIGHT + 70;
        Circle adderCircle = new Circle(adderCenterX, adderCenterY, 18);
        adderCircle.getStyleClass().add("mmu-adder-circle");
        Label adderPlus = new Label("+");
        adderPlus.getStyleClass().add("mmu-adder-label");
        adderPlus.setLayoutX(adderCenterX - 5);
        adderPlus.setLayoutY(adderCenterY - 13);

        Line offsetToAdder = line(offsetBoxX + offsetBoxW / 2, offsetBoxY + BOX_HEIGHT, adderCenterX, adderCenterY - 18);
        Polyline pointerToAdder = elbow(
                pointerBoxX + pointerBoxW / 2, pointerBoxY + BOX_HEIGHT,
                pointerBoxX + pointerBoxW / 2, adderCenterY,
                adderCenterX + 18, adderCenterY);

        // ---- Adder output descends toward the page table (the highlighted row is the dynamic target) ----
        double tableX = pointerBoxX - 40;
        double tableY = adderCenterY + 90;

        Line adderDownStub = line(adderCenterX, adderCenterY + 18, adderCenterX, tableY - 20);
        Label physBitsLabel = bitLabel(viewModel.getPhysicalAddressBits(), adderCenterX + 6, adderCenterY + 45);

        Label tableHeaderLabel = sectionLabel("Page Table", tableX, tableY - 24);
        Label tableSizeLabel = new Label("2^" + viewModel.getPageBits() + " entries");
        tableSizeLabel.getStyleClass().add("mmu-bit-value");
        tableSizeLabel.setLayoutX(tableX + 260);
        tableSizeLabel.setLayoutY(tableY - 24);

        PageTableView pageTableView = new PageTableView(viewModel);
        pageTableView.setLayoutX(tableX);
        pageTableView.setLayoutY(tableY);

        canvas.getChildren().addAll(
                wordPassLine, pageDownLine, shiftDownLine, offsetToAdder, pointerToAdder, adderDownStub,
                wordBitsStart, wordBitsEnd, pageBitsLabel, zeroFillLabel, shiftBitsLabel, physBitsLabel,
                vaHeader, paHeader, pageTitle, wordTitleVA, blockTitle, wordTitlePA,
                pageBox, wordBoxVA, blockBoxPA, wordBoxPA, offsetBox, pointerBox,
                adderCircle, adderPlus, tableHeaderLabel, tableSizeLabel, pageTableView);

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
        Label blockBitsLabel = bitLabel(viewModel.getFrameBits(), 0, 0);

        ColumnDrop vDrop = columnDrop(1, viewModel.currentVBitProperty());
        ColumnDrop dDrop = columnDrop(1, viewModel.currentDBitProperty());
        ColumnDrop diskDrop = columnDrop(viewModel.getDiskBits(), viewModel.currentDiskHexProperty());

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
        bindActive(pageBitsLabel, viewModel.lineActiveProperty(MmuLine.PAGE_TO_OFFSET));

        bindActive(shiftDownLine, viewModel.lineActiveProperty(MmuLine.ZERO_FILL_TO_OFFSET));
        bindActive(zeroFillLabel, viewModel.lineActiveProperty(MmuLine.ZERO_FILL_TO_OFFSET));
        bindActive(shiftBitsLabel, viewModel.lineActiveProperty(MmuLine.ZERO_FILL_TO_OFFSET));

        bindActive(offsetToAdder, viewModel.lineActiveProperty(MmuLine.OFFSET_TO_ADDER));
        bindActive(pointerToAdder, viewModel.lineActiveProperty(MmuLine.POINTER_TO_ADDER));

        bindActive(adderDownStub, viewModel.lineActiveProperty(MmuLine.ADDER_TO_ROW));
        bindActive(physBitsLabel, viewModel.lineActiveProperty(MmuLine.ADDER_TO_ROW));
        bindActive(addressToRowLine, viewModel.lineActiveProperty(MmuLine.ADDER_TO_ROW));
        bindActive(addressLabel, viewModel.lineActiveProperty(MmuLine.ADDER_TO_ROW));

        bindActive(wordPassLine, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));
        bindActive(wordBitsStart, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));
        bindActive(wordBitsEnd, viewModel.lineActiveProperty(MmuLine.WORD_PASSTHROUGH));

        bindActive(blockToBoxLine, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockFlowLabel, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockBitsTick, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));
        bindActive(blockBitsLabel, viewModel.lineActiveProperty(MmuLine.ROW_TO_BLOCK));

        for (ColumnDrop drop : List.of(vDrop, dDrop, diskDrop))
            for (Node node : drop.nodes())
                bindActive(node, viewModel.pageTableAccessedProperty());

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

            // Block's bit-width tag rides along the top of that same wire rather than a separate line
            positionTick(blockBitsTick, columnX, tableBottom);
            blockBitsLabel.setLayoutX(columnX + 8);
            blockBitsLabel.setLayoutY(tableBottom + 2);
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

    // Places a drop line straight down from a table column's header, with a bit-width tick near the
    // top and an arrowhead where it meets the value readout below.
    private void positionColumnDrop(Region columnAnchor, double tableBottom, ColumnDrop drop) {
        if (columnAnchor == null || columnAnchor.getScene() == null)
            return;

        Bounds columnBounds = canvas.sceneToLocal(columnAnchor.localToScene(columnAnchor.getBoundsInLocal()));
        double columnX = columnBounds.getCenterX();
        double dropBottom = tableBottom + 40;

        drop.dropLine().setStartX(columnX);
        drop.dropLine().setStartY(tableBottom);
        drop.dropLine().setEndX(columnX);
        drop.dropLine().setEndY(dropBottom);

        positionTick(drop.tickMark(), columnX, tableBottom);

        drop.arrow().getPoints().setAll(
                columnX - 5, dropBottom - 7,
                columnX, dropBottom,
                columnX + 5, dropBottom - 7);

        drop.bitsLabel().setLayoutX(columnX + 8);
        drop.bitsLabel().setLayoutY(tableBottom + 2);

        drop.valueLabel().setLayoutX(columnX - drop.valueLabel().getBoundsInLocal().getWidth() / 2 - 2);
        drop.valueLabel().setLayoutY(dropBottom + 6);

        for (Node node : drop.nodes())
            node.toFront();
    }

    // Short diagonal stroke crossing a wire to denote its bit width, matching the reference schematic
    private void positionTick(Line tickMark, double centerX, double centerY) {
        tickMark.setStartX(centerX - 6);
        tickMark.setStartY(centerY + 16);
        tickMark.setEndX(centerX + 6);
        tickMark.setEndY(centerY + 6);
    }

    // A column's drop line from the table down to its resolved value: line + bit-width tick + arrowhead + labels
    private record ColumnDrop(Line dropLine, Line tickMark, Polyline arrow, Label bitsLabel, Label valueLabel) {
        Node[] nodes() {
            return new Node[] { dropLine, tickMark, arrow, bitsLabel, valueLabel };
        }
    }

    private ColumnDrop columnDrop(int bits, StringProperty valueProperty) {
        Line dropLine = line(0, 0, 0, 0);
        Line tickMark = tick();
        Polyline arrow = elbow();
        Label bitsLabel = bitLabel(bits, 0, 0);

        Label valueLabel = new Label();
        valueLabel.textProperty().bind(valueProperty);
        valueLabel.getStyleClass().add("mmu-bit-value");

        return new ColumnDrop(dropLine, tickMark, arrow, bitsLabel, valueLabel);
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

    // Cell for a field that sits directly adjacent to a neighboring field (e.g. Page|Word), sharing a
    // single divider border so the pair reads as one continuous box, with its title rendered separately above.
    private Region valueCell(String edgeStyleClass, StringProperty valueProperty, int hexDigits) {
        Label valueLabel = new Label("0x" + "0".repeat(hexDigits));
        valueLabel.getStyleClass().add("va-breakdown-value");
        valueLabel.setFont(FIELD_FONT);
        valueLabel.applyCss();

        double width = Math.max(MIN_FIELD_WIDTH, valueLabel.prefWidth(-1) + FIELD_PADDING);

        valueLabel.textProperty().bind(valueProperty);

        StackPane cell = new StackPane(valueLabel);
        cell.getStyleClass().addAll("va-breakdown-cell", edgeStyleClass);
        cell.setAlignment(Pos.CENTER);
        cell.setPrefSize(width, BOX_HEIGHT);
        cell.setMinSize(width, BOX_HEIGHT);
        cell.setMaxSize(width, BOX_HEIGHT);
        return cell;
    }

    private Label fieldTitle(String text, double x, double y) {
        Label label = new Label(text);
        label.getStyleClass().add("va-breakdown-title");
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
    }

    private Label sectionLabel(String text, double x, double y) {
        Label label = new Label(text);
        label.getStyleClass().add("mmu-section-label");
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
    }

    private Label bitLabel(int bits, double x, double y) {
        Label label = new Label(bits + "b");
        label.getStyleClass().add("mmu-bit-width");
        label.setLayoutX(x);
        label.setLayoutY(y);
        return label;
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

    private static double fieldWidth(int digits) {
        Text sample = new Text("F".repeat(Math.max(digits, 1)));
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

    private static int hexDigits(int bits) {
        return Math.max(1, Math.ceilDiv(bits, 4)) + 2;
    }

    // Exact digit count (no safety buffer), matching PagedMmuTabViewModel's own hex formatting exactly -
    // safe to use as-is here since valueCell measures the real rendered label instead of estimating.
    private static int exactHexDigits(int bits) {
        return Math.max(1, Math.ceilDiv(bits, 4));
    }
}
