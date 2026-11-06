package id.nusafishing.integration.npc;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import id.nusafishing.NusaFishing;
import id.nusafishing.gui.EventFishCollectorGUI;
import id.nusafishing.gui.ShopGUI;
import id.nusafishing.fisch.inventory.VirtualRodInventoryGUI;

/**
 * Integrasi opsional FancyNpcs untuk NusaFishing.
 * OPSIONAL dan dinonaktifkan secara default di config (fancynpcs.enabled: false).
 * Tidak akan spawn otomatis tanpa lokasi yang didaftarkan secara eksplisit oleh admin.
 */
public class FancyNpcsHook {

    public enum NpcType {
        MERCHANT("Toko Nelayan"),
        RODS("Koleksi Pancingan"),
        COLLECTOR("Kolektor Ikan Mistik");

        private final String defaultTitle;

        NpcType(String defaultTitle) {
            this.defaultTitle = defaultTitle;
        }

        public String getDefaultTitle() {
            return defaultTitle;
        }

        public static NpcType fromString(String str) {
            if (str == null) return MERCHANT;
            switch (str.trim().toUpperCase()) {
                case "ROD":
                case "RODS":
                case "VIRTUAL_RODS":
                    return RODS;
                case "COLLECTOR":
                case "KOLEKTOR":
                    return COLLECTOR;
                case "SHOP":
                case "MERCHANT":
                case "TOKO":
                default:
                    return MERCHANT;
            }
        }
    }

    public static class RegisteredNpc {
        private final String id;
        private final NpcType type;
        private final Location location;
        private final String displayName;

        public RegisteredNpc(String id, NpcType type, Location location, String displayName) {
            this.id = id;
            this.type = type;
            this.location = location;
            this.displayName = displayName;
        }

        public String getId() { return id; }
        public NpcType getType() { return type; }
        public Location getLocation() { return location; }
        public String getDisplayName() { return displayName; }
    }

    private final NusaFishing plugin;
    private boolean available = false;
    private final Map<String, RegisteredNpc> registeredNpcs = new HashMap<>();
    private File npcFile;
    private FileConfiguration npcConfig;

    public FancyNpcsHook(NusaFishing plugin) {
        this.plugin = plugin;
        loadData();
        checkAvailability();
    }

