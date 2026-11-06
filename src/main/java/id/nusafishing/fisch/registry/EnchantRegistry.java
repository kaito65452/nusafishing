package id.nusafishing.fisch.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import id.nusafishing.fisch.model.FischEnchant;

/**
 * Registry for Fisch Rod Enchantments.
 * Manages enchant definitions, applying enchants to Fishing Rod ItemStacks (via PDC),
 * and aggregating total environmental / rod bonuses.
 */
public class EnchantRegistry {
    public static final NamespacedKey ENCHANTS_KEY = NamespacedKey.fromString("nusafishing:rod_enchants");

    private final Map<String, FischEnchant> enchants = new ConcurrentHashMap<>();

    public EnchantRegistry() {
        registerDefaults();
    }

    /**
     * Registers default Fisch enchants:
     * Resilient, Lucky, Swift, Steady, Abyssal, Mutated.
     */
    private void registerDefaults() {
        register(new FischEnchant("resilient", "Resilient",
                "Meningkatkan ketahanan tali pancing dari ketegangan ikan besar.",
                5, 0.0, 0.0, 0.0, 0.15, 0.0));

        register(new FischEnchant("lucky", "Lucky",
                "Meningkatkan peluang menemukan ikan bertier langka dan mistik.",
                5, 0.10, 0.0, 0.0, 0.0, 0.0));

        register(new FischEnchant("swift", "Swift",
                "Mempercepat kecepatan penarikan reel kail saat strike.",
                5, 0.0, 0.12, 0.0, 0.0, 0.0));

        register(new FischEnchant("steady", "Steady",
                "Menjaga kestabilan joran saat menarik ikan berbobot berat.",
                5, 0.0, 0.0, 0.12, 0.08, 0.0));

        register(new FischEnchant("abyssal", "Abyssal",
                "Membuka peluang menangkap ikan laut dalam dan menambah luck.",
                3, 0.15, 0.0, 0.05, 0.05, 0.08));

        register(new FischEnchant("mutated", "Mutated",
                "Meningkatkan peluang menangkap ikan dengan mutasi unik secara drastis.",
                5, 0.0, 0.0, 0.0, 0.0, 0.15));
    }

    public void register(FischEnchant enchant) {
        if (enchant == null) return;
        enchants.put(enchant.getId(), enchant);
    }

    public FischEnchant getEnchant(String id) {
        if (id == null) return null;
        return enchants.get(id.trim().toLowerCase());
    }

    public Map<String, FischEnchant> getAll() {
        return Collections.unmodifiableMap(enchants);
    }

    /**
     * Clears and reloads enchants from a ConfigurationSection.
     */
    public synchronized void loadFromConfig(ConfigurationSection section) {
        if (section == null) return;

        enchants.clear();
        for (String key : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(key);
            if (sec != null) {
                FischEnchant enchant = FischEnchant.fromConfiguration(key.toLowerCase(), sec);
                if (enchant != null) {
                    enchants.put(enchant.getId(), enchant);
                }
            }
        }

        if (enchants.isEmpty()) {
            registerDefaults();
        }
    }

    /**
     * Extracts active Fisch enchants and levels from an ItemStack PDC.
     */
    public Map<FischEnchant, Integer> getEnchants(ItemStack item) {
        if (item == null || !item.hasItemMeta() || ENCHANTS_KEY == null) {
            return Collections.emptyMap();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Collections.emptyMap();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(ENCHANTS_KEY, PersistentDataType.STRING)) {
            return Collections.emptyMap();
        }

        String raw = pdc.get(ENCHANTS_KEY, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Collections.emptyMap();
        }

        Map<FischEnchant, Integer> result = new LinkedHashMap<>();
        String[] pairs = raw.split(";");
        for (String pair : pairs) {
            String[] kv = pair.split(":");
            if (kv.length == 2) {
                FischEnchant enchant = getEnchant(kv[0].trim());
                if (enchant != null) {
                    try {
                        int level = Integer.parseInt(kv[1].trim());
                        if (level > 0) {
                            result.put(enchant, Math.min(level, enchant.getMaxLevel()));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return result;
    }

    /**
     * Applies an enchant to an ItemStack and updates lore and PDC accordingly.
     */
    public boolean applyEnchant(ItemStack item, FischEnchant enchant, int level) {
        if (item == null || enchant == null || !item.hasItemMeta() || ENCHANTS_KEY == null) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Map<FischEnchant, Integer> current = new LinkedHashMap<>(getEnchants(item));
        int finalLevel = Math.min(Math.max(1, level), enchant.getMaxLevel());
        current.put(enchant, finalLevel);

        // Serialize back to PDC format "id:lvl;id:lvl"
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<FischEnchant, Integer> entry : current.entrySet()) {
            if (!first) sb.append(";");
            sb.append(entry.getKey().getId()).append(":").append(entry.getValue());
            first = false;
        }
        meta.getPersistentDataContainer().set(ENCHANTS_KEY, PersistentDataType.STRING, sb.toString());

        // Update item lore
        updateEnchantLore(meta, current);
        item.setItemMeta(meta);
        return true;
    }

    private void updateEnchantLore(ItemMeta meta, Map<FischEnchant, Integer> enchantMap) {
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Remove old enchant lines
        lore.removeIf(line -> line.startsWith("§9") && (
                line.contains("Resilient") || line.contains("Lucky") ||
                line.contains("Swift") || line.contains("Steady") ||
                line.contains("Abyssal") || line.contains("Mutated")));

        // Insert new enchant lines at beginning or before separator
        int insertIndex = 0;
        for (Map.Entry<FischEnchant, Integer> entry : enchantMap.entrySet()) {
            lore.add(insertIndex++, entry.getKey().formatLore(entry.getValue()));
        }

        meta.setLore(lore);
    }

    /**
     * Aggregated luck boost from all enchants on the item.
     */
    public double getTotalLuckBoost(ItemStack item) {
        double total = 0.0;
        for (Map.Entry<FischEnchant, Integer> entry : getEnchants(item).entrySet()) {
            total += entry.getKey().getBonusLuck(entry.getValue());
        }
        return total;
    }

    /**
     * Aggregated speed boost from all enchants on the item.
     */
    public double getTotalSpeedBoost(ItemStack item) {
        double total = 0.0;
        for (Map.Entry<FischEnchant, Integer> entry : getEnchants(item).entrySet()) {
            total += entry.getKey().getBonusSpeed(entry.getValue());
        }
        return total;
    }

    /**
     * Aggregated weight capacity boost from all enchants on the item.
     */
    public double getTotalWeightBoost(ItemStack item) {
        double total = 0.0;
        for (Map.Entry<FischEnchant, Integer> entry : getEnchants(item).entrySet()) {
            total += entry.getKey().getBonusWeight(entry.getValue());
        }
        return total;
    }

    /**
     * Aggregated resilience boost from all enchants on the item.
     */
    public double getTotalResilienceBoost(ItemStack item) {
        double total = 0.0;
        for (Map.Entry<FischEnchant, Integer> entry : getEnchants(item).entrySet()) {
            total += entry.getKey().getBonusResilience(entry.getValue());
        }
        return total;
    }

    /**
     * Aggregated mutation chance boost from all enchants on the item.
     */
    public double getTotalMutationBoost(ItemStack item) {
        double total = 0.0;
        for (Map.Entry<FischEnchant, Integer> entry : getEnchants(item).entrySet()) {
            total += entry.getKey().getBonusMutationChance(entry.getValue());
        }
        return total;
    }
}
