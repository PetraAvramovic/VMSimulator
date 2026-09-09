package rs.ac.bg.etf.view.tlb;

import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Bounds;
import javafx.scene.effect.BoxBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Direct-mapped TLB body view: a windowed row table, like the MMU tab's page table. There is no
 * parallel search bus -- the owning tab view routes the index bits straight into whichever row the
 * current instruction selected. Exposes that row's left edge as the address anchor (falling back to
 * the middle row until the lookup runs) and the table's bottom-centre as a layout landmark for the
 * block output. Rows are blurred/dimmed until the TLB has actually been consulted.
 */
public class DirectTLBView extends StackPane implements TLBBodyView
{
    private final VBox tableBox = new VBox();
    // Just the data rows, kept separate from the header so it can be fogged while the header stays crisp.
    private final VBox rowsBody = new VBox();
    private final Pane overlay = new Pane();
    private final List<TLBRowView> rows;
    private final ObservableValue<Number> selectedWindowRow;

    private final Region selectionAnchor = marker();
    private final Region bottomAnchor = marker();
    private final BooleanProperty active = new SimpleBooleanProperty(false);
    private final ObjectProperty<Region> addressAnchor = new SimpleObjectProperty<>();
    private final ObjectProperty<Region> tableBottomAnchor = new SimpleObjectProperty<>();

    public DirectTLBView(TLBRowView header, List<TLBRowView> rows, ObservableValue<Number> selectedWindowRow)
    {
        this.rows = rows;
        this.selectedWindowRow = selectedWindowRow;

        // A bordered panel behind the rows, matching AssociativeTLBView / the page table.
        tableBox.getStyleClass().add("tlb-table");
        rowsBody.getChildren().addAll(rows);
        tableBox.getChildren().addAll(header, rowsBody);

        overlay.setMouseTransparent(true);
        overlay.getChildren().addAll(selectionAnchor, bottomAnchor);

        getChildren().addAll(tableBox, overlay);
        addressAnchor.set(selectionAnchor);
        tableBottomAnchor.set(bottomAnchor);

        Runnable reposition = () -> Platform.runLater(this::repositionAnchors);
        sceneProperty().addListener((obs, oldVal, newVal) -> reposition.run());
        widthProperty().addListener((obs, oldVal, newVal) -> reposition.run());
        heightProperty().addListener((obs, oldVal, newVal) -> reposition.run());
        for (TLBRowView row : rows)
            row.visibleProperty().addListener((obs, oldVal, newVal) -> reposition.run());
        selectedWindowRow.addListener((obs, oldVal, newVal) -> reposition.run());

        active.addListener((obs, oldVal, newVal) -> {
            setFogged(!newVal);
            reposition.run();
        });
        setFogged(!active.get());

        reposition.run();
    }

    @Override
    public Region getNode()
    {
        return this;
    }

    @Override
    public ObjectProperty<Region> addressAnchorProperty()
    {
        return addressAnchor;
    }

    @Override
    public ObjectProperty<Region> tableBottomAnchorProperty()
    {
        return tableBottomAnchor;
    }

    @Override
    public BooleanProperty activeProperty()
    {
        return active;
    }

    private void repositionAnchors()
    {
        if (getScene() == null)
            return;

        List<TLBRowView> visible = rows.stream()
                .filter(row -> row.isVisible() && row.getScene() != null)
                .toList();
        if (visible.isEmpty()) {
            selectionAnchor.setVisible(false);
            bottomAnchor.setVisible(false);
            return;
        }

        Bounds panel = overlay.sceneToLocal(tableBox.localToScene(tableBox.getBoundsInLocal()));

        Number sel = selectedWindowRow.getValue();
        int selIndex = sel == null ? -1 : sel.intValue();
        // Before the lookup resolves (or if the selected row scrolled out of the window) the wire
        // parks on the middle row, mirroring PageTableView's fallback.
        TLBRowView target = (active.get() && selIndex >= 0 && selIndex < visible.size())
                ? visible.get(selIndex)
                : visible.get(visible.size() / 2);
        Bounds rowBounds = overlay.sceneToLocal(target.localToScene(target.getBoundsInLocal()));

        selectionAnchor.setVisible(true);
        selectionAnchor.setLayoutX(panel.getMinX());          // left border of the .tlb-table panel
        selectionAnchor.setLayoutY(rowBounds.getCenterY());   // vertical centre of the selected row

        bottomAnchor.setVisible(true);
        bottomAnchor.setLayoutX(panel.getCenterX());
        bottomAnchor.setLayoutY(panel.getMaxY());
    }

    // Blur + dim the rows until the TLB has actually been consulted for the current instruction,
    // matching AssociativeTLBView and the page table.
    private void setFogged(boolean fogged)
    {
        rowsBody.setEffect(fogged ? new BoxBlur(6, 6, 3) : null);
        rowsBody.setOpacity(fogged ? 0.45 : 1.0);
    }

    private static Region marker()
    {
        Region region = new Region();
        region.setManaged(false);
        region.setMouseTransparent(true);
        region.setPrefSize(1, 1);
        return region;
    }
}
