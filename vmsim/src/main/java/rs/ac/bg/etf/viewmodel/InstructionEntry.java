package rs.ac.bg.etf.viewmodel;

import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;

import rs.ac.bg.etf.model.memory.Instruction;
import rs.ac.bg.etf.model.memory.Instruction.AccessType;

/**
 * One editable row in the config screen's Instructions editor window. Mirrors the fields of
 * {@link Instruction} as JavaFX properties so a TableView can bind/edit them directly; converted
 * back into an immutable {@link Instruction} only when the config is launched.
 */
public class InstructionEntry
{
    private final IntegerProperty user = new SimpleIntegerProperty(0);
    private final ObjectProperty<AccessType> accessType = new SimpleObjectProperty<>(AccessType.RD);
    private final LongProperty virtualAddress = new SimpleLongProperty(0);
    private final LongProperty value = new SimpleLongProperty(0);

    public InstructionEntry() {}

    public InstructionEntry(Instruction instruction)
    {
        user.set(instruction.getUser());
        accessType.set(instruction.getAccessType());
        virtualAddress.set(instruction.getVirtualAddress());
        value.set(instruction.getValue());
    }

    public Instruction toInstruction()
    {
        return new Instruction(user.get(), accessType.get(), virtualAddress.get(), value.get());
    }

    public IntegerProperty userProperty() { return user; }
    public ObjectProperty<AccessType> accessTypeProperty() { return accessType; }
    public LongProperty virtualAddressProperty() { return virtualAddress; }
    public LongProperty valueProperty() { return value; }
}
