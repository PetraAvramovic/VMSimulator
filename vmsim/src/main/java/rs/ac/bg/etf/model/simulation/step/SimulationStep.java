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
     * Human-readable description of the action this step performed, read after execute() runs.
     */
    public abstract String getDescription();
}
