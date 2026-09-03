package rs.ac.bg.etf.viewmodel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.simulation.step.FormPhysicalAddressFromTLBStep;
import rs.ac.bg.etf.model.simulation.step.InstructionFetchStep;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.page.FormPageTableAddressStep;
import rs.ac.bg.etf.model.simulation.step.page.FormPhysicalAddressFromPageTableStep;
import rs.ac.bg.etf.model.simulation.step.page.PageTableLookupStep;
import rs.ac.bg.etf.model.table.PageTable;
import rs.ac.bg.etf.model.table.PageTableDescriptor;

/**
 * ViewModel for the paged MMU tab: the page-table-pointer/offset/adder address computation
 * plus a windowed view of the current user's page table around the addressed entry.
 */
public class PagedMMUTabViewModel 
{
    public static final int WINDOW_SIZE = 7;

    /** Identifies each connector wire drawn on the MMU schematic, so the view can light it up on demand. */
    public enum MmuLine {
        PAGE_TO_OFFSET,
        ZERO_FILL_TO_OFFSET,
        OFFSET_TO_ADDER,
        POINTER_TO_ADDER,
        ADDER_TO_ROW,
        WORD_PASSTHROUGH,
        ROW_TO_BLOCK
    }

    public record Row(long page, boolean valid, boolean dirty, long block, long disk, boolean current) {}

    private final PageSimulationContext context;
    private final Simulation simulation;

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

        blockHexDigits.set(hexDigitsFor(frameBits));
        diskHexDigits.set(hexDigitsFor(diskBits));

        simulationViewModel.currentStepNumberProperty().addListener((obs, oldVal, newVal) -> refresh());
        refresh();
    }

    public int getPageBits() { return pageBits; }
    public int getWordBits() { return wordBits; }
    public int getShiftBits() { return shiftBits; }
    public int getOffsetBits() { return offsetBits; }
    public int getPhysicalAddressBits() { return physicalAddressBits; }
    public int getFrameBits() { return frameBits; }
    public int getDiskBits() { return diskBits; }

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

            pageHex.set(toHex(currentPage, hexDigitsFor(pageBits)));
            wordHex.set(toHex(word, hexDigitsFor(wordBits)));
            pageTablePointerHex.set(toHex(pointer, hexDigitsFor(physicalAddressBits)));
            descriptorOffsetHex.set(toHex(offset, hexDigitsFor(offsetBits)));
            descriptorAddressHex.set(toHex(descriptorAddress, hexDigitsFor(physicalAddressBits)));
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
    }

    private static Set<MmuLine> linesFor(SimulationStep<? extends SimulationContext> step) 
    {
        if (step instanceof FormPageTableAddressStep)
            return EnumSet.of(MmuLine.PAGE_TO_OFFSET, MmuLine.ZERO_FILL_TO_OFFSET,
                    MmuLine.OFFSET_TO_ADDER, MmuLine.POINTER_TO_ADDER);
        if (step instanceof PageTableLookupStep)
            return EnumSet.of(MmuLine.ADDER_TO_ROW);
        if (step instanceof FormPhysicalAddressFromPageTableStep)
            return EnumSet.of(MmuLine.WORD_PASSTHROUGH, MmuLine.ROW_TO_BLOCK);
        if (step instanceof FormPhysicalAddressFromTLBStep)
            return EnumSet.of(MmuLine.WORD_PASSTHROUGH);

        return EnumSet.noneOf(MmuLine.class);
    }

    private static int hexDigitsFor(int bits) 
    {
        return Math.max(1, Math.ceilDiv(bits, 4));
    }

    private static String toHex(long value, int digits) 
    {
        return "0x" + String.format("%0" + digits + "X", value);
    }
}
