package rs.ac.bg.etf;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.fxml.FXMLLoader;

import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import rs.ac.bg.etf.model.settings.AppTheme;
import rs.ac.bg.etf.view.ConfigurationView;
import rs.ac.bg.etf.view.MainMenuView;
import rs.ac.bg.etf.view.SettingsView;
import rs.ac.bg.etf.view.SimulationView;
import rs.ac.bg.etf.view.util.InspectorWindows;
import rs.ac.bg.etf.view.util.ResponsiveHost;
import rs.ac.bg.etf.view.util.ResponsiveLayout;
import rs.ac.bg.etf.view.util.UiScale;
import rs.ac.bg.etf.viewmodel.AppViewModel;
import rs.ac.bg.etf.viewmodel.ApplicationScreenState;


/**
 * JavaFX App
 */
public class App extends Application {

    // Start with -Dvmsim.timing=true to have every UI-scale rebuild report how long it took.
    private static final boolean TIMING = Boolean.getBoolean("vmsim.timing");

    private static Scene scene;
    private AppViewModel appViewModel;

    // Hosts whichever screen is showing and tells us when the window wants a different UI scale --
    // see ResponsiveHost / UiScale. A screen is drawn at real pixel sizes for the current scale, so
    // a scale change means building it again.
    private ResponsiveHost rootViewport;
    private Scene mainScene;
    // The screen currently mounted, kept so it can be torn down (SimulationView unhooks itself from
    // the view models) and rebuilt when the UI scale changes. Tracked here rather than read from
    // AppViewModel.currentScreenProperty(), which stays null until the first navigation (the app
    // starts on the main menu without ever setting it).
    private ApplicationScreenState shownScreen = ApplicationScreenState.MAIN_MENU;
    private SimulationView simulationView;

    // A change to a screen's minimum size can arrive several times in one layout pass; the window's
    // own minimum is only recomputed once per pass.
    private boolean windowMinimumSyncPending;

    @Override
    public void start(Stage stage) throws IOException
    {

        stage.setTitle("Virtual Memory Simulator");

        this.appViewModel = new AppViewModel();
        this.rootViewport = new ResponsiveHost();
        this.rootViewport.setOnScaleRequested(this::changeScale);

        // The saved theme has to be current before the first scene is styled below; after that the
        // settings screen drives it.
        ObjectProperty<AppTheme> theme = appViewModel.getSettingsViewModel().themeProperty();
        UiScale.setTheme(theme.get());
        theme.addListener((observable, oldTheme, newTheme) -> changeTheme(newTheme));

        appViewModel.currentScreenProperty().addListener((observable, oldState, newState) -> {
            handleScreenTransition(newState, false);
            // The new screen was built at the previous screen's scale. Take the scale this window wants
            // for it now, before a frame is drawn, instead of showing it at the wrong size and
            // rebuilding once the resize settle delay has passed.
            adoptWantedScale();
        });

        handleScreenTransition(ApplicationScreenState.MAIN_MENU, false);

        // Sized from the display it opens on rather than a fixed pixel size, so it always fits
        // (a fixed 1024x760 window is already taller than a 768-line laptop screen leaves room for).
        Rectangle2D usable = Screen.getPrimary().getVisualBounds();
        Scene scene = new Scene(rootViewport,
                usable.getWidth() * ResponsiveLayout.INITIAL_WINDOW_FRACTION,
                usable.getHeight() * ResponsiveLayout.INITIAL_WINDOW_FRACTION);
        this.mainScene = scene;

        UiScale.applyTheme(scene);

        stage.setScene(scene);

        keepWindowMinimumInSync(stage, scene, usable);
        // Opens filling the screen; the size above is what un-maximizing restores to.
        stage.setMaximized(true);

        stage.show();

    }

    /**
     * The window has settled at a size that wants a different UI scale: switch to it, and then to
     * whatever the rebuilt screen's real measurements say it still needs (see {@link #adoptWantedScale}).
     */
    private void changeScale(double newScale) {
        if (switchScale(newScale))
            adoptWantedScale();
    }

    /**
     * Switches to the scale the mounted screen wants, and keeps going while the rebuilt screen wants
     * yet another (its measured minimum can come out a little above the estimate the scale was chosen
     * from). All in one go, so no intermediate scale is ever drawn.
     */
    private void adoptWantedScale() {
        for (int switches = 0; switches < ResponsiveLayout.MAX_SCALE_SWITCHES; switches++) {
            double wanted = rootViewport.wantedScale();
            if (wanted == UiScale.factor() || !switchScale(wanted))
                return;
        }
    }

    /**
     * Switches the theme and every scaled length over to {@code newScale} and builds the current screen
     * again at that scale, already laid out (see {@link ResponsiveHost#settleNow}).
     *
     * @return false if nothing was switched (already at that scale, or its theme could not be prepared)
     */
    private boolean switchScale(double newScale) {
        if (newScale == UiScale.factor())
            return false;

        long started = System.nanoTime();
        if (!UiScale.prepareTheme(newScale))
            return false;
        long themeReady = System.nanoTime();

        // Take the old screen out of the scene *before* the stylesheet changes: a stylesheet change
        // restyles everything mounted, so with the old screen still in place the whole old tree would
        // be styled at the new scale only to be thrown away, and then the new one styled again.
        if (simulationView != null) {
            simulationView.dispose();
            simulationView = null;
        }
        rootViewport.setContent(null, true);

        UiScale.setFactor(newScale);
        UiScale.applyTheme(mainScene);
        long themeApplied = System.nanoTime();
        handleScreenTransition(shownScreen, true);

        if (TIMING) {
            long done = System.nanoTime();
            System.err.printf("UI scale -> %.2f: theme %d ms, restyle %d ms, build %d ms (includes laying the new screen out)%n",
                    newScale, (themeReady - started) / 1_000_000, (themeApplied - themeReady) / 1_000_000,
                    (done - themeApplied) / 1_000_000);
        }
        return true;
    }

