package rs.ac.bg.etf.viewmodel;

import java.util.ArrayList;
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
import rs.ac.bg.etf.model.simulation.SimulationConfig.TLBType;
import rs.ac.bg.etf.model.simulation.SimulationContext;
import rs.ac.bg.etf.model.simulation.step.InstructionFetchStep;
import rs.ac.bg.etf.model.simulation.step.SimulationStep;
import rs.ac.bg.etf.model.simulation.step.TLBUpdateStep;
import rs.ac.bg.etf.model.simulation.step.page.PageEvictionStep;
import rs.ac.bg.etf.model.simulation.step.page.PageFormPhysicalAddressFromTLBStep;
import rs.ac.bg.etf.model.simulation.step.page.PageTLBEvictionStep;
import rs.ac.bg.etf.model.simulation.step.page.PageTLBLookupStep;
import rs.ac.bg.etf.model.tlb.DirectTLB;
import rs.ac.bg.etf.model.tlb.SetAssociativeTLB;
import rs.ac.bg.etf.model.tlb.TLB;
import rs.ac.bg.etf.model.tlb.TLBEntry;
import rs.ac.bg.etf.view.util.ValueConverter;

public class PagedTLBTabViewModel
{
    // Named so dispose() can detach it: the view that owns this view model is rebuilt whenever the UI
    // scale changes (see UiScale), while the simulation view model it listens to lives on.
    private final ChangeListener<Number> stepListener = (obs, oldVal, newVal) -> refresh();

    public static final int MAX_VISIBLE_ROWS = 5;

    /** Identifies each connector wire drawn on the TLB schematic, so the view can light it up on demand. */
    public enum TlbLine {
        USER_TO_TAG,
        PAGE_TO_TAG,
        ADDRESS_TO_TLB,
        WORD_PASSTHROUGH,
        BLOCK_OUT
    }

    /** How the running instruction touched a slot: found it already cached (HIT), looked and missed
     *  (MISS), or just wrote it (INSERT). MISS only ever marks the single deterministic row of a
     *  direct-mapped TLB. */
    public enum RowHighlight { NONE, HIT, MISS, INSERT }

    /** State of the current instruction's TLB probe, for colouring the split's tag readout. */
    public enum LookupOutcome { PENDING, HIT, MISS, INSERT }

    /** One TLB slot in the visible window: {@code index} is its real slot number, {@code highlight}
     *  marks the slot the running instruction just hit or was inserted into. */
    public record Row(int index, boolean valid, boolean dirty, long tag, long block, RowHighlight highlight) {}

    /** A TLB entry snapshot worth calling out because it changed off-window (an eviction's victim
     *  key, not the addressed one) -- rendered to look like an actual table row, not prose, so it
     *  reads as "here is the entry" rather than another step description. {@code user}/{@code page}
     *  are -1 when the entry's identity isn't safely decodable from its (possibly reduced) tag
     *  alone; the tag itself is always shown regardless. {@code index} (the entry's physical slot)
     *  is -1 when the entry has already been removed from the TLB's own storage by the time this
     *  is computed, so its slot can no longer be looked up. */
    public record TlbSideNote(String headline, int user, long page, int index, long tag, boolean dirty, long block) {}

    private final PageSimulationContext context;
    private final TLB tlb;
    // Non-null iff this is a set-associative TLB; drives the one-table-per-way schematic.
    private final SetAssociativeTLB sat;
    private final Simulation simulation;

    private final ObservableList<Row> visibleRows = FXCollections.observableArrayList();
    // Set-associative only: one window of Rows per way (entriesPerSet lists), each indexed by set
    // number. Left empty for direct / fully-associative TLBs, which use visibleRows instead.
    private final List<ObservableList<Row>> wayRows = new ArrayList<>();
    // Top slot of the visible window; only moves when the current instruction resolves to a slot, so
    // the table "scrolls" to the relevant entry on a hit or an insertion and otherwise stays put.
    private int windowStart = 0;
    private final IntegerProperty tagHexDigits = new SimpleIntegerProperty(1);
    private final IntegerProperty blockHexDigits = new SimpleIntegerProperty(1);

    private final StringProperty userHex = new SimpleStringProperty("/");
    private final StringProperty pageHex = new SimpleStringProperty("/");
    private final StringProperty wordHex = new SimpleStringProperty("/");
    // fullTagHex is the whole user@page key coming out of the merge brace; tagHex is the reduced
    // k@p - m value that survives the direct-mapped split; indexValue is the complementary low m
    // bits the split routes into the row/set index instead.
    private final StringProperty fullTagHex = new SimpleStringProperty("/");
    private final StringProperty tagHex = new SimpleStringProperty("/");
    private final StringProperty indexValue = new SimpleStringProperty("/");
    private final StringProperty blockHex = new SimpleStringProperty("/");
    private final StringProperty paWordHex = new SimpleStringProperty("/");

