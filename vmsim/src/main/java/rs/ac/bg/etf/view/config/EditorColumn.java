package rs.ac.bg.etf.view.config;

import java.util.function.Consumer;

import javafx.scene.layout.Region;

/**
 * One column of a config editor window's hand-rolled table (see {@link ConfigEditorWindowSupport}):
 * a header label, a fixed width shared verbatim by the header cell and every row's cell for that
 * column, and a factory that builds the cell shown for one row's entry. {@code replaceSelf} lets a
 * cell swap itself out in place (e.g. a display label swapping to a TextField on double-click,
 * see {@code ConfigEditorWindowSupport.openValueEditor}) -- the cell is a direct, plain child of
 * its row's HBox rather than wrapped in an extra container.
 */
final class EditorColumn<T>
{
    final String header;
    final double width;
    final CellBuilder<T> cellFactory;

    EditorColumn(String header, double width, CellBuilder<T> cellFactory)
    {
        this.header = header;
        this.width = width;
        this.cellFactory = cellFactory;
    }

    @FunctionalInterface
    interface CellBuilder<T>
    {
        Region build(T item, double width, Consumer<Region> replaceSelf);
    }
}
