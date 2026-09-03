package rs.ac.bg.etf;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;

import rs.ac.bg.etf.view.ConfigurationView;
import rs.ac.bg.etf.view.MainMenuView;
import rs.ac.bg.etf.view.SimulationView;
import rs.ac.bg.etf.viewmodel.AppViewModel;
import rs.ac.bg.etf.viewmodel.ApplicationScreenState;


/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;
    private AppViewModel appViewModel;
    
    private StackPane rootViewportBox; 

    @Override
    public void start(Stage stage) throws IOException 
    {

        stage.setTitle("Virtual Memory Simulator");

        this.appViewModel = new AppViewModel();
        this.rootViewportBox = new StackPane();

        appViewModel.currentScreenProperty().addListener((observable, oldState, newState) -> {
            handleScreenTransition(newState);
        });

        handleScreenTransition(ApplicationScreenState.MAIN_MENU);
    

        Scene scene = new Scene(rootViewportBox, 1024, 680);

        String cssPath = getClass().getResource("light-theme.css").toExternalForm();
        scene.getStylesheets().add(cssPath);

        stage.setScene(scene);

        stage.setMinWidth(800);
        stage.setMinHeight(600);

        stage.show();
        
    }

    private void handleScreenTransition(ApplicationScreenState state) {
        switch (state) {
            case MAIN_MENU: 
                // Pass the specific sub-ViewModel out of your coordinator safely
                MainMenuView menuView = new MainMenuView(appViewModel.getMainMenuViewModel());

                // Swap the root layout node container instantly inside our single Scene graph
                rootViewportBox.getChildren().setAll(menuView.getRootContainerNode());
                break;
            case CONFIGURATION:
                ConfigurationView configurationView = new ConfigurationView(appViewModel.getConfigurationViewModel());

                rootViewportBox.getChildren().setAll(configurationView.getRootContainerNode());
                break;

            case SETTINGS:
                System.out.println("App.java: Rendering Settings Screen...");
                break;

            case SIMULATION:
                SimulationView simulationView = new SimulationView(appViewModel.getSimulationViewModel());

                rootViewportBox.getChildren().setAll(simulationView.getRootContainerNode());
                break;

        }
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