    /**
     * Memeriksa apakah fitur diaktifkan di config dan plugin FancyNpcs tersedia.
     */
    public void checkAvailability() {
        this.available = false;

        if (!isEnabledInConfig()) {
            plugin.getLogger().info("[FancyNpcs] Integrasi FancyNpcs dinonaktifkan di config.yml (fancynpcs.enabled: false).");
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            plugin.getLogger().info("[FancyNpcs] Plugin FancyNpcs tidak terpasang di server. Fitur NPC dilewati.");
            return;
        }

        try {
            Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin");
            registerInteractListener();
            this.available = true;
            plugin.getLogger().info("[FancyNpcs] Berhasil terhubung ke FancyNpcs!");

            // Spawn NPC yang hanya telah didaftarkan admin
            syncAdminNpcs();
        } catch (Throwable t) {
            plugin.getLogger().warning("[FancyNpcs] Gagal menginisialisasi FancyNpcs: " + t.getMessage());
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isEnabledInConfig() {
        return plugin.getConfig().getBoolean("fancynpcs.enabled", false);
    }

    /**
     * Daftarkan event listener NpcInteractEvent secara refleksi.
     */
    @SuppressWarnings("unchecked")
    private void registerInteractListener() {
        try {
            Class<?> eventClass = Class.forName("de.oliver.fancynpcs.api.events.NpcInteractEvent");
            Listener dummyListener = new Listener() {};

            EventExecutor executor = (listener, event) -> {
                if (eventClass.isInstance(event)) {
                    handleNpcClick(event);
                }
            };

            Bukkit.getPluginManager().registerEvent(
                    (Class<? extends Event>) eventClass,
                    dummyListener,
                    EventPriority.NORMAL,
                    executor,
                    plugin
            );
        } catch (Throwable t) {
            plugin.getLogger().warning("[FancyNpcs] Tidak dapat mendaftarkan listener NpcInteractEvent: " + t.getMessage());
        }
    }

    /**
     * Memproses klik pada NPC.
     */
    private void handleNpcClick(Object event) {
        try {
            Method getNpcMethod = event.getClass().getMethod("getNpc");
            Object npc = getNpcMethod.invoke(event);
            if (npc == null) return;

            Method getDataMethod = npc.getClass().getMethod("getData");
            Object data = getDataMethod.invoke(npc);
            if (data == null) return;

            Method getNameMethod = data.getClass().getMethod("getName");
            String npcName = (String) getNameMethod.invoke(data);

            Method getPlayerMethod = event.getClass().getMethod("getPlayer");
            Player player = (Player) getPlayerMethod.invoke(event);
            if (player == null || npcName == null) return;

            RegisteredNpc registered = registeredNpcs.get(npcName.toLowerCase());
            if (registered == null) {
                return;
            }

            // Batalkan event interaksi default jika ada method setCancelled
            try {
                Method cancelMethod = event.getClass().getMethod("setCancelled", boolean.class);
                cancelMethod.invoke(event, true);
            } catch (Throwable ignored) {}

            // Buka menu sesuai tipe
            switch (registered.getType()) {
                case MERCHANT:
                    Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(player, plugin).open());
                    break;
                case RODS:
                    Bukkit.getScheduler().runTask(plugin, () -> new VirtualRodInventoryGUI(player, plugin).open());
                    break;
                case COLLECTOR:
                    Bukkit.getScheduler().runTask(plugin, () -> new EventFishCollectorGUI(player, plugin).open());
                    break;
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[FancyNpcs] Error saat memproses interaksi NPC: " + t.getMessage());
        }
    }

    /**
     * Mendaftarkan lokasi NPC oleh admin.
     *
     * @param id          ID unik NPC (misal: "nelayan_dermaga")
     * @param type        Tipe NPC (MERCHANT, RODS, COLLECTOR)
     * @param location    Lokasi spawn
     * @param displayName Nama tampilan NPC di atas kepala
     * @return true jika berhasil
     */
    public boolean registerNpcLocation(String id, NpcType type, Location location, String displayName) {
        if (id == null || location == null || location.getWorld() == null) {
            return false;
        }

        String key = id.toLowerCase();
        RegisteredNpc npc = new RegisteredNpc(key, type != null ? type : NpcType.MERCHANT, location,
                displayName != null ? displayName : "§e" + (type != null ? type.getDefaultTitle() : "NPC Nelayan"));
        registeredNpcs.put(key, npc);

        saveData();

        if (available) {
            spawnOrUpdateFancyNpc(npc);
        }
        return true;
    }

    /**
     * Menghapus NPC yang didaftarkan.
     */
    public boolean removeNpcLocation(String id) {
        if (id == null) return false;
        String key = id.toLowerCase();
        RegisteredNpc removed = registeredNpcs.remove(key);
        if (removed != null) {
            saveData();
            if (available) {
                removeFancyNpc(key);
            }
            return true;
        }
        return false;
    }

    public Set<String> getRegisteredNpcIds() {
        return Collections.unmodifiableSet(registeredNpcs.keySet());
    }

    public RegisteredNpc getRegisteredNpc(String id) {
        return id != null ? registeredNpcs.get(id.toLowerCase()) : null;
    }

    /**
     * Sinkronisasi NPC admin ke plugin FancyNpcs.
     */
    public void syncAdminNpcs() {
        if (!available) return;
        for (RegisteredNpc npc : registeredNpcs.values()) {
            spawnOrUpdateFancyNpc(npc);
        }
    }

    private void spawnOrUpdateFancyNpc(RegisteredNpc npc) {
        try {
            Class<?> pluginClass = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin");
            Object fancyPlugin = pluginClass.getMethod("get").invoke(null);
            Object npcManager = fancyPlugin.getClass().getMethod("getNpcManager").invoke(fancyPlugin);

            Method getNpcMethod = npcManager.getClass().getMethod("getNpc", String.class);
            Object existingNpc = getNpcMethod.invoke(npcManager, npc.getId());

            if (existingNpc != null) {
                // Update lokasi
                Object data = existingNpc.getClass().getMethod("getData").invoke(existingNpc);
                data.getClass().getMethod("setLocation", Location.class).invoke(data, npc.getLocation());
                data.getClass().getMethod("setDisplayName", String.class).invoke(data, npc.getDisplayName());
                existingNpc.getClass().getMethod("updateForAll").invoke(existingNpc);
            } else {
                // Buat NPC baru via NpcData
                Class<?> dataClass = Class.forName("de.oliver.fancynpcs.api.NpcData");
                Constructor<?> dataConstructor = dataClass.getConstructor(String.class, Location.class);
                Object npcData = dataConstructor.newInstance(npc.getId(), npc.getLocation());
                npcData.getClass().getMethod("setDisplayName", String.class).invoke(npcData, npc.getDisplayName());

                Method createNpcMethod = npcManager.getClass().getMethod("createNpc", dataClass);
                Object createdNpc = createNpcMethod.invoke(npcManager, npcData);

                if (createdNpc != null) {
                    Method registerMethod = npcManager.getClass().getMethod("registerNpc", createdNpc.getClass());
                    registerMethod.invoke(npcManager, createdNpc);
                    createdNpc.getClass().getMethod("create").invoke(createdNpc);
                    createdNpc.getClass().getMethod("spawnForAll").invoke(createdNpc);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[FancyNpcs] Gagal men-spawn NPC " + npc.getId() + ": " + t.getMessage());
        }
    }

    private void removeFancyNpc(String id) {
        try {
            Class<?> pluginClass = Class.forName("de.oliver.fancynpcs.api.FancyNpcsPlugin");
            Object fancyPlugin = pluginClass.getMethod("get").invoke(null);
            Object npcManager = fancyPlugin.getClass().getMethod("getNpcManager").invoke(fancyPlugin);

            Method getNpcMethod = npcManager.getClass().getMethod("getNpc", String.class);
            Object existingNpc = getNpcMethod.invoke(npcManager, id);

            if (existingNpc != null) {
                Method removeMethod = npcManager.getClass().getMethod("removeNpc", existingNpc.getClass());
                removeMethod.invoke(npcManager, existingNpc);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[FancyNpcs] Gagal menghapus NPC FancyNpcs " + id + ": " + t.getMessage());
        }
    }

    private void loadData() {
        this.npcFile = new File(plugin.getDataFolder(), "fancynpcs_data.yml");
        if (!npcFile.exists()) {
            return;
        }
        this.npcConfig = YamlConfiguration.loadConfiguration(npcFile);
        ConfigurationSection section = npcConfig.getConfigurationSection("npcs");
        if (section == null) return;

        for (String id : section.getKeys(false)) {
            String typeStr = section.getString(id + ".type", "MERCHANT");
            NpcType type = NpcType.fromString(typeStr);
            String displayName = section.getString(id + ".display_name", "§eNPC Nelayan");

            String worldName = section.getString(id + ".world");
            if (worldName == null || Bukkit.getWorld(worldName) == null) continue;

            double x = section.getDouble(id + ".x");
            double y = section.getDouble(id + ".y");
            double z = section.getDouble(id + ".z");
            float yaw = (float) section.getDouble(id + ".yaw", 0.0);
            float pitch = (float) section.getDouble(id + ".pitch", 0.0);

            Location loc = new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
            registeredNpcs.put(id.toLowerCase(), new RegisteredNpc(id.toLowerCase(), type, loc, displayName));
        }
    }

    private void saveData() {
        if (npcFile == null) {
            npcFile = new File(plugin.getDataFolder(), "fancynpcs_data.yml");
        }
        if (npcConfig == null) {
            npcConfig = new YamlConfiguration();
        }

        npcConfig.set("npcs", null);
        for (RegisteredNpc npc : registeredNpcs.values()) {
            String path = "npcs." + npc.getId();
            npcConfig.set(path + ".type", npc.getType().name());
            npcConfig.set(path + ".display_name", npc.getDisplayName());
            if (npc.getLocation().getWorld() != null) {
                npcConfig.set(path + ".world", npc.getLocation().getWorld().getName());
            }
            npcConfig.set(path + ".x", npc.getLocation().getX());
            npcConfig.set(path + ".y", npc.getLocation().getY());
            npcConfig.set(path + ".z", npc.getLocation().getZ());
            npcConfig.set(path + ".yaw", npc.getLocation().getYaw());
            npcConfig.set(path + ".pitch", npc.getLocation().getPitch());
        }

        try {
            npcConfig.save(npcFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[FancyNpcs] Gagal menyimpan fancynpcs_data.yml: " + e.getMessage());
        }
    }
}
