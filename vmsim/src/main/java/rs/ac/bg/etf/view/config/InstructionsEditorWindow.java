package rs.ac.bg.etf.view.config;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.converter.IntegerStringConverter;

import rs.ac.bg.etf.model.memory.Instruction.AccessType;
import rs.ac.bg.etf.view.util.HexLongConverter;
import rs.ac.bg.etf.viewmodel.InstructionEntry;

/**
 * Editor window for the config screen's "Program" section: the ordered RD/WR/EX instruction list
 * the simulation executes. Lazily built and toggled open/closed exactly like the read-only
 * inspector windows (see {@code view/inspector/MemoryInspectorWindow}), but every cell here is
 * editable and writes straight back into the {@link InstructionEntry} rows the config screen's
 * viewmodel owns -- there's no separate "save" step.
 */
public class InstructionsEditorWindow
{
    private final ObservableList<InstructionEntry> instructions;

    private Stage stage;

    public InstructionsEditorWindow(ObservableList<InstructionEntry> instructions)
    {
        this.instructions = instructions;
    }

    /** Opens the window, or closes it if already open -- a second click on the launching button
     *  toggles it shut, matching the inspector windows' toggle pattern. */
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
        TableView<InstructionEntry> table = new TableView<>();

        TableColumn<InstructionEntry, Integer> userCol = new TableColumn<>("User");
        userCol.setPrefWidth(60);
        userCol.setCellValueFactory(data -> data.getValue().userProperty().asObject());
        userCol.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        userCol.setOnEditCommit(e -> e.getRowValue().userProperty().set(e.getNewValue()));

        TableColumn<InstructionEntry, AccessType> accessTypeCol = new TableColumn<>("Access");
        accessTypeCol.setPrefWidth(90);
        accessTypeCol.setCellValueFactory(data -> data.getValue().accessTypeProperty());
        accessTypeCol.setCellFactory(ComboBoxTableCell.<InstructionEntry, AccessType>forTableColumn(AccessType.values()));

        TableColumn<InstructionEntry, Long> vaCol = new TableColumn<>("Virtual Address");
        vaCol.setPrefWidth(160);
        vaCol.setCellValueFactory(data -> data.getValue().virtualAddressProperty().asObject());
        vaCol.setCellFactory(TextFieldTableCell.forTableColumn(new HexLongConverter()));
        vaCol.setOnEditCommit(e -> e.getRowValue().virtualAddressProperty().set(e.getNewValue()));

        TableColumn<InstructionEntry, Long> valueCol = new TableColumn<>("Value (WR only)");
        valueCol.setPrefWidth(160);
        valueCol.setCellValueFactory(data -> data.getValue().valueProperty().asObject());
        valueCol.setCellFactory(TextFieldTableCell.forTableColumn(new HexLongConverter()));
        valueCol.setOnEditCommit(e -> e.getRowValue().valueProperty().set(e.getNewValue()));

        table.getColumns().addAll(List.of(userCol, accessTypeCol, vaCol, valueCol));
        table.getColumns().add(ConfigEditorWindowSupport.deleteColumn(instructions));

        return ConfigEditorWindowSupport.build(
                owner,
                "Instruction Editor",
                "Program",
                "The ordered sequence of RD / WR / EX memory accesses the simulation executes, one per row.",
                table,
                instructions,
                InstructionEntry::new);
    }
}
