package rs.ac.bg.etf.view.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;
import javafx.util.converter.LongStringConverter;

import rs.ac.bg.etf.view.util.HexLongConverter;

/**
 * Shared chrome for the config screen's bulk-data editor windows (Instructions, Page Table
 * Entries, Initial Memory Content): a title, a description, an "Add Row" button, a live row count,
 * and a hand-rolled header/rows table built from HBox/VBox/Label -- deliberately mirroring
 * {@code WindowedTableView.headerCell()}/{@code RowNode} (the app's other, already-correctly-
 * aligned hand-rolled table) node-for-node: a header cell and a row's cell for the same column are
 * both a plain node with {@code setPrefWidth(width)} + {@code setAlignment(Pos.CENTER)} set
 * directly, nothing wrapping it and nothing else touching its width. Earlier attempts wrapped
 * cells in a StackPane and centered/sized the wrapper instead, or via CSS/bound properties; TextField
 * and ComboBox both carry a built-in style class ("text-field"/"combo-box") that collides with this
 * stylesheet's own unrelated, unscoped width rules for the config screen's own fields -- dropping
 * those default classes (see {@code openValueEditor}/{@code openEnumEditor}) removes that trap too.
 *
 * <p>Each row is rebuilt from scratch on any change to the backing list (see {@code rebuildRows}),
 * which is what lets {@code reorderable} (drag-to-reorder, e.g. for Instructions, where row order
 * is execution order) be just a drag handle that removes + re-inserts the dragged item at the drop
 * target's index -- the same list mutation Add/Remove/edit already trigger a re-render through.
 */
final class ConfigEditorWindowSupport
{
    private static final double ROOT_PADDING = 16;
    private static final double DEFAULT_WIDTH = 560;
    private static final double DEFAULT_HEIGHT = 420;
    private static final double MIN_WIDTH = 420;
    private static final double MIN_HEIGHT = 320;
    private static final double DELETE_COLUMN_WIDTH = 40;
    private static final double DRAG_HANDLE_WIDTH = 28;
    private static final double ROW_HEIGHT = 34;
    // Gap between adjacent columns. Applied identically to the header row and every data row (both
    // built below) so it never breaks their column-for-column alignment.
    private static final double COLUMN_SPACING = 6;
    // Width slim-scroll's vertical scroll-bar can claim from the rows viewport once enough rows
    // overflow it -- reserved in the computed minimum width below so a scrollbar appearing can't
    // squeeze row cells narrower than what the (unaffected, scrollbar-free) header already committed to.
    private static final double SCROLLBAR_ALLOWANCE = 12;

    private ConfigEditorWindowSupport() {}

    static <T> Stage build(
            Window owner,
            String stageTitle,
            String sectionLabel,
            String description,
            ObservableList<T> items,
            List<EditorColumn<T>> columns,
            Supplier<T> newRowFactory)
    {
        return build(owner, stageTitle, sectionLabel, description, items, columns, newRowFactory, false);
    }

