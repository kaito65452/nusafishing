package id.nusafishing.fisch.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Data model for fish mutations in Fisch mechanics.
 * Mutations apply visual styling, price scaling, weight boost, and special effects to caught fish.
 */
public class FischMutation {
    private final String id;
    private final String name;
    private final String prefix;
    private final String suffix;
    private final double priceMultiplier;
    private final double weightMultiplier;
    private final double chance; // Relative weight / probability chance
    private final String colorCode;
    private final String description;
    private final Map<String, Object> requiredConditions;

    public FischMutation(String id, String name, String prefix, String suffix,
                         double priceMultiplier, double weightMultiplier, double chance,
                         String colorCode, String description,
                         Map<String, Object> requiredConditions) {
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.prefix = prefix != null ? prefix : "";
        this.suffix = suffix != null ? suffix : "";
        this.priceMultiplier = Math.max(0.01, priceMultiplier);
        this.weightMultiplier = Math.max(0.01, weightMultiplier);
        this.chance = Math.max(0.0, chance);
        this.colorCode = colorCode != null && !colorCode.isBlank() ? colorCode : "§f";
        this.description = description != null ? description : "";
        this.requiredConditions = requiredConditions != null 
                ? Collections.unmodifiableMap(new HashMap<>(requiredConditions))
                : Collections.emptyMap();
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public double getPriceMultiplier() {
        return priceMultiplier;
    }

    public double getWeightMultiplier() {
        return weightMultiplier;
    }

    public double getChance() {
        return chance;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getRequiredConditions() {
        return requiredConditions;
    }

    /**
     * Formats the caught fish's name with prefix/suffix and mutation color.
     * Example: "§fAlbino §aKerapu Emas"
     */
    public String applyToName(String baseName) {
        StringBuilder sb = new StringBuilder();
        if (!prefix.isBlank()) {
            sb.append(colorCode).append(prefix.trim()).append(" ");
        }
        sb.append(baseName);
        if (!suffix.isBlank()) {
            sb.append(" ").append(colorCode).append(suffix.trim());
        }
        return sb.toString();
    }

    /**
     * Returns a badge representation e.g. "§f[Albino]".
     */
    public String getFormattedBadge() {
        return colorCode + "[" + name + "]";
    }

    /**
     * Verifies if this mutation's required conditions (weather, season, day/night, weight) are met.
     */
    public boolean meetsConditions(FischWeather weather, FischSeason season, boolean isNight, double fishWeight) {
        if (requiredConditions.isEmpty()) {
            return true;
        }

        // Check required weather
        Object reqWeather = requiredConditions.get("weather");
        if (reqWeather != null) {
            String wStr = reqWeather.toString().trim().toUpperCase();
            if (!wStr.equals("ANY") && weather != null && !weather.name().equalsIgnoreCase(wStr)) {
                return false;
            }
        }

        // Check required season
        Object reqSeason = requiredConditions.get("season");
        if (reqSeason != null) {
            String sStr = reqSeason.toString().trim().toUpperCase();
            if (!sStr.equals("ANY") && season != null && !season.name().equalsIgnoreCase(sStr)) {
                return false;
            }
        }

        // Check night condition
        Object reqNight = requiredConditions.get("night_only");
        if (reqNight instanceof Boolean && (Boolean) reqNight) {
            if (!isNight) {
                return false;
            }
        }

        // Check day condition
        Object reqDay = requiredConditions.get("day_only");
        if (reqDay instanceof Boolean && (Boolean) reqDay) {
            if (isNight) {
                return false;
            }
        }

        // Check minimum weight
        Object minWeightObj = requiredConditions.get("min_weight");
        if (minWeightObj instanceof Number) {
            double minWeight = ((Number) minWeightObj).doubleValue();
            if (fishWeight < minWeight) {
                return false;
            }
        }

        return true;
    }

    /**
     * Deserializes a FischMutation from a Bukkit ConfigurationSection.
     */
    public static FischMutation fromConfiguration(String id, ConfigurationSection sec) {
        if (sec == null) return null;

        String name = sec.getString("name", id);
        String prefix = sec.getString("prefix", name);
        String suffix = sec.getString("suffix", "");
        double priceMultiplier = sec.getDouble("price_multiplier", 1.0);
        double weightMultiplier = sec.getDouble("weight_multiplier", 1.0);
        double chance = sec.getDouble("chance", 0.05);
        String colorCode = sec.getString("color_code", "§f");
        String description = sec.getString("description", "");

        Map<String, Object> conditions = new HashMap<>();
        ConfigurationSection condSec = sec.getConfigurationSection("conditions");
        if (condSec != null) {
            for (String key : condSec.getKeys(false)) {
                conditions.put(key, condSec.get(key));
            }
        }

        return new FischMutation(id, name, prefix, suffix, priceMultiplier, weightMultiplier,
                chance, colorCode, description, conditions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FischMutation that = (FischMutation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "FischMutation{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", priceMultiplier=" + priceMultiplier +
                ", weightMultiplier=" + weightMultiplier +
                ", chance=" + chance +
                '}';
    }
}
