package rs.ac.bg.etf.view.tlb;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.scene.layout.Region;

/**
 * Contract shared by every TLB-type body view (associative now; direct / set-associative later).
 * Deliberately translation-type-agnostic: the body view only publishes layout landmarks, it does
 * not know whether the address is split or where the block leaves. The owning tab view
 * ({@link PagedTLBTabView}) decides what to feed into {@link #addressAnchorProperty()} (the whole
 * tag for associative, only the low index/set bits for the others) and always routes the block
 * output off the bottom of the table via {@link #tableBottomAnchorProperty()}.
 */
public interface TLBBodyView {
    /** The body view itself, for adding to a parent's scene graph. */
    Region getNode();

    /**
     * Marker node where the tab view routes its address wire in: the tag for a fully-associative
     * TLB, the index/set bits for direct / set-associative.
     */
    ObjectProperty<Region> addressAnchorProperty();

    /**
     * Marker node at the bottom-centre of the row table. A pure layout landmark carrying no
     * "block" meaning; the tab view combines it with the row's block-column X to place the
     * block-output wire.
     */
    ObjectProperty<Region> tableBottomAnchorProperty();

    /** Lights the body view's search wiring while the lookup step is the active one. */
    BooleanProperty activeProperty();

    /**
     * Moves the anchor markers to match the current layout right now, instead of waiting for the
     * body view's own deferred job. The tab view routes its wires to those markers, and the two
     * deferred jobs run in no guaranteed order, so the tab calls this before reading them.
     */
    void syncAnchors();
}
