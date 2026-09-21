package rs.ac.bg.etf.viewmodel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.beans.value.ChangeListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.simulation.step.InstructionFetchStep;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.page.FormPageTableAddressStep;
import rs.ac.bg.etf.model.simulation.step.page.FormPhysicalAddressFromPageTableStep;
import rs.ac.bg.etf.model.simulation.step.page.PageEvictionStep;
import rs.ac.bg.etf.model.simulation.step.page.PageTLBEvictionStep;
import rs.ac.bg.etf.model.simulation.step.page.PageTableLookupStep;
import rs.ac.bg.etf.model.simulation.step.page.PageTableUpdateDirtyBitStep;
import rs.ac.bg.etf.model.table.PageTable;
import rs.ac.bg.etf.model.table.PageTableDescriptor;
import rs.ac.bg.etf.view.util.ValueConverter;

/**
 * ViewModel for the paged MMU tab: the page-table-pointer/offset/adder address computation
 * plus a windowed view of the current user's page table around the addressed entry.
 */
public class PagedMMUTabViewModel 
{
    // Named so dispose() can detach it: the view that owns this view model is rebuilt whenever the UI
    // scale changes (see UiScale), while the simulation view model it listens to lives on.
    private final ChangeListener<Number> stepListener = (obs, oldVal, newVal) -> refresh();

    public static final int WINDOW_SIZE = 7;

    /** Identifies each connector wire drawn on the MMU schematic, so the view can light it up on demand. */
    public enum MmuLine {
        PAGE_TO_OFFSET,
        ZERO_FILL_TO_OFFSET,
        OFFSET_TO_ADDER,
        POINTER_TO_ADDER,
        // Address bus driven into the page table once the descriptor address is formed -- lights up a
        // step before ADDER_TO_ROW, which is the actual lookup resolving to a specific row.
        ADDER_TO_TABLE,
        ADDER_TO_ROW,
        WORD_PASSTHROUGH,
        ROW_TO_BLOCK
    }

    public record Row(long page, boolean valid, boolean dirty, long block, long disk, boolean current) {}

    /** A page-table descriptor snapshot worth calling out because it changed off-window (not the
     *  addressed page) or was otherwise flagged -- rendered to look like an actual table row, not
     *  prose, so it reads as "here is the entry" rather than another step description. */
    public record MmuSideNote(String headline, int user, long page, boolean valid, boolean dirty, long block, long disk) {}

    private final PageSimulationContext context;
    private final Simulation simulation;
    // Kept only so the full-table inspector window (opened from the view, not owned by this
    // ViewModel) can listen for step changes on its own -- see currentStepNumberProperty().
    private final IntegerProperty currentStepNumber;

    private final ObservableList<Row> visibleRows = FXCollections.observableArrayList();
    private final IntegerProperty blockHexDigits = new SimpleIntegerProperty(1);
    private final IntegerProperty diskHexDigits = new SimpleIntegerProperty(1);

    private final StringProperty pageHex = new SimpleStringProperty("/");
    private final StringProperty wordHex = new SimpleStringProperty("/");
    private final StringProperty blockHex = new SimpleStringProperty("/");
    private final StringProperty paWordHex = new SimpleStringProperty("/");
    private final StringProperty pageTablePointerHex = new SimpleStringProperty("/");
    private final StringProperty descriptorOffsetHex = new SimpleStringProperty("/");
    private final StringProperty descriptorAddressHex = new SimpleStringProperty("/");
    private final StringProperty currentVBit = new SimpleStringProperty("/");
    private final StringProperty currentDBit = new SimpleStringProperty("/");
    private final StringProperty currentDiskHex = new SimpleStringProperty("/");

    private final Map<MmuLine, BooleanProperty> lineActive = new EnumMap<>(MmuLine.class);
    // Notification card for a page-table descriptor change worth calling out explicitly -- null
    // when there's nothing to show. Dismissible: dismissSideNote() hides it and it stays hidden
    // for this same occurrence (tracked via lastNoteStep) until a genuinely different step
    // produces a new one.
    private final ObjectProperty<MmuSideNote> sideNote = new SimpleObjectProperty<>(null);
    private SimulationStep<? extends SimulationContext> lastNoteStep = null;
    private boolean sideNoteDismissed = false;

