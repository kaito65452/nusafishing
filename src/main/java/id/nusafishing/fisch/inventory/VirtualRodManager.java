package id.nusafishing.fisch.inventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.FishingRod;

/**
 * Pengelola sistem Virtual Rod Inventory ala Fisch Roblox.
 * Menyimpan rod pemain secara virtual, mengelola equip/unequip rod aktif,
 * dan menyimpan data pemain secara asynchronous dengan mekanisme debounce
 * untuk menjamin zero I/O lag dan zero exploit.
 */
public class VirtualRodManager {

    public static final String VIRTUAL_ROD_TAG = "virtual_rod";
    public static final String VIRTUAL_OWNER_TAG = "virtual_owner";
    public static final String VIRTUAL_SOULBOUND_TAG = "soulbound";

    private final NusaFishing plugin;
    private final NamespacedKey virtualRodKey;
    private final NamespacedKey virtualOwnerKey;
    private final NamespacedKey soulboundKey;
    private final NamespacedKey rodIdKey;
    private final NamespacedKey durabilityKey;

    private final Map<UUID, PlayerVirtualRods> cache = new ConcurrentHashMap<>();
    private final File dataFile;
    private FileConfiguration dataConfig;
    private BukkitTask debounceTask;

    private static final long DEBOUNCE_DELAY_MS = 2500L; // 2.5 detik setelah aksi terakhir

    public VirtualRodManager(NusaFishing plugin) {
        this.plugin = plugin;
        this.virtualRodKey = new NamespacedKey(plugin, VIRTUAL_ROD_TAG);
        this.virtualOwnerKey = new NamespacedKey(plugin, VIRTUAL_OWNER_TAG);
        this.soulboundKey = new NamespacedKey(plugin, VIRTUAL_SOULBOUND_TAG);
        this.rodIdKey = new NamespacedKey(plugin, "rod_id");
        this.durabilityKey = new NamespacedKey(plugin, "durability");

        this.dataFile = new File(plugin.getDataFolder(), "virtual_rods.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[VirtualRod] Gagal membuat virtual_rods.yml: " + e.getMessage());
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        startDebounceTask();
    }

