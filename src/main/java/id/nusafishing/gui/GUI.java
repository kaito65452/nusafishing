package id.nusafishing.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class GUI implements InventoryHolder {
    protected final Inventory inventory;
    protected final Map<Integer, Runnable> clickHandlers = new HashMap<>();
    protected final Player player;
    protected InventoryClickEvent lastClickEvent;

    private static final Map<UUID, GUI> openInventories = new ConcurrentHashMap<>();

    public GUI(Player player, String title, int rows) {
        this.player = player;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Set an item in the inventory with a click handler
     *
     * @param slot The slot to set the item in
     * @param item The item to set
     * @param clickHandler The handler to run when the item is clicked
     */
    protected void setItem(int slot, ItemStack item, Runnable clickHandler) {
        if (item != null) {
            GuiSecurityUtils.markGuiItem(item);
        }
        inventory.setItem(slot, item);
        if (clickHandler != null) {
            clickHandlers.put(slot, clickHandler);
        }
    }

    /**
     * Set an item in the inventory without a click handler
     *
     * @param slot The slot to set the item in
     * @param item The item to set
     */
    protected void setItem(int slot, ItemStack item) {
        setItem(slot, item, null);
    }

    /**
     * Handle a click in the inventory
     *
     * @param event The click event
     * @return true if the click was handled
     */
    public boolean handleClick(InventoryClickEvent event) {
        this.lastClickEvent = event;
        int slot = event.getRawSlot();
        if (clickHandlers.containsKey(slot)) {
            clickHandlers.get(slot).run();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return true;
        }
        return false;
    }

    public InventoryClickEvent getLastClickEvent() {
        return lastClickEvent;
    }

    /**
     * Open the inventory for the player
     */
    public void open() {
        player.openInventory(inventory);
        openInventories.put(player.getUniqueId(), this);
    }

    /**
     * Close the inventory for the player
     */
    public void close() {
        player.closeInventory();
        openInventories.remove(player.getUniqueId());
    }

    /**
     * Get the GUI a player currently has open
     *
     * @param player The player
     * @return The GUI they have open, or null
     */
    public static GUI getOpenGUI(Player player) {
        return openInventories.get(player.getUniqueId());
    }

    /**
     * Remove a player from the open inventories map
     *
     * @param player The player to remove
     */
    public static void removePlayer(Player player) {
        openInventories.remove(player.getUniqueId());
    }

    /**
     * Tutup semua inventori GUI yang sedang terbuka dan bersihkan referensi.
     */
    public static void closeAll() {
        for (GUI gui : new ArrayList<>(openInventories.values())) {
            if (gui.player != null && gui.player.isOnline()) {
                gui.player.closeInventory();
                GuiSecurityUtils.cleanLeakedGuiItems(gui.player);
            }
        }
        openInventories.clear();
    }
}