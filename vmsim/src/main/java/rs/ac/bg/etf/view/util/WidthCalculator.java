package rs.ac.bg.etf.view.util;

import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class WidthCalculator {
    private static final Font CELL_FONT = Font.font("Consolas", 13);
    private static final Font HEADER_FONT = Font.font("Consolas", javafx.scene.text.FontWeight.BOLD, 12);
    private static final double CELL_PADDING = 16;

    public static double columnWidth(String header, int digits) 
    {
        Text valueSample = new Text("F".repeat(Math.max(digits + 2, 1)));
        valueSample.setFont(CELL_FONT);
        Text headerSample = new Text(header);
        headerSample.setFont(HEADER_FONT);
        double widest = Math.max(valueSample.getLayoutBounds().getWidth(), headerSample.getLayoutBounds().getWidth());
        return widest + CELL_PADDING;
    }
}
