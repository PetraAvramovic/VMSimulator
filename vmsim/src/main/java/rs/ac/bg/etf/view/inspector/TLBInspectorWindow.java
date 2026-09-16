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

import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.tlb.SetAssociativeTLB;
import rs.ac.bg.etf.model.tlb.TLB;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.view.util.WindowedTableColumn;
import rs.ac.bg.etf.view.util.WindowedTableView;

/**
 * A separate, resizable window for browsing the whole TLB (up to {@code tlbSize} entries -- the
 * schematic only ever shows a handful of rows around the addressed slot/set). One instance is
 * built lazily and reused across opens/closes (see {@link #toggle}), mirroring
 * {@code PageTableInspectorWindow}'s pattern exactly.
 *
 * <p>Set-associative adds a "Way" picker -- one table per way, like the schematic's stacked
 * way-tables; every other TLB type has exactly one way, so the picker stays out of the layout
 * entirely rather than showing a single, inconsequential choice.
 *
 * <p>Stays live while open: the underlying {@link TLB} is the same mutable instance the
 * simulation itself updates, so re-pulling the currently visible window's data on every step
 * (driven by {@code currentStepNumber}) is all that's needed to keep it current.
 */
public class TLBInspectorWindow
{
    private static final double PICKER_ROW_HEIGHT = 36;
    private static final double ROOT_PADDING = 12;

    private final SimulationContext context;
    private final TLB tlb;
    private final SetAssociativeTLB setAssociativeTlb; // non-null iff set-associative

    private Stage stage;
    private ComboBox<Integer> wayPicker;
    private WindowedTableView<TLBRow> tableView;

    public TLBInspectorWindow(SimulationContext context, IntegerProperty currentStepNumber)
    {
        this.context = context;
        this.tlb = context.getTLB();
        this.setAssociativeTlb = tlb instanceof SetAssociativeTLB sat ? sat : null;
        currentStepNumber.addListener((o, ov, nv) -> {
            if (tableView != null)
                tableView.refresh();
        });
    }

    /** Opens the window, or closes it if already open -- a second click on the schematic's TLB
     *  toggles it shut. */
    public void toggle(Window owner)
    {
        if (stage != null && stage.isShowing())
        {
            stage.hide();
            return;
        }
        if (stage == null)
            stage = build(owner);
        stage.show();
        stage.toFront();
    }

    private Stage build(Window owner)
    {
        tableView = new WindowedTableView<>(tlbColumns(), "entry");
        VBox.setVgrow(tableView, Priority.ALWAYS);

        VBox root = new VBox(8);
        root.getStyleClass().add("page-table-inspector");
        root.setPadding(new Insets(ROOT_PADDING));

        double pickerHeight = 0;
        if (setAssociativeTlb != null)
        {
            Label wayLabel = new Label("Way");
            wayLabel.getStyleClass().add("va-breakdown-title");

            wayPicker = new ComboBox<>();
            for (int way = 0; way < setAssociativeTlb.getEntriesPerSet(); way++)
                wayPicker.getItems().add(way);
            wayPicker.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
                if (nv != null)
                    selectWay(nv);
            });

            HBox pickerBar = new HBox(8, wayLabel, wayPicker);
            pickerBar.setAlignment(Pos.CENTER_LEFT);
            pickerBar.setMinHeight(PICKER_ROW_HEIGHT);
            pickerBar.setPrefHeight(PICKER_ROW_HEIGHT);
            root.getChildren().addAll(pickerBar, tableView);
            pickerHeight = PICKER_ROW_HEIGHT + 8; // + the root VBox's own spacing
        }
        else
        {
            root.getChildren().add(tableView);
            tableView.setRowSource(new TLBRowSource(tlb, 0));
        }

        Stage s = new Stage();
        s.initModality(Modality.NONE);
        if (owner != null)
            s.initOwner(owner);
        s.setTitle("TLB Inspector");

        Scene scene = new Scene(root, 460, 420);
        scene.getStylesheets().add(getClass().getResource("/rs/ac/bg/etf/light-theme.css").toExternalForm());
        s.setScene(scene);

        s.setMinWidth(tableView.minimumWidth() + 2 * ROOT_PADDING);
        s.setMinHeight(pickerHeight + tableView.minimumHeight() + 2 * ROOT_PADDING + 8);

        if (setAssociativeTlb != null)
            wayPicker.getSelectionModel().select(Integer.valueOf(0));

        return s;
    }

    // Column widths/hex digits mirror exactly how the schematic (PagedTLBTabViewModel /
    // PagedTLBRowView) sizes the same Tag/Block columns.
    private List<WindowedTableColumn<TLBRow>> tlbColumns()
    {
        int indexBits = tlb.getIndexComponentBits();
        int tagBits = tlb.getProcessIdBits() + tlb.getAddressComponentBits() - indexBits;
        int frameBits = context.getPhysicalAddressBits() - context.getWordBits();
        int tagDigits = ValueConverter.hexDigitsFor(tagBits);
        int blockDigits = ValueConverter.hexDigitsFor(frameBits);
        long entryCount = setAssociativeTlb != null ? setAssociativeTlb.getNumSets() : tlb.getSize();
        int indexDigits = Long.toString(Math.max(0, entryCount - 1)).length();
        String indexHeader = setAssociativeTlb != null ? "Set" : "Index";

        return List.of(
                new WindowedTableColumn<>(indexHeader, WidthCalculator.plainColumnWidth(indexHeader, indexDigits),
                        row -> Long.toString(row.index())),
                new WindowedTableColumn<>("V", WidthCalculator.plainColumnWidth("V", 1),
                        row -> row.valid() ? "1" : "0"),
                new WindowedTableColumn<>("D", WidthCalculator.plainColumnWidth("D", 1),
                        row -> row.dirty() ? "1" : "0"),
                new WindowedTableColumn<>("Tag", WidthCalculator.columnWidth("Tag", tagDigits),
                        row -> ValueConverter.toHex(row.tag(), tagDigits)),
                new WindowedTableColumn<>("Block", WidthCalculator.columnWidth("Block", blockDigits),
                        row -> ValueConverter.toHex(row.block(), blockDigits)));
    }

    private void selectWay(int way)
    {
        tableView.setRowSource(new TLBRowSource(tlb, way));
    }
}
