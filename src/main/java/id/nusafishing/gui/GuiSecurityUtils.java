package id.nusafishing.gui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import id.nusafishing.NusaFishing;

/**
 * Utility untuk menandai item GUI dan item pemberian admin serta
 * membersihkan item GUI yang bocor ke inventori pemain biasa.
 */
public final class GuiSecurityUtils {
    public static final NamespacedKey KEY_GUI_ITEM = new NamespacedKey("nusafishing", "nf_gui_item");
    public static final NamespacedKey KEY_ADMIN_GIVEN = new NamespacedKey("nusafishing", "admin_given");

    private GuiSecurityUtils() {}

    /**
     * Tandai ItemStack sebagai item tampilan GUI agar tidak dapat diambil/dimanfaatkan.
     */
    public static ItemStack markGuiItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(KEY_GUI_ITEM, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Cek apakah ItemStack bertag item GUI.
     */
    public static boolean isGuiItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(KEY_GUI_ITEM, PersistentDataType.BYTE);
    }

    /**
     * Tandai ikan sebagai hasil give admin (untuk kontrol penjualan/dupe).
     */
    public static ItemStack markAdminGiven(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(KEY_ADMIN_GIVEN, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Cek apakah ItemStack merupakan ikan pemberian admin.
     */
    public static boolean isAdminGiven(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(KEY_ADMIN_GIVEN, PersistentDataType.BYTE);
    }

    /**
     * Pindai seluruh inventori pemain (termasuk armor & off-hand) dan bersihkan item bertag GUI.
     * @return jumlah item yang dibersihkan
     */
    public static int cleanLeakedGuiItems(Player player) {
        if (player == null || !player.isOnline()) return 0;
        int cleanedCount = 0;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isGuiItem(item)) {
                contents[i] = null;
                cleanedCount++;
            }
        }

        ItemStack cursorItem = player.getItemOnCursor();
        if (isGuiItem(cursorItem)) {
            player.setItemOnCursor(null);
            cleanedCount++;
        }

        if (cleanedCount > 0) {
            player.getInventory().setContents(contents);
            player.updateInventory();
            if (NusaFishing.getInstance() != null) {
                NusaFishing.getInstance().getLogger().warning(
                    "[SECURITY] Membersihkan " + cleanedCount + " item GUI yang bocor dari pemain: " + player.getName()
                );
            }
        }

        return cleanedCount;
    }
}
