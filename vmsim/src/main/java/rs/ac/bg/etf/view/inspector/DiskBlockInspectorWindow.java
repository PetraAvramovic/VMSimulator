package rs.ac.bg.etf.view.inspector;

import java.util.List;

import javafx.beans.property.IntegerProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import rs.ac.bg.etf.model.simulation.PageSimulationContext;
import rs.ac.bg.etf.view.util.ValueConverter;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.view.util.WindowedTableColumn;
import rs.ac.bg.etf.view.util.WindowedTableView;

/**
 * A separate, resizable window for browsing one disk block's full content -- every word offset
 * from 0 to the page size, not just the offsets a write has actually touched (an offset nothing
 * wrote to reads as 0, same as physical memory). One instance is built lazily and reused across
 * opens/closes (see {@link #toggle}), and reusable from anywhere a disk address is clickable: the
 * OS tab's own disk box (whichever transfer is currently in flight) and the page table inspector's
 * Disk column (any page's backing block, on demand).
 *
 * <p>Stays live while open: {@code refresh()} re-pulls the currently visible window's data
 * straight from the live {@code Disk} whenever the simulation steps, so a write-back while the
 * window is open shows up immediately.
 */
public class DiskBlockInspectorWindow
{
    private static final double ROOT_PADDING = 12;
    // JavaFX has no way to measure the native title bar's own font/chrome (icon, minimize/
    // maximize/close buttons) -- this is a generous stand-in so the estimated title width below
    // errs on the side of a little too wide rather than still clipping the address.
    private static final Font TITLE_FONT = Font.font(12);
    private static final double TITLE_CHROME_WIDTH = 150;
    // Header label ("Disk Block 0x… content:") + the VBox's own spacing below it.
    private static final double HEADER_ROW_HEIGHT = 30;

    private final PageSimulationContext context;
    private final int addressDigits;
    private final int offsetDigits;
    private final int valueDigits;

    private Stage stage;
    private Label header;
    private WindowedTableView<DiskWord> tableView;
    private long address = -1;

    public DiskBlockInspectorWindow(PageSimulationContext context, IntegerProperty currentStepNumber)
    {
        this.context = context;
        // From the simulation config, like every other hex field in the app -- bits, not digits;
        // hexDigitsFor converts. (A raw bit count passed straight to toHex as "digits" is what
        // produced the wildly over-long, all-but-unreadable address the title used to show.)
        this.addressDigits = ValueConverter.hexDigitsFor(context.getDiskBits());
        this.offsetDigits = ValueConverter.hexDigitsFor(Math.max(1, context.getWordBits()));
        this.valueDigits = ValueConverter.hexDigitsFor(context.getAddressableUnit() * 8);
        currentStepNumber.addListener((o, ov, nv) -> {
            if (tableView != null)
                tableView.refresh();
        });
    }

    /** Opens the window on {@code diskAddress}'s block, or closes it if it's already open on that
     *  same address -- a second click on whatever's showing this exact block toggles it shut. */
    public void toggle(Window owner, long diskAddress)
    {
        if (stage != null && stage.isShowing() && address == diskAddress)
        {
            stage.hide();
            return;
        }
        if (stage == null)
            stage = build(owner);
        selectAddress(diskAddress);
        stage.show();
        stage.toFront();
    }

    private void selectAddress(long diskAddress)
    {
        this.address = diskAddress;
        stage.setTitle(titleFor(diskAddress));
        header.setText(titleFor(diskAddress) + " content:");
        tableView.setRowSource(new DiskBlockRowSource(context, diskAddress));
    }

    private String titleFor(long diskAddress)
    {
        return "Disk Block " + ValueConverter.toHex(diskAddress, addressDigits);
    }

    private Stage build(Window owner)
    {
        header = new Label();
        header.getStyleClass().add("mmu-section-label");

        tableView = new WindowedTableView<>(diskColumns(), null);
        VBox.setVgrow(tableView, Priority.ALWAYS);

        VBox root = new VBox(8, header, tableView);
        root.setPadding(new Insets(ROOT_PADDING));

        Stage s = new Stage();
        s.initModality(Modality.NONE);
        if (owner != null)
            s.initOwner(owner);

        Scene scene = new Scene(root, 300, 420);
        scene.getStylesheets().add(getClass().getResource("/rs/ac/bg/etf/light-theme.css").toExternalForm());
        s.setScene(scene);

        // The longest title this window will ever show is fixed the moment addressDigits is known
        // (every digit at its widest, "F") -- so the floor can be set once here rather than
        // recomputed on every selectAddress().
        double titleMinWidth = titleWidth("Disk Block 0x" + "F".repeat(addressDigits));
        s.setMinWidth(Math.max(tableView.minimumWidth() + 2 * ROOT_PADDING, titleMinWidth));
        s.setMinHeight(HEADER_ROW_HEIGHT + tableView.minimumHeight() + 2 * ROOT_PADDING);

        return s;
    }

    private static double titleWidth(String title)
    {
        Text sample = new Text(title);
        sample.setFont(TITLE_FONT);
        return sample.getLayoutBounds().getWidth() + TITLE_CHROME_WIDTH;
    }

    private List<WindowedTableColumn<DiskWord>> diskColumns()
    {
        return List.of(
                new WindowedTableColumn<>("Offset", WidthCalculator.columnWidth("Offset", offsetDigits),
                        word -> ValueConverter.toHex(word.offset(), offsetDigits)),
                new WindowedTableColumn<>("Value", WidthCalculator.columnWidth("Value", valueDigits),
                        word -> ValueConverter.toHex(word.value(), valueDigits)));
    }
}