    // Bit widths are fixed for the lifetime of a simulation, so these are plain fields, not properties
    private final int pageBits;
    private final int wordBits;
    private final int shiftBits;
    private final int offsetBits;
    private final int physicalAddressBits;
    private final int frameBits;
    private final int diskBits;

    public PagedMMUTabViewModel(PageSimulationContext context, SimulationViewModel simulationViewModel) 
    {
        this.context = context;
        this.simulation = simulationViewModel.getSimulation();
        this.currentStepNumber = simulationViewModel.currentStepNumberProperty();

        for (MmuLine line : MmuLine.values())
            lineActive.put(line, new SimpleBooleanProperty(false));

        this.physicalAddressBits = context.getPhysicalAddressBits();
        this.wordBits = context.getWordBits();
        this.frameBits = physicalAddressBits - wordBits;
        this.diskBits = context.getDiskBits();
        this.pageBits = Long.numberOfTrailingZeros(context.getMaxPages());
        // Descriptor size is always rounded up to a power of two, so index*size is a bit concatenation
        this.shiftBits = Integer.numberOfTrailingZeros(context.getPageTableDescriptorSize());
        this.offsetBits = pageBits + shiftBits;

        blockHexDigits.set(ValueConverter.hexDigitsFor(frameBits));
        diskHexDigits.set(ValueConverter.hexDigitsFor(diskBits));

        currentStepNumber.addListener(stepListener);
        refresh();
    }

    public int getPageBits() { return pageBits; }
    public int getWordBits() { return wordBits; }
    public int getShiftBits() { return shiftBits; }
    public int getOffsetBits() { return offsetBits; }
    public int getPhysicalAddressBits() { return physicalAddressBits; }
    public int getFrameBits() { return frameBits; }
    public int getDiskBits() { return diskBits; }

    /** The full simulation context -- e.g. for the full-table inspector window to read all users' page tables. */
    public PageSimulationContext getContext() { return context; }

    /** Ticks on every executed/undone step -- e.g. so the full-table inspector can refresh while open. */
    public IntegerProperty currentStepNumberProperty() { return currentStepNumber; }

    public ObservableList<Row> getVisibleRows() 
    {
        return visibleRows;
    }

    public IntegerProperty blockHexDigitsProperty() 
    {
        return blockHexDigits;
    }

    public IntegerProperty diskHexDigitsProperty() 
    {
        return diskHexDigits;
    }

    /** True while the given connector's step has run since the last instruction fetch. */
    public BooleanProperty lineActiveProperty(MmuLine line) 
    {
        return lineActive.get(line);
    }

    /** True once the page table has actually been looked up for the current instruction (row is resolved, not just guessed). */
    public BooleanProperty pageTableAccessedProperty()
    {
        return lineActive.get(MmuLine.ADDER_TO_ROW);
    }

    /** Notification card describing a page-table descriptor change worth calling out; null when
     *  there's nothing to show. */
    public ObjectProperty<MmuSideNote> sideNoteProperty()
    {
        return sideNote;
    }

    /** Dismisses the current notification card; it stays hidden until a genuinely new occurrence. */
    public void dismissSideNote()
    {
        sideNoteDismissed = true;
        sideNote.set(null);
    }

    public StringProperty pageHexProperty() { return pageHex; }
    public StringProperty wordHexProperty() { return wordHex; }
    public StringProperty blockHexProperty() { return blockHex; }
    public StringProperty paWordHexProperty() { return paWordHex; }
    public StringProperty pageTablePointerHexProperty() { return pageTablePointerHex; }
    public StringProperty descriptorOffsetHexProperty() { return descriptorOffsetHex; }
    public StringProperty descriptorAddressHexProperty() { return descriptorAddressHex; }
    public StringProperty currentVBitProperty() { return currentVBit; }
    public StringProperty currentDBitProperty() { return currentDBit; }
    public StringProperty currentDiskHexProperty() { return currentDiskHex; }

