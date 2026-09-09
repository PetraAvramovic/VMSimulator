package rs.ac.bg.etf.viewmodel;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.os.PageOSMemoryManager;
import rs.ac.bg.etf.model.os.PageOSMemoryManager.FrameMapping;
import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.model.simulation.Simulation;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.simulation.step.InstructionFetchStep;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.page.PageEvictionStep;
import rs.ac.bg.etf.model.simulation.step.page.PageLoadIntoMemoryStep;
import rs.ac.bg.etf.model.simulation.step.page.PageStoreToDiskStep;
import rs.ac.bg.etf.model.table.PageTableDescriptor;
import rs.ac.bg.etf.view.util.ValueConverter;

/**
 * ViewModel for the paged OS tab: a state dashboard for the OS's physical-memory
 * management -- the frame table (free / allocated / kernel), the disk transfer that
 * services a page fault, the FIFO replacement queue, and a per-user page-table summary.
 *
 * <p>Like the MMU/TLB tabs it is rebuilt on every {@code currentStepNumberProperty}
 * change, and it decides which connector wires are "lit" by walking the executed steps
 * back to the last instruction fetch.
 */
public class PagedOSTabViewModel
{
    /** Connector wires drawn on the OS schematic. */
    public enum OsLine
    {
        LOAD_PAGE,   // disk block -> active frame row (PageLoadIntoMemoryStep)
        WRITE_BACK,  // active frame row -> disk block (PageStoreToDiskStep)
        FRAME_ROW    // the active frame row is engaged in the current OS sub-flow
    }

    /** How a frame is presented in the frame table / occupancy strip. */
    public enum FrameState { FREE, ALLOCATED, KERNEL, VICTIM, EVICTING }

    public record FrameRow(long frame, FrameState state, int user, long page,
                           boolean valid, boolean dirty, long disk,
                           boolean active, boolean nextVictim) {}

    /** One entry of the FIFO replacement queue, oldest first. */
    public record QueueChip(long frame, int position, boolean head, boolean tail) {}

    /** One word of the disk block currently in transit (drives the block popup). */
    public record DiskWord(long offset, long value) {}

    public record UserSummary(int user, long ptpAddress, int validPageCount) {}

    private final PageSimulationContext context;
    private final Simulation simulation;
    private final PageOSMemoryManager osManager;

    private final ObservableList<FrameRow> frameRows = FXCollections.observableArrayList();
    private final ObservableList<QueueChip> replacementOrder = FXCollections.observableArrayList();
    private final ObservableList<DiskWord> diskBlockWords = FXCollections.observableArrayList();
    private final ObservableList<UserSummary> userSummaries = FXCollections.observableArrayList();

    private final IntegerProperty frameHexDigits = new SimpleIntegerProperty(1);
    private final IntegerProperty diskHexDigits = new SimpleIntegerProperty(1);
    private final IntegerProperty ptpHexDigits = new SimpleIntegerProperty(1);

    private final StringProperty diskAddressHex = new SimpleStringProperty("/");
    private final StringProperty diskBlockSummary = new SimpleStringProperty("/");
    private final StringProperty nextVictimHex = new SimpleStringProperty("/");
    private final StringProperty activeFrameHex = new SimpleStringProperty("/");
    private final StringProperty loadPageValueHex = new SimpleStringProperty("/");
    private final StringProperty writeBackValueHex = new SimpleStringProperty("/");
    private final StringProperty activeFrameUserHex = new SimpleStringProperty("/");
    private final StringProperty activeFramePageHex = new SimpleStringProperty("/");

    private final BooleanProperty diskEngaged = new SimpleBooleanProperty(false);
    private final BooleanProperty memoryFull = new SimpleBooleanProperty(false);
    /** Row index of the frame the current OS sub-flow touches, or -1. Row index == frame number. */
    private final IntegerProperty activeFrameIndex = new SimpleIntegerProperty(-1);

    private final Map<OsLine, BooleanProperty> lineActive = new EnumMap<>(OsLine.class);

    // Fixed for the lifetime of a simulation -> plain fields, not properties
    private final int physicalAddressBits;
    private final int wordBits;
    private final int frameBits;
    private final int diskBits;
    private final int pageBits;
    private final long frameCount;
    private final long pageSize;
    private final int numberOfUsers;

