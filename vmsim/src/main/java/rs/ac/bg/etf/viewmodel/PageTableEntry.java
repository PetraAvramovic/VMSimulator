package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;

/**
 * One editable row in the config screen's Page Table editor window: a single user's initial
 * descriptor for one page, mirroring {@link rs.ac.bg.etf.model.simulation.SimulationConfig.PageTableDescriptorInit}
 * plus the (userId, page) key it's filed under. Flattened back into the nested
 * {@code Map<Integer, Map<Long, PageTableDescriptorInit>>} the config expects only when launched.
 */
public class PageTableEntry
{
    private final IntegerProperty userId = new SimpleIntegerProperty(0);
    private final LongProperty page = new SimpleLongProperty(0);
    private final BooleanProperty valid = new SimpleBooleanProperty(true);
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final LongProperty block = new SimpleLongProperty(0);

    public IntegerProperty userIdProperty() { return userId; }
    public LongProperty pageProperty() { return page; }
    public BooleanProperty validProperty() { return valid; }
    public BooleanProperty dirtyProperty() { return dirty; }
    public LongProperty blockProperty() { return block; }
}
