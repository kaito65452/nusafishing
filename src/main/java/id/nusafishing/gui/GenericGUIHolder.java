package id.nusafishing.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder to uniquely identify plugin GUIs
 * and eliminate any risk of title-spoofing or inventory desync.
 */
public class GenericGUIHolder implements InventoryHolder {
    private final GUI gui;
    private Inventory inventory;

    public GenericGUIHolder(GUI gui) {
        this.gui = gui;
    }

    public GUI getGUI() {
        return gui;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
