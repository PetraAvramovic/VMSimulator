package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.view.util.ValueConverter;

/**
 * ViewModel for the Memory tab: a small windowed view of physical memory centred on whichever
 * address the current instruction has addressed so far, fogged until that address has actually
 * been formed -- the same idiom {@code PagedMMUTabViewModel}/{@code PageTableView} use for the
 * page table.
 */
public class MemoryTabViewModel
{
    public static final int WINDOW_SIZE = 15;

    public record Row(long address, long value, boolean locked, boolean current) {}

    private final PageSimulationContext context;
    private final PageOSMemoryManager osManager;
    // Kept only so the full-memory inspector window (opened from the view, not owned by this
    // ViewModel) can listen for step changes on its own -- see currentStepNumberProperty().
    private final IntegerProperty currentStepNumber;

    private final ObservableList<Row> visibleRows = FXCollections.observableArrayList();
    private final BooleanProperty memoryAddressed = new SimpleBooleanProperty(false);
    private final StringProperty physicalAddressHex;

    // Fixed for the lifetime of a simulation -> plain fields, not properties
    private final int physicalAddressBits;
    private final int wordBits;
    private final int valueHexDigits;
    private final long memorySize;

    // Physical memory can have up to 2^62 addressable words, so -- like PagedOSTabViewModel's
    // frame table -- only a fixed WINDOW_SIZE window is ever materialised, recentred only when an
    // address actually gets formed; otherwise it stays wherever it last was.
    private long lastCenterAddress = 0;

    public MemoryTabViewModel(PageSimulationContext context, SimulationViewModel simulationViewModel)
    {
        this.context = context;
        this.osManager = context.getOSMemoryManager();
        this.currentStepNumber = simulationViewModel.currentStepNumberProperty();

        this.physicalAddressBits = context.getPhysicalAddressBits();
        this.wordBits = context.getWordBits();
        this.valueHexDigits = ValueConverter.hexDigitsFor(context.getAddressableUnit() * 8);
        this.memorySize = context.getPhysicalMemorySize();

        // Already exactly the value/"/" sentinel this tab needs -- the left sidebar shows the same
        // thing, so reuse it rather than re-deriving the same logic here.
        this.physicalAddressHex = simulationViewModel.currentPhysicalAddressHexProperty();

        simulationViewModel.currentStepNumberProperty().addListener((obs, oldVal, newVal) -> refresh());
        refresh();
    }

    public int getPhysicalAddressBits() { return physicalAddressBits; }
    public int getWordBits() { return wordBits; }
    public int getValueHexDigits() { return valueHexDigits; }

    /** The full simulation context -- e.g. for the full-memory inspector window. */
    public PageSimulationContext getContext() { return context; }

    /** Ticks on every executed/undone step -- e.g. so the full-memory inspector can refresh while open. */
    public IntegerProperty currentStepNumberProperty() { return currentStepNumber; }

    /** The address the embedded schematic's window is currently centred on -- used to seed the inspector popup. */
    public long getWindowCenterAddress() { return lastCenterAddress; }

    public ObservableList<Row> getVisibleRows() { return visibleRows; }

    /** True once the current instruction has actually formed a physical address (drives fog + wire). */
    public BooleanProperty memoryAddressedProperty() { return memoryAddressed; }

    public StringProperty physicalAddressHexProperty() { return physicalAddressHex; }

    public void refresh()
    {
        long currentPA = context.getCurrentPhysicalAddress();
        boolean addressed = context.hasCurrentInstruction() && currentPA >= 0;
        memoryAddressed.set(addressed);
        if (addressed)
            lastCenterAddress = currentPA;

        visibleRows.clear();

        long windowStart = lastCenterAddress - WINDOW_SIZE / 2;
        windowStart = Math.max(0, Math.min(windowStart, Math.max(0, memorySize - WINDOW_SIZE)));
        long rowsToShow = Math.min(WINDOW_SIZE, memorySize);

        for (long i = 0; i < rowsToShow; i++)
        {
            long address = windowStart + i;
            long frame = address >>> wordBits;
            visibleRows.add(new Row(
                    address, context.getValueAtAddress(address),
                    osManager.isLocked(frame), addressed && address == currentPA));
        }
    }
}