    public PagedOSTabViewModel(PageSimulationContext context, SimulationViewModel simulationViewModel)
    {
        this.context = context;
        this.simulation = simulationViewModel.getSimulation();
        this.osManager = context.getOSMemoryManager();

        for (OsLine line : OsLine.values())
            lineActive.put(line, new SimpleBooleanProperty(false));

        this.physicalAddressBits = context.getPhysicalAddressBits();
        this.wordBits = context.getWordBits();
        this.frameBits = physicalAddressBits - wordBits;
        this.diskBits = context.getDiskBits();
        this.pageBits = Long.numberOfTrailingZeros(context.getMaxPages());
        this.frameCount = osManager.getMaxFrames();
        this.pageSize = context.getPageSize();
        this.numberOfUsers = context.getNumberOfUsers();

        frameHexDigits.set(ValueConverter.hexDigitsFor(frameBits));
        diskHexDigits.set(ValueConverter.hexDigitsFor(diskBits));
        ptpHexDigits.set(ValueConverter.hexDigitsFor(physicalAddressBits));

        simulationViewModel.currentStepNumberProperty().addListener((obs, oldVal, newVal) -> refresh());
        refresh();
    }

    // ---- fixed getters -----------------------------------------------------------------

    public int getPhysicalAddressBits() { return physicalAddressBits; }
    public int getWordBits() { return wordBits; }
    public int getFrameBits() { return frameBits; }
    public int getDiskBits() { return diskBits; }
    public int getPageBits() { return pageBits; }
    public long getFrameCount() { return frameCount; }
    public long getPageSize() { return pageSize; }
    public int getNumberOfUsers() { return numberOfUsers; }

    // ---- observable surface ----------------------------------------------------------

    public ObservableList<FrameRow> getFrameRows() { return frameRows; }
    public ObservableList<QueueChip> getReplacementOrder() { return replacementOrder; }
    public ObservableList<DiskWord> getDiskBlockWords() { return diskBlockWords; }
    public ObservableList<UserSummary> getUserSummaries() { return userSummaries; }

    public IntegerProperty frameHexDigitsProperty() { return frameHexDigits; }
    public IntegerProperty diskHexDigitsProperty() { return diskHexDigits; }
    public IntegerProperty ptpHexDigitsProperty() { return ptpHexDigits; }

    public StringProperty diskAddressHexProperty() { return diskAddressHex; }
    public StringProperty diskBlockSummaryProperty() { return diskBlockSummary; }
    public StringProperty nextVictimHexProperty() { return nextVictimHex; }
    public StringProperty activeFrameHexProperty() { return activeFrameHex; }
    public StringProperty loadPageValueHexProperty() { return loadPageValueHex; }
    public StringProperty writeBackValueHexProperty() { return writeBackValueHex; }
    public StringProperty activeFrameUserHexProperty() { return activeFrameUserHex; }
    public StringProperty activeFramePageHexProperty() { return activeFramePageHex; }

    public BooleanProperty diskEngagedProperty() { return diskEngaged; }
    public BooleanProperty memoryFullProperty() { return memoryFull; }
    public IntegerProperty activeFrameIndexProperty() { return activeFrameIndex; }

    /** True while the given connector's step has run since the last instruction fetch. */
    public BooleanProperty lineActiveProperty(OsLine line) { return lineActive.get(line); }

    // ---- refresh -------------------------------------------------------------------

