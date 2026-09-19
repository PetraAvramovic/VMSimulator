package rs.ac.bg.etf.view;

import java.util.Map;

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
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.step.StepDescription;
import rs.ac.bg.etf.view.tlb.PagedTLBTabView;
import rs.ac.bg.etf.view.util.BackButton;
import rs.ac.bg.etf.view.util.StepDescriptionFormatter;
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
    private static final double SIDEBAR_MIN_WIDTH = 200;
    private static final double SIDEBAR_MAX_WIDTH = 420;
    // Circle's own CSS radius property isn't reliable for sizing (PagedMMUTabView's adder circle
    // sets its radius the same way, in code, and only styles fill/stroke via CSS -- see
    // .mmu-adder-circle), so the tab notification dot's size lives here instead of in CSS.
    private static final double TAB_NOTIFICATION_BADGE_RADIUS = 4;
    // Breathing room between the dot and the tab text that follows it, reserved only while the
    // badge is actually showing.
    private static final double TAB_NOTIFICATION_BADGE_GAP = 6;
    // The instruction list's Index column has no configured upper bound (an instruction file can be
    // any length), so its width is reserved for this many digits rather than measured from the
    // actual (possibly much shorter) instruction list -- otherwise a short test file sizes the
    // header/columns too narrow for whatever longer file replaces it later.
    private static final int MAX_INDEX_DIGITS = 4;
    // Extra breathing room below the step description, on top of the right sidebar's own uniform
    // child spacing -- see buildRightSidebar.
    private static final double STEP_DESC_GAP = 16;
    // Fixed reserve for the step description -- enough for 3 wrapped lines at its own font size, so
    // the nav rows below it sit at one constant position regardless of how long the current step's
    // description is or how many lines it wraps to. A description that somehow needs more than
    // that (an extreme case: the sidebar dragged to its narrowest plus an unusually long message)
    // is clipped rather than pushing the buttons around -- see buildRightSidebar.
    private static final double STEP_DESC_HEIGHT = 60;

    private final SplitPane layoutContainer;

    public SimulationView(SimulationViewModel viewModel) {
        VBox leftSidebar = buildLeftSidebar(viewModel);
        TabPane tabPane = buildTabPane(viewModel);
        VBox rightSidebar = buildRightSidebar(viewModel);

        this.layoutContainer = new SplitPane(leftSidebar, tabPane, rightSidebar);
        this.layoutContainer.getStyleClass().add("simulation-container");
        this.layoutContainer.setDividerPositions(0.18, 0.82);
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
        VBox leftSidebar = new VBox(12) {
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
        header.getStyleClass().add("column-header");

        Region instructionList = buildInstructionList(viewModel);

        Label currentVaHeader = new Label("Virtual Address");
        currentVaHeader.getStyleClass().add("column-header");
        Label currentVaLabel = new Label();
        currentVaLabel.getStyleClass().add("sidebar-value-label");
        currentVaLabel.textProperty().bind(viewModel.currentVirtualAddressHexProperty());

        Label currentPaHeader = new Label("Physical Address");
        currentPaHeader.getStyleClass().add("column-header");
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
        scrollPane.setPrefHeight(196);
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
        currentInstructionIndex.addListener((obs, oldIndex, newIndex) -> {
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
        });

        return card;
    }

    private Label instructionCell(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("page-table-cell");
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
    private TabPane buildTabPane(SimulationViewModel viewModel) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab mmuTab = new Tab("MMU", buildMmuTabContent(viewModel));
        Tab tlbTab = new Tab("TLB", buildTlbTabContent(viewModel));
        Tab osTab = new Tab("OS", buildOsTabContent(viewModel));
        Tab memoryTab = new Tab("Memory", buildMemoryTabContent(viewModel));

        tabPane.getTabs().addAll(mmuTab, tlbTab, osTab, memoryTab);

        Map<Tab, SimulationComponent> tabComponents = Map.of(
                mmuTab, SimulationComponent.MMU, tlbTab, SimulationComponent.TLB,
                osTab, SimulationComponent.OS, memoryTab, SimulationComponent.MEMORY);

        // First (and only) place tab selection is tracked anywhere in this app -- the badges below
        // are the reason it's needed: a focused tab must never show its own notification.
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) ->
                viewModel.selectedComponentProperty().set(tabComponents.get(newTab)));
        viewModel.selectedComponentProperty().set(tabComponents.get(tabPane.getSelectionModel().getSelectedItem()));

        tabComponents.forEach((tab, component) -> attachNotificationBadge(tab, viewModel, component));

        return tabPane;
    }

    // Tab.setGraphic() is a plain, synchronous, first-class API -- no skin timing to work around --
    // but the moment *any* graphic is set, the tab's internal header Label reserves its own
    // -fx-graphic-text-gap next to the text, even for a graphic that reports zero width. So an
    // unaffected tab's text only ever sits exactly where it would with no badge feature at all if
    // the graphic isn't set there in the first place -- hence attaching/detaching the whole node
    // rather than trying to shrink it to nothing while inactive.
    private void attachNotificationBadge(Tab tab, SimulationViewModel viewModel, SimulationComponent component) {
        Circle badge = new Circle(TAB_NOTIFICATION_BADGE_RADIUS, Color.web("#3498db"));
        double slotSize = TAB_NOTIFICATION_BADGE_RADIUS * 2 + TAB_NOTIFICATION_BADGE_GAP;
        StackPane badgeSlot = new StackPane(badge);
        badgeSlot.setMinSize(slotSize, TAB_NOTIFICATION_BADGE_RADIUS * 2);
        badgeSlot.setPrefSize(slotSize, TAB_NOTIFICATION_BADGE_RADIUS * 2);
        badgeSlot.setMaxSize(slotSize, TAB_NOTIFICATION_BADGE_RADIUS * 2);

        BooleanProperty notified = viewModel.tabNotificationProperty(component);
        Runnable syncGraphic = () -> tab.setGraphic(notified.get() ? badgeSlot : null);
        notified.addListener((o, ov, nv) -> syncGraphic.run());
        syncGraphic.run();
    }

    private Node buildMemoryTabContent(SimulationViewModel viewModel) {
        if (viewModel.getContext() instanceof PageSimulationContext pageContext) {
            MemoryTabViewModel memoryTabViewModel = new MemoryTabViewModel(pageContext, viewModel);
            return new MemoryTabView(memoryTabViewModel);
        }

        return placeholderTabContent(
                "Memory\n\n" +
                "Will visualize physical memory contents around the currently addressed word.");
    }

    private Node buildOsTabContent(SimulationViewModel viewModel) {
        if (viewModel.getContext() instanceof PageSimulationContext pageContext) {
            PagedOSTabViewModel osTabViewModel = new PagedOSTabViewModel(pageContext, viewModel);
            return new PagedOSTabView(osTabViewModel);
        }

        return placeholderTabContent(
                "Operating System\n\n" +
                "Will visualize OS-level bookkeeping such as the eviction policy, page/segment table " +
                "management, and per-user memory allocation.");
    }

    private Node buildMmuTabContent(SimulationViewModel viewModel) {
        if (viewModel.getContext() instanceof PageSimulationContext pageContext) {
            PagedMMUTabViewModel mmuTabViewModel = new PagedMMUTabViewModel(pageContext, viewModel);
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
        VBox rightSidebar = new VBox(12) {
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
        logHeader.getStyleClass().add("column-header");

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
        logListView.setPrefHeight(220);

        // Follow the log as new steps are appended, so a step landing past the visible window
        // (the common case once the log outgrows the fixed-height panel) scrolls into view rather
        // than requiring the user to notice and scroll down manually.
        viewModel.getLogEntries().addListener((ListChangeListener<StepDescription>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    int lastIndex = viewModel.getLogEntries().size() - 1;
                    Platform.runLater(() -> logListView.scrollTo(lastIndex));
                }
            }
        });

        Label stepDescHeader = new Label("Step Description");
        stepDescHeader.getStyleClass().add("column-header");

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

        HBox navBox = new HBox(10, prevBtn, nextBtn);
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

        HBox instructionNavBox = new HBox(10, instructionStartBtn, nextInstructionBtn);
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
        return this.layoutContainer;
    }
}
