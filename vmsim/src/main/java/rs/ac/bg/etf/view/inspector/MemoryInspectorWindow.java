package rs.ac.bg.etf.view.inspector;

import rs.ac.bg.etf.view.util.UiScale;
import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.beans.property.IntegerProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.view.util.InspectorWindows;
import rs.ac.bg.etf.view.util.LockedRow;
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
    private final ChangeListener<Number> refreshOnStep;

    // Design-size; scaled where it is used, inside build() (see InspectorWindows for the scale).
    private static final double ROOT_PADDING = 12;

    private final PageSimulationContext context;

    private Stage stage;
    private WindowedTableView<MemoryRow> tableView;

    public MemoryInspectorWindow(PageSimulationContext context, IntegerProperty currentStepNumber)
    {
        this.context = context;
        // Registered weakly on the (long-lived) simulation step property so this window can be garbage
        // collected once the tab view that created it has been rebuilt for a new UI scale (see
        // UiScale); this field is what keeps the listener alive for as long as the window itself is.
        refreshOnStep = (o, ov, nv) -> {
            if (tableView != null)
                tableView.refresh();
        };
        currentStepNumber.addListener(new WeakChangeListener<>(refreshOnStep));
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
            stage = InspectorWindows.build(() -> build(owner));
        tableView.centerOn(seedAddress);
        stage.show();
        stage.toFront();
    }

    private Stage build(Window owner)
    {
        tableView = new WindowedTableView<>(memoryColumns(), "address",
                row -> row.locked() ? LockedRow.STYLE_CLASS : null,
                row -> row.locked() ? LockedRow.TOOLTIP : null);
        tableView.setRowSource(new MemoryRowSource(context));
        VBox.setVgrow(tableView, Priority.ALWAYS);

        double rootPadding = UiScale.px(ROOT_PADDING);
        VBox root = new VBox(UiScale.px(8), tableView);
        root.getStyleClass().add("inspector-root");
        root.setPadding(new Insets(rootPadding));

        Stage s = new Stage();
        s.initModality(Modality.NONE);
        if (owner != null)
            s.initOwner(owner);
        s.setTitle("Memory Inspector");

        Scene scene = new Scene(root, UiScale.px(360), UiScale.px(420));
        UiScale.applyTheme(scene);
        s.setScene(scene);

        s.setMinWidth(tableView.minimumWidth() + 2 * rootPadding);
        s.setMinHeight(tableView.minimumHeight() + 2 * rootPadding);

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
