package rs.ac.bg.etf.view.os;

import javafx.collections.ListChangeListener;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel;
import rs.ac.bg.etf.viewmodel.PagedOSTabViewModel.UserSummary;

/**
 * Compact per-user page-table summary: page-table pointer and current valid-page count.
 * The full per-user page table itself stays on the MMU tab.
 */
public class UserSummaryView extends VBox
{
    private final PagedOSTabViewModel viewModel;
    private final VBox rows = new VBox(2);

    public UserSummaryView(PagedOSTabViewModel viewModel)
    {
        this.viewModel = viewModel;
        getStyleClass().add("os-user-summary");

        Label header = new Label("Page Tables (per user)");
        header.getStyleClass().add("mmu-section-label");
        getChildren().addAll(header, rows);

        viewModel.getUserSummaries().addListener((ListChangeListener<UserSummary>) c -> rebuild());
        rebuild();
    }

    private void rebuild()
    {
        rows.getChildren().clear();
        int ptpDigits = viewModel.ptpHexDigitsProperty().get();
        double userWidth = WidthCalculator.columnWidth("User 0000", 1);
        double ptpWidth = WidthCalculator.columnWidth("PTP " + "0".repeat(ptpDigits + 2), 1);

        for (UserSummary summary : viewModel.getUserSummaries())
        {
            Label user = cell("User " + summary.user(), userWidth);
            Label ptp = cell("PTP " + ValueConverter.toHex(summary.ptpAddress(), ptpDigits), ptpWidth);
            Label valid = cell("valid: " + summary.validPageCount(), 0);

            HBox row = new HBox(12, user, ptp, valid);
            row.getStyleClass().add("os-user-summary-row");
            rows.getChildren().add(row);
        }
    }

    private static Label cell(String text, double width)
    {
        Label label = new Label(text);
        label.getStyleClass().add("os-user-summary-cell");
        if (width > 0)
            label.setPrefWidth(width);
        return label;
    }
}