    public void refresh() 
    {
        recomputeActiveLines();
        visibleRows.clear();

        boolean hasInstruction = context.hasCurrentInstruction();
        boolean pageTableAccessed = lineActive.get(MmuLine.ADDER_TO_ROW).get();
        boolean physicalAddressFormed = lineActive.get(MmuLine.WORD_PASSTHROUGH).get();
        boolean blockFormed = lineActive.get(MmuLine.ROW_TO_BLOCK).get();

        int user = hasInstruction ? context.getCurrentInstruction().getUser() : 0;
        long currentPage = hasInstruction ? context.getPageComponent() : 0;
        long maxPages = context.getMaxPages();

        if (hasInstruction)
        {
            long word = context.getWordComponent();
            long pointer = context.getPageTableStartAddress(user);
            long offset = currentPage << shiftBits;
            long descriptorAddress = pointer + offset;

            pageHex.set(toHex(currentPage, ValueConverter.hexDigitsFor(pageBits)));
            wordHex.set(toHex(word, ValueConverter.hexDigitsFor(wordBits)));
            pageTablePointerHex.set(toHex(pointer, ValueConverter.hexDigitsFor(physicalAddressBits)));
            descriptorOffsetHex.set(toHex(offset, ValueConverter.hexDigitsFor(offsetBits)));
            descriptorAddressHex.set(toHex(descriptorAddress, ValueConverter.hexDigitsFor(physicalAddressBits)));
        }
        else
        {
            pageHex.set("/");
            wordHex.set("/");
            pageTablePointerHex.set("/");
            descriptorOffsetHex.set("/");
            descriptorAddressHex.set("/");
        }

        // The physical address side only becomes known once it's actually been formed, even though
        // the current page's descriptor is technically readable earlier.
        paWordHex.set(physicalAddressFormed ? wordHex.get() : "/");

        PageTable pageTable = context.getPageTable(user);
        PageTableDescriptor currentDescriptor = hasInstruction ? pageTable.getEntry(currentPage) : null;

        blockHex.set(blockFormed && currentDescriptor != null ? toHex(currentDescriptor.getBlock(), blockHexDigits.get()) : "/");

        if (pageTableAccessed && currentDescriptor != null)
        {
            currentVBit.set(currentDescriptor.isValid() ? "1" : "0");
            currentDBit.set(currentDescriptor.isDirty() ? "1" : "0");
            currentDiskHex.set(toHex(currentDescriptor.getDisk(), diskHexDigits.get()));
        }
        else
        {
            currentVBit.set("/");
            currentDBit.set("/");
            currentDiskHex.set("/");
        }

        // Always show a window of entries (never an empty table); fogging (handled by the view) signals
        // that the table hasn't actually been looked up yet, rather than leaving the schematic looking broken.
        long windowStart = currentPage - WINDOW_SIZE / 2;
        windowStart = Math.max(0, Math.min(windowStart, Math.max(0, maxPages - WINDOW_SIZE)));
        long rowsToShow = Math.min(WINDOW_SIZE, maxPages);

        for (long i = 0; i < rowsToShow; i++)
        {
            long page = windowStart + i;
            PageTableDescriptor descriptor = pageTable.getEntry(page);
            visibleRows.add(new Row(
                    page, descriptor.isValid(), descriptor.isDirty(),
                    descriptor.getBlock(), descriptor.getDisk(), hasInstruction && page == currentPage));
        }
    }

    // Walks executed steps back to (but excluding) the most recent instruction fetch, unioning
    // the connectors each step type touches, so wires "stay lit" for the rest of that instruction.
    private void recomputeActiveLines()
    {
        Set<MmuLine> active = EnumSet.noneOf(MmuLine.class);

        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        for (int i = history.size() - 1; i >= 0; i--)
        {
            SimulationStep<? extends SimulationContext> step = history.get(i);
            if (step instanceof InstructionFetchStep)
                break;

            active.addAll(linesFor(step));
        }

        for (MmuLine line : MmuLine.values())
            lineActive.get(line).set(active.contains(line));

        recomputeSideNote(history);
    }

