package id.nusafishing.fisch.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.util.ItemBuilder;

/**
 * GUI Koleksi Rod Virtual Fisch Roblox.
 * Menampilkan koleksi rod pemain, status kepemilikan, dan tombol equip/unequip.
 */
public class VirtualRodInventoryGUI {

    public static final String GUI_TITLE = "§8✦ Koleksi Pancingan ✦";

    private final Player player;
    private final NusaFishing plugin;
    private final VirtualRodManager rodManager;
    private final Inventory inventory;
    private final VirtualRodHolder holder;
    private final Map<Integer, String> slotToRodId = new HashMap<>();

    public VirtualRodInventoryGUI(Player player, NusaFishing plugin) {
        this.player = player;
        this.plugin = plugin;
        this.rodManager = plugin.getVirtualRodManager();
        this.holder = new VirtualRodHolder(player.getUniqueId());
        this.inventory = Bukkit.createInventory(holder, 54, GUI_TITLE);
        this.holder.setInventory(inventory);
    }

    /**
     * Membuka GUI untuk pemain dan merender isi item.
     */
    public void open() {
        render();
        VirtualRodListener.registerOpenGUI(player, this);
        player.openInventory(inventory);
    }

    /**
     * Render seluruh item di dalam GUI.
     */
    public void render() {
        inventory.clear();
        slotToRodId.clear();

        PlayerVirtualRods data = rodManager.getPlayerData(player.getUniqueId());
        String equippedRodId = data != null ? data.getEquippedRodId() : null;

        // 1. Bingkai tepi (Border)
        fillBorder();

        // 2. Info Header (Slot 4)
        int totalRods = plugin.getConfigManager().getFishingRods().size();
        int ownedCount = data != null ? data.getOwnedRods().size() : 0;
        FishingRod equippedRod = (equippedRodId != null) ? plugin.getConfigManager().getFishingRods().get(equippedRodId) : null;

        ItemStack headerItem = new ItemBuilder(Material.NETHER_STAR)
                .name("§6§l✦ KOLEKSI ALAT PANCING ✦")
                .lore("§7Kelola koleksi alat pancing pribadimu layaknya di Fisch!")
                .lore("§7Semua alat pancing tersimpan aman di Virtual Inventory.")
                .lore("")
                .lore("§fTotal Dimiliki: §a" + ownedCount + " §7/ §e" + totalRods)
                .lore("§fPancingan Aktif: " + (equippedRod != null ? equippedRod.getTier().getColor() + equippedRod.getName() : "§cTidak Ada"))
                .lore("")
                .lore("§eKlik pancingan yang dimiliki untuk menggunakannya.")
                .build();
        inventory.setItem(4, headerItem);

        // 3. Tombol Tutup (Slot 49)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER)
                .name("§c§lTutup Menu")
                .lore("§7Klik untuk menutup inventori")
                .build();
        inventory.setItem(49, closeItem);

        // 4. Tombol Petunjuk (Slot 48)
        ItemStack infoItem = new ItemBuilder(Material.BOOK)
                .name("§e§lInformasi & Bantuan")
                .lore("§7- Pancingan aktif akan otomatis berada di tanganmu.")
                .lore("§7- Pancingan virtual bersifat §dSoulbound§7 (anti hilang).")
                .lore("§7- Durability tersimpan otomatis tanpa exploit.")
                .build();
        inventory.setItem(48, infoItem);

        // 5. Render Daftar Rods (Slot 10 s/d 43 tanpa border)
        List<FishingRod> rods = new ArrayList<>(plugin.getConfigManager().getFishingRods().values());
        int slot = 10;

        for (FishingRod rod : rods) {
            // Lewati border samping kanan/kiri
            if (slot % 9 == 0) slot++;
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;

            boolean owned = (data != null && data.hasRod(rod.getId()));
            boolean isEquipped = (equippedRodId != null && equippedRodId.equalsIgnoreCase(rod.getId()));

            ItemStack displayItem;
            if (owned) {
                int curDur = data.getDurability(rod.getId(), rod.getDurability());
                displayItem = buildOwnedRodDisplay(rod, isEquipped, curDur);
            } else {
                displayItem = buildLockedRodDisplay(rod);
            }

            inventory.setItem(slot, displayItem);
            slotToRodId.put(slot, rod.getId());
            slot++;
        }
    }

    /**
     * Memproses klik slot di dalam GUI.
     */
    public void handleClick(int slot) {
        if (slot == 49) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            return;
        }

        String rodId = slotToRodId.get(slot);
        if (rodId == null) {
            return;
        }

        PlayerVirtualRods data = rodManager.getPlayerData(player.getUniqueId());
        if (data == null || !data.hasRod(rodId)) {
            player.sendMessage("§cKamu belum memiliki pancingan ini! Kunjungi Toko untuk membeli.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String currentEquipped = data.getEquippedRodId();
        if (rodId.equalsIgnoreCase(currentEquipped)) {
            // Unequip
            rodManager.unequipRod(player);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.0f, 0.8f);
        } else {
            // Equip
            rodManager.equipRod(player, rodId);
            player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1.0f, 1.2f);
        }

        // Re-render GUI untuk merefleksikan perubahan status
        render();
    }

    private ItemStack buildOwnedRodDisplay(FishingRod rod, boolean isEquipped, int durability) {
        ItemStack item = rod.createItemStack(durability);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();

            lore.add("");
            if (isEquipped) {
                meta.setDisplayName("§a§l[AKTIF] §r" + meta.getDisplayName());
                lore.add("§a✔ SEDANG DIGUNAKAN");
                lore.add("§c► Klik untuk Lepas (Unequip)");
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                lore.add("§e✔ SUDAH DIMILIKI");
                lore.add("§a► Klik untuk Gunakan (Equip)");
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildLockedRodDisplay(FishingRod rod) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7🔒 " + rod.getName() + " §8[Terkunci]");
            List<String> lore = new ArrayList<>();
            lore.add("§8" + rod.getDescription());
            lore.add("");
            lore.add("§7Tier: " + rod.getTier().getColor() + rod.getTier().getDisplayName());
            lore.add("§7Kapasitas: §f" + formatWeight(rod.getMaxWeightCapacity()));
            lore.add("§7Harga Toko: §e$" + String.format("%.0f", rod.getPrice()));
            lore.add("");
            lore.add("§c✘ Belum dimiliki!");
            lore.add("§7Dapatkan melalui Toko Nelayan atau Event khusus.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorder() {
        ItemStack borderPane = new ItemBuilder(Material.CYAN_STAINED_GLASS_PANE).name(" ").build();
        ItemStack darkBorderPane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, borderPane);
            inventory.setItem(45 + i, borderPane);
        }
        for (int i = 1; i < 5; i++) {
            inventory.setItem(i * 9, darkBorderPane);
            inventory.setItem(i * 9 + 8, darkBorderPane);
        }
    }

    private String formatWeight(double weight) {
        if (weight < 1.0) {
            return String.format("%.0f g", weight * 1000);
        } else if (weight < 1000.0) {
            return String.format("%.1f kg", weight);
        } else {
            return String.format("%.1f ton", weight / 1000.0);
        }
    }
}
