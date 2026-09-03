package rs.ac.bg.etf.view;

import javafx.scene.Parent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.viewmodel.PagedMMUTabViewModel;
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

    private void clampSidebarWidth(VBox sidebar) {
        sidebar.setMinWidth(SIDEBAR_MIN_WIDTH);
        sidebar.setMaxWidth(SIDEBAR_MAX_WIDTH);
    }

    // -------------------------------------------------------------------------
    // LEFT SIDEBAR: Virtual address list + current VA/PA readouts
    // -------------------------------------------------------------------------
    private VBox buildLeftSidebar(SimulationViewModel viewModel) {
        VBox leftSidebar = new VBox(12);
        leftSidebar.getStyleClass().add("sidebar-panel");

        Label header = new Label("Virtual Addresses");
        header.getStyleClass().add("column-header");

        Label placeholder = new Label(
                "The list of virtual addresses being executed will be displayed here once the MMU implementation is connected.");
        placeholder.getStyleClass().add("sidebar-info-label");

        Label currentVaHeader = new Label("Current VA");
        currentVaHeader.getStyleClass().add("column-header");
        Label currentVaLabel = new Label();
        currentVaLabel.textProperty().bind(viewModel.currentVirtualAddressHexProperty());

        Label currentPaHeader = new Label("Current PA");
        currentPaHeader.getStyleClass().add("column-header");
        Label currentPaLabel = new Label();
        currentPaLabel.textProperty().bind(viewModel.currentPhysicalAddressHexProperty());

        leftSidebar.getChildren().addAll(
                header, placeholder, currentVaHeader, currentVaLabel, currentPaHeader, currentPaLabel);
        return leftSidebar;
    }

    // -------------------------------------------------------------------------
    // CENTER: MMU / TLB / OS tabs (placeholder content for now)
    // -------------------------------------------------------------------------
    private TabPane buildTabPane(SimulationViewModel viewModel) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab mmuTab = new Tab("MMU", buildMmuTabContent(viewModel));

        Tab tlbTab = new Tab("TLB", placeholderTabContent(
                "Translation Lookaside Buffer\n\n" +
                "Will visualize the TLB lookup/indexing structure and the address-formation logic, " +
                "specific to the configured TLB type."));

        Tab osTab = new Tab("OS", placeholderTabContent(
                "Operating System\n\n" +
                "Will visualize OS-level bookkeeping such as the eviction policy, page/segment table " +
                "management, and per-user memory allocation."));

        tabPane.getTabs().addAll(mmuTab, tlbTab, osTab);
        return tabPane;
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
}
