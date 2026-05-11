module rs.ac.bg.etf {
    requires javafx.controls;
    requires javafx.fxml;

    opens rs.ac.bg.etf to javafx.fxml;
    exports rs.ac.bg.etf;
}
