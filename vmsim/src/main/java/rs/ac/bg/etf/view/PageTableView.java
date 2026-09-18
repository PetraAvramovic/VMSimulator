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
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel.Row;

import java.util.ArrayList;
import java.util.List;


/**
 * Renders a windowed page table grid; exposes the highlighted row's node so a connector line can target it.
 */
public class PageTableView extends VBox {
    // Drawn noticeably larger than the shared page-table-cell base (used by the OS/inspector tables)
    // since this is the MMU schematic's own centrepiece -- scoped via the "page-table-inline" marker
    // class in CSS (.page-table-inline .page-table-cell), not a change to the shared style. Shared
    // with the TLB schematic's own row tables (see WidthCalculator's own doc comment) so both read
    // as exactly the same size.
    private static final double CELL_FONT_SIZE = WidthCalculator.LARGE_CELL_FONT_SIZE;
    private static final double INDEX_COL_WIDTH = WidthCalculator.LARGE_INDEX_COL_WIDTH;
    private static final double BIT_COL_WIDTH = WidthCalculator.LARGE_BIT_COL_WIDTH; // V / D columns

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
        getStyleClass().addAll("page-table-view", "page-table-inline");
        rowsContainer.getStyleClass().add("page-table-rows");

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
        Label validHeaderCell = cell("V", BIT_COL_WIDTH);
        Label dirtyHeaderCell = cell("D", BIT_COL_WIDTH);
        Label blockHeaderCell = cell("Block", WidthCalculator.columnWidth("Block", viewModel.blockHexDigitsProperty().get(), CELL_FONT_SIZE));
        Label diskHeaderCell = cell("Disk", WidthCalculator.columnWidth("Disk", viewModel.diskHexDigitsProperty().get(), CELL_FONT_SIZE));
        header.getChildren().addAll(
                cell("Index", INDEX_COL_WIDTH),
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

            Label indexCell = cell("", INDEX_COL_WIDTH);
            Label validCell = cell("", BIT_COL_WIDTH);
            Label dirtyCell = cell("", BIT_COL_WIDTH);
            Label blockCell = cell("", 100); // Placeholder width, will adjust dynamically
            Label diskCell  = cell("", 175); // Placeholder width, will adjust dynamically

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
        double blockWidth = WidthCalculator.columnWidth("Block", viewModel.blockHexDigitsProperty().get(), CELL_FONT_SIZE);
        double diskWidth = WidthCalculator.columnWidth("Disk", viewModel.diskHexDigitsProperty().get(), CELL_FONT_SIZE);

        List<Row> rowsDataList = viewModel.getVisibleRows();
        boolean isTableAccessed = viewModel.pageTableAccessedProperty().get();
        Region middleAnchorNode = null;
        Region resolvedActiveAnchorNode = null;
        int resolvedActiveIndex = -1;

        for (int i = 0; i < recycledRowsPool.size(); i++) {
            StaticRowContainer rowComponent = recycledRowsPool.get(i);

            // Clear prior styling states safely
            rowComponent.rootRowBox().getStyleClass().remove("page-table-row-current");
            rowComponent.rootRowBox().getStyleClass().remove("page-table-row-before-current");

            if (i < rowsDataList.size()) {
                Row rowData = rowsDataList.get(i);
                rowComponent.rootRowBox().setVisible(true);

                // Update text contents inline without breaking scene graph nodes
                rowComponent.indexLabel().setText(Long.toString(rowData.page()));
                rowComponent.validLabel().setText(rowData.valid() ? "1" : "0");
                rowComponent.dirtyLabel().setText(rowData.dirty() ? "1" : "0");
                
                rowComponent.blockLabel().setText(ValueConverter.toHex(rowData.block(), viewModel.blockHexDigitsProperty().get()));
                rowComponent.blockLabel().setPrefWidth(blockWidth);
                
                rowComponent.diskLabel().setText(ValueConverter.toHex(rowData.disk(), viewModel.diskHexDigitsProperty().get()));
                rowComponent.diskLabel().setPrefWidth(diskWidth);

                if (rowData.current() && isTableAccessed) {
                    rowComponent.rootRowBox().getStyleClass().add("page-table-row-current");
                    resolvedActiveAnchorNode = rowComponent.rootRowBox();
                    resolvedActiveIndex = i;
                }
            } else {
                // Hide trailing fallback rows if simulation window is smaller than capacity
                rowComponent.rootRowBox().setVisible(false);
            }

            if (i == recycledRowsPool.size() / 2) {
                middleAnchorNode = rowComponent.rootRowBox();
            }
        }

        // The row directly above the highlight still draws its own plain bottom divider (every row
        // does), which sits right against the highlight's top border and reads as a stray line
        // poking out of it. Suppressing just that one row's divider lets the highlight's own border
        // stand alone on that edge, same as it already does on the other three.
        if (resolvedActiveIndex > 0) {
            recycledRowsPool.get(resolvedActiveIndex - 1).rootRowBox().getStyleClass().add("page-table-row-before-current");
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
