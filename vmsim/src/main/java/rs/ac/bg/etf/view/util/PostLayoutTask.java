package rs.ac.bg.etf.view.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.Scene;

/**
 * A repositioning job -- re-routing wires to wherever layout has just put the boxes they connect --
 * that runs at most once per pulse, right after the scene's layout pass.
 *
 * <p>The schematic tabs used to do {@code Platform.runLater(job)} from every layout-property
 * listener, which cost twice over during a window resize:
 * <ul>
 *   <li><b>Once per change, not once per pass.</b> A resize changes a dozen layout properties per
 *       pulse, and each one queued its own run of a job that makes dozens of {@code localToScene}
 *       and property-setter calls.</li>
 *   <li><b>One frame late.</b> A {@code runLater} raised from a layout listener only runs once the
 *       pulse is over, so that pulse draws the boxes where layout just put them and the wires where
 *       they were. A post-layout pulse listener runs after layout but before the nodes are
 *       synchronized for rendering, so boxes and wires are drawn together.</li>
 * </ul>
 *
 * <p>A request raised outside a pulse (a click, a simulation step) may not have a pulse coming, so
 * a {@code runLater} is queued as well; whichever fires first runs the job and the other finds
 * nothing to do.
 *
 * <p>A job whose owner is not showing is skipped: {@code TabPaneSkin} keeps every tab's content in
 * the scene and sizes it on every resize, just invisibly, so without this the wires of tabs nobody
 * can see were re-routed on every pulse too. The request is picked up again the moment the hidden
 * ancestor turns visible.
 *
 * <p>Jobs must be idempotent and derive everything from the current layout, since they may run more
 * than once for the same state. Not thread-safe -- FX application thread only.
 */
public final class PostLayoutTask {
    // Upper bound on rounds of "run everything that is waiting" in flushPending(): a job may request
    // another, and two that keep re-requesting each other must not loop forever.
    private static final int MAX_FLUSH_ROUNDS = 8;

    // Every task with a request waiting, in the order they were made. See flushPending().
    private static final Set<PostLayoutTask> waiting = new LinkedHashSet<>();

    private final Node owner;
    private final Runnable job;
    private final boolean skipWhenHidden;

    // One Runnable instance, so the scene's listener list can be told to drop exactly this one.
    private final Runnable onPulse = this::run;
    private final ChangeListener<Boolean> onAncestorVisibility = this::ancestorVisibilityChanged;

    private boolean pending;
    private Scene hookedScene;
    // The hidden ancestor this task is waiting on, or null when it is not waiting.
    private Node parkedOn;

    /**
     * @param owner the node whose scene the job is synchronized with, and whose visibility gates it
     * @param job   what to run; see the class comment for what it may assume
     */
    public PostLayoutTask(Node owner, Runnable job) {
        this(owner, job, true);
    }

    /**
     * @param skipWhenHidden false for a job that must run even while its owner is not showing (e.g.
     *                       telling the workbench a hidden tab's minimum size changed)
     */
    public PostLayoutTask(Node owner, Runnable job, boolean skipWhenHidden) {
        this.owner = owner;
        this.job = job;
        this.skipWhenHidden = skipWhenHidden;
    }

    /**
     * Runs every job that is waiting for the next layout pass, right now, until nothing is waiting.
     * A screen that has just been built calls this between explicit layout passes so it reaches its
     * final state in one go, before the first frame is drawn, instead of over the next few pulses
     * (which shows wires in places they are about to leave).
     *
     * @return whether any job ran
     */
    public static boolean flushPending() {
        boolean ranAny = false;
        for (int round = 0; round < MAX_FLUSH_ROUNDS && !waiting.isEmpty(); round++) {
            for (PostLayoutTask task : new ArrayList<>(waiting)) {
                task.run();
                ranAny = true;
            }
        }
        return ranAny;
    }

    /** Asks for the job to run once, after the next layout pass; requests before then are merged. */
    public void request() {
        if (parkedOn != null)
            return;
        if (pending) {
            // A request made while the screen was still being built (no scene yet) only got the
            // runLater fallback. The first layout pass then raises more requests; they must not be
            // merged away without hooking the pulse now that there is one -- or the job would run
            // after that pulse's frame is drawn, showing wires a frame late.
            hookIntoPulse();
            return;
        }
        pending = true;
        waiting.add(this);
        hookIntoPulse();
        Platform.runLater(onPulse);
    }

    private void hookIntoPulse() {
        Scene scene = owner.getScene();
        if (scene != null && hookedScene == null) {
            hookedScene = scene;
            scene.addPostLayoutPulseListener(onPulse);
        }
    }

    /**
     * Runs the job right now (unless the owner is hidden) and drops a request still waiting for the
     * next pulse, since it would only redo this. For a job that reads what another task's job
     * writes: jobs of different tasks run in no guaranteed order, so the reader calls the writer's
     * {@code runNow()} first instead of hoping it already ran.
     */
    public void runNow() {
        if (parkedOn != null)
            return;
        pending = true;
        run();
    }

    private void run() {
        if (!pending)
            return;
        // Cleared before the job runs, so a request the job itself raises is honoured (for the next
        // pulse) instead of being merged into the run that is already under way.
        pending = false;
        waiting.remove(this);
        if (hookedScene != null) {
            hookedScene.removePostLayoutPulseListener(onPulse);
            hookedScene = null;
        }

        Node hidden = skipWhenHidden ? firstHiddenAncestor() : null;
        if (hidden != null) {
            parkedOn = hidden;
            hidden.visibleProperty().addListener(onAncestorVisibility);
            return;
        }
        job.run();
    }

    private Node firstHiddenAncestor() {
        for (Node node = owner; node != null; node = node.getParent())
            if (!node.isVisible())
                return node;
        return null;
    }

    private void ancestorVisibilityChanged(ObservableValue<? extends Boolean> observable, Boolean wasVisible, Boolean isVisible) {
        if (!isVisible || parkedOn == null)
            return;
        parkedOn.visibleProperty().removeListener(onAncestorVisibility);
        parkedOn = null;
        request();
    }
}