    public void refresh()
    {
        recomputeActiveLines();

        SimulationStep<? extends SimulationContext> lastFrameStep = lastFrameStepSinceFetch();
        PageEvictionStep<?> evictionStep = evictionSinceFetch();

        long activeFrame = -1;
        long evictingFrame = -1;
        if (lastFrameStep instanceof PageLoadIntoMemoryStep<?> load)
        {
            activeFrame = load.getFrame();
        }
        else if (lastFrameStep instanceof PageStoreToDiskStep<?> store)
        {
            activeFrame = store.getFrame();
            evictingFrame = activeFrame;
        }
        else if (lastFrameStep instanceof PageEvictionStep<?> evict)
        {
            activeFrame = evict.getVictimFrame();
            evictingFrame = activeFrame;
        }

        activeFrameIndex.set(activeFrame >= 0 ? (int) activeFrame : -1);

        boolean full = osManager.getFreeFrame() == -1;
        memoryFull.set(full);

        List<Long> order = osManager.getReplacementOrder();
        long nextVictimFrame = (full && !order.isEmpty()) ? order.get(0) : -1;

        rebuildFrameRows(activeFrame, evictingFrame, nextVictimFrame);
        rebuildReplacementOrder(order);
        rebuildUserSummaries();

        boolean loadLit = lineActive.get(OsLine.LOAD_PAGE).get();
        boolean writeLit = lineActive.get(OsLine.WRITE_BACK).get();
        diskEngaged.set(loadLit || writeLit);

        // The incoming page is read from its own backing block; a dirty victim is written to
        // the victim page's backing block -- two different disk addresses.
        PageTableDescriptor incoming = context.getCurrentDescriptor();
        long loadDiskAddr = (loadLit && incoming != null) ? incoming.getDisk() : -1;
        PageStoreToDiskStep<?> storeStep = storeSinceFetch();
        long writeDiskAddr = (writeLit && storeStep != null) ? storeStep.getDiskAddress() : -1;

        // The disk box shows whichever transfer is most recent.
        long boxDiskAddr = -1;
        if (lastFrameStep instanceof PageStoreToDiskStep<?> s)
            boxDiskAddr = s.getDiskAddress();
        else if (lastFrameStep instanceof PageLoadIntoMemoryStep<?> && incoming != null)
            boxDiskAddr = incoming.getDisk();
        rebuildDiskBlock(boxDiskAddr);

        nextVictimHex.set(nextVictimFrame >= 0 ? toHex(nextVictimFrame, frameHexDigits.get()) : "/");
        activeFrameHex.set(activeFrame >= 0 ? toHex(activeFrame, frameHexDigits.get()) : "/");
        loadPageValueHex.set(loadDiskAddr >= 0 ? toHex(loadDiskAddr, diskHexDigits.get()) : "/");
        writeBackValueHex.set(writeDiskAddr >= 0 ? toHex(writeDiskAddr, diskHexDigits.get()) : "/");

        setActiveFrameOwner(activeFrame, evictionStep);
    }

    private void rebuildFrameRows(long activeFrame, long evictingFrame, long nextVictimFrame)
    {
        // Every frame is a row (the frame-table view virtualises + scrolls); row index == frame number.
        java.util.ArrayList<FrameRow> rows = new java.util.ArrayList<>((int) frameCount);
        for (long frame = 0; frame < frameCount; frame++)
        {
            FrameMapping mapping = osManager.getFrameMapping(frame);
            boolean locked = osManager.isLocked(frame);

            FrameState state;
            if (frame == evictingFrame)
                state = FrameState.EVICTING;
            else if (locked)
                state = FrameState.KERNEL;
            else if (mapping == null)
                state = FrameState.FREE;
            else if (frame == nextVictimFrame)
                state = FrameState.VICTIM;
            else
                state = FrameState.ALLOCATED;

            PageTableDescriptor descriptor = mapping != null ? mapping.descriptor() : null;
            rows.add(new FrameRow(
                    frame, state,
                    mapping != null ? mapping.user() : -1,
                    mapping != null ? mapping.page() : -1,
                    descriptor != null && descriptor.isValid(),
                    descriptor != null && descriptor.isDirty(),
                    descriptor != null ? descriptor.getDisk() : 0,
                    frame == activeFrame,
                    frame == nextVictimFrame));
        }
        frameRows.setAll(rows);
    }

    private void rebuildReplacementOrder(List<Long> order)
    {
        replacementOrder.clear();
        for (int i = 0; i < order.size(); i++)
            replacementOrder.add(new QueueChip(order.get(i), i, i == 0, i == order.size() - 1));
    }

    private void rebuildUserSummaries()
    {
        userSummaries.clear();
        for (int user = 0; user < numberOfUsers; user++)
            userSummaries.add(new UserSummary(
                    user,
                    context.getPageTableStartAddress(user),
                    context.getPageTable(user).getValidEntries().size()));
    }

