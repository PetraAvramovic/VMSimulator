package rs.ac.bg.etf.viewmodel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.simulation.SimulationConfig.TranslationType;
import rs.ac.bg.etf.model.simulation.exceptions.InvalidConfig;
import rs.ac.bg.etf.model.simulation.SimulationConfig;
import rs.ac.bg.etf.model.simulation.SimulationConfig.InitialPage;
import rs.ac.bg.etf.model.simulation.SimulationConfig.PageTableDescriptorInit;
import rs.ac.bg.etf.model.simulation.SimulationConfig.TLBType;
import rs.ac.bg.etf.viewmodel.listeners.ConfigurationNavigationListener;

public class ConfigurationViewModel 
{
    private ConfigurationNavigationListener navigationListener;
    private SimulationConfig loadedConfig = null; // Store the loaded config to preserve all fields

    private final ObjectProperty<TranslationType> translationType = 
            new SimpleObjectProperty<>(TranslationType.PAGED);
            
    private final ObjectProperty<TLBType> tlbType = 
            new SimpleObjectProperty<>(TLBType.ASSOCIATIVE);

    private final IntegerProperty wordBits = new SimpleIntegerProperty(12);
    private final IntegerProperty physicalAddressBits = new SimpleIntegerProperty(16);

    private final IntegerProperty pageBits = new SimpleIntegerProperty(4); 
    private final IntegerProperty segmentBits = new SimpleIntegerProperty(4);

    private final IntegerProperty tlbSize = new SimpleIntegerProperty(16);
    private final IntegerProperty tlbEntriesPerSet = new SimpleIntegerProperty(2);

    private final IntegerProperty addressableUnit = new SimpleIntegerProperty(1);
    private final IntegerProperty users = new SimpleIntegerProperty(2);

    private final StringProperty validationErrorMessage = new SimpleStringProperty("");

    // Bulk-data sections edited through their own windows (InstructionsEditorWindow,
    // PageTablesEditorWindow, MemoryInitEditorWindow) rather than inline on the config screen --
    // these back SimulationConfig's instructions/pageTables/initialPages, which validateAndLaunch()
    // flattens/regroups into on launch (see the loops there for the reverse of this flattening).
    private final ObservableList<InstructionEntry> instructionEntries = FXCollections.observableArrayList();
    private final ObservableList<PageTableEntry> pageTableEntries = FXCollections.observableArrayList();
    private final ObservableList<MemoryInitEntry> memoryInitEntries = FXCollections.observableArrayList();

    public ConfigurationViewModel(ConfigurationNavigationListener navigationListener) 
    {
        this.navigationListener = navigationListener;
    }

