package rs.ac.bg.etf.viewmodel;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.simulation.SimulationContext;

public class MemoryViewModel 
{
    private SimulationContext context;

    private static final int VIEWPORT_ROW_COUNT = 100;

    public MemoryViewModel(SimulationContext context) 
    {
        this.context = context;
    }

    public String fetchCellContent(long targetAddress) 
    {
        long value = context.getValueAtAddress(targetAddress);

        return Long.toBinaryString(value);
    }

    public ObservableList<Long> fetchViewportSlice(long baseAddress) {
        ObservableList<Long> visibleSlice = FXCollections.observableArrayList();
        
        // Calculate system maximum capacity to prevent running out of bounds
        long maxSystemAddress = context.getPhysicalMemorySize();

        for (int i = 0; i < VIEWPORT_ROW_COUNT; i++) {
            long currentTargetAddress = baseAddress + i;
            
            if (currentTargetAddress >= maxSystemAddress) {
                break; // Slipped past maximum physical RAM boundary
            }
            
            visibleSlice.add(currentTargetAddress);
        }
        
        return visibleSlice;
    }
}
