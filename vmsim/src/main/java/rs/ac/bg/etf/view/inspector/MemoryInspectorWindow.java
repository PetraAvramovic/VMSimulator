package rs.ac.bg.etf.view.inspector;

import java.util.List;

import javafx.beans.property.IntegerProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.view.util.WindowedTableColumn;
import rs.ac.bg.etf.view.util.WindowedTableView;

/**
 * A separate, resizable window for browsing the whole of physical memory (up to
 * {@code 2^physicalAddressBits} words -- the Memory tab's own schematic only ever shows a handful
 * of rows around the addressed word). One instance is built lazily and reused across opens/closes
 * (see {@link #toggle}), mirroring {@code TLBInspectorWindow}'s no-picker branch exactly: memory
 * has exactly one row source, so it's built once at construction time, never swapped.
 *
 * <p>Stays live while open: the underlying {@code Memory} is the same mutable instance the
 * simulation itself updates, so re-pulling the currently visible window's data on every step
 * (driven by {@code currentStepNumber}) is all that's needed to keep it current. Unlike the
 * page-table/TLB inspectors, opening seeks the window to whatever address the schematic was
 * centred on at click time, rather than always starting at address 0.
 */
public class MemoryInspectorWindow
{
    private static final double ROOT_PADDING = 12;

    private final PageSimulationContext context;

    private Stage stage;
    private WindowedTableView<MemoryRow> tableView;

    public MemoryInspectorWindow(PageSimulationContext context, IntegerProperty currentStepNumber)
    {
        this.context = context;
        currentStepNumber.addListener((o, ov, nv) -> {
            if (tableView != null)
                tableView.refresh();
        });
    }

    /** Opens the window seeked to {@code seedAddress}, or closes it if already open -- a second
     *  click on the schematic's memory table toggles it shut. */
    public void toggle(Window owner, long seedAddress)
    {
        if (stage != null && stage.isShowing())
        {
            stage.hide();
            return;
        }
        if (stage == null)
            stage = build(owner);
        tableView.centerOn(seedAddress);
        stage.show();
        stage.toFront();
    }

    private Stage build(Window owner)
    {
        tableView = new WindowedTableView<>(memoryColumns(), "address",
                row -> row.locked() ? "page-table-row-locked" : null);
        tableView.setRowSource(new MemoryRowSource(context));
        VBox.setVgrow(tableView, Priority.ALWAYS);

        VBox root = new VBox(8, tableView);
        root.getStyleClass().add("page-table-inspector");
        root.setPadding(new Insets(ROOT_PADDING));

        Stage s = new Stage();
        s.initModality(Modality.NONE);
        if (owner != null)
            s.initOwner(owner);
        s.setTitle("Memory Inspector");

        Scene scene = new Scene(root, 360, 420);
        scene.getStylesheets().add(getClass().getResource("/rs/ac/bg/etf/light-theme.css").toExternalForm());
        s.setScene(scene);

        s.setMinWidth(tableView.minimumWidth() + 2 * ROOT_PADDING);
        s.setMinHeight(tableView.minimumHeight() + 2 * ROOT_PADDING);

        return s;
    }

    // Column widths/hex digits mirror exactly how the schematic's own MemoryTableView sizes the
    // same Address/Value columns.
    private List<WindowedTableColumn<MemoryRow>> memoryColumns()
    {
        int addressDigits = ValueConverter.hexDigitsFor(context.getPhysicalAddressBits());
        int valueDigits = ValueConverter.hexDigitsFor(context.getAddressableUnit() * 8);

        return List.of(
                new WindowedTableColumn<>("Address", WidthCalculator.columnWidth("Address", addressDigits),
                        row -> ValueConverter.toHex(row.address(), addressDigits)),
                new WindowedTableColumn<>("Value", WidthCalculator.columnWidth("Value", valueDigits),
                        row -> ValueConverter.toHex(row.value(), valueDigits)));
    }
}
