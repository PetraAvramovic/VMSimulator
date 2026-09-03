package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import rs.ac.bg.etf.model.simulation.SimulationConfig.TranslationType;
import rs.ac.bg.etf.model.simulation.exceptions.InvalidConfig;
import rs.ac.bg.etf.model.simulation.SimulationConfig;
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

}
