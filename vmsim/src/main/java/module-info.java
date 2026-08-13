module rs.ac.bg.etf {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.dataformat.toml;

    opens rs.ac.bg.etf to javafx.fxml;
    opens rs.ac.bg.etf.model.simulation to com.fasterxml.jackson.databind;
    opens rs.ac.bg.etf.model.memory to com.fasterxml.jackson.databind;

    exports rs.ac.bg.etf;
}
