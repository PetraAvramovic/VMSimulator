package rs.ac.bg.etf.view.config;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.stage.Window;

import rs.ac.bg.etf.viewmodel.MemoryInitEntry;

/**
 * Editor window for the config screen's "Initial Page Content" section: word values pre-loaded
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
        List<EditorColumn<MemoryInitEntry>> columns = List.of(
                new EditorColumn<>("User", 60,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.intCell(entry.userIdProperty(), width, replaceSelf)),
                new EditorColumn<>("Page", 90,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.longCell(entry.pageProperty(), width, replaceSelf)),
                new EditorColumn<>("Offset", 110,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.longCell(entry.offsetProperty(), width, replaceSelf)),
                new EditorColumn<>("Value", 150,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.hexLongCell(entry.valueProperty(), width, replaceSelf)));

        return ConfigEditorWindowSupport.build(
                owner,
                "Initial Page Content Editor",
                "Initial Page Content",
                "Word values pre-loaded at specific offsets of a user's page (applied only if that page's Page Table entry is valid).",
                entries,
                columns,
                MemoryInitEntry::new);
    }
}