    private void rebuildDiskBlock(long diskAddress)
    {
        diskBlockWords.clear();
        if (diskAddress < 0)
        {
            diskAddressHex.set("/");
            diskBlockSummary.set("/");
            return;
        }

        SortedMap<Long, Long> block = context.getDisk().readBlock(diskAddress);
        for (Map.Entry<Long, Long> entry : block.entrySet())
            diskBlockWords.add(new DiskWord(entry.getKey(), entry.getValue()));

        diskAddressHex.set(toHex(diskAddress, diskHexDigits.get()));
        diskBlockSummary.set(block.size() + (block.size() == 1 ? " word" : " words"));
    }

    private void setActiveFrameOwner(long activeFrame, PageEvictionStep<?> evictionStep)
    {
        FrameMapping mapping = activeFrame >= 0 ? osManager.getFrameMapping(activeFrame) : null;
        if (mapping != null)
        {
            activeFrameUserHex.set(toHex(mapping.user(), ValueConverter.hexDigitsFor(Math.max(1, pageBits))));
            activeFramePageHex.set(toHex(mapping.page(), ValueConverter.hexDigitsFor(Math.max(1, pageBits))));
        }
        else if (evictionStep != null)
        {
            activeFrameUserHex.set(Integer.toString(evictionStep.getVictimUser()));
            activeFramePageHex.set(toHex(evictionStep.getVictimPage(), ValueConverter.hexDigitsFor(Math.max(1, pageBits))));
        }
        else
        {
            activeFrameUserHex.set("/");
            activeFramePageHex.set("/");
        }
    }

    // Walk executed steps back to (but excluding) the most recent instruction fetch,
    // unioning the connectors each step type touches, so wires stay lit for the rest of
    // that instruction.
    private void recomputeActiveLines()
    {
        Set<OsLine> active = EnumSet.noneOf(OsLine.class);

        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        for (int i = history.size() - 1; i >= 0; i--)
        {
            SimulationStep<? extends SimulationContext> step = history.get(i);
            if (step instanceof InstructionFetchStep)
                break;

            active.addAll(linesFor(step));
        }

        for (OsLine line : OsLine.values())
            lineActive.get(line).set(active.contains(line));
    }

    private static Set<OsLine> linesFor(SimulationStep<? extends SimulationContext> step)
    {
        if (step instanceof PageLoadIntoMemoryStep)
            return EnumSet.of(OsLine.LOAD_PAGE, OsLine.FRAME_ROW);
        if (step instanceof PageStoreToDiskStep)
            return EnumSet.of(OsLine.WRITE_BACK, OsLine.FRAME_ROW);
        if (step instanceof PageEvictionStep)
            return EnumSet.of(OsLine.FRAME_ROW);

        return EnumSet.noneOf(OsLine.class);
    }

    /** Most recent load / store / eviction step since the last instruction fetch, or null. */
    private SimulationStep<? extends SimulationContext> lastFrameStepSinceFetch()
    {
        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        for (int i = history.size() - 1; i >= 0; i--)
        {
            SimulationStep<? extends SimulationContext> step = history.get(i);
            if (step instanceof InstructionFetchStep)
                return null;
            if (step instanceof PageLoadIntoMemoryStep
                    || step instanceof PageStoreToDiskStep
                    || step instanceof PageEvictionStep)
                return step;
        }
        return null;
    }

    /** The eviction step since the last instruction fetch, if the current instruction evicted, or null. */
    private PageEvictionStep<?> evictionSinceFetch()
    {
        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        for (int i = history.size() - 1; i >= 0; i--)
        {
            SimulationStep<? extends SimulationContext> step = history.get(i);
            if (step instanceof InstructionFetchStep)
                return null;
            if (step instanceof PageEvictionStep<?> evict)
                return evict;
        }
        return null;
    }

    /** The write-back step since the last instruction fetch, if a dirty victim was written back, or null. */
    private PageStoreToDiskStep<?> storeSinceFetch()
    {
        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        for (int i = history.size() - 1; i >= 0; i--)
        {
            SimulationStep<? extends SimulationContext> step = history.get(i);
            if (step instanceof InstructionFetchStep)
                return null;
            if (step instanceof PageStoreToDiskStep<?> store)
                return store;
        }
        return null;
    }

    private static String toHex(long value, int digits)
    {
        return "0x" + String.format("%0" + digits + "X", value);
    }
}
