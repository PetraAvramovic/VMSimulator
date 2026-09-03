package rs.ac.bg.etf.view;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.effect.BoxBlur;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel.Row;

import java.util.ArrayList;
import java.util.List;


/**
 * Renders a windowed page table grid; exposes the highlighted row's node so a connector line can target it.
 */
public class PageTableView extends VBox {
    private static final double CELL_PADDING = 16;
    private static final Font CELL_FONT = Font.font("Consolas", 13);
    private static final Font HEADER_FONT = Font.font("Consolas", javafx.scene.text.FontWeight.BOLD, 12);

    private final PagedMMUTabViewModel viewModel;
    private final VBox rowsContainer = new VBox(2);
    private final ObjectProperty<Region> currentEntryAnchor = new SimpleObjectProperty<>();
    private final ObjectProperty<Region> blockColumnAnchor = new SimpleObjectProperty<>();
    private final ObjectProperty<Region> validColumnAnchor = new SimpleObjectProperty<>();
    private final ObjectProperty<Region> dirtyColumnAnchor = new SimpleObjectProperty<>();
    private final ObjectProperty<Region> diskColumnAnchor = new SimpleObjectProperty<>();

    // Permanent cache holding exactly 7 reusable row view components
    private final List<StaticRowContainer> recycledRowsPool = new ArrayList<>();

    public PageTableView(PagedMMUTabViewModel viewModel) {
        this.viewModel = viewModel;
        getStyleClass().add("page-table-view");

        getChildren().addAll(buildHeaderRow(), rowsContainer);

        // 1. Initialize the 7 permanent graphic row tracks exactly once
        initializeRecycledRowsPool();

        // 2. Simply refresh text and styles when view model properties change
        viewModel.getVisibleRows().addListener((ListChangeListener<Row>) change -> updateRowData());
        viewModel.blockHexDigitsProperty().addListener((obs, oldVal, newVal) -> updateRowData());
        viewModel.diskHexDigitsProperty().addListener((obs, oldVal, newVal) -> updateRowData());
        viewModel.pageTableAccessedProperty().addListener((obs, oldVal, accessed) -> updateFog(accessed));

        updateRowData();
        updateFog(viewModel.pageTableAccessedProperty().get());
    }

    public ObjectProperty<Region> currentEntryAnchorProperty() {
        return currentEntryAnchor;
    }

    public ObjectProperty<Region> blockColumnAnchorProperty() {
        return blockColumnAnchor;
    }

    public ObjectProperty<Region> validColumnAnchorProperty() {
        return validColumnAnchor;
    }

    public ObjectProperty<Region> dirtyColumnAnchorProperty() {
        return dirtyColumnAnchor;
    }

    public ObjectProperty<Region> diskColumnAnchorProperty() {
        return diskColumnAnchor;
    }

    private HBox buildHeaderRow() {
        HBox header = new HBox();
        header.getStyleClass().add("page-table-header");
        Label validHeaderCell = cell("V", 30);
        Label dirtyHeaderCell = cell("D", 30);
        Label blockHeaderCell = cell("Block", columnWidth("Block", viewModel.blockHexDigitsProperty().get()));
        Label diskHeaderCell = cell("Disk", columnWidth("Disk", viewModel.diskHexDigitsProperty().get()));
        header.getChildren().addAll(
                cell("Index", 60),
                validHeaderCell,
                dirtyHeaderCell,
                blockHeaderCell,
                diskHeaderCell);
        validColumnAnchor.set(validHeaderCell);
        dirtyColumnAnchor.set(dirtyHeaderCell);
        blockColumnAnchor.set(blockHeaderCell);
        diskColumnAnchor.set(diskHeaderCell);
        return header;
    }

    /**
     * Allocates the exact number of rows needed for your sliding window size.
     * They stay attached to the parent container permanently.
     */
    private void initializeRecycledRowsPool() {
        for (int i = 0; i < PagedMMUTabViewModel.WINDOW_SIZE; i++) {
            HBox rowBox = new HBox();
            rowBox.getStyleClass().add("page-table-row");

            Label indexCell = cell("", 60);
            Label validCell = cell("", 30);
            Label dirtyCell = cell("", 30);
            Label blockCell = cell("", 80); // Placeholder width, will adjust dynamically
            Label diskCell  = cell("", 140); // Placeholder width, will adjust dynamically

            rowBox.getChildren().addAll(indexCell, validCell, dirtyCell, blockCell, diskCell);
            rowsContainer.getChildren().add(rowBox);

            recycledRowsPool.add(new StaticRowContainer(rowBox, indexCell, validCell, dirtyCell, blockCell, diskCell));
        }
    }