    private final ObjectProperty<TLBType> tlbType = new SimpleObjectProperty<>(TLBType.ASSOCIATIVE);
    private final Map<TlbLine, BooleanProperty> lineActive = new EnumMap<>(TlbLine.class);
    // Notification card for an eviction's off-window TLB-entry invalidation -- the victim key is
    // generally not the addressed one, so it never shows up in the visible window above. Null when
    // there's nothing to show. Dismissible the same way PagedMMUTabViewModel's own card is.
    private final ObjectProperty<TlbSideNote> sideNote = new SimpleObjectProperty<>(null);
    private SimulationStep<? extends SimulationContext> lastNoteStep = null;
    private boolean sideNoteDismissed = false;

    // Outcome of the current instruction's probe, and which visible-window row (0-based, or -1) it
    // resolved to -- both drive the direct-mapped schematic's colouring and address anchor.
    private final ObjectProperty<LookupOutcome> lookupOutcome = new SimpleObjectProperty<>(LookupOutcome.PENDING);
    private final IntegerProperty selectedWindowRow = new SimpleIntegerProperty(-1);
    // Set-associative only: which way (0-based) the current instruction hit or was inserted into,
    // or -1 (miss / pending). The view uses it to route the block output off that way's table.
    private final IntegerProperty resolvedWay = new SimpleIntegerProperty(-1);

    // Bit widths are fixed for the lifetime of a simulation, so these are plain fields, not properties
    private final int processIdBits;
    private final int pageBits;
    private final int wordBits;
    // Reduced tag width: the whole k@p key for a fully-associative TLB, k@p - m once a direct- or
    // set-associative TLB folds its low m bits out into the row/set index.
    private final int indexBits;
    private final int tagBits;
    private final int frameBits;
    // Fixed set geometry: entriesPerSet = 1 / numSets = size for non-set-associative TLBs, so the
    // window helpers below stay correct for every TLB flavour.
    private final int entriesPerSet;
    private final int numSets;
    private final IntegerProperty currentStepNumber;

    public PagedTLBTabViewModel(PageSimulationContext context, SimulationViewModel simulationViewModel)
    {
        this.context = context;
        this.tlb = context.getTLB();
        this.sat = tlb instanceof SetAssociativeTLB setAssociative ? setAssociative : null;
        this.simulation = simulationViewModel.getSimulation();
        this.currentStepNumber = simulationViewModel.currentStepNumberProperty();

        for (TlbLine line : TlbLine.values())
            lineActive.put(line, new SimpleBooleanProperty(false));

        this.entriesPerSet = sat != null ? sat.getEntriesPerSet() : 1;
        this.numSets = sat != null ? sat.getNumSets() : tlb.getSize();
        for (int w = 0; w < entriesPerSet; w++)
            wayRows.add(FXCollections.observableArrayList());

        this.processIdBits = tlb.getProcessIdBits();
        this.pageBits = tlb.getAddressComponentBits();
        this.wordBits = context.getWordBits();
        this.indexBits = tlb.getIndexComponentBits();
        this.tagBits = processIdBits + pageBits - indexBits;
        this.frameBits = context.getPhysicalAddressBits() - context.getWordBits();

        tagHexDigits.set(ValueConverter.hexDigitsFor(tagBits));
        blockHexDigits.set(ValueConverter.hexDigitsFor(frameBits));
        tlbType.set(context.getTlbType());

        currentStepNumber.addListener(stepListener);
        refresh();
    }

    /** The underlying simulation context, for a TLB inspector window opened off the schematic. */
    public PageSimulationContext getContext() { return context; }

    public IntegerProperty currentStepNumberProperty() { return currentStepNumber; }

    public int getMaxVisibleRows()
    {
        return Math.min(MAX_VISIBLE_ROWS, numSets);
    }

    public ObservableList<Row> getVisibleRows()
    {
        return visibleRows;
    }

    /** True for a set-associative TLB, which the tab renders as one windowed table per way. */
    public boolean isSetAssociative()
    {
        return sat != null;
    }

    /** Number of way-tables (associativity); 1 for a non-set-associative TLB. */
    public int getWayCount()
    {
        return entriesPerSet;
    }

