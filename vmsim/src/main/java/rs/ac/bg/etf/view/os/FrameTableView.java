package rs.ac.bg.etf.view.os;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.InnerShadow;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import rs.ac.bg.etf.view.util.AddressScaleScrollBar;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.FrameRow;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.FrameState;

/**
 * Physical memory from the OS's perspective: Frame / State / User / Page / V / D / Disk,
 * each row led by a colour swatch that doubles as the occupancy strip.
 *
 * <p>Only a fixed window of {@code VISIBLE_ROWS} rows exists at any time -- physical memory
 * can have billions of frames. A custom {@link ScrollBar} (and the mouse wheel, and the
 * seek field) drives {@code viewModel.windowStartProperty()} over the full {@code long}
 * frame range; every window move rebuilds just those few rows. The view also recentres on
 * the frame the current OS sub-flow touches.
 */
public class FrameTableView extends VBox
{
    public static final double ROW_HEIGHT = 24;
    public static final int VISIBLE_ROWS = 12;

    private static final double SWATCH_WIDTH = 20;
    private static final double STATE_WIDTH = 78;
    private static final double USER_WIDTH = 46;
    private static final double PAGE_WIDTH = 52;
    private static final double BIT_WIDTH = 30;
    private static final double PANEL_PAD = 8;   // matches .frame-table-view -fx-padding
    private static final double BAR_WIDTH = 12;
    private static final double SEEK_BAR_HEIGHT = 30;
    private static final int WHEEL_STEP = 3;
    /** How long a successful "Seek frame" keeps its landed-on row accented. */
    private static final Duration FOUND_HIGHLIGHT_DURATION = Duration.seconds(2.5);
    /** One dim<->bright cycle of the found-row glow's pulse. */
    private static final Duration FOUND_GLOW_PULSE = Duration.seconds(0.6);
    private static final double FOUND_GLOW_RADIUS_MIN = 2;
    private static final double FOUND_GLOW_RADIUS_MAX = 9;
    private static final double FOUND_GLOW_CHOKE_MIN = 0.05;
    private static final double FOUND_GLOW_CHOKE_MAX = 0.2;
    /** Translucent, not the flat accent blue -- a subtle wash rather than a solid glowing ring. */
    private static final Color FOUND_GLOW_COLOR = Color.web("#3498db", 0.45);

    private final PagedOSTabViewModel viewModel;
    private final long frameCount;
    private final int rowsShown;
    private final boolean scrolls;

    private final VBox rowsBox = new VBox();
    private final List<RowNode> pool = new ArrayList<>();
    private final ScrollBar scrollBar = new ScrollBar();
    private final TextField seekField = new TextField();
    private final Label seekStatus = new Label();
    private final ObjectProperty<Region> activeRowAnchor = new SimpleObjectProperty<>();
    private final PauseTransition foundHighlightTimer = new PauseTransition(FOUND_HIGHLIGHT_DURATION);

    private boolean syncingScrollBar = false;
    // The frame a successful seek landed on, accented for FOUND_HIGHLIGHT_DURATION; matched by
    // frame number (not row identity) so the accent follows the frame if it's still in view when
    // the window later moves, and -1 once the timer clears it.
    private long foundFrame = -1;

    public FrameTableView(PagedOSTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        this.frameCount = Math.max(1, viewModel.getFrameCount());
        this.rowsShown = (int) Math.min(frameCount, VISIBLE_ROWS);
        this.scrolls = frameCount > rowsShown;
        getStyleClass().add("frame-table-view");
        setFocusTraversable(true);
        foundHighlightTimer.setOnFinished(e -> { foundFrame = -1; render(); });

        viewModel.setVisibleRowCount(rowsShown);

        getChildren().addAll(buildSeekBar(), buildHeader(), buildBody());

        double contentW = SWATCH_WIDTH + frameWidth() + STATE_WIDTH + USER_WIDTH + PAGE_WIDTH
                + 2 * BIT_WIDTH + diskWidth();
        double width = contentW + (scrolls ? BAR_WIDTH : 0) + 2 * PANEL_PAD + 4;
        double height = SEEK_BAR_HEIGHT + ROW_HEIGHT * (rowsShown + 1) + 2 * PANEL_PAD + 6;
        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);

