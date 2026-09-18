package rs.ac.bg.etf.view.config;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.converter.LongStringConverter;

import rs.ac.bg.etf.view.util.HexLongConverter;
import rs.ac.bg.etf.viewmodel.PageTableEntry;

/**
 * Editor window for the config screen's "Page Table Entries" section: the initial per-user page
 * table descriptors (valid/dirty/block) the simulation starts from -- everything else defaults to
 * an invalid (unmapped) page. Lazily built and toggled open/closed like the read-only inspector
 * windows, but editable; see {@link InstructionsEditorWindow} for the shared pattern.
 */
public class PageTablesEditorWindow
{
    private final ObservableList<PageTableEntry> entries;

    private Stage stage;

    public PageTablesEditorWindow(ObservableList<PageTableEntry> entries)
    {
        this.entries = entries;
    }

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
        TableView<PageTableEntry> table = new TableView<>();

        TableColumn<PageTableEntry, Integer> userCol = new TableColumn<>("User");
        userCol.setPrefWidth(60);
        userCol.setCellValueFactory(data -> data.getValue().userIdProperty().asObject());
        userCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        userCol.setOnEditCommit(e -> e.getRowValue().userIdProperty().set(e.getNewValue()));

        TableColumn<PageTableEntry, Long> pageCol = new TableColumn<>("Page");
        pageCol.setPrefWidth(100);
        pageCol.setCellValueFactory(data -> data.getValue().pageProperty().asObject());
        pageCol.setCellFactory(TextFieldTableCell.forTableColumn(new LongStringConverter()));
        pageCol.setOnEditCommit(e -> e.getRowValue().pageProperty().set(e.getNewValue()));

        TableColumn<PageTableEntry, Boolean> validCol = new TableColumn<>("Valid");
        validCol.setPrefWidth(60);
        validCol.setCellValueFactory(data -> data.getValue().validProperty());
        validCol.setCellFactory(CheckBoxTableCell.forTableColumn(index -> table.getItems().get(index).validProperty()));

        TableColumn<PageTableEntry, Boolean> dirtyCol = new TableColumn<>("Dirty");
        dirtyCol.setPrefWidth(60);
        dirtyCol.setCellValueFactory(data -> data.getValue().dirtyProperty());
        dirtyCol.setCellFactory(CheckBoxTableCell.forTableColumn(index -> table.getItems().get(index).dirtyProperty()));

        TableColumn<PageTableEntry, Long> blockCol = new TableColumn<>("Block");
        blockCol.setPrefWidth(130);
        blockCol.setCellValueFactory(data -> data.getValue().blockProperty().asObject());
        blockCol.setCellFactory(TextFieldTableCell.forTableColumn(new HexLongConverter()));
        blockCol.setOnEditCommit(e -> e.getRowValue().blockProperty().set(e.getNewValue()));

        table.getColumns().addAll(List.of(userCol, pageCol, validCol, dirtyCol, blockCol));
        table.getColumns().add(ConfigEditorWindowSupport.deleteColumn(entries));

        return ConfigEditorWindowSupport.build(
                owner,
                "Page Table Editor",
                "Page Table Entries",
                "Initial per-user page table descriptors. A page left out of this list starts invalid (unmapped).",
                table,
                entries,
                PageTableEntry::new);
    }
}