    /** The visible window of set rows for one way (set-associative only). */
    public ObservableList<Row> getWayRows(int way)
    {
        return wayRows.get(way);
    }

    public IntegerProperty resolvedWayProperty() { return resolvedWay; }

    public int getProcessIdBits() { return processIdBits; }
    public int getPageBits() { return pageBits; }
    public int getWordBits() { return wordBits; }
    /** Bits folded out of the tag into the row/set index (0 for a fully-associative TLB). */
    public int getIndexBits() { return indexBits; }
    /** Width of the whole user@page key, before the index bits are split off. */
    public int getFullKeyBits() { return processIdBits + pageBits; }
    /** Width of the reduced tag actually stored/compared/shown (k@p - m). */
    public int getTagBits() { return tagBits; }
    public int getFrameBits() { return frameBits; }

    public ObjectProperty<LookupOutcome> lookupOutcomeProperty() { return lookupOutcome; }
    public IntegerProperty selectedWindowRowProperty() { return selectedWindowRow; }

    public IntegerProperty tagHexDigitsProperty() { return tagHexDigits; }
    public IntegerProperty blockHexDigitsProperty() { return blockHexDigits; }

    public StringProperty userHexProperty() { return userHex; }
    public StringProperty pageHexProperty() { return pageHex; }
    public StringProperty wordHexProperty() { return wordHex; }
    /** Whole user@page key value out of the merge brace ("/" until the address is formed). */
    public StringProperty fullTagHexProperty() { return fullTagHex; }
    public StringProperty tagHexProperty() { return tagHex; }
    /** Low index-bits slice of the key routed into the row/set select ("/" until the address is formed). */
    public StringProperty indexValueProperty() { return indexValue; }
    public StringProperty blockHexProperty() { return blockHex; }
    public StringProperty paWordHexProperty() { return paWordHex; }

    public ObjectProperty<TLBType> tlbTypeProperty() { return tlbType; }

    /** True while the given connector's step has run since the last instruction fetch. */
    public BooleanProperty lineActiveProperty(TlbLine line)
    {
        return lineActive.get(line);
    }

    /** Notification card describing a TLB entry change worth calling out; null when there's
     *  nothing to show. */
    public ObjectProperty<TlbSideNote> sideNoteProperty()
    {
        return sideNote;
    }

    /** Dismisses the current notification card; it stays hidden until a genuinely new occurrence. */
    public void dismissSideNote()
    {
        sideNoteDismissed = true;
        sideNote.set(null);
    }