    /** @param reorderable whether each row gets a drag handle to reorder the list by dragging (see
     *                     the class doc) -- only meaningful where row order itself matters. */
    static <T> Stage build(
            Window owner,
            String stageTitle,
            String sectionLabel,
            String description,
            ObservableList<T> items,
            List<EditorColumn<T>> columns,
            Supplier<T> newRowFactory,
            boolean reorderable)
    {
        HBox headerRow = new HBox(COLUMN_SPACING);
        headerRow.getStyleClass().add("config-editor-header-row");
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPrefHeight(ROW_HEIGHT);
        if (reorderable)
            headerRow.getChildren().add(fixedWidthSpacer(DRAG_HANDLE_WIDTH));
        for (EditorColumn<T> column : columns)
            headerRow.getChildren().add(headerCell(column.header, column.width));
        headerRow.getChildren().add(fixedWidthSpacer(DELETE_COLUMN_WIDTH));

        VBox rowsBox = new VBox();
        // Row order is meaningful (instruction execution order; first-match page/user lookups), so
        // rows are drawn in list order with no sorting affordance at all -- unlike TableView, there
        // is no header-click-to-sort to accidentally trigger.
        Runnable rebuildRows = () -> {
            rowsBox.getChildren().clear();
            for (T item : items)
                rowsBox.getChildren().add(buildRow(item, items, columns, reorderable));
        };
        rebuildRows.run();
        items.addListener((ListChangeListener<T>) change -> rebuildRows.run());

        ScrollPane rowsScroll = new ScrollPane(rowsBox);
        rowsScroll.getStyleClass().addAll("slim-scroll", "config-editor-rows-scroll");
        rowsScroll.setFitToWidth(true);
        rowsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(rowsScroll, Priority.ALWAYS);

        VBox tableCard = new VBox(headerRow, rowsScroll);
        tableCard.getStyleClass().add("config-editor-card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label titleLabel = new Label(sectionLabel);
        titleLabel.getStyleClass().add("column-header");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("main-menu-subtitle");
        descriptionLabel.setWrapText(true);

        Button addButton = new Button("+ Add Row");
        addButton.setOnAction(e -> {
            items.add(newRowFactory.get());
            rowsScroll.setVvalue(1.0);
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

        // The table's own actual content width (mirrors WindowedTableView.minimumWidth() ->
        // stage.setMinWidth() in the inspector windows) -- otherwise a header/row cell can be
        // squeezed by HBox below what its text needs and silently ellipsize to "...", since neither
        // carries its own minWidth lock (only prefWidth, matching WindowedTableView.headerCell()
        // exactly); the fix is ensuring the window itself can never get that narrow in the first
        // place, not locking every cell's width individually. Also used to open wide enough to show
        // the whole table immediately, not just to floor how far it can be shrunk afterwards.
        int cellCount = columns.size() + 1 + (reorderable ? 1 : 0); // + delete column, + drag handle
        double contentWidth = columns.stream().mapToDouble(c -> c.width).sum()
                + DELETE_COLUMN_WIDTH
                + (reorderable ? DRAG_HANDLE_WIDTH : 0)
                + (cellCount - 1) * COLUMN_SPACING // one gap between each pair of adjacent cells
                + 2 * ROOT_PADDING
                + SCROLLBAR_ALLOWANCE;

        Scene scene = new Scene(root, Math.max(DEFAULT_WIDTH, contentWidth), DEFAULT_HEIGHT);
        scene.getStylesheets().add(
                ConfigEditorWindowSupport.class.getResource("/rs/ac/bg/etf/light-theme.css").toExternalForm());
        stage.setScene(scene);

        stage.setMinWidth(Math.max(MIN_WIDTH, contentWidth));
        stage.setMinHeight(MIN_HEIGHT);

        return stage;
    }

    private static <T> HBox buildRow(T item, ObservableList<T> items, List<EditorColumn<T>> columns, boolean reorderable)
    {
        HBox row = new HBox(COLUMN_SPACING);
        row.getStyleClass().add("config-editor-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(ROW_HEIGHT);
        row.setPrefHeight(ROW_HEIGHT);
        row.setMaxHeight(ROW_HEIGHT);

        if (reorderable)
            row.getChildren().add(dragHandleCell(item, items, row));

        for (EditorColumn<T> column : columns) {
            int index = row.getChildren().size();
            Region cell = column.cellFactory.build(item, column.width,
                    replacement -> row.getChildren().set(index, replacement));
            row.getChildren().add(cell);
        }

        Button removeButton = new Button("✕");
        removeButton.getStyleClass().add("row-delete-button");
        removeButton.setOnAction(e -> items.remove(item));
        HBox deleteCell = new HBox(removeButton);
        deleteCell.setAlignment(Pos.CENTER);
        deleteCell.setPrefWidth(DELETE_COLUMN_WIDTH);
        deleteCell.setMinWidth(DELETE_COLUMN_WIDTH);
        deleteCell.setMaxWidth(DELETE_COLUMN_WIDTH);
        row.getChildren().add(deleteCell);

        return row;
    }

    /**
     * A drag handle cell: dragging it starts a same-window reorder (JavaFX's standard
     * Dragboard-based drag-and-drop, restricted to plain string payloads carrying just the
     * dragged item's identity), with the whole row (not just the handle) accepting the drop so
     * there's a comfortably large target. Dropping on a row removes the dragged item from its old
     * position and re-inserts it at that row's current index -- an ordinary list mutation the
     * existing rebuildRows listener already re-renders in response to.
     */
    private static <T> Region dragHandleCell(T item, ObservableList<T> items, HBox row)
    {
        Label handle = new Label("⋮⋮");
        handle.getStyleClass().add("config-editor-drag-handle");
        handle.setPrefWidth(DRAG_HANDLE_WIDTH);
        handle.setMinWidth(DRAG_HANDLE_WIDTH);
        handle.setMaxWidth(DRAG_HANDLE_WIDTH);
        handle.setAlignment(Pos.CENTER);

        handle.setOnDragDetected(event -> {
            Dragboard dragboard = handle.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(String.valueOf(items.indexOf(item)));
            dragboard.setContent(content);
            row.getStyleClass().add("config-editor-row-dragging");
            event.consume();
        });
        handle.setOnDragDone(event -> {
            row.getStyleClass().remove("config-editor-row-dragging");
            event.consume();
        });

        row.setOnDragOver(event -> {
            if (event.getGestureSource() != handle && event.getDragboard().hasString())
                event.acceptTransferModes(TransferMode.MOVE);
            event.consume();
        });
        row.setOnDragEntered(event -> {
            if (event.getGestureSource() != handle && event.getDragboard().hasString())
                row.getStyleClass().add("config-editor-row-drag-over");
        });
        row.setOnDragExited(event -> row.getStyleClass().remove("config-editor-row-drag-over"));
        row.setOnDragDropped(event -> {
            Dragboard dragboard = event.getDragboard();
            boolean success = dragboard.hasString();
            if (success) {
                int sourceIndex = Integer.parseInt(dragboard.getString());
                // Guard against dropping a row onto itself: items.get(sourceIndex) == item then,
                // and removing it first would make items.indexOf(item) return -1 afterwards.
                if (sourceIndex >= 0 && sourceIndex < items.size() && items.get(sourceIndex) != item) {
                    T dragged = items.remove(sourceIndex);
                    items.add(items.indexOf(item), dragged);
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });

        return handle;
    }

    /** Mirrors WindowedTableView.headerCell() exactly: a Label with only prefWidth + CENTER
     *  alignment set, nothing else touching its size. */
    private static Label headerCell(String text, double width)
    {
        Label label = new Label(text);
        label.getStyleClass().add("config-editor-header-cell");
        label.setPrefWidth(width);
        label.setAlignment(Pos.CENTER);
        return label;
    }

    private static Region fixedWidthSpacer(double width)
    {
        Region region = new Region();
        region.setPrefWidth(width);
        region.setMinWidth(width);
        region.setMaxWidth(width);
        return region;
    }

    // -------------------------------------------------------------------------------------------
    // Shared cell builders. Every value cell displays as a plain, borderless label -- matching
    // this column's header exactly (same style class, same width, same setAlignment(CENTER) call)
    // -- and only swaps to an actual input control on double-click (openValueEditor/openEnumEditor),
    // mirroring TableView's own TextFieldTableCell/ComboBoxTableCell double-click-to-edit
    // convention. Boolean cells are the one exception: a checkbox is conventionally always live,
    // never label-then-edit, in TableView's own CheckBoxTableCell too.
    // -------------------------------------------------------------------------------------------

    /** A decimal-only integer cell (User/UserId columns). */
    static Region intCell(IntegerProperty property, double width, Consumer<Region> replaceSelf)
    {
        return valueCell(property.get(), new IntegerStringConverter(), property::set, width, replaceSelf);
    }

    /** A decimal-only long cell (Page/Offset columns). */
    static Region longCell(LongProperty property, double width, Consumer<Region> replaceSelf)
    {
        return valueCell(property.get(), new LongStringConverter(), property::set, width, replaceSelf);
    }

    /** A hex-displayed long cell (address/value/block columns). */
    static Region hexLongCell(LongProperty property, double width, Consumer<Region> replaceSelf)
    {
        return valueCell(property.get(), new HexLongConverter(), property::set, width, replaceSelf);
    }

    /** A checkbox bound directly to a boolean column (Valid/Dirty) -- always interactive, no
     *  click-to-edit step, matching TableView's own CheckBoxTableCell convention. Wrapped in its
     *  own StackPane: CheckBoxSkin doesn't honor setAlignment()/-fx-alignment the way a Label does,
     *  so the checkbox is centered by the wrapper instead (StackPane's own default alignment is
     *  already CENTER), with the fixed width set on the wrapper, not the checkbox itself. */
    static Region boolField(BooleanProperty property, double width, Consumer<Region> replaceSelf)
    {
        CheckBox checkBox = new CheckBox();
        checkBox.getStyleClass().add("config-editor-checkbox");
        checkBox.selectedProperty().bindBidirectional(property);

        StackPane wrapper = new StackPane(checkBox);
        wrapper.setPrefWidth(width);
        wrapper.setMinWidth(width);
        wrapper.setMaxWidth(width);
        return wrapper;
    }

    /** A double-click-to-edit dropdown cell for an enum-valued column (Access). */
    static <E extends Enum<E>> Region enumCell(ObjectProperty<E> property, E[] values, double width, Consumer<Region> replaceSelf)
    {
        Label display = new Label(property.get().name());
        display.getStyleClass().add("config-editor-cell-label");
        display.setPrefWidth(width);
        display.setAlignment(Pos.CENTER);

        display.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2)
                openEnumEditor(property, values, width, display, replaceSelf);
        });

        return display;
    }

    private static <E extends Enum<E>> void openEnumEditor(
            ObjectProperty<E> property, E[] values, double width, Label display, Consumer<Region> replaceSelf)
    {
        ComboBox<E> editor = new ComboBox<>(FXCollections.observableArrayList(values));
        // setAll, not add: ComboBox's own built-in "combo-box" style class carries a global,
        // unscoped -fx-pref-width: 180px rule (from the config screen's own combo boxes) that would
        // also match this node since it loads the same stylesheet.
        editor.getStyleClass().setAll("config-editor-combo");
        editor.setPrefWidth(width);
        editor.setValue(property.get());

        boolean[] handled = { false };
        Runnable commit = () -> {
            if (handled[0])
                return;
            handled[0] = true;
            E value = editor.getValue() == null ? property.get() : editor.getValue();
            property.set(value);
            display.setText(value.name());
            replaceSelf.accept(display);
        };

        editor.setOnAction(e -> commit.run());
        editor.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused)
                commit.run();
        });

        replaceSelf.accept(editor);
        editor.requestFocus();
        editor.show();
    }

