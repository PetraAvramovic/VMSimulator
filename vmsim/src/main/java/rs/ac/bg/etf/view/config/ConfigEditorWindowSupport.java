package rs.ac.bg.etf.view.config;

import java.util.function.Supplier;

import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Shared chrome for the config screen's bulk-data editor windows (Instructions, Page Table
 * Entries, Initial Memory Content): a title, a description, an "Add Row" button, a live row count,
 * and the caller-built {@link TableView} itself. Mirrors the read-only inspector windows' Stage
 * setup ({@code view/inspector/*InspectorWindow}), but these tables are editable and their content
 * -- an {@link ObservableList} the config screen's viewmodel owns -- is what gets edited, not a
 * live simulation snapshot.
 */
final class ConfigEditorWindowSupport
{
    private static final double ROOT_PADDING = 16;
    private static final double DEFAULT_WIDTH = 560;
    private static final double DEFAULT_HEIGHT = 420;
    private static final double MIN_WIDTH = 420;
    private static final double MIN_HEIGHT = 320;
    // How far a column may grow past the prefWidth its editor window gave it when the table is
    // resized wider -- enough to soak up modest extra window width without either leaving the
    // whole table looking editor-window-cramped (UNCONSTRAINED_RESIZE_POLICY, no stretch at all)
    // or stretching every checkbox/hex value out to fill an arbitrarily wide window
    // (CONSTRAINED_RESIZE_POLICY with no cap).
    private static final double MAX_WIDTH_STRETCH_FACTOR = 1.5;

    private ConfigEditorWindowSupport() {}

    static <T> Stage build(
            Window owner,
            String stageTitle,
            String sectionLabel,
            String description,
            TableView<T> table,
            ObservableList<T> items,
            Supplier<T> newRowFactory)
    {
        table.setEditable(true);
        table.setItems(items);
        table.getStyleClass().addAll("slim-scroll", "config-editor-table");

        // Row order is meaningful (instruction execution order; first-match page/user lookups), so
        // clicking a header must never let TableView silently re-sort the backing list in place.
        // Non-resizable columns (the trailing delete column) are left alone -- their fixed
        // prefWidth is already the intended width, not a floor to grow from.
        for (TableColumn<T, ?> column : table.getColumns()) {
            column.setSortable(false);
            if (column.isResizable() && column.getPrefWidth() > 0) {
                column.setMinWidth(column.getPrefWidth());
                column.setMaxWidth(column.getPrefWidth() * MAX_WIDTH_STRETCH_FACTOR);
            }
        }
        // Columns stretch to fill extra window width, but only up to the cap set above; once every
        // column is at its max, any further width is left blank rather than keeping stretched.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        VBox.setVgrow(table, Priority.ALWAYS);

        // The native header row is replaced below with a plain Label per column, each bound to
        // that column's own widthProperty() -- guaranteeing it lines up with the column's cells
        // exactly, rather than trying to hand-match Modena's own (undocumented, version-specific)
        // header padding/reserved-sort-arrow space to .table-cell's padding. CSS collapses the
        // native header to zero height, since TableView has no supported way to drop it outright.
        HBox headerRow = new HBox();
        headerRow.getStyleClass().add("config-editor-table-header");
        for (TableColumn<T, ?> column : table.getColumns()) {
            Label headerLabel = new Label(column.getText());
            headerLabel.getStyleClass().add("config-editor-table-header-cell");
            headerLabel.prefWidthProperty().bind(column.widthProperty());
            headerLabel.minWidthProperty().bind(column.widthProperty());
            headerLabel.maxWidthProperty().bind(column.widthProperty());
            headerRow.getChildren().add(headerLabel);
        }

        VBox tableCard = new VBox(headerRow, table);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label titleLabel = new Label(sectionLabel);
        titleLabel.getStyleClass().add("column-header");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("main-menu-subtitle");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setPadding(new Insets(0, 0, 0, 0));

        Button addButton = new Button("+ Add Row");
        addButton.setOnAction(e -> {
            T row = newRowFactory.get();
            items.add(row);
            table.getSelectionModel().select(row);
            table.scrollTo(row);
        });

        Label countLabel = new Label();
        countLabel.textProperty().bind(Bindings.concat(Bindings.size(items).asString(), " row(s)"));
        countLabel.getStyleClass().add("main-menu-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox toolbar = new HBox(10, addButton, spacer, countLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(10, titleLabel, descriptionLabel, toolbar, tableCard);
        root.setPadding(new Insets(ROOT_PADDING));

        Stage stage = new Stage();
        stage.initModality(Modality.NONE);
        if (owner != null)
            stage.initOwner(owner);
        stage.setTitle(stageTitle);

        Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
        scene.getStylesheets().add(
                ConfigEditorWindowSupport.class.getResource("/rs/ac/bg/etf/light-theme.css").toExternalForm());
        stage.setScene(scene);

        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        return stage;
    }

    /** A narrow, non-sortable trailing column with one "remove this row" button per row. */
    static <T> TableColumn<T, Void> deleteColumn(ObservableList<T> items)
    {
        TableColumn<T, Void> column = new TableColumn<>("");
        column.setSortable(false);
        column.setReorderable(false);
        column.setResizable(false);
        column.setPrefWidth(48);
        column.setMinWidth(48);
        column.setMaxWidth(48);
        column.setCellFactory(col -> new TableCell<>() {
            private final Button removeButton = new Button("✕");
            {
                getStyleClass().add("row-delete-cell");
                removeButton.getStyleClass().add("row-delete-button");
                removeButton.setOnAction(e -> {
                    T item = getTableView().getItems().get(getIndex());
                    items.remove(item);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty)
            {
                super.updateItem(item, empty);
                setGraphic(empty ? null : removeButton);
            }
        });
        return column;
    }
}
