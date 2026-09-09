package rs.ac.bg.etf.view;

import javafx.beans.property.ObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;
import javafx.stage.FileChooser;
import java.io.File;
import rs.ac.bg.etf.model.simulation.SimulationConfig.TLBType;
import rs.ac.bg.etf.model.simulation.SimulationConfig.TranslationType;
import rs.ac.bg.etf.viewmodel.ConfigurationViewModel;

/**
 * THE DYNAMIC HARDWARE CONFIGURATION OPTIONS FORM VIEW
 * Renders declarative layout blocks and maps numeric inputs type-safely.
 */
public class ConfigurationView {
    private final VBox layoutContainer;

    // IntegerProperty.asObject() hands back a fresh wrapper each call, and bindBidirectional
    // only holds it weakly. Without a strong reference here the GC reclaims the wrapper and the
    // ComboBox silently stops tracking the viewmodel property (e.g. after a config file load).
    private final ObjectProperty<Integer> addressableUnitAsObject;
    private final ObjectProperty<Integer> usersAsObject;
    private final ObjectProperty<Integer> tlbEntriesPerSetAsObject;

    public ConfigurationView(ConfigurationViewModel viewModel) {
        this.addressableUnitAsObject = viewModel.addressableUnitProperty().asObject();
        this.usersAsObject = viewModel.usersProperty().asObject();
        this.tlbEntriesPerSetAsObject = viewModel.tlbEntriesPerSetProperty().asObject();

        this.layoutContainer = new VBox();
        this.layoutContainer.getStyleClass().add("main-menu-container");
        this.layoutContainer.setAlignment(Pos.CENTER);

        // =========================================================================
        // 1. TOP HEADER SECTION
        // =========================================================================
        Label headerLabel = new Label("Simulation Configuration Parameters");
        headerLabel.getStyleClass().add("main-menu-title");

        Label subtitleLabel = new Label("Configure the hardware bit boundaries and translation engine rules.");
        subtitleLabel.getStyleClass().add("main-menu-subtitle");

        // =========================================================================
        // 2. CENTRAL SIDE-BY-SIDE HORIZONTAL COLUMNS TRACK
        // =========================================================================
        HBox columnsContainer = new HBox(40); // 40px gap between columns
        columnsContainer.setAlignment(Pos.CENTER);

        // -------------------------------------------------------------------------
        // COLUMN A (LEFT): GENERAL HARDWARE OPTIONS
        // -------------------------------------------------------------------------
        VBox generalColumn = new VBox(15);
        generalColumn.getStyleClass().add("form-grid-base");

        // Column header
        Label generalHeaderLabel = new Label("General Hardware Options");
        generalHeaderLabel.getStyleClass().add("column-header");

        // Model Selector
        VBox modelFieldWrapper = new VBox(5);
        Label modelLabel = new Label("Translation Model");
        ComboBox<TranslationType> archBox = new ComboBox<>();
        archBox.getItems().setAll(TranslationType.values());
        archBox.valueProperty().bindBidirectional(viewModel.translationTypeProperty());
        archBox.setConverter(new StringConverter<>() {
            @Override public String toString(TranslationType type) {
                if (type == null) return "";
                return switch (type) {
                    case PAGED -> "Paged";
                    case SEGMENTED -> "Segmented";
                    case SEGMENTED_PAGED -> "Segmented Paging";
                };
            }
            @Override public TranslationType fromString(String s) { return null; }
        });
        modelFieldWrapper.getChildren().addAll(modelLabel, archBox);

        // Word Bits input field
        VBox wordBitsWrapper = new VBox(5);
        Label wordLabel = new Label("Word Width (Bits)");
        TextField wordField = new TextField();
        wordField.textProperty().bindBidirectional(viewModel.wordBitsProperty(), new javafx.util.converter.NumberStringConverter());
        wordBitsWrapper.getChildren().addAll(wordLabel, wordField);

        // Physical Address Bits input field
        VBox physBitsWrapper = new VBox(5);
        Label physLabel = new Label("Physical Address Width (Bits)");
        TextField physField = new TextField();
        physField.textProperty().bindBidirectional(viewModel.physicalAddressBitsProperty(), new javafx.util.converter.NumberStringConverter());
        physBitsWrapper.getChildren().addAll(physLabel, physField);

        // NEW FIELD: Addressable Unit (Bits)
        VBox addressableUnitWrapper = new VBox(5);
        Label unitLabel = new Label("Addressable Unit (Bytes)");
        ComboBox<Integer> unitBox = new ComboBox<>();
        unitBox.getItems().addAll(1, 2, 4, 8);
        unitBox.valueProperty().bindBidirectional(addressableUnitAsObject);
        addressableUnitWrapper.getChildren().addAll(unitLabel, unitBox);

        // NEW FIELD: Users count
        VBox usersWrapper = new VBox(5);
        Label usersLabel = new Label("User Processes");
        ComboBox<Integer> usersBox = new ComboBox<>();
        usersBox.getItems().addAll(1, 2, 4, 8, 16, 32);
        usersBox.valueProperty().bindBidirectional(usersAsObject);
        usersWrapper.getChildren().addAll(usersLabel, usersBox);
        

        generalColumn.getChildren().addAll(generalHeaderLabel, modelFieldWrapper, wordBitsWrapper, physBitsWrapper, addressableUnitWrapper, usersWrapper);

        // -------------------------------------------------------------------------
        // COLUMN B (MIDDLE): TLB CACHE OPTIONS
        // -------------------------------------------------------------------------
        VBox tlbColumn = new VBox(15);
        tlbColumn.getStyleClass().add("form-grid-base");

        // Column header
        Label tlbHeaderLabel = new Label("TLB Cache Options");
        tlbHeaderLabel.getStyleClass().add("column-header");

        // TLB Structure Selector
        VBox tlbTypeWrapper = new VBox(5);
        Label tlbLabel = new Label("TLB Structure");
        ComboBox<TLBType> tlbBox = new ComboBox<>();
        tlbBox.getItems().setAll(TLBType.values());
        tlbBox.valueProperty().bindBidirectional(viewModel.tlbTypeProperty());
        tlbBox.setConverter(new StringConverter<>() {
            @Override public String toString(TLBType policy) {
                if (policy == null) return "";
                return switch (policy) {
                    case ASSOCIATIVE -> "Associative";
                    case DIRECT -> "Direct";
                    case SET_ASSOCIATIVE -> "Set Associative";
                };
            }
            @Override public TLBType fromString(String s) { return null; }
        });
        tlbTypeWrapper.getChildren().addAll(tlbLabel, tlbBox);

        // NEW FIELD: TLB Size (Total entries count)
        VBox tlbSizeWrapper = new VBox(5);
        Label tlbSizeLabel = new Label("TLB Size (Entries)");
        TextField tlbSizeField = new TextField();
        tlbSizeField.textProperty().bindBidirectional(viewModel.tlbSizeProperty(), new javafx.util.converter.NumberStringConverter());
        tlbSizeWrapper.getChildren().addAll(tlbSizeLabel, tlbSizeField);

        // NEW CONDITIONAL FIELD: Entries per Set (Only visible if SET_ASSOCIATIVE lookup is selected!)
        VBox entriesPerSetWrapper = new VBox(5);
        Label entriesPerSetLabel = new Label("Entries Per Set");
        
        // 1. Declare the ComboBox to handle Integer values directly
        ComboBox<Integer> entriesPerSetBox = new ComboBox<>();
        
        // 2. Populate the items dropdown array with fixed values 2 and 4
        entriesPerSetBox.getItems().addAll(2, 4);
        
        // 3. Bind the value property directly to your IntegerProperty view model token!
        entriesPerSetBox.valueProperty().bindBidirectional(tlbEntriesPerSetAsObject);
        
        entriesPerSetWrapper.getChildren().addAll(entriesPerSetLabel, entriesPerSetBox);

        // CONDITIONAL RENDERING LINK: Directly checks enum value from tlbTypeProperty
        entriesPerSetWrapper.visibleProperty().bind(viewModel.tlbTypeProperty().isEqualTo(TLBType.SET_ASSOCIATIVE));
        entriesPerSetWrapper.managedProperty().bind(entriesPerSetWrapper.visibleProperty());

        tlbColumn.getChildren().addAll(tlbHeaderLabel, tlbTypeWrapper, tlbSizeWrapper, entriesPerSetWrapper);

        // -------------------------------------------------------------------------
        // COLUMN C (RIGHT): EXTRA CONDITIONAL TRANSLATION TRACK SUB-OPTIONS
        // -------------------------------------------------------------------------
        VBox dynamicOptionsColumn = new VBox(15);
        dynamicOptionsColumn.getStyleClass().add("form-grid-base");

        // Dynamic column header that changes based on translation type
        Label dynamicHeaderLabel = new Label();
        dynamicHeaderLabel.getStyleClass().add("column-header");

        // Bind header text based on translation type
        viewModel.translationTypeProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == TranslationType.PAGED) {
                dynamicHeaderLabel.setText("Paging Options");
            } else if (newVal == TranslationType.SEGMENTED) {
                dynamicHeaderLabel.setText("Segmentation Options");
            } else if (newVal == TranslationType.SEGMENTED_PAGED) {
                dynamicHeaderLabel.setText("Segmented Paging Options");
            }
        });
        // Initialize header on creation
        dynamicHeaderLabel.setText("Paging Options");

        // Paged track options input box
        VBox pagedSubWrapper = new VBox(5);
        Label pageBitsLabel = new Label("Page Bits");
        TextField pageSizeField = new TextField();
        pageSizeField.textProperty().bindBidirectional(viewModel.pageBitsProperty(), new javafx.util.converter.NumberStringConverter());
        pagedSubWrapper.getChildren().addAll(pageBitsLabel, pageSizeField);
        
        pagedSubWrapper.visibleProperty().bind(viewModel.translationTypeProperty().isEqualTo(TranslationType.PAGED));
        pagedSubWrapper.managedProperty().bind(pagedSubWrapper.visibleProperty());

        // Segmented track options input box
        VBox segmentedSubWrapper = new VBox(5);
        Label segBitsLabel = new Label("Segment Bits");
        TextField maxSegsField = new TextField();
        maxSegsField.textProperty().bindBidirectional(viewModel.segmentBitsProperty(), new javafx.util.converter.NumberStringConverter());
        segmentedSubWrapper.getChildren().addAll(segBitsLabel, maxSegsField);
        
        segmentedSubWrapper.visibleProperty().bind(viewModel.translationTypeProperty().isEqualTo(TranslationType.SEGMENTED));
        segmentedSubWrapper.managedProperty().bind(segmentedSubWrapper.visibleProperty());

        // Segmented Paging track options input box - shows both page and segment bits
        VBox segmentedPagedSubWrapper = new VBox(10);
        
        VBox segmentedPagedPageWrapper = new VBox(5);
        Label segmentedPagedPageLabel = new Label("Page Bits");
        TextField segmentedPagedPageField = new TextField();
        segmentedPagedPageField.textProperty().bindBidirectional(viewModel.pageBitsProperty(), new javafx.util.converter.NumberStringConverter());
        segmentedPagedPageWrapper.getChildren().addAll(segmentedPagedPageLabel, segmentedPagedPageField);
        
        VBox segmentedPagedSegmentWrapper = new VBox(5);
        Label segmentedPagedSegmentLabel = new Label("Segment Bits");
        TextField segmentedPagedSegmentField = new TextField();
        segmentedPagedSegmentField.textProperty().bindBidirectional(viewModel.segmentBitsProperty(), new javafx.util.converter.NumberStringConverter());
        segmentedPagedSegmentWrapper.getChildren().addAll(segmentedPagedSegmentLabel, segmentedPagedSegmentField);
        
        segmentedPagedSubWrapper.getChildren().addAll(segmentedPagedPageWrapper, segmentedPagedSegmentWrapper);
        segmentedPagedSubWrapper.visibleProperty().bind(viewModel.translationTypeProperty().isEqualTo(TranslationType.SEGMENTED_PAGED));
        segmentedPagedSubWrapper.managedProperty().bind(segmentedPagedSubWrapper.visibleProperty());

        // Dynamic visibility wrapper container for the column pane asset itself
        dynamicOptionsColumn.getChildren().addAll(dynamicHeaderLabel, pagedSubWrapper, segmentedSubWrapper, segmentedPagedSubWrapper);
        
        // Ensure the entire Right Column Box hides away completely if NEITHER paged nor segmented nor segmented-paged matches
        dynamicOptionsColumn.visibleProperty().bind(
            viewModel.translationTypeProperty().isEqualTo(TranslationType.PAGED)
            .or(viewModel.translationTypeProperty().isEqualTo(TranslationType.SEGMENTED))
            .or(viewModel.translationTypeProperty().isEqualTo(TranslationType.SEGMENTED_PAGED))
        );
        dynamicOptionsColumn.managedProperty().bind(dynamicOptionsColumn.visibleProperty());

        // Add the three compiled column layouts into the central horizontal display group
        columnsContainer.getChildren().addAll(generalColumn, tlbColumn, dynamicOptionsColumn);

        // =========================================================================
        // 3. BOTTOM FOOTER NAVIGATION AND TRACK FEEDBACK LAYER
        // =========================================================================
        Label errorBanner = new Label();
        errorBanner.getStyleClass().add("error-banner-label");

        errorBanner.setWrapText(true);
        errorBanner.setMaxWidth(800); // Allow it to expand wide across the screen
        errorBanner.setMinHeight(Region.USE_PREF_SIZE); // FORCE it to take up its full text height
        errorBanner.setAlignment(Pos.CENTER);
        
        // 4. Force the parent layout VBox container to let this label fill the space horizontally
        VBox.setVgrow(errorBanner, Priority.ALWAYS);

        errorBanner.textProperty().bind(viewModel.validationErrorMessageProperty());

        StackPane errorWrapper = new StackPane(errorBanner);
        errorWrapper.setAlignment(Pos.CENTER);
        
        // Tell the main VBox layout engine to ALWAYS prioritize allocating vertical space 
        // to this error region over generic empty window gaps.
        VBox.setVgrow(errorWrapper, Priority.ALWAYS);


        Button backBtn = new Button("◀ Back");
        backBtn.setOnAction(e -> viewModel.onConfigToMainMenu());

        Button launchBtn = new Button("Launch Engine ▶");
        launchBtn.getStyleClass().add("button-primary"); 
        launchBtn.setOnAction(e -> viewModel.validateAndLaunch());

        Button loadConfigBtn = new Button("📁 Load Config");
        loadConfigBtn.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select TOML Configuration File");
            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("TOML Files", "*.toml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
            );
            File selectedFile = fileChooser.showOpenDialog(layoutContainer.getScene().getWindow());
            if (selectedFile != null) {
                try {
                    viewModel.loadConfigFromFile(selectedFile.getAbsolutePath());
                } catch (Exception ex) {
                    viewModel.validationErrorMessageProperty().set("Error loading config: " + ex.getMessage());
                }
            }
        });

        HBox actionRow = new HBox(20);
        actionRow.setAlignment(Pos.CENTER);
        HBox.setHgrow(actionRow, Priority.ALWAYS);
        
        // Create a spacer that spreads buttons across the row
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        actionRow.getChildren().addAll(loadConfigBtn, spacer, backBtn, launchBtn);

        // Pack the global segments vertically onto your main display viewport scene tree
        layoutContainer.getChildren().addAll(
            headerLabel, 
            subtitleLabel, 
            columnsContainer, 
            errorBanner, 
            actionRow
        );
        
        // Push intermediate vertical node adjustments spacing padding cushions
        layoutContainer.setSpacing(25); 
    }

    /**
     * Exposes the root container node so App.java can clip it to the viewport scene tree.
     */
    public Parent getRootContainerNode() {
        return this.layoutContainer;
    }
}