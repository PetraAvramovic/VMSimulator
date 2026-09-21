package rs.ac.bg.etf.view.inspector;

import rs.ac.bg.etf.view.util.UiScale;
import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.beans.property.IntegerProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.view.util.InspectorWindows;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.view.util.WindowedTableColumn;
import rs.ac.bg.etf.view.util.WindowedTableView;

/**
 * A separate, resizable window for browsing one user's full page table (up to {@code 2^pageBits}
 * entries -- the small table drawn directly in the MMU schematic only ever shows a handful of
 * rows around the addressed page). One instance is built lazily and reused across opens/closes
 * (see {@link #toggle}), mirroring {@code MemoryInspectorWindow}'s pattern. Each row's Disk cell
 * is itself clickable, opening a {@link DiskBlockInspectorWindow} on that page's backing block.
 *
 * <p>Stays live while open: the underlying {@code PageTable} objects are the same mutable
 * instances the simulation itself updates, so re-pulling the currently visible window's data on
 * every step (driven by {@code currentStepNumber}) is all that's needed to keep it current.
 */
public class PageTableInspectorWindow
{
    private final ChangeListener<Number> refreshOnStep;

    // Design-size; scaled where they are used, inside build() (see InspectorWindows for the scale).
    private static final double PICKER_ROW_HEIGHT = 36;
    private static final double ROOT_PADDING = 12;

    private final PageSimulationContext context;
    private final DiskBlockInspectorWindow diskInspector;

    private Stage stage;
    private ComboBox<Integer> userPicker;
    private WindowedTableView<PageTableRow> tableView;

    public PageTableInspectorWindow(PageSimulationContext context, IntegerProperty currentStepNumber)
    {
        this.context = context;
        this.diskInspector = new DiskBlockInspectorWindow(context, currentStepNumber);
        // Registered weakly on the (long-lived) simulation step property so this window can be garbage
        // collected once the tab view that created it has been rebuilt for a new UI scale (see
        // UiScale); this field is what keeps the listener alive for as long as the window itself is.
        refreshOnStep = (o, ov, nv) -> {
            if (tableView != null)
                tableView.refresh();
        };
        currentStepNumber.addListener(new WeakChangeListener<>(refreshOnStep));
    }

    /** Opens the window (defaulting the user picker to the current instruction's user), or closes
     *  it if already open -- a second click on the schematic's page table toggles it shut. */
    public void toggle(Window owner)
    {
        if (stage != null && stage.isShowing())
        {
            stage.hide();
            return;
        }
        if (stage == null)
            stage = InspectorWindows.build(() -> build(owner));
        userPicker.getSelectionModel().select(Integer.valueOf(defaultUser()));
        stage.show();
        stage.toFront();
    }

    private int defaultUser()
    {
        return context.hasCurrentInstruction() ? context.getCurrentInstruction().getUser() : 0;
    }

    private Stage build(Window owner)
    {
        // Built before the table/columns so the Disk column's onClick (below) can open the disk
        // inspector owned by this window, rather than by whatever owns this one.
        Stage s = new Stage();
        s.initModality(Modality.NONE);
        if (owner != null)
            s.initOwner(owner);
        s.setTitle("Page Table Inspector");

        Label userLabel = new Label("User");
        userLabel.getStyleClass().add("field-box-title");

        userPicker = new ComboBox<>();
        for (int user = 0; user < context.getNumberOfUsers(); user++)
            userPicker.getItems().add(user);
        userPicker.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null)
                selectUser(nv);
        });

        double pickerRowHeight = UiScale.px(PICKER_ROW_HEIGHT);
        double rootPadding = UiScale.px(ROOT_PADDING);

        HBox pickerBar = new HBox(UiScale.px(8), userLabel, userPicker);
        pickerBar.setAlignment(Pos.CENTER_LEFT);
        pickerBar.setMinHeight(pickerRowHeight);
        pickerBar.setPrefHeight(pickerRowHeight);

        tableView = new WindowedTableView<>(pageTableColumns(s), "page");
        VBox.setVgrow(tableView, Priority.ALWAYS);

        VBox root = new VBox(UiScale.px(8), pickerBar, tableView);
        root.getStyleClass().add("inspector-root");
        root.setPadding(new Insets(rootPadding));

        Scene scene = new Scene(root, UiScale.px(460), UiScale.px(420));
        UiScale.applyTheme(scene);
        s.setScene(scene);

        s.setMinWidth(tableView.minimumWidth() + 2 * rootPadding);
        s.setMinHeight(pickerRowHeight + tableView.minimumHeight() + 2 * rootPadding + UiScale.px(8));

        return s;
    }

    // Column widths/hex digits mirror exactly how the schematic's own small PageTableView (and
    // PagedMMUTabViewModel before it) size the same Block/Disk columns.
    private List<WindowedTableColumn<PageTableRow>> pageTableColumns(Window owner)
    {
        long maxPages = context.getMaxPages();
        int frameBits = context.getPhysicalAddressBits() - context.getWordBits();
        int blockDigits = ValueConverter.hexDigitsFor(frameBits);
        int diskDigits = ValueConverter.hexDigitsFor(context.getDiskBits());
        int indexDigits = Long.toString(Math.max(0, maxPages - 1)).length();

        return List.of(
                new WindowedTableColumn<>("Index", WidthCalculator.plainColumnWidth("Index", indexDigits),
                        row -> Long.toString(row.page())),
                new WindowedTableColumn<>("V", WidthCalculator.plainColumnWidth("V", 1),
                        row -> row.valid() ? "1" : "0"),
                new WindowedTableColumn<>("D", WidthCalculator.plainColumnWidth("D", 1),
                        row -> row.dirty() ? "1" : "0"),
                new WindowedTableColumn<>("Block", WidthCalculator.columnWidth("Block", blockDigits),
                        row -> ValueConverter.toHex(row.block(), blockDigits)),
                new WindowedTableColumn<>("Disk", WidthCalculator.columnWidth("Disk", diskDigits),
                        row -> ValueConverter.toHex(row.disk(), diskDigits),
                        row -> diskInspector.toggle(owner, row.disk())));
    }

    private void selectUser(int user)
    {
        tableView.setRowSource(new PageTableRowSource(context.getPageTable(user), context.getMaxPages()));
    }
}