    public void validateAndLaunch()
    {
        validationErrorMessage.set("");

        SimulationConfig config;
        
        // If a config was loaded from file, use it as the base and update with any UI changes
        if (loadedConfig != null) {
            config = loadedConfig;
            // Update the config with any changes made in the UI
            config.setWordBits(wordBits.get());
            config.setPhysicalAddressBits(physicalAddressBits.get());
            config.setAddressableUnit(addressableUnit.get());
            config.setNumberOfUsers(users.get());
            config.setTranslationType(translationType.get());
            config.setTlbType(tlbType.get());
            config.setTlbSize(tlbSize.get());
            
            if (tlbType.get() == TLBType.SET_ASSOCIATIVE)
                config.setTlbEntriesPerSet(tlbEntriesPerSet.get());

            if (translationType.get() == TranslationType.PAGED || translationType.get() == TranslationType.SEGMENTED_PAGED) {
                config.setPageBits(pageBits.get());
            }
            if (translationType.get() == TranslationType.SEGMENTED || translationType.get() == TranslationType.SEGMENTED_PAGED) {
                config.setSegmentBits(segmentBits.get());
            }
        } else {
            // Create a new config from UI values
            config = new SimulationConfig();
            config.setWordBits(wordBits.get());
            config.setPhysicalAddressBits(physicalAddressBits.get());
            config.setAddressableUnit(addressableUnit.get());
            config.setNumberOfUsers(users.get());

            config.setTranslationType(translationType.get());
            config.setTlbType(tlbType.get());
            config.setTlbSize(tlbSize.get());
            
            if (tlbType.get() == TLBType.SET_ASSOCIATIVE)
                config.setTlbEntriesPerSet(tlbEntriesPerSet.get());

            if (translationType.get() == TranslationType.PAGED || translationType.get() == TranslationType.SEGMENTED_PAGED) {
                config.setPageBits(pageBits.get());
            } else if (translationType.get() == TranslationType.SEGMENTED || translationType.get() == TranslationType.SEGMENTED_PAGED) {
                config.setSegmentBits(segmentBits.get());
            }
        }

        // The Instructions/Page Table/Initial Memory Content editor windows are the single source
        // of truth for these three sections regardless of whether config came from a loaded file
        // or was built fresh above -- re-flatten them into the shapes SimulationConfig expects.
        ArrayList<Instruction> instructionList = new ArrayList<>();
        for (InstructionEntry entry : instructionEntries)
            instructionList.add(entry.toInstruction());
        config.setInstructions(instructionList);

        Map<Integer, Map<Long, PageTableDescriptorInit>> pageTables = new HashMap<>();
        for (PageTableEntry entry : pageTableEntries) {
            pageTables
                .computeIfAbsent(entry.userIdProperty().get(), userId -> new HashMap<>())
                .put(entry.pageProperty().get(), new PageTableDescriptorInit(
                    entry.validProperty().get(), entry.dirtyProperty().get(), entry.blockProperty().get()));
        }
        config.setPageTables(pageTables);

        // Group flat (userId, page, offset, value) rows back into one InitialPage per
        // (userId, page), each carrying a content map of its offset/value rows.
        Map<Integer, Map<Long, TreeMap<Long, Long>>> contentByUserAndPage = new LinkedHashMap<>();
        for (MemoryInitEntry entry : memoryInitEntries) {
            contentByUserAndPage
                .computeIfAbsent(entry.userIdProperty().get(), userId -> new LinkedHashMap<>())
                .computeIfAbsent(entry.pageProperty().get(), page -> new TreeMap<>())
                .put(entry.offsetProperty().get(), entry.valueProperty().get());
        }
        ArrayList<InitialPage> initialPages = new ArrayList<>();
        for (Map.Entry<Integer, Map<Long, TreeMap<Long, Long>>> userEntry : contentByUserAndPage.entrySet()) {
            for (Map.Entry<Long, TreeMap<Long, Long>> pageEntry : userEntry.getValue().entrySet()) {
                initialPages.add(new InitialPage(userEntry.getKey(), pageEntry.getKey(), pageEntry.getValue()));
            }
        }
        config.setInitialPages(initialPages);

        try {
            // Trigger your model's native validation method (e.g., throws IllegalArgumentException)
            config.validate();

            // If it passes validation without throwing an exception, fire the session launch signal!
            if (navigationListener != null) {
                navigationListener.onConfigToSimulation(config);
            }

        } catch (InvalidConfig e) {
           
            validationErrorMessage.set(e.getMessage());
        }

    }

    public void onConfigToMainMenu()
    {
        navigationListener.onConfigToMainMenu();
    }

    /**
     * Called by the View when a config file is loaded
     */
    public void loadConfigFromFile(String filePath) throws Exception
    {
        SimulationConfig config = new SimulationConfig();
        SimulationConfig.loadFromFile(filePath, config);
        setLoadedConfig(config);
        validationErrorMessage.set("");
    }

