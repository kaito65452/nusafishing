package id.nusafishing.fisch.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import id.nusafishing.compat.CustomModelDataHelper;

/**
 * Data model for Fisch Totems.
 * Totems can be placed or activated to alter world weather/season and grant potent fishing luck and mutation boosts.
 */
public class FischTotem {
    public static final NamespacedKey TOTEM_KEY = NamespacedKey.fromString("nusafishing:totem_id");

    public enum TotemEffectType {
        WIND_TOTEM("Totem Angin", FischWeather.WINDY, null, 0.25, 0.15),
        TEMPEST_TOTEM("Totem Badai", FischWeather.STORM, null, 0.35, 0.30),
        SUNDIAL_TOTEM("Totem Jam Matahari", FischWeather.CLEAR, null, 0.15, 0.05),
        SMOKESCREEN_TOTEM("Totem Kabut Asap", FischWeather.FOG, null, 0.20, 0.25),
        AURORA_TOTEM("Totem Aurora", FischWeather.AURORA, null, 0.75, 1.00),
        ECLIPSE_TOTEM("Totem Gerhana", FischWeather.ECLIPSE, null, 1.00, 1.50);

        private final String defaultName;
        private final FischWeather defaultWeather;
        private final FischSeason defaultSeason;
        private final double defaultLuckBoost;
        private final double defaultMutationBoost;

        TotemEffectType(String defaultName, FischWeather defaultWeather, FischSeason defaultSeason,
                        double defaultLuckBoost, double defaultMutationBoost) {
            this.defaultName = defaultName;
            this.defaultWeather = defaultWeather;
            this.defaultSeason = defaultSeason;
            this.defaultLuckBoost = defaultLuckBoost;
            this.defaultMutationBoost = defaultMutationBoost;
        }

        public String getDefaultName() {
            return defaultName;
        }

        public FischWeather getDefaultWeather() {
            return defaultWeather;
        }

        public FischSeason getDefaultSeason() {
            return defaultSeason;
        }

        public double getDefaultLuckBoost() {
            return defaultLuckBoost;
        }

        public double getDefaultMutationBoost() {
            return defaultMutationBoost;
        }

        public static TotemEffectType fromString(String name) {
            if (name == null || name.isBlank()) return WIND_TOTEM;
            try {
                return valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return WIND_TOTEM;
            }
        }
    }

    private final String id;
    private final String name;
    private final String description;
    private final int durationSeconds;
    private final double cost;
    private final TotemEffectType effectType;
    private final FischWeather weatherTarget;
    private final FischSeason seasonTarget;
    private final double luckBoost;
    private final double mutationBoost;
    private final Material material;
    private final int customModelData;

    public FischTotem(String id, String name, String description, int durationSeconds, double cost,
                      TotemEffectType effectType, FischWeather weatherTarget, FischSeason seasonTarget,
                      double luckBoost, double mutationBoost, Material material, int customModelData) {
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description != null ? description : "";
        this.durationSeconds = Math.max(10, durationSeconds);
        this.cost = Math.max(0.0, cost);
        this.effectType = effectType != null ? effectType : TotemEffectType.WIND_TOTEM;
        this.weatherTarget = weatherTarget != null ? weatherTarget : this.effectType.getDefaultWeather();
        this.seasonTarget = seasonTarget;
        this.luckBoost = luckBoost >= 0 ? luckBoost : this.effectType.getDefaultLuckBoost();
        this.mutationBoost = mutationBoost >= 0 ? mutationBoost : this.effectType.getDefaultMutationBoost();
        this.material = material != null ? material : Material.TOTEM_OF_UNDYING;
        this.customModelData = customModelData;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public double getCost() {
        return cost;
    }

    public TotemEffectType getEffectType() {
        return effectType;
    }

    public FischWeather getWeatherTarget() {
        return weatherTarget;
    }

    public FischSeason getSeasonTarget() {
        return seasonTarget;
    }

    public double getLuckBoost() {
        return luckBoost;
    }

    public double getMutationBoost() {
        return mutationBoost;
    }

    public Material getMaterial() {
        return material;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    /**
     * Creates an ItemStack representing this Fisch Totem.
     */
    public ItemStack createItemStack(int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = new ArrayList<>();
            if (!description.isBlank()) {
                lore.add("§7" + ChatColor.translateAlternateColorCodes('&', description));
                lore.add("");
            }
            lore.add("§6Efek Totem:");
            if (weatherTarget != null) {
                lore.add(" §7• Cuaca: " + weatherTarget.getFormattedName());
            }
            if (seasonTarget != null) {
                lore.add(" §7• Musim: " + seasonTarget.getFormattedName());
            }
            if (luckBoost > 0) {
                lore.add(String.format(" §7• Bonus Luck: §a+%.0f%%", luckBoost * 100.0));
            }
            if (mutationBoost > 0) {
                lore.add(String.format(" §7• Bonus Mutasi: §d+%.0f%%", mutationBoost * 100.0));
            }
            int minutes = durationSeconds / 60;
            int seconds = durationSeconds % 60;
            String durStr = seconds == 0 ? minutes + " menit" : durationSeconds + " detik";
            lore.add(" §7• Durasi: §e" + durStr);
            lore.add("");
            lore.add("§eKlik kanan §7untuk mengaktifkan totem di world ini.");

            meta.setLore(lore);

            if (customModelData > 0) {
                CustomModelDataHelper.applyCustomModelData(meta, customModelData);
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

            // Store totem id in PDC
            if (TOTEM_KEY != null) {
                meta.getPersistentDataContainer().set(TOTEM_KEY, PersistentDataType.STRING, id);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Deserializes a FischTotem from a ConfigurationSection.
     */
    public static FischTotem fromConfiguration(String id, ConfigurationSection sec) {
        if (sec == null) return null;

        String name = sec.getString("name", "Totem");
        String description = sec.getString("description", "");
        int durationSeconds = sec.getInt("duration_seconds", 300);
        double cost = sec.getDouble("cost", 1000.0);
        TotemEffectType effectType = TotemEffectType.fromString(sec.getString("effect_type", "WIND_TOTEM"));

        String weatherStr = sec.getString("weather_target", null);
        FischWeather weatherTarget = weatherStr != null ? FischWeather.fromString(weatherStr) : effectType.getDefaultWeather();

        String seasonStr = sec.getString("season_target", null);
        FischSeason seasonTarget = seasonStr != null ? FischSeason.fromString(seasonStr) : null;

        double luckBoost = sec.getDouble("luck_boost", effectType.getDefaultLuckBoost());
        double mutationBoost = sec.getDouble("mutation_boost", effectType.getDefaultMutationBoost());

        String matName = sec.getString("material", "TOTEM_OF_UNDYING");
        Material material;
        try {
            material = Material.valueOf(matName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.TOTEM_OF_UNDYING;
        }

        int customModelData = sec.getInt("custom_model_data", 0);

        return new FischTotem(id, name, description, durationSeconds, cost, effectType,
                weatherTarget, seasonTarget, luckBoost, mutationBoost, material, customModelData);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FischTotem that = (FischTotem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
