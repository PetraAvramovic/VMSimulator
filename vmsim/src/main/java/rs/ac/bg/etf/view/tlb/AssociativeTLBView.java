package rs.ac.bg.etf.view.tlb;

import java.util.List;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.effect.BoxBlur;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;

/**
 * Renders TLB rows with short stub lines fanning out from every row's left edge into a single
 * shared vertical bus line, illustrating the fully-associative (parallel) tag search. Exposes an
 * anchor at the bus line's midpoint (where the owning tab view routes the tag wire) and a second
 * anchor at the bottom-centre of the table (a layout landmark the tab view uses to route the
 * block output).
 */
public class AssociativeTLBView extends StackPane implements TLBBodyView
{
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final double BUS_STUB_LENGTH = 18.0;

    private final VBox rowsBox = new VBox();
    // Just the data rows, kept separate from the header so it can be fogged while the header stays crisp.
    private final VBox rowsBody = new VBox();
    private final Pane linesOverlay = new Pane();
    private final List<TLBRowView> rows;
    private final Line busLine = line();
    private final Region busAnchor = new Region();
    private final Region bottomAnchor = new Region();
    private final BooleanProperty active = new SimpleBooleanProperty(false);
    private final ObjectProperty<Region> addressAnchor = new SimpleObjectProperty<>();
    private final ObjectProperty<Region> tableBottomAnchor = new SimpleObjectProperty<>();

    public AssociativeTLBView(TLBRowView header, List<TLBRowView> rows)
    {
        this.rows = rows;

        // A bordered panel behind the rows so the search-bus stub lines visibly terminate on the
        // table instead of floating in empty space.
        rowsBox.getStyleClass().add("tlb-table");
        rowsBody.getChildren().addAll(rows);
        rowsBox.getChildren().addAll(header, rowsBody);

        marker(busAnchor);
        marker(bottomAnchor);
        linesOverlay.getChildren().add(busLine);
        linesOverlay.getChildren().add(busAnchor);
        linesOverlay.getChildren().add(bottomAnchor);

        for (TLBRowView row : rows) {
            Line stub = line();
            linesOverlay.getChildren().add(stub);
            row.getProperties().put("tlbBusStub", stub);
        }

        getChildren().addAll(rowsBox, linesOverlay);
        addressAnchor.set(busAnchor);
        tableBottomAnchor.set(bottomAnchor);

        Runnable reposition = () -> Platform.runLater(this::repositionLines);
        sceneProperty().addListener((obs, oldVal, newVal) -> reposition.run());
        widthProperty().addListener((obs, oldVal, newVal) -> reposition.run());
        heightProperty().addListener((obs, oldVal, newVal) -> reposition.run());
        for (TLBRowView row : rows)
            row.visibleProperty().addListener((obs, oldVal, newVal) -> reposition.run());

        active.addListener((obs, oldVal, newVal) -> {
            busLine.pseudoClassStateChanged(ACTIVE, newVal);
            for (TLBRowView row : rows) {
                Node stub = (Node) row.getProperties().get("tlbBusStub");
                if (stub != null)
                    stub.pseudoClassStateChanged(ACTIVE, newVal);
            }
            setFogged(!newVal);
        });
        setFogged(!active.get());

        reposition.run();
    }

    @Override
    public Region getNode() {
        return this;
    }

    @Override
    public ObjectProperty<Region> addressAnchorProperty() {
        return addressAnchor;
    }

    @Override
    public ObjectProperty<Region> tableBottomAnchorProperty() {
        return tableBottomAnchor;
    }

    @Override
    public BooleanProperty activeProperty() {
        return active;
    }

    private void repositionLines() {
        Region topRow = null;
        Region bottomRow = null;
        double busX = 0;
        double panelLeftX = 0;
        double panelCenterX = 0;
        double panelBottomY = 0;
        boolean busXResolved = false;

        for (TLBRowView row : rows) {
            Line stub = (Line) row.getProperties().get("tlbBusStub");
            if (!row.isVisible() || row.getScene() == null) {
                stub.setVisible(false);
                continue;
            }

            Bounds rowBounds = linesOverlay.sceneToLocal(row.localToScene(row.getBoundsInLocal()));
            double rowCenterY = rowBounds.getCenterY();

            if (!busXResolved) {
                // Stubs meet the .tlb-table panel's outer border, never the row content inside its
                // padding, so no segment is drawn across the table body.
                Bounds panelBounds = linesOverlay.sceneToLocal(rowsBox.localToScene(rowsBox.getBoundsInLocal()));
                panelLeftX = panelBounds.getMinX();
                panelCenterX = panelBounds.getCenterX();
                panelBottomY = panelBounds.getMaxY();
                busX = panelLeftX - BUS_STUB_LENGTH;
                busXResolved = true;
            }

            stub.setVisible(true);
            stub.setStartX(panelLeftX);
            stub.setStartY(rowCenterY);
            stub.setEndX(busX);
            stub.setEndY(rowCenterY);

            if (topRow == null)
                topRow = row;
            bottomRow = row;
        }

        if (!busXResolved || topRow == null) {
            busLine.setVisible(false);
            busAnchor.setVisible(false);
            bottomAnchor.setVisible(false);
            return;
        }

        Bounds topBounds = linesOverlay.sceneToLocal(topRow.localToScene(topRow.getBoundsInLocal()));
        Bounds bottomBounds = linesOverlay.sceneToLocal(bottomRow.localToScene(bottomRow.getBoundsInLocal()));
        double topY = topBounds.getCenterY();
        double bottomY = bottomBounds.getCenterY();

        busLine.setVisible(true);
        busLine.setStartX(busX);
        busLine.setStartY(topY);
        busLine.setEndX(busX);
        busLine.setEndY(bottomY);

        busAnchor.setVisible(true);
        busAnchor.setLayoutX(busX);
        busAnchor.setLayoutY((topY + bottomY) / 2);

        bottomAnchor.setVisible(true);
        bottomAnchor.setLayoutX(panelCenterX);
        bottomAnchor.setLayoutY(panelBottomY);
    }

    // Blur + dim the rows until the TLB has actually been consulted for the current instruction,
    // matching the page table's "not yet accessed" treatment.
    private void setFogged(boolean fogged) {
        rowsBody.setEffect(fogged ? new BoxBlur(6, 6, 3) : null);
        rowsBody.setOpacity(fogged ? 0.45 : 1.0);
    }

    private static void marker(Region region) {
        region.setManaged(false);
        region.setMouseTransparent(true);
        region.setPrefSize(1, 1);
    }

    private Line line() {
        Line line = new Line();
        // Unmanaged so the hand-positioned bus/stub geometry (which reaches left of the rows, into
        // negative overlay coords) never inflates the overlay's bounds and shoves the rows around.
        line.setManaged(false);
        line.getStyleClass().add("tlb-search-line");
        return line;
    }
}
