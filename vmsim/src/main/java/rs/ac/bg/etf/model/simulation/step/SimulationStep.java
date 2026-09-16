package rs.ac.bg.etf.model.simulation.step;

import rs.ac.bg.etf.model.simulation.SimulationContext;

public abstract class SimulationStep<T extends SimulationContext>
{
    protected T context;

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
}
