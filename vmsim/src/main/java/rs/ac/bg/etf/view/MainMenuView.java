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

        // 3. Start Button Widget (Primary Color Action Tag)
        Button startBtn = new Button("Start Simulation");
        startBtn.getStyleClass().add("button-primary"); // Blue button accent rule!
        startBtn.setOnAction(e -> viewModel.executeStartNavigation());

        // 4. Settings Button Widget (Standard Neutral Styling)
        Button settingsBtn = new Button("Settings");
        settingsBtn.setOnAction(e -> viewModel.executeSettingsRequest());

        // Mount all layout nodes seamlessly onto your panel viewport container
        layoutContainer.getChildren().addAll(titleLabel, subtitleLabel, startBtn, settingsBtn);
    }

    /**
     * Exposes the root container layout node so App.java can clip it to the window scene.
     */
    public Parent getRootContainerNode() 
    {
        return this.layoutContainer;
    }
}