        viewModel.windowStartProperty().addListener((o, ov, nv) -> Platform.runLater(this::render));
        viewModel.framesRevisionProperty().addListener((o, ov, nv) -> Platform.runLater(this::render));
        Platform.runLater(this::render);
    }

    /** Full rendered height of the panel. */
    public double panelHeight() { return getPrefHeight(); }

    /** Full rendered width of the panel. */
    public double panelWidth() { return getPrefWidth(); }

    /** The row node for the frame the current OS sub-flow touches, else the middle row. */
    public ObjectProperty<Region> activeRowAnchorProperty() { return activeRowAnchor; }

    // ------------------------------------------------------------------------------------

    private double frameWidth() { return WidthCalculator.columnWidth("Frame", viewModel.frameHexDigitsProperty().get()); }
    private double diskWidth() { return WidthCalculator.columnWidth("Disk", viewModel.diskHexDigitsProperty().get()); }

    private HBox buildSeekBar()
    {
        Label label = new Label("Seek frame");
        label.getStyleClass().add("page-table-cell");
        seekField.getStyleClass().add("os-frame-seek");
        seekField.setPromptText("0x… or decimal");
        seekField.setPrefColumnCount(9);
        seekField.setOnAction(e -> seek());
        seekField.textProperty().addListener((o, ov, nv) -> {
            seekField.getStyleClass().remove("os-frame-seek-error");
            seekStatus.setText("");
        });
        seekStatus.getStyleClass().add("os-disk-summary");

        HBox bar = new HBox(8, label, seekField, seekStatus);
        bar.getStyleClass().add("os-frame-seek-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setMinHeight(SEEK_BAR_HEIGHT);
        bar.setPrefHeight(SEEK_BAR_HEIGHT);
        return bar;
    }

    private HBox buildHeader()
    {
        HBox header = new HBox();
        header.getStyleClass().add("frame-table-header");
        header.setPrefHeight(ROW_HEIGHT);
        header.setPadding(new Insets(0, scrolls ? BAR_WIDTH : 0, 0, 0));
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

    private HBox buildBody()
    {
        for (int i = 0; i < rowsShown; i++)
        {
            RowNode r = new RowNode();
            pool.add(r);
            rowsBox.getChildren().add(r.box);
        }
        rowsBox.getStyleClass().add("frame-table-rows");
        HBox.setHgrow(rowsBox, javafx.scene.layout.Priority.ALWAYS);

        scrollBar.getStyleClass().add("slim-scroll");
        scrollBar.setOrientation(Orientation.VERTICAL);
        AddressScaleScrollBar.configure(scrollBar, frameCount, rowsShown);
        scrollBar.setPrefWidth(BAR_WIDTH);
        scrollBar.setMinWidth(BAR_WIDTH);
        scrollBar.setManaged(scrolls);
        scrollBar.setVisible(scrolls);
        scrollBar.prefHeightProperty().bind(rowsBox.heightProperty());
        scrollBar.valueProperty().addListener((o, ov, nv) -> {
            if (!syncingScrollBar)
                viewModel.setWindowStart(AddressScaleScrollBar.toWindowStart(scrollBar, frameCount, rowsShown));
        });

        HBox body = new HBox(rowsBox, scrollBar);

        body.addEventHandler(ScrollEvent.SCROLL, e -> {
            if (!scrolls)
                return;
            long step = e.getDeltaY() > 0 ? -WHEEL_STEP : WHEEL_STEP;
            viewModel.setWindowStart(viewModel.windowStartProperty().get() + step);
            e.consume();
        });
        setOnKeyPressed(e -> {
            long cur = viewModel.windowStartProperty().get();
            switch (e.getCode())
            {
                case PAGE_UP -> viewModel.setWindowStart(cur - rowsShown);
                case PAGE_DOWN -> viewModel.setWindowStart(cur + rowsShown);
                case HOME -> viewModel.setWindowStart(0);
                case END -> viewModel.setWindowStart(Long.MAX_VALUE);
                default -> { return; }
            }
            e.consume();
        });
        return body;
    }

    private void seek()
    {
        String text = seekField.getText().trim();
        if (text.isEmpty())
            return;

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
        if (target < 0 || target >= frameCount)
        {
            flagSeekError("out of range (0 .. " + ValueConverter.toHex(frameCount - 1, viewModel.frameHexDigitsProperty().get()) + ")");
            return;
        }
        seekField.getStyleClass().remove("os-frame-seek-error");
        seekStatus.setText("");
        foundFrame = target;
        foundHighlightTimer.playFromStart();
        viewModel.centerWindowOn(target);
        // centerWindowOn() only actually moves windowStart (and so only triggers a render via
        // that listener) if the target isn't already centred -- re-seeking the same frame after
        // its glow has already cleared would otherwise never re-render, so force one explicitly.
        Platform.runLater(this::render);
    }

    private void flagSeekError(String message)
    {
        if (!seekField.getStyleClass().contains("os-frame-seek-error"))
            seekField.getStyleClass().add("os-frame-seek-error");
        seekStatus.setText(message);
    }

    private void render()
    {
        long start = viewModel.windowStartProperty().get();

        syncingScrollBar = true;
        AddressScaleScrollBar.syncValue(scrollBar, start, frameCount, rowsShown);
        syncingScrollBar = false;

        Region anchor = null;
        Region middle = null;
        for (int i = 0; i < pool.size(); i++)
        {
            RowNode r = pool.get(i);
            long frame = start + i;
            if (frame >= frameCount)
            {
                r.hide();
                continue;
            }
            FrameRow data = viewModel.frameRowAt(frame);
            r.update(data);
            r.setFound(frame == foundFrame);
            if (i == pool.size() / 2)
                middle = r.box;
            if (data.active())
                anchor = r.box;
        }
        activeRowAnchor.set(anchor != null ? anchor : middle);
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

    /** One frame row: swatch + the seven data columns, mutated in place as the window moves. */
    private final class RowNode
    {
        // The glow effect (see setFound) is computed from the rendered node's own alpha, not its
        // geometric bounds -- a row with no background wash (i.e. anything but the active/victim/
        // evicting states) is otherwise fully transparent except for its text glyphs, so the glow
        // would silhouette each label separately instead of the row as a whole. glowBacker is a
        // permanently opaque (matching .frame-table-view's own white) layer behind "content" that
        // the effect actually applies to, giving one clean rectangle regardless of row state.
        private final Region glowBacker = new Region();
        private final HBox content = new HBox();
        final StackPane box = new StackPane(glowBacker, content);
        private final Label swatch = new Label();
        private final Label frame = dataCell(frameWidth());
        private final Label state = dataCell(STATE_WIDTH);
        private final Label user = dataCell(USER_WIDTH);
        private final Label page = dataCell(PAGE_WIDTH);
        private final Label valid = dataCell(BIT_WIDTH);
        private final Label dirty = dataCell(BIT_WIDTH);
        private final Label disk = dataCell(diskWidth());

        RowNode()
        {
            glowBacker.getStyleClass().add("frame-table-row-glow-backer");
            glowBacker.setMouseTransparent(true);
            content.getStyleClass().add("frame-table-row");
            content.setAlignment(Pos.CENTER_LEFT);
            box.setMinHeight(ROW_HEIGHT);
            box.setPrefHeight(ROW_HEIGHT);
            box.setMaxHeight(ROW_HEIGHT);
            swatch.getStyleClass().add("os-frame-swatch");
            swatch.setAlignment(Pos.CENTER);
            swatch.setMinSize(SWATCH_WIDTH, ROW_HEIGHT);
            swatch.setPrefSize(SWATCH_WIDTH, ROW_HEIGHT);
            content.getChildren().addAll(swatch, frame, state, user, page, valid, dirty, disk);
        }

        void update(FrameRow row)
        {
            box.setVisible(true);
            box.setManaged(true);
            content.getStyleClass().removeAll("frame-table-row-active", "os-row-victim", "os-row-evicting");

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
                content.getStyleClass().add("os-row-evicting");
            else if (row.nextVictim())
                content.getStyleClass().add("os-row-victim");
            else if (row.active())
                content.getStyleClass().add("frame-table-row-active");
        }

        private InnerShadow foundGlow;
        private Timeline foundPulse;

        /**
         * Toggles the brief "Seek frame" landed-here accent: a pulsing glow, independent of the
         * state-driven styling above. An InnerShadow, not a DropShadow: a DropShadow paints
         * outside the node's own bounds, so with rows packed edge to edge (zero gap between
         * them, and the scrollbar/panel border flush left and right) its visibility on each side
         * ends up at the mercy of whatever neighboring content happens to be painted over it --
         * inconsistent and partial. An InnerShadow glows inward from the edges of the row's own
         * opaque shape (glowBacker), so it is fully self-contained and reads uniformly on all
         * four sides regardless of neighboring rows.
         */
        void setFound(boolean found)
        {
            if (found)
            {
                if (foundGlow == null)
                {
                    foundGlow = new InnerShadow();
                    foundGlow.setBlurType(BlurType.GAUSSIAN);
                    foundGlow.setColor(FOUND_GLOW_COLOR);
                    foundGlow.setOffsetX(0);
                    foundGlow.setOffsetY(0);
                }
                box.setEffect(foundGlow);
                if (foundPulse == null)
                {
                    foundPulse = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(foundGlow.radiusProperty(), FOUND_GLOW_RADIUS_MIN),
                                    new KeyValue(foundGlow.chokeProperty(), FOUND_GLOW_CHOKE_MIN)),
                            new KeyFrame(FOUND_GLOW_PULSE,
                                    new KeyValue(foundGlow.radiusProperty(), FOUND_GLOW_RADIUS_MAX),
                                    new KeyValue(foundGlow.chokeProperty(), FOUND_GLOW_CHOKE_MAX)));
                    foundPulse.setAutoReverse(true);
                    foundPulse.setCycleCount(Timeline.INDEFINITE);
                }
                foundPulse.playFromStart();
            }
            else if (foundGlow != null)
            {
                foundPulse.stop();
                box.setEffect(null);
            }
        }

        void hide()
        {
            box.setVisible(false);
            box.setManaged(false);
            // Stop a glow pulse rather than leaving it animating forever, invisibly, behind a
            // row that's scrolled out of the window.
            setFound(false);
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