    // Describes whatever page-table mutation the single current step just made -- scoped to that
    // one step only (matching SimulationViewModel's tab-notification badge exactly, so the card
    // always explains precisely why this tab is currently badged, no more and no less). A step
    // producing a *different* card than last time is a new occurrence and clears any prior
    // dismissal; re-seeing the same step (e.g. switching tabs and back with nothing new having
    // run) respects it.
    private void recomputeSideNote(List<SimulationStep<? extends SimulationContext>> history)
    {
        SimulationStep<? extends SimulationContext> step = history.isEmpty() ? null : history.get(history.size() - 1);
        if (step != lastNoteStep)
        {
            sideNoteDismissed = false;
            lastNoteStep = step;
        }
        sideNote.set(sideNoteDismissed ? null : sideNoteFor(step));
    }

    // Most of these mutate the *addressed* page's own descriptor, already visible as the
    // highlighted row above; the card still spells it out explicitly rather than leaving the user
    // to infer it, and PageEvictionStep/PageTLBEvictionStep's write-back cover the one case that's
    // genuinely off-window: a victim/evicted entry belonging to a different page entirely. Values
    // come from context fields those steps set in execute(), not from the step instances
    // themselves -- the instanceof check here only identifies *which* card to build.
    private MmuSideNote sideNoteFor(SimulationStep<? extends SimulationContext> step)
    {
        if (step instanceof PageTableUpdateDirtyBitStep)
        {
            int user = context.getCurrentInstruction().getUser();
            return descriptorSideNote("Dirty bit set", user, context.getPageComponent());
        }
        if (step instanceof PageEvictionStep)
        {
            return descriptorSideNote("Invalidated entry", context.getPageEvictionVictimUser(), context.getPageEvictionVictimPage());
        }
        if (step instanceof PageTLBEvictionStep && context.getTlbWritebackUser() >= 0)
        {
            return descriptorSideNote("Dirty bit written back from TLB", context.getTlbWritebackUser(), context.getTlbWritebackPage());
        }
        return null;
    }

    // Reads the descriptor's current (post-mutation) field values directly from the model, rather
    // than needing the step itself to carry them -- the step only needs to identify *which*
    // descriptor, via (user, page).
    private MmuSideNote descriptorSideNote(String headline, int user, long page)
    {
        PageTableDescriptor descriptor = context.getPageTable(user).getEntry(page);
        return new MmuSideNote(headline, user, page, descriptor.isValid(), descriptor.isDirty(), descriptor.getBlock(), descriptor.getDisk());
    }

    private static Set<MmuLine> linesFor(SimulationStep<? extends SimulationContext> step) 
    {
        if (step instanceof FormPageTableAddressStep)
            return EnumSet.of(MmuLine.PAGE_TO_OFFSET, MmuLine.ZERO_FILL_TO_OFFSET,
                    MmuLine.OFFSET_TO_ADDER, MmuLine.POINTER_TO_ADDER, MmuLine.ADDER_TO_TABLE);
        if (step instanceof PageTableLookupStep)
            return EnumSet.of(MmuLine.ADDER_TO_ROW);
        if (step instanceof FormPhysicalAddressFromPageTableStep)
            return EnumSet.of(MmuLine.WORD_PASSTHROUGH, MmuLine.ROW_TO_BLOCK);
        // A TLB hit forms the physical address without touching the page table, so this schematic
        // stays dark for it -- that path is the TLB tab's to draw (PageFormPhysicalAddressFromTLBStep,
        // a subclass of FormPhysicalAddressFromTLBStep, must NOT be matched here).

        return EnumSet.noneOf(MmuLine.class);
    }

    private static String toHex(long value, int digits) 
    {
        return "0x" + String.format("%0" + digits + "X", value);
    }

    /** Stops following the simulation -- call when the view using this view model is discarded. */
    public void dispose()
    {
        currentStepNumber.removeListener(stepListener);
    }
}
