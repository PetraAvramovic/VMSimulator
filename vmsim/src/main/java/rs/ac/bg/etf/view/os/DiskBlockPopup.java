package rs.ac.bg.etf.view.os;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.DiskWord;

/**
 * Modeless window listing the full contents of the disk block currently in transit.
 * One reused instance; its table is bound to the view model so it tracks stepping while open.
 */
public class DiskBlockPopup
{
    private final PagedOSTabViewModel viewModel;
    private final Label caption = new Label();
    private Stage stage;

    public DiskBlockPopup(PagedOSTabViewModel viewModel)
    {
        this.viewModel = viewModel;
    }

    public void toggle(Window owner)
    {
        if (stage != null && stage.isShowing())
        {
            stage.hide();
            return;
        }
        open(owner);
    }

    private void open(Window owner)
    {
        if (stage == null)
            stage = build(owner);

        caption.textProperty().bind(viewModel.diskAddressHexProperty()
                .map(addr -> "Disk block " + addr + "  (" + viewModel.diskBlockSummaryProperty().get() + ")"));
        stage.show();
        stage.toFront();
    }

    private Stage build(Window owner)
    {
        TableView<DiskWord> table = new TableView<>(viewModel.getDiskBlockWords());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("no non-zero data present"));

        int offsetDigits = ValueConverter.hexDigitsFor(Math.max(1, viewModel.getWordBits()));
        TableColumn<DiskWord, String> offsetCol = new TableColumn<>("Offset");
        offsetCol.setCellValueFactory(cd -> new SimpleStringProperty(ValueConverter.toHex(cd.getValue().offset(), offsetDigits)));
        TableColumn<DiskWord, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(cd -> new SimpleStringProperty(ValueConverter.toHex(cd.getValue().value(), 1)));
        table.getColumns().add(offsetCol);
        table.getColumns().add(valueCol);

        VBox root = new VBox(8, caption, table);
        root.setStyle("-fx-padding: 12;");
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        Stage s = new Stage();
        s.initModality(Modality.NONE);
        if (owner != null)
            s.initOwner(owner);
        s.setTitle("Disk block");
        s.setScene(new Scene(root, 320, 420));
        return s;
    }
}
