package rs.ac.bg.etf.viewmodel;

import java.util.List;

import javafx.beans.value.ChangeListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.page.PageLoadIntoMemoryStep;
import rs.ac.bg.etf.view.util.ValueConverter;

/**
 * ViewModel for the Memory tab: a small windowed view of physical memory centred on whichever
 * address the current instruction has addressed so far, fogged until that address has actually
 * been formed -- the same idiom {@code PagedMMUTabViewModel}/{@code PageTableView} use for the
 * page table.
 */
public class MemoryTabViewModel
{
    // Named so dispose() can detach it: the view that owns this view model is rebuilt whenever the UI
    // scale changes (see UiScale), while the simulation view model it listens to lives on.
    private final ChangeListener<Number> stepListener = (obs, oldVal, newVal) -> refresh();

    public static final int WINDOW_SIZE = 15;

    public record Row(long address, long value, boolean locked, boolean current) {}

    private final PageSimulationContext context;
    private final Simulation simulation;
    private final PageOSMemoryManager osManager;
    // Kept only so the full-memory inspector window (opened from the view, not owned by this
    // ViewModel) can listen for step changes on its own -- see currentStepNumberProperty().
    private final IntegerProperty currentStepNumber;

    private final ObservableList<Row> visibleRows = FXCollections.observableArrayList();
    private final BooleanProperty memoryAddressed = new SimpleBooleanProperty(false);
    // True whenever the table itself should be shown unfogged -- either memoryAddressed, or the
    // step that just ran loaded a page into a frame (the physical address isn't formed yet, but
    // there is already a frame worth looking at). Deliberately separate from memoryAddressed,
    // which alone drives the Physical Address wire: that wire must stay dark while the PA box
    // still reads "/", even on a step where the table itself has something to show.
    private final BooleanProperty memoryVisible = new SimpleBooleanProperty(false);
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
        this.simulation = simulationViewModel.getSimulation();
        this.osManager = context.getOSMemoryManager();
        this.currentStepNumber = simulationViewModel.currentStepNumberProperty();

        this.physicalAddressBits = context.getPhysicalAddressBits();
        this.wordBits = context.getWordBits();
        this.valueHexDigits = ValueConverter.hexDigitsFor(context.getAddressableUnit() * 8);
        this.memorySize = context.getPhysicalMemorySize();

        // Already exactly the value/"/" sentinel this tab needs -- the left sidebar shows the same
        // thing, so reuse it rather than re-deriving the same logic here.
        this.physicalAddressHex = simulationViewModel.currentPhysicalAddressHexProperty();

        currentStepNumber.addListener(stepListener);
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

    /** True once the current instruction has actually formed a physical address (drives the wire). */
    public BooleanProperty memoryAddressedProperty() { return memoryAddressed; }

    /** True whenever the table itself has something to show, unfogged (see the field's own doc). */
    public BooleanProperty memoryVisibleProperty() { return memoryVisible; }

    public StringProperty physicalAddressHexProperty() { return physicalAddressHex; }

    public void refresh()
    {
        long currentPA = context.getCurrentPhysicalAddress();
        boolean addressed = context.hasCurrentInstruction() && currentPA >= 0;
        memoryAddressed.set(addressed);

        // A page just loaded into a frame, but this instruction's own physical address hasn't been
        // formed yet (that happens next) -- rather than leave the table fogged and pointed at
        // wherever it last was, or centre it on a word offset that isn't known yet, open it right at
        // the frame's first word: that frame is what this step is about.
        SimulationStep<? extends SimulationContext> last = lastExecutedStep();
        boolean justLoaded = !addressed && last instanceof PageLoadIntoMemoryStep;

        long windowStart;
        if (addressed)
        {
            lastCenterAddress = currentPA;
            windowStart = lastCenterAddress - WINDOW_SIZE / 2;
        }
        else if (justLoaded)
        {
            lastCenterAddress = context.getCurrentFrame() << wordBits;
            windowStart = lastCenterAddress;
        }
        else
        {
            windowStart = lastCenterAddress - WINDOW_SIZE / 2;
        }
        memoryVisible.set(addressed || justLoaded);

        visibleRows.clear();

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

    /** The single most-recently-executed step, or null before any step has run. */
    private SimulationStep<? extends SimulationContext> lastExecutedStep()
    {
        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    /** Stops following the simulation -- call when the view using this view model is discarded. */
    public void dispose()
    {
        currentStepNumber.removeListener(stepListener);
    }
}
