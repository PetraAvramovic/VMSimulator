package rs.ac.bg.etf.model.simulation;

/** Which simulated hardware/OS subsystem a {@link rs.ac.bg.etf.model.simulation.step.SimulationStep}
 *  touched -- a domain concept, not a UI one; the view layer is the only place that maps this to a
 *  workbench tab. */
public enum SimulationComponent
{
    MMU, TLB, OS, MEMORY
}