    /**
     * REFINED COMPONENT RECYCLER
     * Updates labels and toggles styles cleanly without re-inserting nodes.
     */
    private void updateRowData() {
        double blockWidth = columnWidth("Block", viewModel.blockHexDigitsProperty().get());
        double diskWidth = columnWidth("Disk", viewModel.diskHexDigitsProperty().get());

        List<Row> rowsDataList = viewModel.getVisibleRows();
        boolean isTableAccessed = viewModel.pageTableAccessedProperty().get();
        Region middleAnchorNode = null;
        Region resolvedActiveAnchorNode = null;

        for (int i = 0; i < recycledRowsPool.size(); i++) {
            StaticRowContainer rowComponent = recycledRowsPool.get(i);
            
            // Clear prior styling states safely
            rowComponent.rootRowBox().getStyleClass().remove("page-table-row-current");

            if (i < rowsDataList.size()) {
                Row rowData = rowsDataList.get(i);
                rowComponent.rootRowBox().setVisible(true);

                // Update text contents inline without breaking scene graph nodes
                rowComponent.indexLabel().setText(Long.toString(rowData.page()));
                rowComponent.validLabel().setText(rowData.valid() ? "1" : "0");
                rowComponent.dirtyLabel().setText(rowData.dirty() ? "1" : "0");
                
                rowComponent.blockLabel().setText(toHex(rowData.block(), viewModel.blockHexDigitsProperty().get()));
                rowComponent.blockLabel().setPrefWidth(blockWidth);
                
                rowComponent.diskLabel().setText(toHex(rowData.disk(), viewModel.diskHexDigitsProperty().get()));
                rowComponent.diskLabel().setPrefWidth(diskWidth);

                if (rowData.current() && isTableAccessed) {
                    rowComponent.rootRowBox().getStyleClass().add("page-table-row-current");
                    resolvedActiveAnchorNode = rowComponent.rootRowBox();
                }
            } else {
                // Hide trailing fallback rows if simulation window is smaller than capacity
                rowComponent.rootRowBox().setVisible(false);
            }

            if (i == recycledRowsPool.size() / 2) {
                middleAnchorNode = rowComponent.rootRowBox();
            }
        }

        // =========================================================================
        // PURE ARCHITECTURAL FALLBACK
        // Seamlessly swaps anchors instantly with zero layout positioning drops!
        // =========================================================================
        if (isTableAccessed && resolvedActiveAnchorNode != null) {
            currentEntryAnchor.set(resolvedActiveAnchorNode);
        } else {
            currentEntryAnchor.set(middleAnchorNode);
        }
    }

    private Label cell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("page-table-cell");
        label.setPrefWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private void updateFog(boolean accessed) {
        rowsContainer.setEffect(accessed ? null : new BoxBlur(6, 6, 3));
        rowsContainer.setOpacity(accessed ? 1.0 : 0.45);
    }

    private static String toHex(long value, int digits) {
        return String.format("0x%0" + digits + "X", value);
    }

    private static double columnWidth(String header, int digits) {
        Text valueSample = new Text("F".repeat(Math.max(digits + 2, 1)));
        valueSample.setFont(CELL_FONT);
        Text headerSample = new Text(header);
        headerSample.setFont(HEADER_FONT);
        double widest = Math.max(valueSample.getLayoutBounds().getWidth(), headerSample.getLayoutBounds().getWidth());
        return widest + CELL_PADDING;
    }

    /**
     * Small structural helper record to store your 7 permanent label handles.
     */
    private record StaticRowContainer(
            HBox rootRowBox,
            Label indexLabel,
            Label validLabel,
            Label dirtyLabel,
            Label blockLabel,
            Label diskLabel
    ) {}
}
