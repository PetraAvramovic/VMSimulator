package rs.ac.bg.etf.view.os;

import rs.ac.bg.etf.view.util.UiScale;
import java.util.List;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import rs.ac.bg.etf.view.util.InspectorWindows;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.view.util.WindowedRowSource;
import rs.ac.bg.etf.view.util.WindowedTableColumn;
import rs.ac.bg.etf.view.util.WindowedTableView;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.QueueChip;

/**
 * A separate, resizable window for browsing the FIFO replacement queue's full content -- the strip
 * drawn on the OS tab itself only ever shows the first/last couple of entries. Built lazily and
 * reused across opens/closes (see {@link #toggle}), on the same {@code WindowedTableView} chrome
 * every other inspector (page table / TLB / memory) uses, rather than a plain default-styled table.
 *
 * <p>Stays live while open: {@code refresh()} re-pulls the currently visible window's data whenever
 * the queue changes or physical memory's full/not-full state flips (the head's "imminent victim"
 * highlight depends on the latter, not just on being the head).
 */
public class ReplacementQueueInspectorWindow
{
    // Design-size; scaled where it is used, inside build() (see InspectorWindows for the scale).
    private static final double ROOT_PADDING = 12;

    private final PagedOSTabViewModel viewModel;
    private Stage stage;
    private WindowedTableView<QueueChip> tableView;

    public ReplacementQueueInspectorWindow(PagedOSTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        viewModel.getReplacementOrder().addListener((ListChangeListener<QueueChip>) c -> {
            if (tableView != null)
                tableView.refresh();
        });
        viewModel.memoryFullProperty().addListener((o, ov, nv) -> {
            if (tableView != null)
                tableView.refresh();
        });
    }

    /** Opens the window, or closes it if already open -- a second click on the strip toggles it shut. */
    public void toggle(Window owner)
    {
        if (stage != null && stage.isShowing())
        {
            stage.hide();
            return;
        }
        if (stage == null)
            stage = InspectorWindows.build(() -> build(owner));
        stage.show();
        stage.toFront();
    }

    private Stage build(Window owner)
    {
        // Null entryNoun: no seek bar -- the queue is short enough (bounded by however many
        // frames are actually resident) that jumping to an arbitrary position isn't useful.
        tableView = new WindowedTableView<>(queueColumns(), null, this::headStyleClass);
        tableView.setRowSource(queueSource());
        VBox.setVgrow(tableView, Priority.ALWAYS);

        double rootPadding = UiScale.px(ROOT_PADDING);
        VBox root = new VBox(tableView);
        root.setPadding(new Insets(rootPadding));

        Stage s = new Stage();
        s.initModality(Modality.NONE);
        if (owner != null)
            s.initOwner(owner);
        s.setTitle("Replacement Queue Inspector");

        Scene scene = new Scene(root, UiScale.px(300), UiScale.px(360));
        UiScale.applyTheme(scene);
        s.setScene(scene);

        s.setMinWidth(tableView.minimumWidth() + 2 * rootPadding);
        s.setMinHeight(tableView.minimumHeight() + 2 * rootPadding);

        return s;
    }

    private List<WindowedTableColumn<QueueChip>> queueColumns()
    {
        int digits = Math.max(1, viewModel.frameHexDigitsProperty().get());
        double positionWidth = WidthCalculator.plainColumnWidth("Position", "HEAD / TAIL".length());

        return List.of(
                new WindowedTableColumn<>("Frame", WidthCalculator.columnWidth("Frame", digits),
                        chip -> ValueConverter.toHex(chip.frame(), digits)),
                new WindowedTableColumn<>("Position", positionWidth, ReplacementQueueInspectorWindow::role));
    }

    // Only the head row is ever tinted, and only while it's actually the imminent victim (memory
    // full) -- the same replacement-queue-slot-head convention the strip itself uses, rather than always
    // flagging the oldest resident as if an eviction were already about to happen. The tail carries
    // no background tint (just its "TAIL" position label): it isn't a distinguished state the way
    // an imminent eviction is.
    private String headStyleClass(QueueChip chip)
    {
        return chip.head() && viewModel.memoryFullProperty().get() ? "replacement-queue-row-head" : null;
    }

    private WindowedRowSource<QueueChip> queueSource()
    {
        ObservableList<QueueChip> order = viewModel.getReplacementOrder();
        return new WindowedRowSource<>()
        {
            @Override public long getEntryCount() { return order.size(); }
            @Override public QueueChip rowAt(long index) { return order.get((int) index); }
        };
    }

    private static String role(QueueChip chip)
    {
        if (chip.head() && chip.tail())
            return "HEAD / TAIL";
        if (chip.head())
            return "HEAD";
        if (chip.tail())
            return "TAIL";
        return "";
    }
}
