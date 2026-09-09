package rs.ac.bg.etf.view;

import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.view.tlb.PagedTLBTabView;
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

    private final SplitPane layoutContainer;

    public SimulationView(SimulationViewModel viewModel) {
        VBox leftSidebar = buildLeftSidebar(viewModel);
        TabPane tabPane = buildTabPane(viewModel);
        VBox rightSidebar = buildRightSidebar(viewModel);

        clampSidebarWidth(leftSidebar);
        clampSidebarWidth(rightSidebar);

        this.layoutContainer = new SplitPane(leftSidebar, tabPane, rightSidebar);
        this.layoutContainer.getStyleClass().add("simulation-container");
        this.layoutContainer.setDividerPositions(0.18, 0.82);
    }

    private Button buildBackButton(SimulationViewModel viewModel) {
        Button backButton = new Button("←");
        backButton.getStyleClass().add("back-button");
        backButton.setFocusTraversable(false);
        backButton.setOnAction(e -> viewModel.navigateBack());
        return backButton;
    }

    private void clampSidebarWidth(VBox sidebar) {
        sidebar.setMinWidth(SIDEBAR_MIN_WIDTH);
        sidebar.setMaxWidth(SIDEBAR_MAX_WIDTH);
    }

    // -------------------------------------------------------------------------
    // LEFT SIDEBAR: Instruction list + current VA/PA readouts
    // -------------------------------------------------------------------------
    private VBox buildLeftSidebar(SimulationViewModel viewModel) {
        VBox leftSidebar = new VBox(12);
        leftSidebar.getStyleClass().add("sidebar-panel");

        Button backButton = buildBackButton(viewModel);

        Label header = new Label("Instructions");
        header.getStyleClass().add("column-header");

        ListView<String> instructionListView = new ListView<>(viewModel.getInstructionEntries());
        instructionListView.getStyleClass().add("instruction-list");
        instructionListView.setPrefHeight(220);
        instructionListView.setFocusTraversable(false);
        instructionListView.setSelectionModel(null);

        ObservableValue<Number> currentInstructionIndex = viewModel.currentInstructionIndexProperty();
        instructionListView.setCellFactory(list -> new InstructionCell(currentInstructionIndex));

        // Pin the running instruction to the top of the viewport: a bare scrollTo(index) only
        // guarantees visibility (landing the row at the bottom when advancing forward), so first
        // park the last row at the bottom, then scroll back up so the target settles at the top.
        // Near the end of the list there aren't enough rows below it to reach the very top.
        currentInstructionIndex.addListener((obs, oldIndex, newIndex) -> {
            int index = newIndex.intValue();
            int lastRow = viewModel.getInstructionEntries().size() - 1;
            if (index >= 0 && lastRow >= 0) {
                instructionListView.scrollTo(lastRow);
                instructionListView.scrollTo(index);
            }
        });

        Label currentVaHeader = new Label("Current VA");
        currentVaHeader.getStyleClass().add("column-header");
        Label currentVaLabel = new Label();
        currentVaLabel.textProperty().bind(viewModel.currentVirtualAddressHexProperty());

        Label currentPaHeader = new Label("Current PA");
        currentPaHeader.getStyleClass().add("column-header");
        Label currentPaLabel = new Label();
        currentPaLabel.textProperty().bind(viewModel.currentPhysicalAddressHexProperty());

        leftSidebar.getChildren().addAll(
                backButton, header, instructionListView, currentVaHeader, currentVaLabel, currentPaHeader, currentPaLabel);
        return leftSidebar;
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

        tabPane.getTabs().addAll(mmuTab, tlbTab, osTab);
        return tabPane;
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
        VBox rightSidebar = new VBox(12);
        rightSidebar.getStyleClass().add("sidebar-panel");

        Label logHeader = new Label("Execution Log");
        logHeader.getStyleClass().add("column-header");

        ListView<String> logListView = new ListView<>(viewModel.getLogEntries());
        logListView.setPrefHeight(220);

        Label stepDescHeader = new Label("Step Description");
        stepDescHeader.getStyleClass().add("column-header");

        Label stepDescLabel = new Label();
        stepDescLabel.textProperty().bind(viewModel.stepDescriptionProperty());
        stepDescLabel.getStyleClass().add("sidebar-info-label");

        Label stepCounterLabel = new Label();
        stepCounterLabel.getStyleClass().add("step-counter-label");
        stepCounterLabel.textProperty().bind(viewModel.currentStepNumberProperty().asString("Step: %d"));

        Button prevBtn = new Button("◀ Previous");
        prevBtn.setOnAction(e -> viewModel.executePreviousStep());

        Button nextBtn = new Button("Next ▶");
        nextBtn.setOnAction(e -> viewModel.executeNextStep());

        HBox navBox = new HBox(10, prevBtn, nextBtn);

        rightSidebar.getChildren().addAll(
                logHeader, logListView, stepDescHeader, stepDescLabel, stepCounterLabel, navBox);
        return rightSidebar;
    }

    /**
     * Exposes the root container layout node so App.java can mount it into the window scene.
     */
    public Parent getRootContainerNode() {
        return this.layoutContainer;
    }

    /**
     * Instruction-list cell that carries a {@code :current} pseudo-class while its row is the
     * instruction being executed, tracked live off the view model's current-index property.
     */
    private static final class InstructionCell extends ListCell<String> {
        private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");

        private final ObservableValue<Number> currentInstructionIndex;

        InstructionCell(ObservableValue<Number> currentInstructionIndex) {
            this.currentInstructionIndex = currentInstructionIndex;
            currentInstructionIndex.addListener((obs, oldIndex, newIndex) -> refreshCurrent());
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item);
            refreshCurrent();
        }

        private void refreshCurrent() {
            boolean current = !isEmpty() && getIndex() == currentInstructionIndex.getValue().intValue();
            pseudoClassStateChanged(CURRENT, current);
        }
    }
}
