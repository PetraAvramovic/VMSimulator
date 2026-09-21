package rs.ac.bg.etf.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import rs.ac.bg.etf.model.settings.AppTheme;
import rs.ac.bg.etf.view.util.BackButton;
import rs.ac.bg.etf.view.util.UiScale;
import rs.ac.bg.etf.viewmodel.SettingsViewModel;

/**
 * The settings screen: application-wide preferences, currently just the colour theme. A change
 * takes effect immediately -- the view only edits {@link SettingsViewModel#themeProperty()}, and
 * {@code App} restyles every open window when it changes.
 */
public class SettingsView {
    // Distance the floating back button sits from the top-left corner, matching the config screen.
    private final Insets BACK_BUTTON_MARGIN = UiScale.insets(15);

    private final Parent rootContainer;

    public SettingsView(SettingsViewModel viewModel) {
        // A small block of fixed content, so (as on the main menu) its natural size is also the
        // smallest it should be drawn at: a window narrower or shorter than that rebuilds the screen
        // at a smaller UI scale (see ResponsiveHost) instead of clipping it.
        VBox layoutContainer = new VBox() {
            @Override
            protected double computeMinWidth(double height) {
                return computePrefWidth(height);
            }

            @Override
            protected double computeMinHeight(double width) {
                return computePrefHeight(width);
            }
        };
        layoutContainer.getStyleClass().add("screen-container");

        Label titleLabel = new Label("Settings");
        titleLabel.getStyleClass().add("screen-title");

        Label subtitleLabel = new Label("Customize how the simulator looks.");
        subtitleLabel.getStyleClass().add("screen-subtitle");

        // ---- Appearance ------------------------------------------------------------------
        Label appearanceHeader = new Label("Appearance");
        appearanceHeader.getStyleClass().add("section-title");

        Label themeLabel = new Label("Theme");
        ComboBox<AppTheme> themeBox = new ComboBox<>();
        themeBox.getItems().setAll(AppTheme.values());
        themeBox.setConverter(new StringConverter<>() {
            @Override public String toString(AppTheme theme) {
                return theme == null ? "" : theme.displayName();
            }
            @Override public AppTheme fromString(String text) { return null; }
        });
        themeBox.valueProperty().bindBidirectional(viewModel.themeProperty());
        VBox themeField = new VBox(UiScale.px(5), themeLabel, themeBox);

        VBox form = new VBox(UiScale.px(15), appearanceHeader, themeField);
        form.getStyleClass().add("form-column");
        // Only as wide as its fields, so the container centres it rather than stretching it across the window.
        form.setMaxWidth(Region.USE_PREF_SIZE);

        layoutContainer.getChildren().addAll(titleLabel, subtitleLabel, form);

        // Same circular chevron back button as the other screens, floating over the content.
        Button backButton = BackButton.create(viewModel::executeBackNavigation);
        StackPane root = new StackPane(layoutContainer, backButton);
        StackPane.setAlignment(backButton, Pos.TOP_LEFT);
        StackPane.setMargin(backButton, BACK_BUTTON_MARGIN);

        this.rootContainer = root;
    }

    /**
     * Exposes the root container node so App.java can mount it in the viewport.
     */
    public Parent getRootContainerNode() {
        return rootContainer;
    }
}
