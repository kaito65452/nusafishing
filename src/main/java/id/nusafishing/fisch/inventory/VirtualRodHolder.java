package id.nusafishing.fisch.inventory;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Custom InventoryHolder untuk Virtual Rod Inventory GUI.
 * Digunakan untuk identifikasi unik dan keamanan mutlak anti-exploit.
 */
public class VirtualRodHolder implements InventoryHolder {

    private final UUID playerId;
    private Inventory inventory;

    public VirtualRodHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