    /**
     * The user picked another theme: make it the current one and restyle every window that is open.
     * Themes differ only in colours (see {@link UiScale}), so unlike a scale change nothing is
     * rebuilt -- the mounted screens are simply restyled in place.
     */
    private void changeTheme(AppTheme newTheme) {
        if (newTheme == null || newTheme == UiScale.theme())
            return;

        // Inspector and editor windows have scenes of their own. Only Stages are restyled: a popup
        // (a ComboBox's list, say) follows the scene that owns it.
        List<Scene> scenes = new ArrayList<>();
        for (Window window : Window.getWindows())
            if (window instanceof Stage && window.getScene() != null)
                scenes.add(window.getScene());

        // If the theme could not be applied, put the setting back so it doesn't claim one that isn't showing.
        if (!UiScale.changeTheme(newTheme, scenes))
            appViewModel.getSettingsViewModel().themeProperty().set(UiScale.theme());
    }

    /**
     * Keeps the window from being shrunk below what the current screen can be scaled down to
     * legibly ({@link ResponsiveLayout#MIN_SCALE}). The screen reports that in scene pixels; the
     * window's own minimum also has to cover its title bar and borders, which are only known once
     * it is showing.
     */
    private void keepWindowMinimumInSync(Stage stage, Scene scene, Rectangle2D usable) {
        Runnable sync = () -> {
            windowMinimumSyncPending = false;
            double decorationWidth = stage.getWidth() - scene.getWidth();
            double decorationHeight = stage.getHeight() - scene.getHeight();
            if (Double.isNaN(decorationWidth) || Double.isNaN(decorationHeight)) {
                decorationWidth = 0;
                decorationHeight = 0;
            }

            // Never ask for more than the display has: on a tiny screen the smallest legible
            // layout simply can't fit, and the screen is then drawn at the floor scale instead.
            stage.setMinWidth(Math.min(usable.getWidth(),
                    Math.max(ResponsiveLayout.MIN_WINDOW_WIDTH, rootViewport.getMinSceneWidth() + decorationWidth)));
            stage.setMinHeight(Math.min(usable.getHeight(),
                    Math.max(ResponsiveLayout.MIN_WINDOW_HEIGHT, rootViewport.getMinSceneHeight() + decorationHeight)));
        };
        Runnable scheduleSync = () -> {
            if (!windowMinimumSyncPending) {
                windowMinimumSyncPending = true;
                Platform.runLater(sync);
            }
        };

        rootViewport.minSceneWidthProperty().addListener((observable, oldValue, newValue) -> scheduleSync.run());
        rootViewport.minSceneHeightProperty().addListener((observable, oldValue, newValue) -> scheduleSync.run());
        stage.showingProperty().addListener((observable, wasShowing, isShowing) -> {
            if (isShowing)
                scheduleSync.run();
        });
    }

    /**
     * Builds and mounts a screen.
     *
     * @param rebuild true when the same screen is being rebuilt at a new UI scale (so the old one is
     *                torn down and its measured minimum size carries over), false for navigation
     */
    private void handleScreenTransition(ApplicationScreenState state, boolean rebuild) {
        // The inspector windows belong to the simulation: leaving it (its back button) closes them, so
        // none stays open over the main menu. A rebuild for a new UI scale is the same screen again --
        // they stay.
        if (!rebuild && shownScreen == ApplicationScreenState.SIMULATION && state != ApplicationScreenState.SIMULATION)
            InspectorWindows.closeAll();

        if (simulationView != null) {
            simulationView.dispose();
            simulationView = null;
        }
        shownScreen = state;

        switch (state) {
            case MAIN_MENU:
                // Pass the specific sub-ViewModel out of your coordinator safely
                MainMenuView menuView = new MainMenuView(appViewModel.getMainMenuViewModel());

                // Swap the screen hosted inside the single Scene graph instantly
                rootViewport.setContent(menuView.getRootContainerNode(), rebuild);
                break;
            case CONFIGURATION:
                ConfigurationView configurationView = new ConfigurationView(appViewModel.getConfigurationViewModel());

                rootViewport.setContent(configurationView.getRootContainerNode(), rebuild);
                break;

            case SETTINGS:
                SettingsView settingsView = new SettingsView(appViewModel.getSettingsViewModel());

                rootViewport.setContent(settingsView.getRootContainerNode(), rebuild);
                break;

            case SIMULATION:
                simulationView = new SimulationView(appViewModel.getSimulationViewModel());

                rootViewport.setContent(simulationView.getRootContainerNode(), rebuild);
                break;

        }

        // Lay the new screen out completely now, in this event, so the first frame drawn is the final
        // one and not the first of several intermediate layouts (which shows wires in the wrong place
        // for a moment). A no-op before the window exists.
        rootViewport.settleNow();
    }


    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }

}
