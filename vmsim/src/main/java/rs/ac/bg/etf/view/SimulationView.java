package rs.ac.bg.etf.view;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Window;
import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.view.inspector.MemoryInspectorWindow;
import rs.ac.bg.etf.view.inspector.PageTableInspectorWindow;
import rs.ac.bg.etf.view.inspector.TLBInspectorWindow;
import rs.ac.bg.etf.view.os.ReplacementQueueInspectorWindow;
import rs.ac.bg.etf.view.tlb.PagedTLBTabView;
import rs.ac.bg.etf.view.util.BackButton;
import rs.ac.bg.etf.view.util.PostLayoutTask;
import rs.ac.bg.etf.view.util.StepDescriptionFormatter;
import rs.ac.bg.etf.view.util.UiScale;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.viewmodel.MemoryTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel;
import rs.ac.bg.etf.viewmodel.SimulationViewModel;

/**
 * Outer-shell View for the simulation workbench: left sidebar, MMU/TLB/OS tabs, right sidebar.
 */
public class SimulationView {
    // Logical drag limits so a sidebar can never swallow the whole workbench or collapse to nothing
    private final double SIDEBAR_MIN_WIDTH = UiScale.px(200);
    private final double SIDEBAR_MAX_WIDTH = UiScale.px(420);
    // Circle's own CSS radius property isn't reliable for sizing (PagedMMUTabView's adder circle
    // sets its radius the same way, in code, and only styles fill/stroke via CSS -- see
    // .mmu-adder-circle), so the tab notification dot's size lives here instead of in CSS. Kept in
    // step with .tab-label's own font-size (14px, up from Modena's default ~13px) by hand -- there's
    // no live binding to the CSS value, so if that font-size changes again, resize these to match.
    private final double TAB_NOTIFICATION_BADGE_RADIUS = UiScale.px(5);
    // Breathing room between the dot and the tab text that follows it, reserved only while the
    // badge is actually showing.
    private final double TAB_NOTIFICATION_BADGE_GAP = UiScale.px(7);
    // The instruction list's Index column has no configured upper bound (an instruction file can be
    // any length), so its width is reserved for this many digits rather than measured from the
    // actual (possibly much shorter) instruction list -- otherwise a short test file sizes the
    // header/columns too narrow for whatever longer file replaces it later.
    private static final int MAX_INDEX_DIGITS = 4;
    // Extra breathing room below the step description, on top of the right sidebar's own uniform
    // child spacing -- see buildRightSidebar.
    private final double STEP_DESC_GAP = UiScale.px(16);
    // Fixed reserve for the step description -- enough for 3 wrapped lines at its own font size, so
    // the nav rows below it sit at one constant position regardless of how long the current step's
    // description is or how many lines it wraps to. A description that somehow needs more than
    // that (an extreme case: the sidebar dragged to its narrowest plus an unusually long message)
    // is clipped rather than pushing the buttons around -- see buildRightSidebar.
    private final double STEP_DESC_HEIGHT = UiScale.px(60);
    // Heights of the two sidebar lists. Fixed on purpose: the workbench adapts to the window by
    // rebuilding at another UI scale (see UiScale), so these stay at their 1080p design proportions
    // rather than stretching with the window -- which would also push the step description and nav
    // rows off the constant position the sidebar is designed around.
    private final double INSTRUCTION_LIST_PREF_HEIGHT = UiScale.px(196);
    private final double EXECUTION_LOG_PREF_HEIGHT = UiScale.px(220);
    // Where the SplitPane's two dividers sit, as fractions of the workbench's logical width, i.e.
    // how wide each sidebar is at the 1080p design size (18% each side).
    private static final double LEFT_DIVIDER_POSITION = 0.18;
    private static final double RIGHT_DIVIDER_POSITION = 0.82;
    // Style class of the tab strip TabPane's skin creates; found to size the workbench's minimum
    // height (see buildTabPane).
    private static final String TAB_HEADER_AREA_STYLE_CLASS = "tab-header-area";

    private final SplitPane layoutContainer;
    // What getRootContainerNode() actually hands App: the menu bar stacked above layoutContainer.
    private final VBox workbenchRoot;

    // Everything this view registered on the (longer-lived) view models, undone by dispose().
    // The workbench is rebuilt whenever the UI scale changes, so an old view must not stay wired to
    // the view models -- it would keep updating (and keep everything it references alive) forever.
    private final List<Runnable> teardown = new ArrayList<>();

    // Tabs whose content hasn't been built yet (see lazyTab).
    private final Map<Tab, Supplier<Node>> pendingTabContent = new HashMap<>();