    /**
     * Memulai timer debounce berkala untuk auto-save data dirty ke disk tanpa lag.
     */
    private void startDebounceTask() {
        // Cek setiap 40 ticks (2 detik)
        this.debounceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            List<PlayerVirtualRods> toSave = new ArrayList<>();

            for (PlayerVirtualRods data : cache.values()) {
                if (data.isDirty() && (now - data.getLastModified() >= DEBOUNCE_DELAY_MS)) {
                    data.setDirty(false);
                    toSave.add(data);
                }
            }

            if (!toSave.isEmpty()) {
                saveBatchAsync(toSave);
            }
        }, 40L, 40L);
    }

    /**
     * Mengambil data virtual rod pemain. Jika belum ada di cache, muat dari file.
     */
    public PlayerVirtualRods getPlayerData(UUID uuid) {
        if (uuid == null) return null;
        return cache.computeIfAbsent(uuid, this::loadFromDisk);
    }

    /**
     * Memuat data pemain dari virtual_rods.yml.
     */
    private PlayerVirtualRods loadFromDisk(UUID uuid) {
        PlayerVirtualRods data = new PlayerVirtualRods(uuid);
        String path = "players." + uuid.toString();

        if (dataConfig.contains(path)) {
            List<String> rods = dataConfig.getStringList(path + ".owned_rods");
            String equipped = dataConfig.getString(path + ".equipped_rod", null);
            Map<String, Integer> durabilities = new HashMap<>();

            ConfigurationSection durSection = dataConfig.getConfigurationSection(path + ".durability");
            if (durSection != null) {
                for (String key : durSection.getKeys(false)) {
                    durabilities.put(key.toLowerCase(), durSection.getInt(key));
                }
            }

            data.loadFromData(new HashSet<>(rods), equipped, durabilities);
        } else {
            // Berikan default basic rod jika pemain baru dan dikonfigurasi
            if (plugin.getConfig().getBoolean("virtual_rods.give_starter_rod", true)) {
                data.addRod("basic_rod");
                data.markDirty();
            }
        }
        return data;
    }

    /**
     * Menyimpan batch data secara asinkron ke file.
     */
    private void saveBatchAsync(List<PlayerVirtualRods> batch) {
        // Ambil snapshot in-memory terlebih dahulu pada thread saat ini
        Map<String, Map<String, Object>> snapshot = new HashMap<>();
        for (PlayerVirtualRods data : batch) {
            Map<String, Object> playerData = new HashMap<>();
            playerData.put("owned_rods", new ArrayList<>(data.getOwnedRods()));
            playerData.put("equipped_rod", data.getEquippedRodId());
            playerData.put("durability", new HashMap<>(data.getAllDurabilities()));
            snapshot.put("players." + data.getPlayerId().toString(), playerData);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            synchronized (dataFile) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
                for (Map.Entry<String, Map<String, Object>> entry : snapshot.entrySet()) {
                    String path = entry.getKey();
                    Map<String, Object> values = entry.getValue();
                    config.set(path + ".owned_rods", values.get("owned_rods"));
                    config.set(path + ".equipped_rod", values.get("equipped_rod"));
                    config.set(path + ".durability", values.get("durability"));
                }
                try {
                    config.save(dataFile);
                } catch (IOException e) {
                    plugin.getLogger().severe("[VirtualRod] Gagal menyimpan virtual_rods.yml async: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Menyimpan semua data pemain (digunakan saat plugin disable atau reload).
     *
     * @param async Jika true simpan di thread terpisah, jika false simpan synchronous
     */
    public void saveAll(boolean async) {
        if (debounceTask != null) {
            debounceTask.cancel();
        }

        List<PlayerVirtualRods> allDirty = new ArrayList<>();
        for (PlayerVirtualRods data : cache.values()) {
            allDirty.add(data);
        }

        if (allDirty.isEmpty()) return;

        if (async) {
            saveBatchAsync(allDirty);
        } else {
            synchronized (dataFile) {
                FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
                for (PlayerVirtualRods data : allDirty) {
                    String path = "players." + data.getPlayerId().toString();
                    config.set(path + ".owned_rods", new ArrayList<>(data.getOwnedRods()));
                    config.set(path + ".equipped_rod", data.getEquippedRodId());
                    config.set(path + ".durability", new HashMap<>(data.getAllDurabilities()));
                }
                try {
                    config.save(dataFile);
                    plugin.getLogger().info("[VirtualRod] Berhasil menyimpan semua data virtual rod ke disk.");
                } catch (IOException e) {
                    plugin.getLogger().severe("[VirtualRod] Gagal menyimpan data sinkron: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Memeriksa apakah pemain memiliki pancingan tertentu.
     */
    public boolean hasRod(UUID uuid, String rodId) {
        if (uuid == null || rodId == null) return false;
        PlayerVirtualRods data = getPlayerData(uuid);
        return data != null && data.hasRod(rodId);
    }

    /**
     * Menambahkan pancingan ke koleksi pemain.
     */
    public boolean addRod(UUID uuid, String rodId) {
        if (uuid == null || rodId == null) return false;
        PlayerVirtualRods data = getPlayerData(uuid);
        if (data != null) {
            boolean added = data.addRod(rodId);
            if (added) {
                FishingRod rod = plugin.getConfigManager().getFishingRods().get(rodId.toLowerCase());
                if (rod != null) {
                    data.setDurability(rodId, rod.getDurability());
                }
            }
            return added;
        }
        return false;
    }

    /**
     * Menghapus pancingan dari koleksi pemain.
     */
    public boolean removeRod(UUID uuid, String rodId) {
        if (uuid == null || rodId == null) return false;
        PlayerVirtualRods data = getPlayerData(uuid);
        if (data != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && rodId.equalsIgnoreCase(data.getEquippedRodId())) {
                unequipRod(player);
            }
            return data.removeRod(rodId);
        }
        return false;
    }

    /**
     * Mendapatkan ID pancingan aktif yang sedang digunakan pemain.
     */
    public String getEquippedRodId(UUID uuid) {
        if (uuid == null) return null;
        PlayerVirtualRods data = getPlayerData(uuid);
        return data != null ? data.getEquippedRodId() : null;
    }

    /**
     * Menggunakan / Melengkapi (Equip) pancingan untuk pemain.
     * ZERO DUPE & SERVER-SIDE VALIDATION: Memvalidasi kepemilikan di server-side!
     *
     * @param player Pemain yang melengkapi
     * @param rodId  ID pancingan
     * @return true jika berhasil
     */
    public boolean equipRod(Player player, String rodId) {
        if (player == null || rodId == null) return false;
        UUID uuid = player.getUniqueId();
        PlayerVirtualRods data = getPlayerData(uuid);
        if (data == null) return false;

        // Validasi ketat kepemilikan internal
        if (!data.hasRod(rodId)) {
            player.sendMessage("§cKamu belum memiliki alat pancing ini!");
            return false;
        }

        FishingRod rod = plugin.getConfigManager().getFishingRods().get(rodId.toLowerCase());
        if (rod == null) {
            player.sendMessage("§cAlat pancing tidak valid atau konfigurasi hilang!");
            return false;
        }

        // Hapus rod virtual yang mungkin sedang ada di inventori pemain untuk mencegah duplikasi
        removeVirtualRodsFromInventory(player);

        // Ambil durability tersimpan
        int durability = data.getDurability(rodId, rod.getDurability());

        // Buat ItemStack dengan tag PDC virtual soulbound
        ItemStack rodItem = createVirtualRodItemStack(rod, uuid, durability);

        // Berikan ke pemain
        PlayerInventory inv = player.getInventory();
        int heldSlot = inv.getHeldItemSlot();
        ItemStack currentHeld = inv.getItem(heldSlot);

        if (currentHeld == null || currentHeld.getType() == Material.AIR || currentHeld.getType() == Material.FISHING_ROD) {
            inv.setItem(heldSlot, rodItem);
        } else {
            // Cari slot hotbar kosong (0-8)
            int freeSlot = -1;
            for (int i = 0; i < 9; i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || it.getType() == Material.AIR) {
                    freeSlot = i;
                    break;
                }
            }
            if (freeSlot != -1) {
                inv.setItem(freeSlot, rodItem);
            } else {
                // Tambahkan ke inventori umum
                inv.addItem(rodItem);
            }
        }

        data.setEquippedRodId(rodId);
        player.sendMessage("§aBerhasil menggunakan: §r" + rod.getTier().getColor() + rod.getName() + " §7[Virtual Rod]");
        return true;
    }

    /**
     * Melepas (Unequip) pancingan aktif pemain.
     * Menyinkronkan durability terakhir ke data storage sebelum menghapus item dari inventori.
     */
    public boolean unequipRod(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        PlayerVirtualRods data = getPlayerData(uuid);
        if (data == null) return false;

        String equipped = data.getEquippedRodId();
        if (equipped == null) return false;

        // Sinkronisasi sisa durability dari item fisik di inventori
        syncDurabilityFromInventory(player, data, equipped);

        // Hapus item fisik virtual rod dari inventori pemain
        removeVirtualRodsFromInventory(player);

        data.setEquippedRodId(null);
        player.sendMessage("§eAlat pancing telah disimpan kembali ke Virtual Inventory.");
        return true;
    }

    /**
     * Menyinkronkan durability dari item virtual rod di inventori pemain ke PlayerVirtualRods.
     */
    private void syncDurabilityFromInventory(Player player, PlayerVirtualRods data, String rodId) {
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (isVirtualRod(item)) {
                String id = getVirtualRodId(item);
                if (id != null && id.equalsIgnoreCase(rodId)) {
                    int dur = getItemDurability(item);
                    if (dur >= 0) {
                        data.setDurability(rodId, dur);
                    }
                }
            }
        }
    }

    /**
     * Menghapus semua instans virtual rod milik pemain dari inventorynya.
     */
    public void removeVirtualRodsFromInventory(Player player) {
        if (player == null) return;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (isVirtualRod(item)) {
                inv.setItem(i, null);
            }
        }
        // Bersihkan juga slot off-hand
        ItemStack offhand = inv.getItemInOffHand();
        if (isVirtualRod(offhand)) {
            inv.setItemInOffHand(null);
        }
    }

    /**
     * Membuat ItemStack virtual rod yang dilengkapi tag PDC khusus anti-dupe dan soulbound.
     */
    public ItemStack createVirtualRodItemStack(FishingRod rod, UUID owner, int durability) {
        ItemStack item = rod.createItemStack(durability);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(virtualRodKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(virtualOwnerKey, PersistentDataType.STRING, owner.toString());
            pdc.set(soulboundKey, PersistentDataType.BYTE, (byte) 1);
            pdc.set(rodIdKey, PersistentDataType.STRING, rod.getId());
            pdc.set(durabilityKey, PersistentDataType.INTEGER, durability);

            // Tambahkan lore soulbound
            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();
            lore.add("");
            lore.add("§d✦ Virtual Soulbound Rod ✦");
            lore.add("§8Item ini terikat ke akunmu & disimpan aman");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Memeriksa apakah suatu ItemStack adalah Virtual Rod.
     */
    public boolean isVirtualRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(virtualRodKey, PersistentDataType.BYTE);
    }

    /**
     * Memeriksa apakah ItemStack bersifat soulbound.
     */
    public boolean isSoulbound(ItemStack item) {
        if (item == null) return false;
        if (!plugin.getConfig().getBoolean("virtual_rods.soulbound", true)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(soulboundKey, PersistentDataType.BYTE) || pdc.has(virtualRodKey, PersistentDataType.BYTE);
    }

    /**
     * Mengambil ID rod dari virtual rod item.
     */
    public String getVirtualRodId(ItemStack item) {
        if (item == null || !isVirtualRod(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(rodIdKey, PersistentDataType.STRING)) {
            return pdc.get(rodIdKey, PersistentDataType.STRING);
        }
        NamespacedKey legacyKey = NamespacedKey.fromString("nusafishing:rod_id");
        if (legacyKey != null && pdc.has(legacyKey, PersistentDataType.STRING)) {
            return pdc.get(legacyKey, PersistentDataType.STRING);
        }
        return null;
    }

    /**
     * Mengambil durability dari item virtual rod.
     */
    public int getItemDurability(ItemStack item) {
        if (item == null || !isVirtualRod(item)) return -1;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return -1;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(durabilityKey, PersistentDataType.INTEGER)) {
            return pdc.get(durabilityKey, PersistentDataType.INTEGER);
        }
        NamespacedKey legacyKey = NamespacedKey.fromString("nusafishing:durability");
        if (legacyKey != null && pdc.has(legacyKey, PersistentDataType.INTEGER)) {
            return pdc.get(legacyKey, PersistentDataType.INTEGER);
        }
        return -1;
    }

    /**
     * Update durability rod pemain saat memancing.
     */
    public void updateRodDurability(Player player, String rodId, int newDurability) {
        if (player == null || rodId == null) return;
        PlayerVirtualRods data = getPlayerData(player.getUniqueId());
        if (data != null) {
            data.setDurability(rodId, newDurability);
        }
    }
}