    /** Shared implementation behind {@link #intCell}/{@link #longCell}/{@link #hexLongCell}: a
     *  label showing {@code converter}'s formatting of the current value, swapping to a TextField
     *  pre-filled with the same on double-click (see {@link #openValueEditor}). */
    private static <N> Region valueCell(N initialValue, StringConverter<N> converter, Consumer<N> onCommit, double width, Consumer<Region> replaceSelf)
    {
        AtomicReference<N> current = new AtomicReference<>(initialValue);

        Label display = new Label(converter.toString(initialValue));
        display.getStyleClass().add("config-editor-cell-label");
        display.setPrefWidth(width);
        display.setAlignment(Pos.CENTER);

        display.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2)
                openValueEditor(current, converter, onCommit, width, display, replaceSelf);
        });

        return display;
    }

    /** Enter/losing focus commits (invalid text is ignored, keeping the previous value); Escape
     *  cancels without committing. */
    private static <N> void openValueEditor(
            AtomicReference<N> current, StringConverter<N> converter, Consumer<N> onCommit,
            double width, Label display, Consumer<Region> replaceSelf)
    {
        TextField editor = new TextField(converter.toString(current.get()));
        // setAll, not add: TextField's own built-in "text-field" style class carries the same kind
        // of global, unscoped -fx-pref-width: 180px rule.
        editor.getStyleClass().setAll("config-editor-field");
        editor.setPrefWidth(width);

        boolean[] handled = { false };
        Runnable commit = () -> {
            if (handled[0])
                return;
            handled[0] = true;
            N parsed;
            try {
                parsed = converter.fromString(editor.getText());
            } catch (RuntimeException e) {
                parsed = current.get(); // invalid input -- keep the previous value rather than crash the row
            }
            current.set(parsed);
            onCommit.accept(parsed);
            display.setText(converter.toString(parsed));
            replaceSelf.accept(display);
        };
        Runnable cancel = () -> {
            if (handled[0])
                return;
            handled[0] = true;
            replaceSelf.accept(display);
        };

        editor.setOnAction(e -> commit.run());
        editor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE)
                cancel.run();
        });
        editor.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused)
                commit.run();
        });

        replaceSelf.accept(editor);
        editor.requestFocus();
        editor.selectAll();
    }
}