    public SimulationView(SimulationViewModel viewModel) {
        // Built eagerly, unlike the OS tab's own view (see lazyTab) -- the View menu's Replacement
        // Queue Inspector must be able to open regardless of whether that tab has ever been shown.
        // Its own view (built below, still lazily) reuses this exact instance rather than a second one.
        PagedOSTabViewModel osTabViewModel = viewModel.getContext() instanceof PageSimulationContext pageContext
                ? new PagedOSTabViewModel(pageContext, viewModel) : null;
        if (osTabViewModel != null)
            teardown.add(osTabViewModel::dispose);

        VBox leftSidebar = buildLeftSidebar(viewModel);
        TabPane tabPane = buildTabPane(viewModel, osTabViewModel);
        VBox rightSidebar = buildRightSidebar(viewModel);

        // The workbench's minimum size is what App's ResponsiveHost sizes the UI scale against, so it
        // has to be the sum of what its parts really need. SplitPane's own skin is
        // trusted for the divider chrome but not relied on for the parts: the widest sidebar/tab
        // minimums are added up here explicitly and the larger of the two wins.
        this.layoutContainer = new SplitPane(leftSidebar, tabPane, rightSidebar) {
            @Override
            protected double computeMinWidth(double height) {
                double parts = leftSidebar.minWidth(-1) + tabPane.minWidth(-1) + rightSidebar.minWidth(-1);
                return Math.max(super.computeMinWidth(height), parts + snappedLeftInset() + snappedRightInset());
            }

            @Override
            protected double computeMinHeight(double width) {
                double tallest = Math.max(tabPane.minHeight(-1),
                        Math.max(leftSidebar.minHeight(-1), rightSidebar.minHeight(-1)));
                return Math.max(super.computeMinHeight(width), tallest + snappedTopInset() + snappedBottomInset());
            }
        };
        this.layoutContainer.getStyleClass().add("simulation-container");

        // A SplitPane remembers its divider positions as state and silently nudges them whenever a
        // minimum size clamps them -- and the workbench's logical width shifts a few times while the
        // screen first lays out (the scale settles as each tab reports its real minimum), so left
        // alone the sidebars end up wherever those transient sizes pushed them: wider than designed
        // and unequal. Re-applying the design fractions whenever the logical width changes makes the
        // sidebar widths a pure function of the window instead of its layout history. (A divider
        // the user dragged snaps back on the next window resize -- the price of that determinism.)
        Runnable placeDividers = () ->
                layoutContainer.setDividerPositions(LEFT_DIVIDER_POSITION, RIGHT_DIVIDER_POSITION);
        placeDividers.run();
        layoutContainer.widthProperty().addListener((observable, oldWidth, newWidth) -> placeDividers.run());

        MenuBar menuBar = buildMenuBar(viewModel, osTabViewModel);
        this.workbenchRoot = new VBox(menuBar, layoutContainer);
        VBox.setVgrow(layoutContainer, Priority.ALWAYS);
    }

    // -------------------------------------------------------------------------
    // MENU BAR: Simulation (restart / skip to end / new simulation) + View (inspector windows)
    // -------------------------------------------------------------------------
    private MenuBar buildMenuBar(SimulationViewModel viewModel, PagedOSTabViewModel osTabViewModel) {
        MenuItem restartItem = new MenuItem("Restart Simulation");
        restartItem.setOnAction(e -> viewModel.restart());

        MenuItem skipToEndItem = new MenuItem("Skip to End");
        skipToEndItem.setOnAction(e -> viewModel.executeToEnd());

        MenuItem newSimulationItem = new MenuItem("New Simulation…");
        newSimulationItem.setOnAction(e -> viewModel.startNewSimulation());

        Menu simulationMenu = new Menu("Simulation");
        simulationMenu.getItems().addAll(restartItem, skipToEndItem, new SeparatorMenuItem(), newSimulationItem);

        MenuBar menuBar = new MenuBar();
        // VBox only stretches a child whose own maxWidth allows it; spelled out explicitly here
        // rather than assumed, so the bar reliably spans the full workbench width.
        menuBar.setMaxWidth(Double.MAX_VALUE);
        Menu viewMenu = new Menu("View");
        viewMenu.getItems().addAll(buildInspectorMenuItems(menuBar, viewModel, osTabViewModel));

        menuBar.getMenus().addAll(simulationMenu, viewMenu);
        return menuBar;
    }

