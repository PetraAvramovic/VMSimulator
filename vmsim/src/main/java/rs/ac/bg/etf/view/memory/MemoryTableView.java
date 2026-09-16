package rs.ac.bg.etf.view.memory;

import java.util.ArrayList;
import java.util.List;

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
import rs.ac.bg.etf.viewmodel.MemoryTabViewModel;
import rs.ac.bg.etf.viewmodel.MemoryTabViewModel.Row;

/**
 * Renders a small (WINDOW_SIZE-row) window of physical memory around the addressed word; exposes
 * the highlighted row's node so a connector line can target it. Mirrors {@code PageTableView}
 * exactly, minus the V/D/Block/Disk columns -- just Address and Value here.
 */
public class MemoryTableView extends VBox
{
    private final MemoryTabViewModel viewModel;
    private final VBox rowsContainer = new VBox(2);
    private final ObjectProperty<Region> currentEntryAnchor = new SimpleObjectProperty<>();
    private final List<RowNode> pool = new ArrayList<>();

    public MemoryTableView(MemoryTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        getStyleClass().add("page-table-view");

        getChildren().addAll(buildHeaderRow(), rowsContainer);

        for (int i = 0; i < MemoryTabViewModel.WINDOW_SIZE; i++)
        {
            RowNode row = new RowNode();
            pool.add(row);
            rowsContainer.getChildren().add(row.box);
        }

        viewModel.getVisibleRows().addListener((ListChangeListener<Row>) change -> updateRowData());
        viewModel.memoryAddressedProperty().addListener((obs, oldVal, addressed) -> updateFog(addressed));

        updateRowData();
        updateFog(viewModel.memoryAddressedProperty().get());
    }

    public ObjectProperty<Region> currentEntryAnchorProperty() { return currentEntryAnchor; }

    private double addressWidth() { return WidthCalculator.columnWidth("Address", ValueConverter.hexDigitsFor(viewModel.getPhysicalAddressBits())); }
    private double valueWidth() { return WidthCalculator.columnWidth("Value", viewModel.getValueHexDigits()); }

    private HBox buildHeaderRow()
    {
        HBox header = new HBox();
        header.getStyleClass().add("page-table-header");
        header.getChildren().addAll(cell("Address", addressWidth()), cell("Value", valueWidth()));
        return header;
    }

    private void updateRowData()
    {
        List<Row> rows = viewModel.getVisibleRows();
        Region middleAnchor = null;
        Region activeAnchor = null;

        for (int i = 0; i < pool.size(); i++)
        {
            RowNode rowNode = pool.get(i);
            rowNode.box.getStyleClass().removeAll("page-table-row-current", "page-table-row-locked");

            if (i < rows.size())
            {
                Row row = rows.get(i);
                rowNode.box.setVisible(true);

                rowNode.addressLabel.setText(ValueConverter.toHex(row.address(), ValueConverter.hexDigitsFor(viewModel.getPhysicalAddressBits())));
                rowNode.valueLabel.setText(ValueConverter.toHex(row.value(), viewModel.getValueHexDigits()));

                if (row.current())
                    rowNode.box.getStyleClass().add("page-table-row-current");
                else if (row.locked())
                    rowNode.box.getStyleClass().add("page-table-row-locked");
            }
            else
            {
                rowNode.box.setVisible(false);
            }

            if (i == pool.size() / 2)
                middleAnchor = rowNode.box;
            if (i < rows.size() && rows.get(i).current())
                activeAnchor = rowNode.box;
        }

        currentEntryAnchor.set(activeAnchor != null ? activeAnchor : middleAnchor);
    }

    private void updateFog(boolean accessed)
    {
        rowsContainer.setEffect(accessed ? null : new BoxBlur(6, 6, 3));
        rowsContainer.setOpacity(accessed ? 1.0 : 0.45);
    }

    private Label cell(String text, double width)
    {
        Label label = new Label(text);
        label.getStyleClass().add("page-table-cell");
        label.setPrefWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private final class RowNode
    {
        final HBox box = new HBox();
        final Label addressLabel = cell("", addressWidth());
        final Label valueLabel = cell("", valueWidth());

        RowNode()
        {
            box.getStyleClass().add("page-table-row");
            box.getChildren().addAll(addressLabel, valueLabel);
        }
    }
}
