package rs.ac.bg.etf;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.SimulationConfig;
import rs.ac.bg.etf.model.simulation.exceptions.InvalidConfig;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;

    @Override
    public void start(Stage stage) throws IOException {
        SimulationConfig config = new SimulationConfig();
        config.setTlbSize(2048);
        config = SimulationConfig.loadFromFile("C:\\Users\\gaga6\\OneDrive\\Desktop\\Faks\\Diplomski\\VMSimulator\\config\\sim_config.toml", config);

        //System.out.println(config.toString());

        PageSimulationContext context = new PageSimulationContext(config);
        context.init();

        System.out.println(context.toString());

        try {
            config.validateConfig();
        } catch (InvalidConfig e) {
          
            e.printStackTrace();
        }

        scene = new Scene(loadFXML("primary"), 640, 480);
        stage.setScene(scene);
        stage.show();
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