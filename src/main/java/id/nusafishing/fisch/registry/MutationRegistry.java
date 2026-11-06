package id.nusafishing.fisch.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import id.nusafishing.fisch.model.FischMutation;
import id.nusafishing.fisch.model.FischSeason;
import id.nusafishing.fisch.model.FischWeather;

/**
 * High-performance registry for fish mutations.
 * Supports YAML loading, O(1) lookups, and fast weighted probability rolls.
 */
public class MutationRegistry {
    public static final NamespacedKey MUTATION_KEY = NamespacedKey.fromString("nusafishing:fish_mutation");

    private final Map<String, FischMutation> mutations = new ConcurrentHashMap<>();
    private volatile FischMutation[] mutationArray = new FischMutation[0];
    private volatile double totalBaseChance = 0.0;

    public MutationRegistry() {
        registerDefaults();
    }

    /**
     * Registers default Fisch mutations out of the box.
     */
    private void registerDefaults() {
        register(new FischMutation("albino", "Albino", "Albino", "", 1.5, 1.05, 0.08, "§f",
                "Ikan pucat tanpa pigmen warna akibat kelainan genetik langka.", Collections.emptyMap()));

        Map<String, Object> electricCond = new HashMap<>();
        electricCond.put("weather", "STORM");
        register(new FischMutation("electric", "Electric", "Electric", "", 2.2, 1.00, 0.04, "§e",
                "Menyimpan muatan listrik statis tinggi dari badai samudra.", electricCond));

        Map<String, Object> darkCond = new HashMap<>();
        darkCond.put("night_only", true);
        register(new FischMutation("dark", "Dark", "Dark", "", 2.5, 1.15, 0.035, "§8",
                "Terpengaruh energi kegelapan palung laut terdalam.", darkCond));

        Map<String, Object> giantCond = new HashMap<>();
        giantCond.put("min_weight", 1.0);
        register(new FischMutation("giant", "Giant", "Giant", "", 2.0, 2.50, 0.05, "§c",
                "Spesies raksasa berbobot berkali-kali lipat dari ukuran wajar.", giantCond));

        register(new FischMutation("sparkling", "Sparkling", "Sparkling", "", 3.0, 1.00, 0.02, "§b",
                "Berkilau memancarkan serbuk cahaya mistis di dalam air.", Collections.emptyMap()));

        register(new FischMutation("mythic", "Mythic", "Mythic", "", 5.0, 1.50, 0.008, "§5",
                "Mutasi langka legendaris yang diselimuti aura kosmik.", Collections.emptyMap()));

        register(new FischMutation("translucent", "Translucent", "Translucent", "", 2.8, 0.90, 0.025, "§3",
                "Tubuh transparan tembus pandang menyerupai kristal air.", Collections.emptyMap()));

        register(new FischMutation("gold", "Gold", "Gold", "", 4.0, 1.30, 0.015, "§6",
                "Seluruh sisik bermutasi menjadi lapisan emas murni berkilau.", Collections.emptyMap()));
    }

    /**
     * Registers or updates a mutation.
     */
    public synchronized void register(FischMutation mutation) {
        if (mutation == null) return;
        mutations.put(mutation.getId(), mutation);
        rebuildCache();
    }

    /**
     * Clears and reloads mutations from a configuration section.
     */
    public synchronized void loadFromConfig(ConfigurationSection section) {
        if (section == null) return;

        mutations.clear();
        for (String key : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(key);
            if (sec != null) {
                FischMutation mutation = FischMutation.fromConfiguration(key.toLowerCase(), sec);
                if (mutation != null) {
                    mutations.put(mutation.getId(), mutation);
                }
            }
        }

        // Keep defaults if config is empty
        if (mutations.isEmpty()) {
            registerDefaults();
        } else {
            rebuildCache();
        }
    }

    private void rebuildCache() {
        mutationArray = mutations.values().toArray(new FischMutation[0]);
        double sum = 0.0;
        for (FischMutation m : mutationArray) {
            sum += m.getChance();
        }
        totalBaseChance = sum;
    }

    public FischMutation getMutation(String id) {
        if (id == null) return null;
        return mutations.get(id.trim().toLowerCase());
    }

    public Map<String, FischMutation> getAll() {
        return Collections.unmodifiableMap(mutations);
    }

    /**
     * Fast roll for a mutation with a luck/mutation multiplier boost.
     * Returns null if no mutation occurs on this roll.
     */
    public FischMutation rollMutation(Random rnd, double mutationBoost) {
        return rollMutation(rnd, mutationBoost, null, null, false, 0.0);
    }

    /**
     * Fast condition-aware roll for a mutation.
     *
     * @param rnd           Random instance
     * @param mutationBoost Multiplier boost (e.g. 1.0 = normal, 2.0 = double chance)
     * @param weather       Current FischWeather (nullable)
     * @param season        Current FischSeason (nullable)
     * @param isNight       Whether it is currently nighttime
     * @param fishWeight    Weight of the caught fish
     * @return FischMutation if rolled, or null
     */
    public FischMutation rollMutation(Random rnd, double mutationBoost,
                                      FischWeather weather, FischSeason season,
                                      boolean isNight, double fishWeight) {
        FischMutation[] array = this.mutationArray;
        if (array.length == 0 || rnd == null) return null;

        double effectiveBoost = Math.max(0.1, mutationBoost);

        // Filter valid candidates based on current environmental conditions
        List<FischMutation> candidates = new ArrayList<>(array.length);
        double candidateWeightSum = 0.0;

        for (FischMutation m : array) {
            if (m.meetsConditions(weather, season, isNight, fishWeight)) {
                candidates.add(m);
                candidateWeightSum += m.getChance();
            }
        }

        if (candidates.isEmpty() || candidateWeightSum <= 0.0) {
            return null;
        }

        // Calculate if mutation triggers on this catch
        double triggerChance = Math.min(1.0, candidateWeightSum * effectiveBoost);
        if (rnd.nextDouble() > triggerChance) {
            return null; // No mutation triggered
        }

        // Weighted random selection among matching candidates
        double roll = rnd.nextDouble() * candidateWeightSum;
        double current = 0.0;
        for (FischMutation candidate : candidates) {
            current += candidate.getChance();
            if (roll <= current) {
                return candidate;
            }
        }

        return candidates.get(candidates.size() - 1);
    }

    /**
     * Applies a mutation to a fish ItemStack (PDC tag, name prefix, lore badge).
     */
    public boolean applyMutation(ItemStack item, FischMutation mutation) {
        if (item == null || mutation == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        // Save mutation ID to PDC
        if (MUTATION_KEY != null) {
            meta.getPersistentDataContainer().set(MUTATION_KEY, PersistentDataType.STRING, mutation.getId());
        }

        // Update display name
        String currentName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
        meta.setDisplayName(mutation.applyToName(currentName));

        // Add mutation lore line
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add(0, "§7Mutasi: " + mutation.getFormattedBadge());
        if (!mutation.getDescription().isBlank()) {
            lore.add(1, "§8" + mutation.getDescription());
        }
        meta.setLore(lore);

        item.setItemMeta(meta);
        return true;
    }

    /**
     * Reads the FischMutation attached to an ItemStack PDC, if any.
     */
    public FischMutation getMutationFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta() || MUTATION_KEY == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(MUTATION_KEY, PersistentDataType.STRING)) {
            return null;
        }

        String id = pdc.get(MUTATION_KEY, PersistentDataType.STRING);
        return getMutation(id);
    }
}