    private void refresh()
    {
        recomputeActiveLines();

        boolean hasInstruction = context.hasCurrentInstruction();
        boolean addressFormed = lineActive.get(TlbLine.ADDRESS_TO_TLB).get();
        boolean physicalAddressFormed = lineActive.get(TlbLine.WORD_PASSTHROUGH).get();
        boolean blockFormed = lineActive.get(TlbLine.BLOCK_OUT).get();
        boolean inserted = hasInstruction && tlbUpdatedSinceFetch();

        long fullTag = 0;
        long shownTag = 0;
        if (hasInstruction)
        {
            int user = context.getCurrentInstruction().getUser();
            long page = context.getPageComponent();
            long word = context.getWordComponent();
            fullTag = tlb.calculateTag(user, page);
            shownTag = fullTag >>> indexBits;

            userHex.set(toHex(user, ValueConverter.hexDigitsFor(processIdBits)));
            pageHex.set(toHex(page, ValueConverter.hexDigitsFor(pageBits)));
            wordHex.set(toHex(word, ValueConverter.hexDigitsFor(wordBits)));
            fullTagHex.set(addressFormed
                    ? toHex(fullTag, ValueConverter.hexDigitsFor(processIdBits + pageBits)) : "/");
            tagHex.set(addressFormed ? toHex(shownTag, tagHexDigits.get()) : "/");
            long index = indexBits > 0 ? (fullTag & ((1L << indexBits) - 1)) : 0;
            indexValue.set(addressFormed ? toHex(index, ValueConverter.hexDigitsFor(indexBits)) : "/");

            TLBEntry entry = tlb.lookup(fullTag);
            blockHex.set(blockFormed && entry != null ? toHex(entry.getBlock(), blockHexDigits.get()) : "/");
            paWordHex.set(physicalAddressFormed ? wordHex.get() : "/");
        }
        else
        {
            userHex.set("/");
            pageHex.set("/");
            wordHex.set("/");
            fullTagHex.set("/");
            tagHex.set("/");
            indexValue.set("/");
            blockHex.set("/");
            paWordHex.set("/");
        }

        // Probe outcome: PENDING until the lookup wire lights; then HIT / MISS, or INSERT once the
        // refill step has run. The tag readout and (direct-mapped) selected row are coloured from it.
        LookupOutcome outcome;
        if (!hasInstruction || !addressFormed)
            outcome = LookupOutcome.PENDING;
        else if (inserted)
            outcome = LookupOutcome.INSERT;
        else if (tlb.lookup(fullTag) != null)
            outcome = LookupOutcome.HIT;
        else
            outcome = LookupOutcome.MISS;
        lookupOutcome.set(outcome);

        if (sat != null)
        {
            refreshSetAssociative(hasInstruction, addressFormed, inserted, fullTag, outcome);
            return;
        }

        List<TLBEntry> entries = tlb.getEntries();
        int windowRows = getMaxVisibleRows();

        // The window recentres only once the current instruction resolves to a row -- a hit, a miss
        // (direct-mapped: the deterministic row it probed, even if empty), or after its insertion --
        // so stepping into the TLB lookup/refill scrolls the table there and stepping elsewhere
        // leaves it put.
        int relevantIndex = -1;
        if (hasInstruction && (addressFormed || inserted))
        {
            int mapped = tlb.mappedSlot(fullTag);
            relevantIndex = mapped >= 0 ? mapped : indexOfTag(shownTag);
        }
        if (relevantIndex >= 0)
        {
            int maxStart = Math.max(0, entries.size() - windowRows);
            windowStart = Math.max(0, Math.min(relevantIndex - windowRows / 2, maxStart));
        }

        RowHighlight relevantHighlight = switch (outcome)
        {
            case HIT -> RowHighlight.HIT;
            case MISS -> RowHighlight.MISS;
            case INSERT -> RowHighlight.INSERT;
            case PENDING -> RowHighlight.NONE;
        };

        visibleRows.clear();
        for (int i = 0; i < windowRows; i++)
        {
            int slot = windowStart + i;
            RowHighlight highlight = slot == relevantIndex ? relevantHighlight : RowHighlight.NONE;
            TLBEntry entry = slot < entries.size() ? entries.get(slot) : null;
            visibleRows.add(entry == null
                    ? new Row(slot, false, false, 0, 0, highlight)
                    : new Row(slot, entry.isValid(), entry.isDirty(), entry.getTag(), entry.getBlock(), highlight));
        }

        selectedWindowRow.set(
                outcome != LookupOutcome.PENDING
                        && relevantIndex >= windowStart
                        && relevantIndex < windowStart + windowRows
                ? relevantIndex - windowStart : -1);
    }

    // Set-associative variant: one windowed table per way, every table indexed by set number and
    // recentred on the addressed set. The addressed set's row is coloured per way -- the resolved
    // way (hit / just inserted) green or blue, every sibling way red; a plain miss reds them all.
    private void refreshSetAssociative(boolean hasInstruction, boolean addressFormed,
            boolean inserted, long fullTag, LookupOutcome outcome)
    {
        List<TLBEntry> entries = tlb.getEntries();
        int windowRows = getMaxVisibleRows();
        int maxStart = Math.max(0, numSets - windowRows);

        int selectedSet = -1;
        int resolved = -1;
        if (hasInstruction && (addressFormed || inserted))
        {
            selectedSet = sat.setIndexOf(fullTag);
            resolved = sat.wayHolding(fullTag);
            windowStart = Math.max(0, Math.min(selectedSet - windowRows / 2, maxStart));
        }
        resolvedWay.set(resolved);

        boolean resolvedRow = outcome != LookupOutcome.PENDING && selectedSet >= 0;

        for (int w = 0; w < entriesPerSet; w++)
        {
            ObservableList<Row> rows = wayRows.get(w);
            rows.clear();
            for (int i = 0; i < windowRows; i++)
            {
                int set = windowStart + i;
                RowHighlight highlight = RowHighlight.NONE;
                if (resolvedRow && set == selectedSet)
                {
                    highlight = w == resolved
                            ? (outcome == LookupOutcome.INSERT ? RowHighlight.INSERT : RowHighlight.HIT)
                            : RowHighlight.MISS;
                }
                TLBEntry entry = set < numSets ? entries.get(sat.slotFor(set, w)) : null;
                rows.add(entry == null
                        ? new Row(set, false, false, 0, 0, highlight)
                        : new Row(set, entry.isValid(), entry.isDirty(), entry.getTag(), entry.getBlock(), highlight));
            }
        }

        selectedWindowRow.set(
                resolvedRow && selectedSet >= windowStart && selectedSet < windowStart + windowRows
                        ? selectedSet - windowStart : -1);
    }