    /**
     * Sets the loaded config and updates all UI fields
     * Only updates fields that have valid values (not -1)
     */
    public void setLoadedConfig(SimulationConfig config)
    {
        this.loadedConfig = config;
        
        // Update UI fields from the loaded config, only if they have valid values
        if (config.getWordBits() > 0)
            wordBitsProperty().set(config.getWordBits());
        if (config.getPhysicalAddressBits() > 0)
            physicalAddressBitsProperty().set(config.getPhysicalAddressBits());
        if (config.getAddressableUnit() > 0)
            addressableUnitProperty().set(config.getAddressableUnit());
        if (config.getNumberOfUsers() > 0)
            usersProperty().set(config.getNumberOfUsers());
        
        TranslationType loadedType = config.getTranslationType();
        if (loadedType != null)
            translationTypeProperty().set(loadedType);
            
        TLBType loadedTLBType = config.getTlbType();
        if (loadedTLBType != null)
            tlbTypeProperty().set(loadedTLBType);
            
        if (config.getTlbSize() > 0)
            tlbSizeProperty().set(config.getTlbSize());
        if (config.getTlbEntriesPerSet() > 0)
            tlbEntriesPerSetProperty().set(config.getTlbEntriesPerSet());
        if (config.getPageBits() > 0)
            pageBitsProperty().set(config.getPageBits());
        if (config.getSegmentBits() > 0)
            segmentBitsProperty().set(config.getSegmentBits());

        instructionEntries.clear();
        if (config.getInstructions() != null) {
            for (Instruction instruction : config.getInstructions())
                instructionEntries.add(new InstructionEntry(instruction));
        }

        pageTableEntries.clear();
        if (config.getPageTables() != null) {
            for (Map.Entry<Integer, Map<Long, PageTableDescriptorInit>> userEntry : config.getPageTables().entrySet()) {
                for (Map.Entry<Long, PageTableDescriptorInit> pageEntry : userEntry.getValue().entrySet()) {
                    PageTableEntry row = new PageTableEntry();
                    row.userIdProperty().set(userEntry.getKey());
                    row.pageProperty().set(pageEntry.getKey());
                    row.validProperty().set(pageEntry.getValue().valid());
                    row.dirtyProperty().set(pageEntry.getValue().dirty());
                    row.blockProperty().set(pageEntry.getValue().block());
                    pageTableEntries.add(row);
                }
            }
        }

        memoryInitEntries.clear();
        if (config.getInitialPages() != null) {
            for (InitialPage page : config.getInitialPages()) {
                if (page.content() == null)
                    continue;
                for (Map.Entry<Long, Long> contentEntry : page.content().entrySet()) {
                    MemoryInitEntry row = new MemoryInitEntry();
                    row.userIdProperty().set(page.userId());
                    row.pageProperty().set(page.page());
                    row.offsetProperty().set(contentEntry.getKey());
                    row.valueProperty().set(contentEntry.getValue());
                    memoryInitEntries.add(row);
                }
            }
        }
    }

    /**
     * Clears the loaded config
     */
    public void clearLoadedConfig()
    {
        this.loadedConfig = null;
    }

    public ObjectProperty<TranslationType> translationTypeProperty() { return translationType; }
    public ObjectProperty<TLBType> tlbTypeProperty() { return tlbType; }
    public IntegerProperty wordBitsProperty() { return wordBits; }
    public IntegerProperty physicalAddressBitsProperty() { return physicalAddressBits; }
    public IntegerProperty pageBitsProperty() { return pageBits; }
    public IntegerProperty segmentBitsProperty() { return segmentBits; }
    public IntegerProperty tlbSizeProperty() { return tlbSize; }
    public IntegerProperty tlbEntriesPerSetProperty() { return tlbEntriesPerSet; }
    public IntegerProperty addressableUnitProperty() { return addressableUnit; }
    public IntegerProperty usersProperty() { return users; }
    public StringProperty validationErrorMessageProperty() { return validationErrorMessage; }

    public ObservableList<InstructionEntry> getInstructionEntries() { return instructionEntries; }
    public ObservableList<PageTableEntry> getPageTableEntries() { return pageTableEntries; }
    public ObservableList<MemoryInitEntry> getMemoryInitEntries() { return memoryInitEntries; }

}
