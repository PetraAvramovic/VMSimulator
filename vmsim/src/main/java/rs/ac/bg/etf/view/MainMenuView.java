package rs.ac.bg.etf.view;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import rs.ac.bg.etf.viewmodel.MainMenuViewModel;

public class MainMenuView {
    private final VBox layoutContainer;

    public MainMenuView(MainMenuViewModel viewModel) {
        // The menu is a small block of fixed content, so its natural size is also the smallest it
        // should ever be drawn at: a window narrower or shorter than that scales the whole menu
        // down (see ResponsiveHost) instead of clipping it.
        this.layoutContainer = new VBox() {
            @Override
            protected double computeMinWidth(double height) {
                return computePrefWidth(height);
            }

            @Override
            protected double computeMinHeight(double width) {
                return computePrefHeight(width);
            }
        };

        // 1. Tag the master box with the CSS class container name
        this.layoutContainer.getStyleClass().add("screen-container");

        // 2. Headings Labels Text Elements
        Label titleLabel = new Label("Virtual Memory Simulator");
        titleLabel.getStyleClass().addAll("screen-title", "main-menu-title");

        // 3. Resume Button Widget (only shown once a simulation exists to return to)
        Button resumeBtn = new Button("Resume");
        resumeBtn.getStyleClass().addAll("button-large", "button-primary"); // Blue button accent rule!
        resumeBtn.setOnAction(e -> viewModel.executeResumeNavigation());
        resumeBtn.visibleProperty().bind(viewModel.resumeAvailableProperty());
        resumeBtn.managedProperty().bind(resumeBtn.visibleProperty());

        // 4. New Simulation Button Widget: carries the primary accent only while Resume is hidden,
        //    so exactly one action is highlighted at a time.
        Button startBtn = new Button("New Simulation");
        startBtn.getStyleClass().add("button-large");
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
        settingsBtn.getStyleClass().add("button-large");
        settingsBtn.setOnAction(e -> viewModel.executeSettingsRequest());

        // Mount all layout nodes seamlessly onto your panel viewport container
        layoutContainer.getChildren().addAll(titleLabel, resumeBtn, startBtn, settingsBtn);
    }

    /**
     * Exposes the root container layout node so App.java can clip it to the window scene.
     */
    public Parent getRootContainerNode() 
    {
        return this.layoutContainer;
    }
}