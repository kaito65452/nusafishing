package id.nusafishing.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class GUIListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        boolean isGui = (event.getInventory() != null && event.getInventory().getHolder() instanceof GUI)
                || (event.getView() != null && event.getView().getTopInventory() != null
                    && event.getView().getTopInventory().getHolder() instanceof GUI);

        if (isGui) {
            // Batalkan semua jenis klik tanpa terkecuali (shift, number key, swap offhand, drop, double-click, cursor)
            event.setCancelled(true);

            GUI gui = null;
            if (event.getView() != null && event.getView().getTopInventory() != null
                    && event.getView().getTopInventory().getHolder() instanceof GUI) {
                gui = (GUI) event.getView().getTopInventory().getHolder();
            } else {
                Player player = (Player) event.getWhoClicked();
                gui = GUI.getOpenGUI(player);
            }

            if (gui != null) {
                gui.handleClick(event);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        boolean isGui = (event.getInventory() != null && event.getInventory().getHolder() instanceof GUI)
                || (event.getView() != null && event.getView().getTopInventory() != null
                    && event.getView().getTopInventory().getHolder() instanceof GUI);

        if (isGui) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Cegah hopper atau dropper memindahkan item dari/ke GUI
        if ((event.getSource() != null && event.getSource().getHolder() instanceof GUI)
                || (event.getDestination() != null && event.getDestination().getHolder() instanceof GUI)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (event.getInventory() != null && event.getInventory().getHolder() instanceof GUI) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;

        Player player = (Player) event.getPlayer();
        if (event.getInventory() != null && event.getInventory().getHolder() instanceof GUI) {
            GUI.removePlayer(player);
            // Bersihkan jika ada item GUI yang secara tidak sengaja tersangkut di inventori pemain
            GuiSecurityUtils.cleanLeakedGuiItems(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GUI.removePlayer(player);
        GuiSecurityUtils.cleanLeakedGuiItems(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Bersihkan item GUI jika pemain membawa item glitch dari sesi sebelumnya
        GuiSecurityUtils.cleanLeakedGuiItems(event.getPlayer());
    }
}