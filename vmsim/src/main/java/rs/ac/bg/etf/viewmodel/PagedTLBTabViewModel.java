package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class PagedTLBTabViewModel 
{
    public record Row(boolean valid, boolean dirty, long tag, long block, boolean hit) {}

    private ObservableList<Row> rows = FXCollections.observableArrayList();

    private final StringProperty userHex = new SimpleStringProperty("/");
    private final StringProperty pageHex = new SimpleStringProperty("/");
    private final StringProperty wordHex = new SimpleStringProperty("/");
    private final StringProperty blockHex = new SimpleStringProperty("/");
    private final StringProperty paWordHex = new SimpleStringProperty("/");
    private final StringProperty tlbAddress = new SimpleStringProperty("");
    private final StringProperty tlbTag = new SimpleStringProperty("");

    
}
