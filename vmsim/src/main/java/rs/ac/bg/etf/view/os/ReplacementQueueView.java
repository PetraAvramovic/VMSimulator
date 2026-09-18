package rs.ac.bg.etf.view.os;

import java.util.ArrayList;
import java.util.List;

import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.QueueChip;

/**
 * The FIFO replacement queue as a row of slots -- head (next victim) on the left, tail (most
 * recently loaded resident) on the right, both clearly captioned. Physical memory can hold as many
 * resident frames as it has physical frames, so the strip never materialises one box per entry:
 * only the first/last {@link #MAX_ENDS} are drawn, with a single "..." slot standing in for
 * everything collapsed between them. Clicking the strip opens a {@link
 * ReplacementQueueInspectorWindow} listing the queue's full content.
 */
public class ReplacementQueueView extends HBox
{
    /** Entries kept visible at each end before the strip condenses the middle into "...". */
    private static final int MAX_ENDS = 2;
    private static final double SLOT_GAP = 6;

    private final PagedOSTabViewModel viewModel;
    private final Label emptyLabel = new Label("replacement queue empty");
    private final ReplacementQueueInspectorWindow inspector;

    public ReplacementQueueView(PagedOSTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        this.inspector = new ReplacementQueueInspectorWindow(viewModel);
        getStyleClass().add("os-fifo-queue");
        setAlignment(Pos.BOTTOM_LEFT);
        setSpacing(SLOT_GAP);
        setCursor(Cursor.HAND);
        setOnMouseClicked(e -> inspector.toggle(getScene() != null ? getScene().getWindow() : null));
        emptyLabel.getStyleClass().add("mmu-bit-width");

        viewModel.getReplacementOrder().addListener((ListChangeListener<QueueChip>) c -> rebuild());
        viewModel.memoryFullProperty().addListener((o, ov, nv) -> rebuild());
        rebuild();
    }

    private void rebuild()
    {
        getChildren().clear();

        List<QueueChip> order = viewModel.getReplacementOrder();
        if (order.isEmpty())
        {
            getChildren().add(emptyLabel);
            return;
        }

        boolean memoryFull = viewModel.memoryFullProperty().get();
        int digits = viewModel.frameHexDigitsProperty().get();
        List<QueueChip> visible = visibleChips(order);

        for (QueueChip chip : visible)
        {
            Node slot = chip == null
                    ? slotLabel("...", "os-fifo-ellipsis")
                    : chipSlot(chip, digits, memoryFull);
            getChildren().add(withCaption(slot, chip));
        }
    }

    /** First/last {@link #MAX_ENDS} real entries, with a single {@code null} standing in for a
     *  collapsed middle run once the queue is longer than the strip shows in full. */
    private static List<QueueChip> visibleChips(List<QueueChip> order)
    {
        int max = MAX_ENDS * 2;
        if (order.size() <= max)
            return order;

        List<QueueChip> visible = new ArrayList<>();
        visible.addAll(order.subList(0, MAX_ENDS));
        visible.add(null);
        visible.addAll(order.subList(order.size() - MAX_ENDS, order.size()));
        return visible;
    }

    private Node chipSlot(QueueChip chip, int digits, boolean memoryFull)
    {
        Label label = slotLabel(ValueConverter.toHex(chip.frame(), digits), null);
        // The head is "next victim" (amber) only while memory is actually full; otherwise it's
        // just the oldest resident like any other slot. The tail carries no colour of its own --
        // its "TAIL" caption already marks it, and it isn't a distinguished state the way an
        // imminent eviction is.
        if (chip.head() && memoryFull)
            label.getStyleClass().add("os-fifo-slot-head");
        return label;
    }

    private Label slotLabel(String text, String extraStyleClass)
    {
        Label label = new Label(text);
        label.getStyleClass().add("os-fifo-slot");
        if (extraStyleClass != null)
            label.getStyleClass().add(extraStyleClass);
        return label;
    }

    // HEAD/TAIL captions sit above their end slot; every column gets its own caption label (blank
    // where it doesn't apply) so all the slot boxes still line up along one shared bottom edge.
    private Node withCaption(Node slot, QueueChip chip)
    {
        Label caption = new Label(captionFor(chip));
        caption.getStyleClass().add("os-fifo-caption");
        VBox column = new VBox(2, caption, slot);
        column.setAlignment(Pos.BOTTOM_CENTER);
        return column;
    }

    private static String captionFor(QueueChip chip)
    {
        if (chip == null)
            return "";
        if (chip.head() && chip.tail())
            return "HEAD / TAIL";
        if (chip.head())
            return "HEAD";
        if (chip.tail())
            return "TAIL";
        return "";
    }
}
