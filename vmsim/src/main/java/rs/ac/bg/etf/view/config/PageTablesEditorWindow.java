package rs.ac.bg.etf.view.config;

import java.util.List;

import javafx.collections.ObservableList;
import javafx.stage.Stage;
import javafx.stage.Window;

import rs.ac.bg.etf.viewmodel.PageTableEntry;

/**
 * Editor window for the config screen's "Page Tables" section: the initial per-user page table
 * descriptors (valid/dirty/block) the simulation starts from -- everything else defaults to an
 * invalid (unmapped) page. Lazily built and toggled open/closed like the read-only inspector
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
        List<EditorColumn<PageTableEntry>> columns = List.of(
                new EditorColumn<>("User", 60,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.intCell(entry.userIdProperty(), width, replaceSelf)),
                new EditorColumn<>("Page", 100,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.longCell(entry.pageProperty(), width, replaceSelf)),
                new EditorColumn<>("Valid", 60,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.boolField(entry.validProperty(), width, replaceSelf)),
                new EditorColumn<>("Dirty", 60,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.boolField(entry.dirtyProperty(), width, replaceSelf)),
                new EditorColumn<>("Block", 130,
                        (entry, width, replaceSelf) -> ConfigEditorWindowSupport.hexLongCell(entry.blockProperty(), width, replaceSelf)));

        return ConfigEditorWindowSupport.build(
                owner,
                "Page Table Editor",
                "Page Tables",
                "Initial per-user page table descriptors. A page left out of this list starts invalid (unmapped).",
                entries,
                columns,
                PageTableEntry::new);
    }
}
