package rs.ac.bg.etf.view.config;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.converter.LongStringConverter;

import rs.ac.bg.etf.view.util.HexLongConverter;
import rs.ac.bg.etf.viewmodel.MemoryInitEntry;

/**
 * Editor window for the config screen's "Initial Memory Content" section: word values pre-loaded
 * at specific offsets of a user's page. Only takes effect for a page whose Page Table entry is
 * valid (see {@link PageTablesEditorWindow}) -- content for an invalid page seeds the disk instead,
 * per {@code SimulationConfig.getDiskInitContent}. Lazily built and toggled open/closed like the
 * read-only inspector windows, but editable; see {@link InstructionsEditorWindow} for the shared
 * pattern.
 */
public class MemoryInitEditorWindow
{
    private final ObservableList<MemoryInitEntry> entries;

    private Stage stage;

    public MemoryInitEditorWindow(ObservableList<MemoryInitEntry> entries)
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
        TableView<MemoryInitEntry> table = new TableView<>();

        TableColumn<MemoryInitEntry, Integer> userCol = new TableColumn<>("User");
        userCol.setPrefWidth(60);
        userCol.setCellValueFactory(data -> data.getValue().userIdProperty().asObject());
        userCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        userCol.setOnEditCommit(e -> e.getRowValue().userIdProperty().set(e.getNewValue()));

        TableColumn<MemoryInitEntry, Long> pageCol = new TableColumn<>("Page");
        pageCol.setPrefWidth(90);
        pageCol.setCellValueFactory(data -> data.getValue().pageProperty().asObject());
        pageCol.setCellFactory(TextFieldTableCell.forTableColumn(new LongStringConverter()));
        pageCol.setOnEditCommit(e -> e.getRowValue().pageProperty().set(e.getNewValue()));

        TableColumn<MemoryInitEntry, Long> offsetCol = new TableColumn<>("Offset");
        offsetCol.setPrefWidth(110);
        offsetCol.setCellValueFactory(data -> data.getValue().offsetProperty().asObject());
        offsetCol.setCellFactory(TextFieldTableCell.forTableColumn(new LongStringConverter()));
        offsetCol.setOnEditCommit(e -> e.getRowValue().offsetProperty().set(e.getNewValue()));

        TableColumn<MemoryInitEntry, Long> valueCol = new TableColumn<>("Value");
        valueCol.setPrefWidth(150);
        valueCol.setCellValueFactory(data -> data.getValue().valueProperty().asObject());
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn(new HexLongConverter()));
        valueCol.setOnEditCommit(e -> e.getRowValue().valueProperty().set(e.getNewValue()));

        table.getColumns().addAll(List.of(userCol, pageCol, offsetCol, valueCol));
        table.getColumns().add(ConfigEditorWindowSupport.deleteColumn(entries));

        return ConfigEditorWindowSupport.build(
                owner,
                "Initial Memory Content Editor",
                "Initial Memory Content",
                "Word values pre-loaded at specific offsets of a user's page (applied only if that page's Page Table entry is valid).",
                table,
                entries,
                MemoryInitEntry::new);
    }
}