    /**
     * One item per inspector window that can meaningfully open on its own (i.e. without first
     * needing a specific sub-address picked elsewhere, the way the Disk Block inspector does from
     * the OS tab's disk box or the Page Table inspector's own Disk column) -- Page Table, TLB,
     * Memory, and the FIFO Replacement Queue. Each opens its own instance, independent of whichever
     * tab's inline entry point (if any) also opens that same kind of inspector -- the existing
     * per-tab inspectors already tolerate more than one live instance of the same kind (e.g. the OS
     * tab's disk box and the page table inspector's Disk column each own a separate
     * {@code DiskBlockInspectorWindow}), so this follows the same, already-established pattern
     * rather than threading a single shared instance through both call sites.
     */
    private List<MenuItem> buildInspectorMenuItems(MenuBar anchor, SimulationViewModel viewModel, PagedOSTabViewModel osTabViewModel) {
        MenuItem pageTableItem = new MenuItem("Page Table Inspector");
        MenuItem tlbItem = new MenuItem("TLB Inspector");
        MenuItem memoryItem = new MenuItem("Memory Inspector");
        MenuItem replacementQueueItem = new MenuItem("Replacement Queue Inspector");

        // Page Table / Memory / Replacement Queue are paged-specific (like the tabs' own placeholder
        // fallback for a translation type that isn't paged); the TLB inspector only needs the
        // generic SimulationContext, so it stays enabled regardless.
        if (viewModel.getContext() instanceof PageSimulationContext pageContext) {
            PageTableInspectorWindow pageTableInspector =
                    new PageTableInspectorWindow(pageContext, viewModel.currentStepNumberProperty());
            pageTableItem.setOnAction(e -> pageTableInspector.toggle(ownerWindow(anchor)));

            MemoryInspectorWindow memoryInspector =
                    new MemoryInspectorWindow(pageContext, viewModel.currentStepNumberProperty());
            memoryItem.setOnAction(e -> {
                long seed = pageContext.getCurrentPhysicalAddress();
                memoryInspector.toggle(ownerWindow(anchor), seed >= 0 ? seed : 0);
            });
        } else {
            pageTableItem.setDisable(true);
            memoryItem.setDisable(true);
        }

        TLBInspectorWindow tlbInspector = new TLBInspectorWindow(viewModel.getContext(), viewModel.currentStepNumberProperty());
        tlbItem.setOnAction(e -> tlbInspector.toggle(ownerWindow(anchor)));

        if (osTabViewModel != null) {
            ReplacementQueueInspectorWindow replacementQueueInspector = new ReplacementQueueInspectorWindow(osTabViewModel);
            replacementQueueItem.setOnAction(e -> replacementQueueInspector.toggle(ownerWindow(anchor)));
        } else {
            replacementQueueItem.setDisable(true);
        }

        return List.of(pageTableItem, tlbItem, memoryItem, replacementQueueItem);
    }

    private static Window ownerWindow(Node anchor) {
        return anchor.getScene() != null ? anchor.getScene().getWindow() : null;
    }

    /**
     * Detaches this view from the view models (log, current instruction, notification badges and
     * every tab's own view model), so it can be dropped -- e.g. when the screen is rebuilt for a new UI scale.
     */
    public void dispose() {
        teardown.forEach(Runnable::run);
        teardown.clear();
    }

    private Button buildBackButton(SimulationViewModel viewModel) {
        return BackButton.create(viewModel::navigateBack);
    }

