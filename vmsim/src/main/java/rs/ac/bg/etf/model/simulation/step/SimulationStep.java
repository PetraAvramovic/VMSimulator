package rs.ac.bg.etf.model.simulation.step;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import rs.ac.bg.etf.model.simulation.SimulationComponent;
import rs.ac.bg.etf.model.simulation.SimulationContext;

public abstract class SimulationStep<T extends SimulationContext>
{
    protected T context;

    private Set<SimulationComponent> affectedComponents = Collections.emptySet();

    protected SimulationStep(T context)
    {
        this.context = context;
    }

    public abstract SimulationStep<T> execute();
    public abstract void undo();

    /**
     * Locale-free description of the action this step performed, read after execute() runs.
     * The view layer turns this into displayable text.
     */
    public abstract StepDescription getStepDescription();

    /** True for the step that starts a new instruction (only {@link InstructionFetchStep}). */
    public boolean isFirst()
    {
        return false;
    }

    /**
     * Called once from execute(), with the complete set of components this call touched (replaces
     * whatever was set before, never accumulates) -- execute() can run more than once on the same
     * instance (undo() followed by a redo through Simulation.nextStep()), so every call must be
     * self-contained rather than relying on an external reset.
     */
    protected final void setAffectedComponents(SimulationComponent... components)
    {
        EnumSet<SimulationComponent> set = EnumSet.noneOf(SimulationComponent.class);
        Collections.addAll(set, components);
        this.affectedComponents = Collections.unmodifiableSet(set);
    }

    /** Which components this step touched; valid once execute() has run. Empty until then. */
    public final Set<SimulationComponent> getAffectedComponents()
    {
        return affectedComponents;
    }
}
