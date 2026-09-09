package rs.ac.bg.etf.view.os;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.FrameRow;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.FrameState;

/**
 * Physical memory from the OS's perspective: a scrollable, virtualised list of every
 * frame (Frame / State / User / Page / V / D / Disk), each row led by a colour swatch
 * that doubles as the occupancy strip. The view auto-scrolls to the frame the current
 * OS sub-flow touches.
 */
public class FrameTableView extends VBox
{
    public static final double ROW_HEIGHT = 24;

    private static final double SWATCH_WIDTH = 20;
    private static final double STATE_WIDTH = 78;
    private static final double USER_WIDTH = 46;
    private static final double PAGE_WIDTH = 52;
    private static final double BIT_WIDTH = 30;
    // Leaves room for the ListView's vertical scrollbar so header columns stay aligned with rows.
    private static final double SCROLLBAR_PAD = 14;

    private final PagedOSTabViewModel viewModel;
    private final ListView<FrameRow> list = new ListView<>();
    private final ObjectProperty<Region> activeRowAnchor = new SimpleObjectProperty<>();

    public FrameTableView(PagedOSTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        getStyleClass().add("frame-table-view");

        list.getStyleClass().add("frame-table-list");
        list.setItems(viewModel.getFrameRows());
        list.setFixedCellSize(ROW_HEIGHT);
        list.setFocusTraversable(false);
        list.setSelectionModel(null);
        list.setCellFactory(lv -> new FrameCell());

        getChildren().addAll(buildHeader(), list);
        VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);

        viewModel.getFrameRows().addListener((ListChangeListener<FrameRow>) c -> Platform.runLater(this::syncToActive));
        viewModel.activeFrameIndexProperty().addListener((o, ov, nv) -> Platform.runLater(this::syncToActive));
        Platform.runLater(this::syncToActive);
    }

    /** The row node for the frame the current OS sub-flow touches (or null when it is scrolled out of view). */
    public ObjectProperty<Region> activeRowAnchorProperty()
    {
        return activeRowAnchor;
    }

    private double frameWidth() { return WidthCalculator.columnWidth("Frame", viewModel.frameHexDigitsProperty().get()); }
    private double diskWidth() { return WidthCalculator.columnWidth("Disk", viewModel.diskHexDigitsProperty().get()); }

    private HBox buildHeader()
    {
        HBox header = new HBox();
        header.getStyleClass().add("frame-table-header");
        header.setPrefHeight(ROW_HEIGHT);
        header.setPadding(new javafx.geometry.Insets(0, SCROLLBAR_PAD, 0, 0));
        header.getChildren().addAll(
                headerCell("", SWATCH_WIDTH),
                headerCell("Frame", frameWidth()),
                headerCell("State", STATE_WIDTH),
                headerCell("User", USER_WIDTH),
                headerCell("Page", PAGE_WIDTH),
                headerCell("V", BIT_WIDTH),
                headerCell("D", BIT_WIDTH),
                headerCell("Disk", diskWidth()));
        return header;
    }

    /** Scrolls the addressed frame into view and republishes its row node as the wire anchor. */
    private void syncToActive()
    {
        int index = viewModel.activeFrameIndexProperty().get();
        if (index >= 0 && index < viewModel.getFrameRows().size())
        {
            list.scrollTo(Math.max(0, index - 2));
            // The freshly scrolled-in cell is not laid out until the next pulse.
            Platform.runLater(this::republishAnchor);
        }
        else
        {
            republishAnchor();
        }
    }

    private void republishAnchor()
    {
        Region anchor = null;
        for (javafx.scene.Node node : list.lookupAll(".list-cell"))
        {
            if (node instanceof FrameCell cell && cell.getItem() != null && cell.getItem().active())
            {
                anchor = cell;
                break;
            }
        }
        activeRowAnchor.set(anchor);
    }

    private Label headerCell(String text, double width)
    {
        Label label = new Label(text);
        label.getStyleClass().add("page-table-cell");
        label.setPrefWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private static String stateText(FrameState state)
    {
        return switch (state)
        {
            case FREE -> "free";
            case ALLOCATED -> "allocated";
            case KERNEL -> "kernel";
            case VICTIM -> "victim";
            case EVICTING -> "evicting";
        };
    }

    private static String swatchClass(FrameState state)
    {
        return switch (state)
        {
            case FREE -> "os-frame-free";
            case ALLOCATED -> "os-frame-allocated";
            case KERNEL -> "os-frame-kernel";
            case VICTIM -> "os-frame-victim";
            case EVICTING -> "os-frame-evicting";
        };
    }

    /** One frame row: swatch + the seven data columns, mutated in place as the cell is recycled. */
    private final class FrameCell extends ListCell<FrameRow>
    {
        private final Label swatch = new Label();
        private final Label frame = dataCell(frameWidth());
        private final Label state = dataCell(STATE_WIDTH);
        private final Label user = dataCell(USER_WIDTH);
        private final Label page = dataCell(PAGE_WIDTH);
        private final Label valid = dataCell(BIT_WIDTH);
        private final Label dirty = dataCell(BIT_WIDTH);
        private final Label disk = dataCell(diskWidth());
        private final HBox box = new HBox();

        FrameCell()
        {
            getStyleClass().add("frame-table-row");
            swatch.getStyleClass().add("os-frame-swatch");
            swatch.setAlignment(Pos.CENTER);
            swatch.setMinWidth(SWATCH_WIDTH);
            swatch.setPrefWidth(SWATCH_WIDTH);
            box.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().addAll(swatch, frame, state, user, page, valid, dirty, disk);
            setText(null);
        }

        @Override
        protected void updateItem(FrameRow row, boolean empty)
        {
            super.updateItem(row, empty);
            getStyleClass().removeAll("frame-table-row-active", "os-row-victim", "os-row-evicting");

            if (empty || row == null)
            {
                setGraphic(null);
                return;
            }

            setGraphic(box);
            frame.setText(ValueConverter.toHex(row.frame(), viewModel.frameHexDigitsProperty().get()));
            frame.setPrefWidth(frameWidth());
            state.setText(stateText(row.state()));

            boolean occupied = row.user() >= 0;
            user.setText(occupied ? Long.toString(row.user()) : "–");
            page.setText(occupied ? Long.toString(row.page()) : "–");
            valid.setText(occupied ? (row.valid() ? "1" : "0") : "–");
            dirty.setText(occupied ? (row.dirty() ? "1" : "0") : "–");
            disk.setText(occupied ? ValueConverter.toHex(row.disk(), viewModel.diskHexDigitsProperty().get()) : "–");
            disk.setPrefWidth(diskWidth());

            swatch.getStyleClass().removeAll(
                    "os-frame-free", "os-frame-allocated", "os-frame-kernel", "os-frame-victim", "os-frame-evicting");
            swatch.getStyleClass().add(swatchClass(row.state()));
            swatch.setText(row.nextVictim() ? "◀" : "");

            if (row.state() == FrameState.EVICTING)
                getStyleClass().add("os-row-evicting");
            else if (row.nextVictim())
                getStyleClass().add("os-row-victim");
            else if (row.active())
                getStyleClass().add("frame-table-row-active");
        }

        private Label dataCell(double width)
        {
            Label label = new Label();
            label.getStyleClass().add("page-table-cell");
            label.setPrefWidth(width);
            label.setAlignment(Pos.CENTER);
            return label;
        }
    }
}
