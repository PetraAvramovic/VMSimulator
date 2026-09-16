package rs.ac.bg.etf.view.inspector;

import java.util.List;

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
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.view.util.WindowedTableColumn;
import rs.ac.bg.etf.view.util.WindowedTableView;

/**
 * A separate, resizable window for browsing one user's full page table (up to {@code 2^pageBits}
 * entries -- the small table drawn directly in the MMU schematic only ever shows a handful of
 * rows around the addressed page). One instance is built lazily and reused across opens/closes
 * (see {@link #toggle}), mirroring {@code DiskBlockPopup}'s pattern.
 *
 * <p>Stays live while open: the underlying {@code PageTable} objects are the same mutable
 * instances the simulation itself updates, so re-pulling the currently visible window's data on
 * every step (driven by {@code currentStepNumber}) is all that's needed to keep it current.
 */
public class PageTableInspectorWindow
{
    private static final double PICKER_ROW_HEIGHT = 36;
    private static final double ROOT_PADDING = 12;

    private final PageSimulationContext context;

    private Stage stage;
    private ComboBox<Integer> userPicker;
    private WindowedTableView<PageTableRow> tableView;

    public PageTableInspectorWindow(PageSimulationContext context, IntegerProperty currentStepNumber)
    {
        this.context = context;
        currentStepNumber.addListener((o, ov, nv) -> {
            if (tableView != null)
                tableView.refresh();
        });
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
            stage = build(owner);
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
        Label userLabel = new Label("User");
        userLabel.getStyleClass().add("va-breakdown-title");

        userPicker = new ComboBox<>();
        for (int user = 0; user < context.getNumberOfUsers(); user++)
            userPicker.getItems().add(user);
        userPicker.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
            if (nv != null)
                selectUser(nv);
        });

        HBox pickerBar = new HBox(8, userLabel, userPicker);
        pickerBar.setAlignment(Pos.CENTER_LEFT);
        pickerBar.setMinHeight(PICKER_ROW_HEIGHT);
        pickerBar.setPrefHeight(PICKER_ROW_HEIGHT);

        tableView = new WindowedTableView<>(pageTableColumns(), "page");
        VBox.setVgrow(tableView, Priority.ALWAYS);

        VBox root = new VBox(8, pickerBar, tableView);
        root.getStyleClass().add("page-table-inspector");
        root.setPadding(new Insets(ROOT_PADDING));

        Stage s = new Stage();
        s.initModality(Modality.NONE);
        if (owner != null)
            s.initOwner(owner);
        s.setTitle("Page Table Inspector");

        Scene scene = new Scene(root, 460, 420);
        scene.getStylesheets().add(getClass().getResource("/rs/ac/bg/etf/light-theme.css").toExternalForm());
        s.setScene(scene);

        s.setMinWidth(tableView.minimumWidth() + 2 * ROOT_PADDING);
        s.setMinHeight(PICKER_ROW_HEIGHT + tableView.minimumHeight() + 2 * ROOT_PADDING + 8);

        return s;
    }

    // Column widths/hex digits mirror exactly how the schematic's own small PageTableView (and
    // PagedMMUTabViewModel before it) size the same Block/Disk columns.
    private List<WindowedTableColumn<PageTableRow>> pageTableColumns()
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
                        row -> ValueConverter.toHex(row.disk(), diskDigits)));
    }

    private void selectUser(int user)
    {
        tableView.setRowSource(new PageTableRowSource(context.getPageTable(user), context.getMaxPages()));
    }
}
