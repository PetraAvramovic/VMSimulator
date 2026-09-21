package rs.ac.bg.etf.view.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * A vertically-scrollable table over a {@link WindowedRowSource} of up to {@code Long.MAX_VALUE}
 * logical entries, without ever materialising more than a screenful of row nodes -- the same
 * windowed idiom {@code FrameTableView} uses for physical memory (see its own class doc), pulled
 * out into a reusable, column-agnostic component so any other address-scale table can reuse it by
 * supplying its own {@link WindowedTableColumn} list and {@link WindowedRowSource} (a page table
 * today; a future segment table just needs its own row type + source, no changes here).
 *
 * <p>Unlike {@code FrameTableView} (a fixed-size panel embedded in a schematic), this component is
 * meant to live inside a resizable window: the number of visible rows is recomputed from its own
 * live height whenever it changes, never below {@link #MIN_VISIBLE_ROWS}. The caller is expected
 * to floor the containing window's own resizable minimum at {@link #minimumWidth()} /
 * {@link #minimumHeight()} (plus whatever chrome it adds around this component) so that floor is
 * never actually reached by a user trying to see more rows -- only ever by the window's fixed,
 * enforced minimum size.
 */
public class WindowedTableView<R> extends VBox
{
    public static final int MIN_VISIBLE_ROWS = 3;
    private static final int WHEEL_STEP = 3;

    // Design-size lengths, scaled to the UI scale this table is built at (the screen is rebuilt when
    // the scale changes, so they are fixed for this instance's lifetime -- hence instance fields).
    private final double ROW_HEIGHT = UiScale.px(24);
    private final double BAR_WIDTH = UiScale.px(12);
    private final double SEEK_BAR_HEIGHT = UiScale.px(30);
    private final double SEEK_BAR_SPACING = UiScale.px(8);
    // .data-table's own uniform 10px padding + 1px border, on every side (rounded up):
    // reserved on all four edges, so it has to come off both the width and height budgets, and --
    // critically -- off onResize()'s own live "how many rows actually fit" measurement too, or a
    // row can be judged to fit when the padding/border is actually already claiming that space,
    // spilling the last row's box past the panel's own drawn border.
    private final double PANEL_CHROME = UiScale.px(24);

    private final List<WindowedTableColumn<R>> columns;
    private final VBox rowsBox = new VBox();
    private final List<RowNode> pool = new ArrayList<>();
    private final ScrollBar scrollBar = new ScrollBar();
    private final TextField seekField = new TextField();
    private final Label seekStatus = new Label();
    private final HBox header = new HBox();

    private final LongProperty windowStart = new SimpleLongProperty(0);
    private WindowedRowSource<R> rowSource = emptySource();
    private final Function<R, String> rowStyleClassFn;
    private final Function<R, String> rowTooltipFn;

    private int rowsShown = MIN_VISIBLE_ROWS;
    private boolean syncingScrollBar = false;
    /** 0 when there's no seek bar (null entryNoun), else {@link #SEEK_BAR_HEIGHT}. */
    private final double seekBarHeight;

    /** @param entryNoun singular name of one row, e.g. "page" -- used in the seek field's prompt/errors. */
    public WindowedTableView(List<WindowedTableColumn<R>> columns, String entryNoun)
    {
        this(columns, entryNoun, null);
    }

    /**
     * @param entryNoun singular name of one row, e.g. "page" -- used in the seek field's prompt/errors,
     *                     or null to omit the seek bar entirely (e.g. a small table where jumping to
     *                     an arbitrary position isn't a useful action).
     * @param rowStyleClassFn optional per-row function (e.g. "is this address kernel-locked", "is
     *                     this the FIFO queue's head") returning the extra style class to toggle on
     *                     the row (or null for none), in the same column-agnostic spirit as {@link
     *                     WindowedTableColumn}; null if the table has no such notion.
     */
    public WindowedTableView(List<WindowedTableColumn<R>> columns, String entryNoun, Function<R, String> rowStyleClassFn)
    {
        this(columns, entryNoun, rowStyleClassFn, null);
    }

    /**
     * @param rowTooltipFn optional per-row function returning the tooltip text to show while the
     *                     pointer is over the row (or null for no tooltip on that row), e.g. "Locked"
     *                     for a kernel-locked address; null if the table has no such notion.
     */
    public WindowedTableView(List<WindowedTableColumn<R>> columns, String entryNoun,
            Function<R, String> rowStyleClassFn, Function<R, String> rowTooltipFn)
    {
        this.columns = columns;
        this.rowStyleClassFn = rowStyleClassFn;
        this.rowTooltipFn = rowTooltipFn;
        this.seekBarHeight = entryNoun != null ? SEEK_BAR_HEIGHT : 0;
        getStyleClass().add("data-table");
        setFocusTraversable(true);

        if (entryNoun != null)
            getChildren().add(buildSeekBar(entryNoun));
        getChildren().addAll(buildHeader(), buildBody());
        ensurePool(MIN_VISIBLE_ROWS);
        setMinWidth(minimumWidth());
        setMinHeight(minimumHeight());

        // How many rows fit depends on the laid-out height, so it is worked out right after the layout
        // pass (merged when the height changes several times in one) -- not a frame later.
        PostLayoutTask resize = new PostLayoutTask(this, this::onResize, false);
        heightProperty().addListener((o, ov, nv) -> resize.request());
        resize.request();
    }

    /** Swaps the data source (e.g. a different user's page table), resetting the scroll position. */
    public void setRowSource(WindowedRowSource<R> source)
    {
        this.rowSource = source != null ? source : emptySource();
        windowStart.set(0);
        render();
    }

    /** Re-pulls the currently visible window's data without moving it (e.g. after a sim step). */
    public void refresh()
    {
        render();
    }

    public LongProperty windowStartProperty() { return windowStart; }

    /** Scrolls so entry {@code index} sits in the middle of the visible window -- e.g. opening the
     *  popup pre-seeked to whatever address a caller already knows, the same computation {@link
     *  #seek()} performs internally after validating typed-in input. */
    public void centerOn(long index)
    {
        setWindowStart(index - rowsShown / 2);
    }

    /** The narrowest this component can usefully be: every column's own width, plus the scrollbar. */
    public double minimumWidth()
    {
        double contentWidth = columns.stream().mapToDouble(WindowedTableColumn::width).sum();
        return contentWidth + BAR_WIDTH + PANEL_CHROME;
    }

    /** The shortest this component can usefully be: seek bar (if any) + header + MIN_VISIBLE_ROWS rows. */
    public double minimumHeight()
    {
        return seekBarHeight + ROW_HEIGHT + MIN_VISIBLE_ROWS * ROW_HEIGHT + PANEL_CHROME;
    }

    private void setWindowStart(long start)
    {
        long max = Math.max(0, rowSource.getEntryCount() - rowsShown);
        windowStart.set(Math.max(0, Math.min(start, max)));
        render();
    }

    private HBox buildSeekBar(String entryNoun)
    {
        Label label = new Label("Go to " + entryNoun);
        label.getStyleClass().add("data-table-cell");
        seekField.getStyleClass().add("data-table-seek-field");
        seekField.setPromptText("0x… or decimal");
        seekField.setPrefColumnCount(9);
        seekField.setOnAction(e -> seek());
        seekField.textProperty().addListener((o, ov, nv) -> {
            seekField.getStyleClass().remove("data-table-seek-error");
            seekStatus.setText("");
        });
        seekStatus.getStyleClass().add("status-caption");

        HBox bar = new HBox(SEEK_BAR_SPACING, label, seekField, seekStatus);
        bar.getStyleClass().add("data-table-seek-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(SEEK_BAR_HEIGHT);
        bar.setPrefHeight(SEEK_BAR_HEIGHT);
        return bar;
    }

    private HBox buildHeader()
    {
        header.getStyleClass().add("data-table-header-muted");
        header.setPrefHeight(ROW_HEIGHT);
        // Reserve the scrollbar's width unconditionally (not just while it's actually visible) so
        // the header never visibly shifts as the row count/entry count changes.
        header.setPadding(new Insets(0, BAR_WIDTH, 0, 0));
        for (WindowedTableColumn<R> column : columns)
            header.getChildren().add(headerCell(column.header(), column.width()));
        return header;
    }

    private HBox buildBody()
    {
        HBox.setHgrow(rowsBox, Priority.ALWAYS);

        scrollBar.getStyleClass().add("slim-scroll");
        scrollBar.setOrientation(Orientation.VERTICAL);
        scrollBar.setPrefWidth(BAR_WIDTH);
        scrollBar.setMinWidth(BAR_WIDTH);
        scrollBar.prefHeightProperty().bind(rowsBox.heightProperty());
        scrollBar.valueProperty().addListener((o, ov, nv) -> {
            if (!syncingScrollBar)
                setWindowStart(AddressScaleScrollBar.toWindowStart(scrollBar, rowSource.getEntryCount(), rowsShown));
        });

        HBox body = new HBox(rowsBox, scrollBar);
        VBox.setVgrow(body, Priority.ALWAYS);

        body.addEventHandler(ScrollEvent.SCROLL, e -> {
            if (rowSource.getEntryCount() <= rowsShown)
                return;
            long step = e.getDeltaY() > 0 ? -WHEEL_STEP : WHEEL_STEP;
            setWindowStart(windowStart.get() + step);
            e.consume();
        });
        return body;
    }

    // Recomputes how many rows fit in the space actually granted to this component (driven, via
    // VBox/HBox grow priorities up the parent chain, by the containing window's own resize),
    // rebuilds the row pool to match, and re-clamps the window.
    private void onResize()
    {
        double available = getHeight() - seekBarHeight - ROW_HEIGHT - PANEL_CHROME;
        int rows = (int) Math.max(MIN_VISIBLE_ROWS, Math.floor(available / ROW_HEIGHT));
        if (rows == rowsShown)
            return;
        rowsShown = rows;
        ensurePool(rows);
        setWindowStart(windowStart.get());
    }

    private void ensurePool(int rows)
    {
        while (pool.size() < rows)
        {
            RowNode r = new RowNode();
            pool.add(r);
            rowsBox.getChildren().add(r.box);
        }
        for (int i = 0; i < pool.size(); i++)
        {
            boolean shown = i < rows;
            pool.get(i).box.setVisible(shown);
            pool.get(i).box.setManaged(shown);
        }
    }

    private void seek()
    {
        String text = seekField.getText().trim();
        if (text.isEmpty())
            return;

        long entryCount = rowSource.getEntryCount();
        long target;
        try
        {
            target = text.toLowerCase().startsWith("0x")
                    ? Long.parseLong(text.substring(2), 16)
                    : Long.parseLong(text);
        }
        catch (NumberFormatException ex)
        {
            flagSeekError("not a number");
            return;
        }
        if (target < 0 || target >= entryCount)
        {
            flagSeekError("out of range (0 .. " + (entryCount - 1) + ")");
            return;
        }
        seekField.getStyleClass().remove("data-table-seek-error");
        seekStatus.setText("");
        setWindowStart(target - rowsShown / 2);
    }

    private void flagSeekError(String message)
    {
        if (!seekField.getStyleClass().contains("data-table-seek-error"))
            seekField.getStyleClass().add("data-table-seek-error");
        seekStatus.setText(message);
    }

    private void render()
    {
        long entryCount = rowSource.getEntryCount();
        long start = windowStart.get();
        boolean scrolls = entryCount > rowsShown;

        scrollBar.setManaged(scrolls);
        scrollBar.setVisible(scrolls);
        AddressScaleScrollBar.configure(scrollBar, entryCount, rowsShown);

        syncingScrollBar = true;
        AddressScaleScrollBar.syncValue(scrollBar, start, entryCount, rowsShown);
        syncingScrollBar = false;

        for (int i = 0; i < pool.size(); i++)
        {
            if (i >= rowsShown)
                continue; // already hidden by ensurePool
            RowNode r = pool.get(i);
            long index = start + i;
            if (index >= entryCount)
                r.hide();
            else
                r.update(rowSource.rowAt(index));
        }
    }

    private Label headerCell(String text, double width)
    {
        Label label = new Label(text);
        label.getStyleClass().add("data-table-cell");
        label.setPrefWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private static <R> WindowedRowSource<R> emptySource()
    {
        return new WindowedRowSource<>()
        {
            @Override public long getEntryCount() { return 0; }
            @Override public R rowAt(long index) { throw new IndexOutOfBoundsException(Long.toString(index)); }
        };
    }

    /** One pooled row: one Label per column, mutated in place as the window moves. */
    private final class RowNode
    {
        final HBox box = new HBox();
        final List<Label> cells = new ArrayList<>();
        final RowTooltip tooltip = new RowTooltip(box);
        String appliedStyleClass;
        // The row currently bound to this pooled node -- a column's onClick fires against whatever
        // that is *at click time*, since the same Label is reused for a different row as the window
        // scrolls (see update()/hide()).
        R currentRow;

        RowNode()
        {
            box.getStyleClass().add("data-table-row");
            box.setAlignment(Pos.CENTER_LEFT);
            box.setMinHeight(ROW_HEIGHT);
            box.setPrefHeight(ROW_HEIGHT);
            box.setMaxHeight(ROW_HEIGHT);
            for (WindowedTableColumn<R> column : columns)
            {
                Label cell = new Label();
                cell.getStyleClass().add("data-table-cell");
                cell.setPrefWidth(column.width());
                cell.setAlignment(Pos.CENTER);
                if (column.onClick() != null)
                {
                    cell.getStyleClass().add("data-table-cell-clickable");
                    cell.setCursor(Cursor.HAND);
                    cell.setOnMouseClicked(e -> {
                        if (currentRow != null)
                            column.onClick().accept(currentRow);
                        e.consume();
                    });
                }
                cells.add(cell);
                box.getChildren().add(cell);
            }
        }

        void update(R row)
        {
            box.setVisible(true);
            box.setManaged(true);
            currentRow = row;
            for (int i = 0; i < columns.size(); i++)
                cells.get(i).setText(columns.get(i).textFn().apply(row));

            String styleClass = rowStyleClassFn != null ? rowStyleClassFn.apply(row) : null;
            if (!Objects.equals(styleClass, appliedStyleClass))
            {
                if (appliedStyleClass != null)
                    box.getStyleClass().remove(appliedStyleClass);
                if (styleClass != null)
                    box.getStyleClass().add(styleClass);
                appliedStyleClass = styleClass;
            }

            tooltip.set(rowTooltipFn != null ? rowTooltipFn.apply(row) : null);
        }

        void hide()
        {
            box.setVisible(false);
            box.setManaged(false);
            currentRow = null;
            tooltip.set(null);
            if (appliedStyleClass != null)
                box.getStyleClass().remove(appliedStyleClass);
            appliedStyleClass = null;
        }
    }
}
