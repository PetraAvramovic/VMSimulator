package rs.ac.bg.etf.view.util;

import java.util.function.DoubleConsumer;

import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * Hosts the current screen at the size of the window and decides when it should be rebuilt at a
 * different UI scale (see {@link UiScale} / {@link ResponsiveLayout}).
 *
 * <p>The screen is simply given the whole viewport -- no transform -- so what the user sees is real
 * layout at real pixel sizes. What this class adds is the feedback loop: the screen reports its
 * minimum size (its root's {@code minWidth}/{@code minHeight}, in the pixels of the scale it was
 * built at); dividing by that scale gives the screen's <em>design</em> minimum, from which {@link
 * ResponsiveLayout#targetScale} picks the scale this window wants. If a different scale is called
 * for (see {@link #wantedScale()}), the owner is asked to rebuild the screen ({@link
 * #setOnScaleRequested}) after {@link ResponsiveLayout#RESCALE_DELAY_MILLIS}, and no more often than
 * every {@link ResponsiveLayout#RESCALE_INTERVAL_MILLIS} -- so a window being dragged is followed
 * step by step rather than left at its old scale until the drag ends.
 *
 * <p>A rebuild is expensive, so the rules for asking for one are deliberately lopsided:
 * <ul>
 *   <li><b>Growing</b> is decided from the design minimum -- if the window has room for a bigger
 *       scale, take it.</li>
 *   <li><b>Shrinking</b> only happens when the screen <em>really</em> doesn't fit the window right
 *       now (its actual measured minimum at the current scale exceeds the viewport). Text is
 *       measured at whole-pixel font sizes, so a rebuilt screen's minimum can come out a couple of
 *       percent larger than estimated; re-deriving the scale from that estimate on every rebuild
 *       would step down again and again for a screen that fits perfectly well.</li>
 * </ul>
 */
public final class ResponsiveHost extends Region {
    // Sub-pixel slack when deciding whether the screen fits (its minimum is snapped up to whole
    // pixels, the viewport is not).
    private static final double FIT_SLACK = 1;

    private final ReadOnlyDoubleWrapper minSceneWidth = new ReadOnlyDoubleWrapper(this, "minSceneWidth", 0);
    private final ReadOnlyDoubleWrapper minSceneHeight = new ReadOnlyDoubleWrapper(this, "minSceneHeight", 0);
    private final PauseTransition settle = new PauseTransition(Duration.millis(ResponsiveLayout.RESCALE_DELAY_MILLIS));

    private Node content;
    private DoubleConsumer onScaleRequested = requested -> { };

    // When the last rebuild ended, to keep rebuilds RESCALE_INTERVAL_MILLIS apart.
    private boolean rescaledBefore;
    private long lastRescaleNanos;

    // The screen's smallest usable size at scale 1.0. Only ever grows while the same screen is
    // showing: a later measurement can be a few pixels larger than an earlier one purely from
    // rounding, and letting the estimate wobble both ways could flip the scale back and forth.
    private double designMinWidth;
    private double designMinHeight;

    // Registered on whichever scene the host is in, ahead of everything that hooks the pulse later.
    private final Runnable settleInPulse = this::settleInPulse;

    public ResponsiveHost() {
        settle.setOnFinished(event -> requestScaleIfNeeded());
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (oldScene != null)
                oldScene.removePostLayoutPulseListener(settleInPulse);
            if (newScene != null)
                newScene.addPostLayoutPulseListener(settleInPulse);
        });
    }

    /**
     * Swaps the hosted screen ({@code null} detaches it, e.g. just before it is rebuilt).
     *
     * @param sameScreen true when this is the same screen rebuilt at another scale (its measured
     *                   design minimum carries over); false for a different screen
     */
    public void setContent(Node newContent, boolean sameScreen) {
        if (content != null)
            getChildren().remove(content);
        content = newContent;
        if (content != null)
            getChildren().add(content);
        if (!sameScreen) {
            designMinWidth = 0;
            designMinHeight = 0;
        }
        settle.stop();
        requestLayout();
    }

    /** Called (on the FX thread) with the scale this window wants whenever it isn't the current one. */
    public void setOnScaleRequested(DoubleConsumer handler) {
        this.onScaleRequested = handler;
    }

    /** Smallest scene width worth allowing for the hosted screen (its minimum, at MIN_SCALE). */
    public ReadOnlyDoubleProperty minSceneWidthProperty() {
        return minSceneWidth.getReadOnlyProperty();
    }

    public double getMinSceneWidth() {
        return minSceneWidth.get();
    }

    /** Smallest scene height worth allowing for the hosted screen (its minimum, at MIN_SCALE). */
    public ReadOnlyDoubleProperty minSceneHeightProperty() {
        return minSceneHeight.getReadOnlyProperty();
    }

    public double getMinSceneHeight() {
        return minSceneHeight.get();
    }

    @Override
    protected void layoutChildren() {
        if (content == null)
            return;

        content.resizeRelocate(0, 0, getWidth(), getHeight());

        double scale = UiScale.factor();
        designMinWidth = Math.max(designMinWidth, content.minWidth(-1) / scale);
        designMinHeight = Math.max(designMinHeight, content.minHeight(-1) / scale);
        minSceneWidth.set(ResponsiveLayout.smallestSceneSize(designMinWidth));
        minSceneHeight.set(ResponsiveLayout.smallestSceneSize(designMinHeight));

        if (wantedScale() != scale)
            startRescaleTimer();
        else
            settle.stop();
    }

    // Not restarted while it is already counting down: a window that keeps changing must not keep
    // pushing the rebuild back until it stops -- that left the UI at its old scale for the whole drag,
    // then jumped it. The scale wanted is read again when the timer fires, so it is the current one.
    private void startRescaleTimer() {
        if (settle.getStatus() == Animation.Status.RUNNING)
            return;
        double sinceLastMillis = rescaledBefore
                ? (System.nanoTime() - lastRescaleNanos) / 1_000_000.0
                : Double.POSITIVE_INFINITY;
        double wait = Math.max(ResponsiveLayout.RESCALE_DELAY_MILLIS,
                ResponsiveLayout.RESCALE_INTERVAL_MILLIS - sinceLastMillis);
        settle.setDuration(Duration.millis(wait));
        settle.playFromStart();
    }

    // Whether the hosted screen's actual minimum size, at the scale it is built at, fits the window.
    private boolean contentFits() {
        return content.minWidth(-1) <= getWidth() + FIT_SLACK
                && content.minHeight(-1) <= getHeight() + FIT_SLACK;
    }

    /**
     * Brings the screen that was just mounted to its final layout right now: CSS, layout, and every
     * re-route job waiting for a layout pass, for {@link ResponsiveLayout#SETTLE_PASSES} rounds. A new
     * screen otherwise reaches that state over the next few pulses (a canvas grows to its viewport, a
     * label gets measured, a design size propagates, the wires follow), and every one of those pulses
     * is drawn -- wires visibly sitting where they are about to leave. Done in the same event that
     * mounted the screen, before any pulse, the first frame is the settled one. It also gives
     * {@link #wantedScale()} real measurements to decide from.
     */
    public void settleNow() {
        if (content == null || getScene() == null || getWidth() <= 0 || getHeight() <= 0)
            return;
        settleRounds();
    }

    /**
     * Runs after every pulse's own layout pass, before anything is drawn: the re-route jobs waiting
     * for that pass run now, and whatever they (or the new nodes' first styling) changed is laid out
     * again, for {@link ResponsiveLayout#SETTLE_PASSES} rounds. So the frame drawn is the settled one
     * whichever way the screen got here -- a window resize, a step of the simulation, or a tab clicked
     * for the first time (built on the spot, and the TLB tab in particular needs several rounds: its
     * first frame had most of its labels and wires still off, and it visibly "resized" into place).
     * When nothing is waiting a round is a few cheap no-ops.
     */
    private void settleInPulse() {
        if (content == null)
            return;
        settleRounds();
    }

    private void settleRounds() {
        for (int pass = 0; pass < ResponsiveLayout.SETTLE_PASSES; pass++) {
            applyCss();
            layout();
            PostLayoutTask.flushPending();
        }
    }

    /** The scale this window should be showing the screen at: the current one unless a rebuild is worthwhile. */
    public double wantedScale() {
        double current = UiScale.factor();
        if (content == null)
            return current;
        double target = ResponsiveLayout.targetScale(getWidth(), getHeight(), designMinWidth, designMinHeight);

        if (target > current)
            return target;
        if (!contentFits() && current > ResponsiveLayout.MIN_SCALE)
            // Must shrink: as far as the design estimate says, and at least one step -- the estimate
            // can say "fits" for a screen whose measured minimum, rounded up, doesn't.
            return Math.min(target, ResponsiveLayout.stepBelow(current));
        return current;
    }

    private void requestScaleIfNeeded() {
        if (content == null)
            return;
        double wanted = wantedScale();
        if (wanted != UiScale.factor()) {
            onScaleRequested.accept(wanted);
            rescaledBefore = true;
            lastRescaleNanos = System.nanoTime();
        }
    }

    @Override
    protected double computePrefWidth(double height) {
        return content != null ? content.prefWidth(-1) : 0;
    }

    @Override
    protected double computePrefHeight(double width) {
        return content != null ? content.prefHeight(-1) : 0;
    }

    @Override
    protected double computeMinWidth(double height) {
        return 0;
    }

    @Override
    protected double computeMinHeight(double width) {
        return 0;
    }
}
