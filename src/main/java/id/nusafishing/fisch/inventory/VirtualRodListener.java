package id.nusafishing.fisch.inventory;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import id.nusafishing.NusaFishing;

/**
 * Listener keamanan tingkat tinggi untuk Virtual Rod Inventory.
 * 100% Exploit-Free:
 * - Anti-Dupe, Anti-Drop, Anti-Shift Click, Anti-Number Key Swap
 * - Cancel semua event klik/drag pada VirtualRodHolder
 * - Mencegah pemindahan virtual rod ke peti/hopper/container lain
 * - Menjaga pancingan tetap tersimpan aman saat pemain mati (soulbound)
 */
public class VirtualRodListener implements Listener {

    private final NusaFishing plugin;
    private final VirtualRodManager rodManager;
    private static final Map<UUID, VirtualRodInventoryGUI> activeGuis = new ConcurrentHashMap<>();

    public VirtualRodListener(NusaFishing plugin, VirtualRodManager rodManager) {
        this.plugin = plugin;
        this.rodManager = rodManager;
    }

    public static void registerOpenGUI(Player player, VirtualRodInventoryGUI gui) {
        activeGuis.put(player.getUniqueId(), gui);
    }

    public static void unregisterGUI(Player player) {
        activeGuis.remove(player.getUniqueId());
    }

    /**
     * Mencegah exploit klik inventori pada GUI Virtual Rod.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVirtualRodInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        Inventory topInventory = event.getView().getTopInventory();
        boolean isVirtualRodHolder = topInventory != null && topInventory.getHolder() instanceof VirtualRodHolder;
        boolean isVirtualRodTitle = event.getView().getTitle().equals(VirtualRodInventoryGUI.GUI_TITLE);

        if (isVirtualRodHolder || isVirtualRodTitle) {
            // IMMUTABLE FIRST LINE: Batalkan semua kemungkinan interaksi inventori
            event.setCancelled(true);

            // Blokir raw slot < 0 (klik di luar jendela)
            if (event.getRawSlot() < 0) {
                return;
            }

            // Cegah modifikasi hotbar melalui angka (1-9)
            if (event.getClick() == ClickType.NUMBER_KEY) {
                return;
            }

            // Cegah double click take all
            if (event.getClick() == ClickType.DOUBLE_CLICK) {
                return;
            }

            // Cegah shift click & drop key
            if (event.isShiftClick() || event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) {
                return;
            }

            // Cegah offhand swap key 'F'
            if (event.getClick() == ClickType.SWAP_OFFHAND) {
                return;
            }

            // Jika klik terjadi di top inventory (GUI)
            if (event.getRawSlot() < topInventory.getSize()) {
                VirtualRodInventoryGUI gui = activeGuis.get(player.getUniqueId());
                if (gui != null) {
                    gui.handleClick(event.getRawSlot());
                } else {
                    // Fallback jika instans GUI belum tercatat
                    VirtualRodInventoryGUI newGui = new VirtualRodInventoryGUI(player, plugin);
                    newGui.handleClick(event.getRawSlot());
                }
            }
            return;
        }

        // ==========================================
        // CONTAINER PROTECTION (Anti-Chest Storage)
        // ==========================================
        // Mencegah pemain memindahkan virtual rod ke Peti, Hopper, Shulker, atau wadah eksternal
        if (topInventory != null && topInventory != event.getView().getBottomInventory()) {
            // Cegah SWAP_OFFHAND (F key) ke container
            if (event.getClick() == ClickType.SWAP_OFFHAND) {
                ItemStack offhandItem = player.getInventory().getItemInOffHand();
                if (rodManager.isVirtualRod(offhandItem) || rodManager.isVirtualRod(event.getCurrentItem())) {
                    event.setCancelled(true);
                    player.sendMessage("§cAlat pancing virtual tidak dapat dipindahkan ke wadah!");
                    return;
                }
            }

            // 1. Shift click virtual rod dari player inventory ke container
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (rodManager.isVirtualRod(current)) {
                    event.setCancelled(true);
                    player.sendMessage("§cAlat pancing virtual tidak dapat disimpan di dalam peti atau wadah!");
                    return;
                }
            }

            // 2. Klik memasukkan virtual rod yang ada di cursor ke container
            if (event.getRawSlot() < topInventory.getSize() && event.getRawSlot() >= 0) {
                ItemStack cursor = event.getCursor();
                if (rodManager.isVirtualRod(cursor)) {
                    event.setCancelled(true);
                    player.sendMessage("§cAlat pancing virtual tidak dapat disimpan di dalam peti atau wadah!");
                    return;
                }

                // 3. Hotbar number swap ke container
                if (event.getClick() == ClickType.NUMBER_KEY) {
                    int hotbarButton = event.getHotbarButton();
                    if (hotbarButton >= 0 && hotbarButton < 9) {
                        ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
                        if (rodManager.isVirtualRod(hotbarItem)) {
                            event.setCancelled(true);
                            player.sendMessage("§cAlat pancing virtual tidak dapat disimpan di dalam peti atau wadah!");
                            return;
                        }
                    }
                }
            }
        }
    }

    /**
     * Mencegah dragging item ke dalam atau melintasi GUI Virtual Rod.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVirtualRodDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        boolean isVirtualRodHolder = topInventory != null && topInventory.getHolder() instanceof VirtualRodHolder;
        boolean isVirtualRodTitle = event.getView().getTitle().equals(VirtualRodInventoryGUI.GUI_TITLE);

        if (isVirtualRodHolder || isVirtualRodTitle) {
            event.setCancelled(true);
            return;
        }

        // Cegah drag virtual rod ke container eksternal
        if (topInventory != null && topInventory != event.getView().getBottomInventory()) {
            ItemStack oldCursor = event.getOldCursor();
            if (rodManager.isVirtualRod(oldCursor)) {
                for (int rawSlot : event.getRawSlots()) {
                    if (rawSlot < topInventory.getSize()) {
                        event.setCancelled(true);
                        if (event.getWhoClicked() instanceof Player) {
                            ((Player) event.getWhoClicked()).sendMessage("§cAlat pancing virtual tidak dapat disimpan di dalam wadah!");
                        }
                        return;
                    }
                }
            }
        }
    }

    /**
     * Mencegah pemain menaruh virtual rod ke Item Frame, Armor Stand, Allay, dsb.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        ItemStack mainHand = event.getPlayer().getInventory().getItemInMainHand();
        ItemStack offHand = event.getPlayer().getInventory().getItemInOffHand();
        if (rodManager.isVirtualRod(mainHand) || rodManager.isVirtualRod(offHand)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cAlat pancing virtual tidak dapat diletakkan pada entitas!");
        }
    }

    /**
     * Mencegah pemain membuang (Drop) virtual rod jika bersifat Soulbound.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropVirtualRod(PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (rodManager.isVirtualRod(dropped) || rodManager.isSoulbound(dropped)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cAlat pancing virtual terikat pada jiwamu (Soulbound) dan tidak dapat dibuang!");
        }
    }

    /**
     * Mencegah virtual rod tercecer di tanah saat pemain mati.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (rodManager.isVirtualRod(drop) || rodManager.isSoulbound(drop)) {
                iterator.remove();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            unregisterGUI((Player) event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unregisterGUI(event.getPlayer());
    }
}
