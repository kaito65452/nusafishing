package id.nusafishing.fisch.registry;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import id.nusafishing.fisch.model.FischTotem;
import id.nusafishing.fisch.model.FischWeather;

/**
 * Registry for Fisch Totems.
 * Supports YAML loading, default Fisch totem definitions, item parsing, and creation.
 */
public class TotemRegistry {
    public static final NamespacedKey TOTEM_KEY = FischTotem.TOTEM_KEY;

    private final Map<String, FischTotem> totems = new ConcurrentHashMap<>();

    public TotemRegistry() {
        registerDefaults();
    }

    /**
     * Registers default Fisch totems.
     */
    private void registerDefaults() {
        register(new FischTotem("wind_totem", "§bTotem Angin",
                "Memanggil hembusan angin kencang samudra, mempercepat laju penarikan kail.",
                300, 1500.0, FischTotem.TotemEffectType.WIND_TOTEM,
                FischWeather.WINDY, null, 0.25, 0.15, Material.TOTEM_OF_UNDYING, 101));

        register(new FischTotem("tempest_totem", "§1Totem Badai",
                "Membangkitkan badai petir dahsyat yang memancing ikan buas dan mutasi elektrik.",
                300, 2500.0, FischTotem.TotemEffectType.TEMPEST_TOTEM,
                FischWeather.STORM, null, 0.35, 0.30, Material.TOTEM_OF_UNDYING, 102));

        register(new FischTotem("sundial_totem", "§eTotem Jam Matahari",
                "Mengusir awan badai dan menyinari perairan dengan terik matahari siang.",
                300, 1200.0, FischTotem.TotemEffectType.SUNDIAL_TOTEM,
                FischWeather.CLEAR, null, 0.15, 0.05, Material.TOTEM_OF_UNDYING, 103));

        register(new FischTotem("smokescreen_totem", "§7Totem Kabut Asap",
                "Menyelimuti perairan dalam kabut tebal misterius, memikat ikan abyssal.",
                300, 1800.0, FischTotem.TotemEffectType.SMOKESCREEN_TOTEM,
                FischWeather.FOG, null, 0.20, 0.25, Material.TOTEM_OF_UNDYING, 104));

        register(new FischTotem("aurora_totem", "§aTotem Aurora",
                "Menghadirkan fenomena cahaya kosmik Aurora Borealis dengan peluang mutasi drastis.",
                240, 5000.0, FischTotem.TotemEffectType.AURORA_TOTEM,
                FischWeather.AURORA, null, 0.75, 1.00, Material.TOTEM_OF_UNDYING, 105));

        register(new FischTotem("eclipse_totem", "§5Totem Gerhana",
                "Menggelapkan langit dalam Gerhana Matahari total. Peluang mutasi dan ikan mistik mencapai puncaknya.",
                180, 8000.0, FischTotem.TotemEffectType.ECLIPSE_TOTEM,
                FischWeather.ECLIPSE, null, 1.00, 1.50, Material.TOTEM_OF_UNDYING, 106));
    }

    public void register(FischTotem totem) {
        if (totem == null) return;
        totems.put(totem.getId(), totem);
    }

    public FischTotem getTotem(String id) {
        if (id == null) return null;
        return totems.get(id.trim().toLowerCase());
    }

    public Map<String, FischTotem> getAll() {
        return Collections.unmodifiableMap(totems);
    }

    public java.util.Collection<FischTotem> getAllTotems() {
        return totems.values();
    }

    /**
     * Clears and reloads totems from a ConfigurationSection.
     */
    public synchronized void loadFromConfig(ConfigurationSection section) {
        if (section == null) return;

        totems.clear();
        for (String key : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(key);
            if (sec != null) {
                FischTotem totem = FischTotem.fromConfiguration(key.toLowerCase(), sec);
                if (totem != null) {
                    totems.put(totem.getId(), totem);
                }
            }
        }

        if (totems.isEmpty()) {
            registerDefaults();
        }
    }

    /**
     * Creates an ItemStack for the specified totem.
     */
    public ItemStack createTotemItem(String id, int amount) {
        FischTotem totem = getTotem(id);
        if (totem == null) return null;
        return totem.createItemStack(amount);
    }

    /**
     * Checks if an ItemStack is a Fisch Totem.
     */
    public boolean isTotemItem(ItemStack item) {
        return getTotemFromItem(item) != null;
    }

    /**
     * Extracts the FischTotem object from an ItemStack's PDC, if present.
     */
    public FischTotem getTotemFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || TOTEM_KEY == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(TOTEM_KEY, PersistentDataType.STRING)) {
            return null;
        }

        String totemId = pdc.get(TOTEM_KEY, PersistentDataType.STRING);
        return getTotem(totemId);
    }
}
