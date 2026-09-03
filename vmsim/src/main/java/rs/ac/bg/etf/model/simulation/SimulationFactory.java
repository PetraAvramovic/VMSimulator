package rs.ac.bg.etf.model.simulation;

public class SimulationFactory 
{
    public static Simulation createSimulation(SimulationConfig config)
    {
        SimulationContext context = null;
        switch(config.getTranslationType())
        {
            case PAGED:
                context = new PageSimulationContext(config);
                break;
            case SEGMENTED:
                break;
            case SEGMENTED_PAGED:
                break;
            default:
                break;
            
        }

        Simulation simulation = new Simulation(context);

        return simulation;
    }
}
