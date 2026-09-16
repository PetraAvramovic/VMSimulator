package rs.ac.bg.etf.view.os;

import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.QueueChip;

/**
 * The FIFO replacement queue as a row of frame chips, oldest (next victim) on the left,
 * newest on the right. The leftmost chip is tinted only while it is actually the imminent
 * victim (memory full). Wraps to a new line when it runs out of width.
 */
public class ReplacementQueueView extends FlowPane
{
    private final PagedOSTabViewModel viewModel;
    private final Label emptyLabel = new Label("replacement queue empty");

    public ReplacementQueueView(PagedOSTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        getStyleClass().add("os-fifo-strip");
        setAlignment(Pos.CENTER_LEFT);
        setHgap(6);
        setVgap(6);
        emptyLabel.getStyleClass().add("mmu-bit-width");

        viewModel.getReplacementOrder().addListener((ListChangeListener<QueueChip>) c -> rebuild());
        viewModel.memoryFullProperty().addListener((o, ov, nv) -> rebuild());
        rebuild();
    }

    private void rebuild()
    {
        getChildren().clear();

        if (viewModel.getReplacementOrder().isEmpty())
        {
            getChildren().add(emptyLabel);
            return;
        }

        boolean memoryFull = viewModel.memoryFullProperty().get();
        int digits = viewModel.frameHexDigitsProperty().get();

        for (QueueChip chip : viewModel.getReplacementOrder())
        {
            Label c = new Label(ValueConverter.toHex(chip.frame(), digits));
            c.getStyleClass().add("os-fifo-chip");
            // The head is "next victim" whenever memory is full.
            if (chip.head() && memoryFull)
                c.getStyleClass().add("os-fifo-chip-head");
            if (chip.tail() && !chip.head())
                c.getStyleClass().add("os-fifo-chip-tail");
            getChildren().add(c);
        }
    }
}
