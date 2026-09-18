package rs.ac.bg.etf.view.tlb;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import rs.ac.bg.etf.view.util.WidthCalculator;
import rs.ac.bg.etf.viewmodel.PagedTLBTabViewModel.RowHighlight;

public class PagedTLBRowView extends TLBRowView
{
    private final Label blockLabel;

    // Single source of truth for the Block column's on-screen position: PagedTLBTabView projects
    // this cell's bounds to place the block-output wire, so it tracks the real column width.
    private final ObjectProperty<Region> blockCellAnchor = new SimpleObjectProperty<>();

    public PagedTLBRowView(int tagHexWidth, int blockHexWidth)
    {
        super(tagHexWidth);

        blockLabel = cell("", WidthCalculator.columnWidth("Block", blockHexWidth, WidthCalculator.LARGE_CELL_FONT_SIZE));
        getChildren().add(blockLabel);
        blockCellAnchor.set(blockLabel);
    }

    public ObjectProperty<Region> blockCellAnchorProperty()
    {
        return blockCellAnchor;
    }

    // Green for a slot the lookup hit, red for a direct-mapped slot it probed and missed, blue for
    // one just filled by an insertion; every class is cleared first so a recycled row never keeps a
    // stale highlight.
    private static final String HIT_STYLE_CLASS = "tlb-row-hit";
    private static final String MISS_STYLE_CLASS = "tlb-row-miss";
    private static final String INSERT_STYLE_CLASS = "tlb-row-insert";

    public void updateFields(int index, boolean valid, boolean dirty, String tagHex, String blockHex)
    {
        updateCommonFields(index, valid, dirty, tagHex);
        blockLabel.setText(blockHex);
    }

    public void setHighlight(RowHighlight highlight)
    {
        getStyleClass().removeAll(HIT_STYLE_CLASS, MISS_STYLE_CLASS, INSERT_STYLE_CLASS);
        switch (highlight)
        {
            case HIT -> getStyleClass().add(HIT_STYLE_CLASS);
            case MISS -> getStyleClass().add(MISS_STYLE_CLASS);
            case INSERT -> getStyleClass().add(INSERT_STYLE_CLASS);
            case NONE -> { }
        }
    }

    public void setHeaderLabels(String indexText, String vText, String dText, String tagText, String blockText)
    {
        setCommonHeaderLabels(indexText, vText, dText, tagText);
        blockLabel.setText(blockText);
    }
}
