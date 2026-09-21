package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;

/**
 * One editable row in the config screen's Initial Page Content editor window: a single word
 * value pre-loaded at one offset of one user's page. Multiple rows sharing the same (userId, page)
 * are grouped back into one
 * {@link rs.ac.bg.etf.model.simulation.SimulationConfig.InitialPage}'s content map when launched --
 * the flattening mirrors {@code PageTableEntry}'s relationship to the page-table map.
 */
public class MemoryInitEntry
{
    private final IntegerProperty userId = new SimpleIntegerProperty(0);
    private final LongProperty page = new SimpleLongProperty(0);
    private final LongProperty offset = new SimpleLongProperty(0);
    private final LongProperty value = new SimpleLongProperty(0);

    public IntegerProperty userIdProperty() { return userId; }
    public LongProperty pageProperty() { return page; }
    public LongProperty offsetProperty() { return offset; }
    public LongProperty valueProperty() { return value; }
}
