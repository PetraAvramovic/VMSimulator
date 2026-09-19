package rs.ac.bg.etf.view.config;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.stage.Window;

import rs.ac.bg.etf.model.memory.Instruction.AccessType;
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
        List<EditorColumn<InstructionEntry>> columns = List.of(
                new EditorColumn<>("User", 60,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.intCell(entry.userProperty(), width, replaceSelf)),
                new EditorColumn<>("Access", 100,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.enumCell(entry.accessTypeProperty(), AccessType.values(), width, replaceSelf)),
                new EditorColumn<>("Virtual Address", 160,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.hexLongCell(entry.virtualAddressProperty(), width, replaceSelf)),
                new EditorColumn<>("Value (WR only)", 160,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.hexLongCell(entry.valueProperty(), width, replaceSelf)));

        return ConfigEditorWindowSupport.build(
                owner,
                "Instruction Editor",
                "Program",
                "The ordered sequence of RD / WR / EX memory accesses the simulation executes, one per row. Drag a row's handle to reorder it.",
                instructions,
                columns,
                InstructionEntry::new,
                true); // reorderable: instruction order is execution order
    }
}
