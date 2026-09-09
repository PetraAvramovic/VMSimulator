package rs.ac.bg.etf.view;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import rs.ac.bg.etf.viewmodel.MainMenuViewModel;

public class MainMenuView {
    private final VBox layoutContainer;

    public MainMenuView(MainMenuViewModel viewModel) {
        this.layoutContainer = new VBox();
        
        // 1. Tag the master box with the CSS class container name
        this.layoutContainer.getStyleClass().add("main-menu-container");

        // 2. Headings Labels Text Elements
        Label titleLabel = new Label("Virtual Memory Simulator");
        titleLabel.getStyleClass().add("main-menu-title");

        Label subtitleLabel = new Label("Architecture workbench pipeline framework verification tier");
        subtitleLabel.getStyleClass().add("main-menu-subtitle");

        // 3. Resume Button Widget (only shown once a simulation exists to return to)
        Button resumeBtn = new Button("Resume");
        resumeBtn.getStyleClass().add("button-primary"); // Blue button accent rule!
        resumeBtn.setOnAction(e -> viewModel.executeResumeNavigation());
        resumeBtn.visibleProperty().bind(viewModel.resumeAvailableProperty());
        resumeBtn.managedProperty().bind(resumeBtn.visibleProperty());

        // 4. New Simulation Button Widget: carries the primary accent only while Resume is hidden,
        //    so exactly one action is highlighted at a time.
        Button startBtn = new Button("New Simulation");
        startBtn.setOnAction(e -> viewModel.executeStartNavigation());
        Runnable syncStartAccent = () -> {
            startBtn.getStyleClass().remove("button-primary");
            if (!viewModel.resumeAvailableProperty().get()) {
                startBtn.getStyleClass().add("button-primary");
            }
        };
        viewModel.resumeAvailableProperty().addListener((obs, was, is) -> syncStartAccent.run());
        syncStartAccent.run();

        // 5. Settings Button Widget (Standard Neutral Styling)
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> viewModel.executeSettingsRequest());

        // Mount all layout nodes seamlessly onto your panel viewport container
        layoutContainer.getChildren().addAll(titleLabel, subtitleLabel, resumeBtn, startBtn, settingsBtn);
    }

    /**
     * Exposes the root container layout node so App.java can clip it to the window scene.
     */
    public Parent getRootContainerNode() 
    {
        return this.layoutContainer;
    }
}