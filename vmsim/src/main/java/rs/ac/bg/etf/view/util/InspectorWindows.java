package rs.ac.bg.etf.view.util;

import java.util.ArrayList;
import java.util.function.Supplier;

import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * What every inspector window (page table, TLB, memory, disk block, replacement queue) has in common
 * beyond its own table: the scale it is drawn at, and when it goes away.
 *
 * <ul>
 *   <li><b>Scale.</b> An inspector is its own window, sized by the user, not a part of the main
 *       screen -- so it must not shrink with it. Drawn at the main window's scale when the app is
 *       windowed, its text becomes unreadable. It is built at {@link #SCALE_FLOOR} at the least (the
 *       1080p design size) and still grows with the app on a large display; like any window it keeps
 *       the scale it was built at until it is closed (see {@link UiScale#atScale}).</li>
 *   <li><b>Lifetime.</b> An inspector belongs to the simulation: {@link #closeAll()} is called when
 *       the user leaves the simulation screen, so none stays open over the main menu.</li>
 * </ul>
 */
public final class InspectorWindows {
    /** The smallest scale an inspector is ever drawn at. */
    public static final double SCALE_FLOOR = UiScale.DESIGN_FACTOR;

    // Marks a stage as an inspector in its Window properties, so closeAll() can find them all without
    // anything having to keep a list of them (and so keep them alive).
    private static final String INSPECTOR_KEY = "vmsim.inspector";

    private InspectorWindows() {
    }

    /**
     * Builds an inspector's stage: everything {@code builder} asks {@link UiScale} for (lengths, fonts,
     * column widths, the stylesheet) is resolved at the inspector's own scale, and the stage is marked
     * so {@link #closeAll()} closes it.
     */
    public static Stage build(Supplier<Stage> builder) {
        Stage stage = UiScale.atScale(Math.max(SCALE_FLOOR, UiScale.factor()), builder);
        stage.getProperties().put(INSPECTOR_KEY, Boolean.TRUE);
        return stage;
    }

    /** Closes every inspector window that is open. */
    public static void closeAll() {
        // A copy: hiding a window removes it from the list being walked.
        for (Window window : new ArrayList<>(Window.getWindows()))
            if (window.getProperties().containsKey(INSPECTOR_KEY))
                window.hide();
    }
}