    /** Slot holding the entry with this reduced tag, or -1 if it is not currently cached. */
    private int indexOfTag(long shownTag)
    {
        List<TLBEntry> entries = tlb.getEntries();
        for (int i = 0; i < entries.size(); i++)
        {
            TLBEntry entry = entries.get(i);
            if (entry != null && entry.getTag() == shownTag)
                return i;
        }
        return -1;
    }

    /** True if a TLB insertion has run at any point since the current instruction's fetch. */
    private boolean tlbUpdatedSinceFetch()
    {
        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        for (int i = history.size() - 1; i >= 0; i--)
        {
            SimulationStep<? extends SimulationContext> step = history.get(i);
            if (step instanceof InstructionFetchStep)
                return false;
            if (step instanceof TLBUpdateStep)
                return true;
        }
        return false;
    }

    // Walks executed steps back to (but excluding) the most recent instruction fetch, unioning
    // the connectors each step type touches, so wires "stay lit" for the rest of that instruction.
    private void recomputeActiveLines()
    {
        Set<TlbLine> active = EnumSet.noneOf(TlbLine.class);

        List<SimulationStep<? extends SimulationContext>> history = simulation.getExecutedSteps();
        for (int i = history.size() - 1; i >= 0; i--)
        {
            SimulationStep<? extends SimulationContext> step = history.get(i);
            if (step instanceof InstructionFetchStep)
                break;

            active.addAll(linesFor(step));
        }

        for (TlbLine line : TlbLine.values())
            lineActive.get(line).set(active.contains(line));

        recomputeSideNote(history);
    }

    // Describes whatever the single current step just did to a TLB entry outside the visible
    // window -- scoped to that one step only (matching SimulationViewModel's tab-notification
    // badge exactly, and PagedMMUTabViewModel's own card). A step producing a *different* card
    // than last time is a new occurrence and clears any prior dismissal. Every other TLB-affecting
    // step touches the addressed key, already covered by this tab's own row highlighting
    // (RowHighlight HIT/MISS/INSERT); an eviction's victim is the one case that's genuinely a
    // different, otherwise-invisible key.
    private void recomputeSideNote(List<SimulationStep<? extends SimulationContext>> history)
    {
        SimulationStep<? extends SimulationContext> step = history.isEmpty() ? null : history.get(history.size() - 1);
        if (step != lastNoteStep)
        {
            sideNoteDismissed = false;
            lastNoteStep = step;
        }

        TlbSideNote note = null;
        if (!sideNoteDismissed && step instanceof PageEvictionStep && context.didPageEvictionInvalidateTlbEntry())
        {
            note = new TlbSideNote("Invalidated entry", context.getPageEvictionVictimUser(), context.getPageEvictionVictimPage(),
                    context.getPageEvictionInvalidatedTlbIndex(), context.getPageEvictionInvalidatedTlbTag(),
                    context.wasPageEvictionInvalidatedTlbDirty(), context.getPageEvictionInvalidatedTlbBlock());
        }
        else if (!sideNoteDismissed && step instanceof PageTLBEvictionStep)
        {
            String headline = tlb instanceof DirectTLB ? "Replaced entry" : "Evicted entry";
            // No decoded user/page here (sentinel -1): a direct/set-associative TLB stores a
            // reduced tag with the index/set bits already stripped out, so decoding it the same
            // way a fully-associative tag decodes would silently reconstruct the wrong page. The
            // tag itself (shown in the row below) is still the entry's real, undecoded identity.
            note = new TlbSideNote(headline, -1, -1, context.getEvictedTlbIndex(), context.getEvictedTlbTag(),
                    context.wasEvictedTlbDirty(), context.getEvictedTlbBlock());
        }
        sideNote.set(note);
    }

    private static Set<TlbLine> linesFor(SimulationStep<? extends SimulationContext> step)
    {
        if (step instanceof PageTLBLookupStep)
            return EnumSet.of(TlbLine.USER_TO_TAG, TlbLine.PAGE_TO_TAG, TlbLine.ADDRESS_TO_TLB);
        if (step instanceof PageFormPhysicalAddressFromTLBStep)
            return EnumSet.of(TlbLine.WORD_PASSTHROUGH, TlbLine.BLOCK_OUT);

        return EnumSet.noneOf(TlbLine.class);
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