    // Makes a pair of nav buttons split their row evenly and fill its full width, so the row's
    // right edge lines up with the rest of the sidebar's content (e.g. the log list), while never
    // letting either shrink narrower than its own text needs (no ellipsis).
    //
    // The shared floor comes from each button's own real, CSS-styled width the first time it's
    // actually laid out -- captured once into a plain double, not a *live* binding back onto
    // width itself. A live "minWidth = max(a.width, b.width)" binding is a feedback loop: turning
    // on hgrow lets width grow to fill spare row space, which raises the bound minWidth to match,
    // which then stops width from ever coming back down again even once the row is squeezed --
    // that's what sent "Next ▶" off-screen instead of shrinking. hgrow/maxWidth (which let the
    // pair grow to fill, or shrink back down to the floor) are only switched on *after* this
    // one-time measurement, so that first measurement reflects each button's true unstretched
    // size, not whatever the row happened to have room for yet.
    private static void matchWidth(Button a, Button b) {
        ChangeListener<Number> onFirstLayout = new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Number> obs, Number ov, Number nv) {
                if (a.getWidth() <= 0 || b.getWidth() <= 0)
                    return; // wait until both have actually been laid out at least once
                double floor = Math.max(a.getWidth(), b.getWidth());
                a.setMinWidth(floor);
                b.setMinWidth(floor);
                a.setMaxWidth(Double.MAX_VALUE);
                b.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(a, Priority.ALWAYS);
                HBox.setHgrow(b, Priority.ALWAYS);
                a.widthProperty().removeListener(this);
                b.widthProperty().removeListener(this);
            }
        };
        a.widthProperty().addListener(onFirstLayout);
        b.widthProperty().addListener(onFirstLayout);
    }

    // -------------------------------------------------------------------------
    // LEFT SIDEBAR: Instruction list + current VA/PA readouts
    // -------------------------------------------------------------------------
    private VBox buildLeftSidebar(SimulationViewModel viewModel) {
        // A plain VBox naturally sizes itself to its widest child (the instruction list below),
        // so folding SIDEBAR_MIN_WIDTH/SIDEBAR_MAX_WIDTH into that computation -- rather than
        // overriding it with a fixed clampSidebarWidth() call -- gives a sidebar that is always
        // at least as wide as its real, CSS-styled content actually needs on this machine, with
        // no hand-tuned pixel constant standing in for that measurement.
        //
        // computePrefWidth, not computeMinWidth: the instruction list's header cells only pin
        // prefWidth (see instructionCell), so their own computeMinWidth stays at a Label's usual
        // shrink-to-ellipsis default -- far narrower than the columns actually need. That default
        // is also all a VBox has to report once the instruction list is empty (the scrollable
        // "rows" VBox collapses to zero width with no rows in it), so basing the sidebar's floor
        // on computeMinWidth let the SplitPane squeeze it well below the header's real width,
        // squishing the column labels together. computePrefWidth reflects the header's actual
        // fixed-width columns regardless of how many rows are loaded.
        VBox leftSidebar = new VBox(UiScale.px(12)) {
            @Override
            protected double computeMinWidth(double height) {
                return Math.max(SIDEBAR_MIN_WIDTH, super.computePrefWidth(height));
            }

            @Override
            protected double computeMaxWidth(double height) {
                return Math.max(SIDEBAR_MAX_WIDTH, super.computePrefWidth(height));
            }
        };
        leftSidebar.getStyleClass().add("sidebar-panel");

        Button backButton = buildBackButton(viewModel);

        Label header = new Label("Instructions");
        header.getStyleClass().add("section-title");

        Region instructionList = buildInstructionList(viewModel);

        Label currentVaHeader = new Label("Virtual Address");
        currentVaHeader.getStyleClass().add("section-title");
        Label currentVaLabel = new Label();
        currentVaLabel.getStyleClass().add("sidebar-value-label");
        currentVaLabel.textProperty().bind(viewModel.currentVirtualAddressHexProperty());

        Label currentPaHeader = new Label("Physical Address");
        currentPaHeader.getStyleClass().add("section-title");
        Label currentPaLabel = new Label();
        currentPaLabel.getStyleClass().add("sidebar-value-label");
        currentPaLabel.textProperty().bind(viewModel.currentPhysicalAddressHexProperty());

        leftSidebar.getChildren().addAll(
                backButton, header, instructionList, currentVaHeader, currentVaLabel, currentPaHeader, currentPaLabel);
        return leftSidebar;
    }

    /**
     * A fixed Index/Op/User/VA header above a scrollable list of instruction rows -- plain
     * HBoxes/Labels (not a virtualized ListView) inside a vertical-only ScrollPane. The
     * instruction count is bounded by what a human wrote into the config file -- not one of the
     * address-space-scale quantities the sparse-structure rule guards against -- so
     * virtualization buys nothing here, and a plain layout lets JavaFX size the panel to its
     * actual rendered content width by itself (see the {@code VBox} overrides in
     * {@link #buildLeftSidebar}), rather than us re-deriving font/padding/scrollbar metrics by
     * hand. {@code setHbarPolicy(NEVER)} is a real, always-effective guarantee against a
     * horizontal scrollbar, not an estimate that could be wrong on another machine.
     */
    private Region buildInstructionList(SimulationViewModel viewModel) {
        ObservableList<Instruction> instructions = viewModel.getInstructionEntries();
        int rowCount = instructions.size();

        // Every column is sized from the configuration's own worst case, never from whatever values
        // the current instruction file happens to contain -- so the header/columns are never left
        // too narrow for a longer file or a run that simply doesn't happen to use every user id.
        // Index has no configured bound at all (see MAX_INDEX_DIGITS); User comes from the
        // configured user count's highest possible id; VA already came from the configured
        // virtual-address bit width.
        int indexDigits = MAX_INDEX_DIGITS;
        int userDigits = Integer.toString(Math.max(0, viewModel.getContext().getNumberOfUsers() - 1)).length();
        int vaHexDigits = ValueConverter.hexDigitsFor(viewModel.getContext().getFullVirtualAddressBits());
        double indexWidth = WidthCalculator.plainColumnWidth("Index", indexDigits);
        double opWidth = WidthCalculator.plainColumnWidth("Op", 2);
        double userWidth = WidthCalculator.plainColumnWidth("User", userDigits);
        double vaWidth = WidthCalculator.columnWidth("VA", vaHexDigits);

        VBox rows = new VBox();
        rows.getStyleClass().add("instruction-list-rows");
        for (int i = 0; i < rowCount; i++) {
            Instruction instruction = instructions.get(i);
            HBox row = new HBox(
                    instructionCell(Integer.toString(i), indexWidth),
                    instructionCell(instruction.getAccessType().toString(), opWidth),
                    instructionCell(Integer.toString(instruction.getUser()), userWidth),
                    instructionCell(ValueConverter.toHex(instruction.getVirtualAddress(), vaHexDigits), vaWidth));
            row.getStyleClass().add("instruction-list-row");
            // Clicking an instruction jumps the simulation to right after its fetch step, whether
            // that means reverting back to it or running forward to reach it.
            final int instructionIndex = i;
            row.setOnMouseClicked(e -> viewModel.goToInstructionStart(instructionIndex));
            rows.getChildren().add(row);
        }

        ScrollPane scrollPane = new ScrollPane(rows);
        scrollPane.getStyleClass().addAll("instruction-list-scroll", "slim-scroll");
        scrollPane.setPrefHeight(INSTRUCTION_LIST_PREF_HEIGHT);
        scrollPane.setFocusTraversable(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(false);

        // A ScrollPane doesn't size itself to its content's natural width -- same limitation
        // that made ListView unusable here -- so with fitToWidth off, "rows" keeps its own
        // preferred width (correctly reflecting the real, CSS-styled font/padding once this
        // is attached to the live scene) but the pane around it does not follow along on its
        // own. Bind the pane's width to that real content width, plus this machine's actual
        // vertical-scrollbar width once it's known (measured from a real ScrollBar control
        // below, not assumed), instead of guessing either number ourselves. Both minWidth and
        // prefWidth are bound: an explicitly-set prefWidth alone would not be picked up by
        // computeMinWidth()'s default fallback, which the surrounding sidebar relies on.
        DoubleProperty scrollbarWidth = measureVerticalScrollBarWidth(rows);
        var neededWidth = rows.widthProperty().add(scrollbarWidth);
        scrollPane.minWidthProperty().bind(neededWidth);
        scrollPane.prefWidthProperty().bind(neededWidth);

        // The header's own columns line up with the rows' below, but the rows give up their
        // trailing width to the scrollbar -- reserve the same width here (as a plain spacer, not
        // a visible column) so the header doesn't end up wider than the scrolling body beneath it.
        Region scrollbarSpacer = new Region();
        scrollbarSpacer.minWidthProperty().bind(scrollbarWidth);
        scrollbarSpacer.prefWidthProperty().bind(scrollbarWidth);
        scrollbarSpacer.maxWidthProperty().bind(scrollbarWidth);

        HBox header = new HBox(
                instructionCell("Index", indexWidth),
                instructionCell("Op", opWidth),
                instructionCell("User", userWidth),
                instructionCell("VA", vaWidth),
                scrollbarSpacer);
        header.getStyleClass().add("instruction-list-header");

        VBox card = new VBox(header, scrollPane);
        card.getStyleClass().add("instruction-list");

        PseudoClass current = PseudoClass.getPseudoClass("current");
        ObservableValue<Number> currentInstructionIndex = viewModel.currentInstructionIndexProperty();

        // Pin the running instruction near the top of the viewport. Unlike ListView's scrollTo()
        // (which only guarantees visibility), ScrollPane's vvalue is already a direct 0..1
        // fraction of the scrollable range, computed from each row's real laid-out position --
        // no "park at the bottom, then scroll back up" workaround needed.
        ChangeListener<Number> onCurrentInstructionChanged = (obs, oldIndex, newIndex) -> {
            int index = newIndex.intValue();
            for (int i = 0; i < rowCount; i++)
                rows.getChildren().get(i).pseudoClassStateChanged(current, i == index);

            if (index < 0 || index >= rowCount)
                return;

            double maxScroll = rows.getHeight() - scrollPane.getViewportBounds().getHeight();
            if (maxScroll <= 0)
                return;
            double targetY = rows.getChildren().get(index).getBoundsInParent().getMinY();
            scrollPane.setVvalue(Math.min(1.0, Math.max(0.0, targetY / maxScroll)));
        };
        currentInstructionIndex.addListener(onCurrentInstructionChanged);
        teardown.add(() -> currentInstructionIndex.removeListener(onCurrentInstructionChanged));
        // A view rebuilt mid-simulation (see UiScale) starts with an instruction already running:
        // apply its highlight (and scroll it into view, once the rows have been laid out) right away.
        new PostLayoutTask(card, () -> {
            Number index = currentInstructionIndex.getValue();
            onCurrentInstructionChanged.changed(currentInstructionIndex, index, index);
        }, false).request();

        return card;
    }

    private Label instructionCell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("data-table-cell");
        label.setPrefWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    /**
     * The real, CSS-resolved width of a vertical scrollbar on this JavaFX runtime -- read from
     * an actual (invisible, unmanaged) {@code ScrollBar} once it is attached to the live,
     * styled scene, rather than assumed as a pixel constant. Piggybacks on {@code rows}'s own
     * scene attachment so no throwaway {@code Scene} is needed just to resolve CSS.
     */
    private DoubleProperty measureVerticalScrollBarWidth(VBox rows) {
        ScrollBar probe = new ScrollBar();
        probe.setOrientation(Orientation.VERTICAL);
        probe.setManaged(false);
        probe.setVisible(false);
        rows.getChildren().add(probe);

        DoubleProperty width = new SimpleDoubleProperty(0);
        probe.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                probe.applyCss();
                probe.layout();
                width.set(probe.prefWidth(-1));
            }
        });
        return width;
    }

    // -------------------------------------------------------------------------
    // CENTER: MMU / TLB / OS tabs (placeholder content for now)
    // -------------------------------------------------------------------------
    private TabPane buildTabPane(SimulationViewModel viewModel, PagedOSTabViewModel osTabViewModel) {
        // TabPane's skin doesn't report its tab contents' minimum sizes upward (a schematic tab's
        // ScrollPane hides its canvas's size from it anyway), so the strip's own height and the
        // largest tab minimum are added up here -- that is what keeps the workbench from ever being
        // laid out smaller than its widest/tallest schematic can draw in.
        TabPane tabPane = new TabPane() {
            @Override
            protected double computeMinWidth(double height) {
                return Math.max(super.computeMinWidth(height),
                        largestTabMinimum(true) + snappedLeftInset() + snappedRightInset());
            }

            @Override
            protected double computeMinHeight(double width) {
                double headerHeight = tabHeaderArea() instanceof Region strip ? strip.prefHeight(-1) : 0;
                return Math.max(super.computeMinHeight(width),
                        largestTabMinimum(false) + headerHeight + snappedTopInset() + snappedBottomInset());
            }

            // The skin adds the tab strip as a direct child, so scanning the children finds it. Not
            // lookup(): that walks the whole subtree -- and the skin stacks every tab's content
            // *before* the strip, so it would visit every node of every tab built so far, parsing
            // the selector afresh at each one -- and this runs whenever the minimum is recomputed,
            // i.e. on every layout pass of a window resize.
            private Node tabHeaderArea() {
                for (Node child : getChildrenUnmodifiable())
                    if (child.getStyleClass().contains(TAB_HEADER_AREA_STYLE_CLASS))
                        return child;
                return null;
            }

            private double largestTabMinimum(boolean width) {
                double largest = 0;
                for (Tab tab : getTabs()) {
                    Node content = tab.getContent();
                    if (content != null)
                        largest = Math.max(largest, width ? content.minWidth(-1) : content.minHeight(-1));
                }
                return largest;
            }
        };
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Only the selected tab is built here; the others are built the first time they are shown (see
        // lazyTab). Every tab is a full schematic of hundreds of nodes, and this whole view is built again
        // each time the UI scale changes (see UiScale), so building the three hidden ones each time
        // would make every resize several times slower for nothing.
        Tab mmuTab = lazyTab("MMU", () -> buildMmuTabContent(viewModel));
        Tab tlbTab = lazyTab("TLB", () -> buildTlbTabContent(viewModel));
        Tab osTab = lazyTab("OS", () -> buildOsTabContent(osTabViewModel));
        Tab memoryTab = lazyTab("Memory", () -> buildMemoryTabContent(viewModel));

        tabPane.getTabs().addAll(mmuTab, tlbTab, osTab, memoryTab);

        Map<Tab, SimulationComponent> tabComponents = Map.of(
                mmuTab, SimulationComponent.MMU, tlbTab, SimulationComponent.TLB,
                osTab, SimulationComponent.OS, memoryTab, SimulationComponent.MEMORY);

        // This view is rebuilt whenever the UI scale changes (see UiScale), and the view model
        // outlives it -- so it remembers which tab was up, and the rebuilt view opens on the same one.
        SimulationComponent remembered = viewModel.selectedComponentProperty().get();
        tabComponents.forEach((tab, component) -> {
            if (component == remembered)
                tabPane.getSelectionModel().select(tab);
        });
        buildTabContentIfPending(tabPane.getSelectionModel().getSelectedItem());

        // First (and only) place tab selection is tracked anywhere in this app -- the badges below
        // are the reason it's needed: a focused tab must never show its own notification.
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            buildTabContentIfPending(newTab);
            viewModel.selectedComponentProperty().set(tabComponents.get(newTab));
        });
        viewModel.selectedComponentProperty().set(tabComponents.get(tabPane.getSelectionModel().getSelectedItem()));

        tabComponents.forEach((tab, component) -> attachNotificationBadge(tab, viewModel, component));

        return tabPane;
    }

    // A tab whose real content is only built when it is first shown; until then it holds an empty
    // placeholder (which asks for no room and has nothing to style).
    private Tab lazyTab(String title, Supplier<Node> content) {
        Tab tab = new Tab(title, new Region());
        pendingTabContent.put(tab, content);
        return tab;
    }

    private void buildTabContentIfPending(Tab tab) {
        Supplier<Node> content = pendingTabContent.remove(tab);
        if (content != null)
            tab.setContent(content.get());
    }

    // Tab.setGraphic() is a plain, synchronous, first-class API -- no skin timing to work around --
    // but the moment *any* graphic is set, the tab's internal header Label reserves its own
    // -fx-graphic-text-gap next to the text, even for a graphic that reports zero width. So an
    // unaffected tab's text only ever sits exactly where it would with no badge feature at all if
    // the graphic isn't set there in the first place -- hence attaching/detaching the whole node
    // rather than trying to shrink it to nothing while inactive.
    private void attachNotificationBadge(Tab tab, SimulationViewModel viewModel, SimulationComponent component) {
        // Filled by CSS (.tab-notification-badge), like every other colour, so the theme decides it.
        Circle badge = new Circle(TAB_NOTIFICATION_BADGE_RADIUS);
        badge.getStyleClass().add("tab-notification-badge");
        double slotSize = TAB_NOTIFICATION_BADGE_RADIUS * 2 + TAB_NOTIFICATION_BADGE_GAP;
        StackPane badgeSlot = new StackPane(badge);
        badgeSlot.setMinSize(slotSize, TAB_NOTIFICATION_BADGE_RADIUS * 2);
        badgeSlot.setPrefSize(slotSize, TAB_NOTIFICATION_BADGE_RADIUS * 2);
        badgeSlot.setMaxSize(slotSize, TAB_NOTIFICATION_BADGE_RADIUS * 2);

        BooleanProperty notified = viewModel.tabNotificationProperty(component);
        Runnable syncGraphic = () -> tab.setGraphic(notified.get() ? badgeSlot : null);
        ChangeListener<Boolean> onNotificationChanged = (o, ov, nv) -> syncGraphic.run();
        notified.addListener(onNotificationChanged);
        teardown.add(() -> notified.removeListener(onNotificationChanged));
        syncGraphic.run();
    }

    private Node buildMemoryTabContent(SimulationViewModel viewModel) {
        if (viewModel.getContext() instanceof PageSimulationContext pageContext) {
            MemoryTabViewModel memoryTabViewModel = new MemoryTabViewModel(pageContext, viewModel);
            teardown.add(memoryTabViewModel::dispose);
            return new MemoryTabView(memoryTabViewModel);
        }

        return placeholderTabContent(
                "Memory\n\n" +
                "Will visualize physical memory contents around the currently addressed word.");
    }

    // osTabViewModel is built eagerly, in the constructor (see SimulationView(...)) -- not here --
    // so the View menu's Replacement Queue Inspector can use it regardless of whether this tab's
    // view has ever been built; null only when the context isn't paged yet.
    private Node buildOsTabContent(PagedOSTabViewModel osTabViewModel) {
        if (osTabViewModel != null)
            return new PagedOSTabView(osTabViewModel);

        return placeholderTabContent(
                "Operating System\n\n" +
                "Will visualize OS-level bookkeeping such as the eviction policy, page/segment table " +
                "management, and per-user memory allocation.");
    }

    private Node buildMmuTabContent(SimulationViewModel viewModel) {
        if (viewModel.getContext() instanceof PageSimulationContext pageContext) {
            PagedMMUTabViewModel mmuTabViewModel = new PagedMMUTabViewModel(pageContext, viewModel);
            teardown.add(mmuTabViewModel::dispose);
            return new PagedMMUTabView(mmuTabViewModel);
        }

        return placeholderTabContent(
                "Memory Management Unit\n\n" +
                "Will visualize the address translation pipeline (page tables, segment tables, or both), " +
                "specific to the configured translation type.");
    }

    private Label placeholderTabContent(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("tab-placeholder-label");
        label.setWrapText(true);
        return label;
    }

    private Node buildTlbTabContent(SimulationViewModel viewModel) {
        if (viewModel.getContext() instanceof PageSimulationContext pageContext) {
            PagedTLBTabViewModel tlbTabViewModel = new PagedTLBTabViewModel(pageContext, viewModel);
            teardown.add(tlbTabViewModel::dispose);
            return new PagedTLBTabView(tlbTabViewModel);
        }

        return placeholderTabContent(
                "Translation Lookaside Buffer\n\n" +
                "Will visualize the TLB lookup/indexing structure and the address-formation logic, " +
                "specific to the configured TLB type.");
    }

    // -------------------------------------------------------------------------
    // RIGHT SIDEBAR: Execution log, step description, step counter, navigation
    // -------------------------------------------------------------------------
    private VBox buildRightSidebar(SimulationViewModel viewModel) {
        // Same computePrefWidth-derived floor/ceiling buildLeftSidebar uses (see its own comment):
        // the widest real content here is the nav-button rows below, so this keeps the panel (and
        // so the SplitPane divider) from ever landing narrower than what "◀ Previous"/"Next ▶"
        // actually need -- no hand-picked pixel constant standing in for that measurement.
        VBox rightSidebar = new VBox(UiScale.px(12)) {
            @Override
            protected double computeMinWidth(double height) {
                return Math.max(SIDEBAR_MIN_WIDTH, super.computePrefWidth(height));
            }

            @Override
            protected double computeMaxWidth(double height) {
                return Math.max(SIDEBAR_MAX_WIDTH, super.computePrefWidth(height));
            }
        };
        rightSidebar.getStyleClass().add("sidebar-panel");

        Label logHeader = new Label("Execution Log");
        logHeader.getStyleClass().add("section-title");

        ListView<StepDescription> logListView = new ListView<>(viewModel.getLogEntries());
        logListView.getStyleClass().addAll("execution-log", "slim-scroll");
        logListView.setCellFactory(lv -> {
            ListCell<StepDescription> cell = new ListCell<>() {
                @Override
                protected void updateItem(StepDescription item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null
                            : "Step " + (getIndex() + 1) + ": " + StepDescriptionFormatter.format(item));
                }
            };
            // Clicking a past step reverts the simulation to right after that step ran.
            cell.setOnMouseClicked(e -> {
                if (!cell.isEmpty())
                    viewModel.revertToStep(cell.getIndex() + 1);
            });
            return cell;
        });
        logListView.setPrefHeight(EXECUTION_LOG_PREF_HEIGHT);

        // Follow the log as new steps are appended, so a step landing past the visible window
        // (the common case once the log outgrows the fixed-height panel) scrolls into view rather
        // than requiring the user to notice and scroll down manually.
        ListChangeListener<StepDescription> followLog = change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    int lastIndex = viewModel.getLogEntries().size() - 1;
                    Platform.runLater(() -> logListView.scrollTo(lastIndex));
                }
            }
        };
        viewModel.getLogEntries().addListener(followLog);
        teardown.add(() -> viewModel.getLogEntries().removeListener(followLog));
        // A view rebuilt mid-simulation (see UiScale) starts with the log already populated, and
        // should open at its end like the one it replaces was.
        if (!viewModel.getLogEntries().isEmpty()) {
            int lastIndex = viewModel.getLogEntries().size() - 1;
            new PostLayoutTask(logListView, () -> logListView.scrollTo(lastIndex), false).request();
        }

        Label stepDescHeader = new Label("Step Description");
        stepDescHeader.getStyleClass().add("section-title");

        Label stepDescLabel = new Label();
        stepDescLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    StepDescription description = viewModel.currentStepDescriptionProperty().get();
                    return description != null
                            ? StepDescriptionFormatter.format(description)
                            : viewModel.fallbackMessageProperty().get();
                },
                viewModel.currentStepDescriptionProperty(),
                viewModel.fallbackMessageProperty()));
        stepDescLabel.getStyleClass().add("sidebar-info-label");
        stepDescLabel.setWrapText(true);
        // A wrapping Label's own *preferred* width is still its full unwrapped single-line text --
        // wrapping only kicks in once something else constrains it narrower than that. Left alone,
        // this one-sentence status message (e.g. "Simulation ready. Press \"Next\" to begin
        // stepping through execution.") would report a many-hundred-pixel preferred width and,
        // being the widest child, dictate the whole sidebar's width now that it's sized from real
        // content (see buildRightSidebar). Pinning prefWidth to a small sentinel takes it out of
        // that computation entirely; maxWidth staying at Double.MAX_VALUE still lets the VBox's
        // own fillWidth stretch it to whatever the sidebar's *other* content (the nav rows) ends
        // up needing, which is exactly where it should wrap.
        stepDescLabel.setPrefWidth(1);
        stepDescLabel.setMaxWidth(Double.MAX_VALUE);
        // Pinned to one constant height (min = pref = max), so the nav rows below sit at a fixed
        // position no matter how long the current description is or how many lines it wraps to --
        // not derived from the text at all, unlike a "grows to fit, never shrinks" floor, which
        // still moves the buttons down the first time a longer message arrives. The trade-off: a
        // description that needs more than STEP_DESC_HEIGHT's ~3 lines (only plausible with the
        // sidebar dragged to its narrowest plus an unusually long message) is clipped rather than
        // shown in full -- the clip below makes that a clean cut instead of visibly overlapping
        // whatever sits underneath.
        stepDescLabel.setMinHeight(STEP_DESC_HEIGHT);
        stepDescLabel.setPrefHeight(STEP_DESC_HEIGHT);
        stepDescLabel.setMaxHeight(STEP_DESC_HEIGHT);
        javafx.scene.shape.Rectangle stepDescClip = new javafx.scene.shape.Rectangle();
        stepDescClip.widthProperty().bind(stepDescLabel.widthProperty());
        stepDescClip.setHeight(STEP_DESC_HEIGHT);
        stepDescLabel.setClip(stepDescClip);

        // A fixed spacer, not just the VBox's own uniform spacing -- purely for visual breathing
        // room between the description and the nav rows below (see the height floor above for what
        // actually stops them moving).
        Region stepDescGap = new Region();
        stepDescGap.setMinHeight(STEP_DESC_GAP);

        Label stepCounterLabel = new Label();
        stepCounterLabel.getStyleClass().add("step-counter-label");
        stepCounterLabel.textProperty().bind(viewModel.currentStepNumberProperty().asString("Step: %d"));

        Button prevBtn = new Button("◀ Previous");
        prevBtn.getStyleClass().add("button-primary");
        prevBtn.setOnAction(e -> viewModel.executePreviousStep());

        Button nextBtn = new Button("Next ▶");
        nextBtn.getStyleClass().add("button-primary");
        nextBtn.setOnAction(e -> viewModel.executeNextStep());

        HBox navBox = new HBox(UiScale.px(10), prevBtn, nextBtn);
        matchWidth(prevBtn, nextBtn);

        Label instructionCounterLabel = new Label();
        instructionCounterLabel.getStyleClass().add("step-counter-label");
        // currentInstructionIndex is -1 (its "no current instruction" sentinel) before the first
        // fetch; shown as "-" rather than leaking that internal value to the UI.
        instructionCounterLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    int index = viewModel.currentInstructionIndexProperty().get();
                    return "Instruction: " + (index < 0 ? "-" : index);
                },
                viewModel.currentInstructionIndexProperty()));

        Button instructionStartBtn = new Button("◀ Previous");
        instructionStartBtn.getStyleClass().add("button-primary");
        instructionStartBtn.setOnAction(e -> viewModel.revertToInstructionStart());

        Button nextInstructionBtn = new Button("Next ▶");
        nextInstructionBtn.getStyleClass().add("button-primary");
        nextInstructionBtn.setOnAction(e -> viewModel.executeNextInstruction());

        HBox instructionNavBox = new HBox(UiScale.px(10), instructionStartBtn, nextInstructionBtn);
        matchWidth(instructionStartBtn, nextInstructionBtn);

        rightSidebar.getChildren().addAll(
                logHeader, logListView, stepDescHeader, stepDescLabel, stepDescGap, stepCounterLabel, navBox,
                instructionCounterLabel, instructionNavBox);
        return rightSidebar;
    }

    /**
     * Exposes the root container layout node so App.java can mount it into the window scene.
     */
    public Parent getRootContainerNode() {
        return this.workbenchRoot;
    }
}
