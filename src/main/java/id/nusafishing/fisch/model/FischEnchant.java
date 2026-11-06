package id.nusafishing.fisch.model;

import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Data model for Fisch rod enchantments.
 * Enchants boost various fishing stats: luck, reel speed, max weight capacity, tension resilience, and mutation chance.
 */
public class FischEnchant {
    private final String id;
    private final String name;
    private final String description;
    private final int maxLevel;
    private final double bonusLuck;
    private final double bonusSpeed;
    private final double bonusWeight;
    private final double bonusResilience;
    private final double bonusMutationChance;

    public FischEnchant(String id, String name, String description, int maxLevel,
                        double bonusLuck, double bonusSpeed, double bonusWeight,
                        double bonusResilience, double bonusMutationChance) {
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description != null ? description : "";
        this.maxLevel = Math.max(1, maxLevel);
        this.bonusLuck = Math.max(0.0, bonusLuck);
        this.bonusSpeed = Math.max(0.0, bonusSpeed);
        this.bonusWeight = Math.max(0.0, bonusWeight);
        this.bonusResilience = Math.max(0.0, bonusResilience);
        this.bonusMutationChance = Math.max(0.0, bonusMutationChance);
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

    public int getMaxLevel() {
        return maxLevel;
    }

    public double getBonusLuck() {
        return bonusLuck;
    }

    public double getBonusSpeed() {
        return bonusSpeed;
    }

    public double getBonusWeight() {
        return bonusWeight;
    }

    public double getBonusResilience() {
        return bonusResilience;
    }

    public double getBonusMutationChance() {
        return bonusMutationChance;
    }

    /**
     * Calculates total luck boost at the given level.
     */
    public double getBonusLuck(int level) {
        return bonusLuck * Math.min(level, maxLevel);
    }

    /**
     * Calculates total speed boost at the given level.
     */
    public double getBonusSpeed(int level) {
        return bonusSpeed * Math.min(level, maxLevel);
    }

    /**
     * Calculates total weight capacity multiplier bonus at the given level.
     */
    public double getBonusWeight(int level) {
        return bonusWeight * Math.min(level, maxLevel);
    }

    /**
     * Calculates total resilience bonus at the given level.
     */
    public double getBonusResilience(int level) {
        return bonusResilience * Math.min(level, maxLevel);
    }

    /**
     * Calculates total mutation chance boost at the given level.
     */
    public double getBonusMutationChance(int level) {
        return bonusMutationChance * Math.min(level, maxLevel);
    }

    /**
     * Formats lore line for this enchant at the given level, e.g. "§9Lucky III".
     */
    public String formatLore(int level) {
        String levelStr = toRoman(level);
        return "§9" + name + " " + levelStr;
    }

    /**
     * Converts integer to Roman numeral up to 10.
     */
    public static String toRoman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            case 7: return "VII";
            case 8: return "VIII";
            case 9: return "IX";
            case 10: return "X";
            default: return String.valueOf(level);
        }
    }

    /**
     * Deserializes a FischEnchant from a ConfigurationSection.
     */
    public static FischEnchant fromConfiguration(String id, ConfigurationSection sec) {
        if (sec == null) return null;

        String name = sec.getString("name", id);
        String description = sec.getString("description", "");
        int maxLevel = sec.getInt("max_level", 5);
        double bonusLuck = sec.getDouble("bonus_luck", 0.0);
        double bonusSpeed = sec.getDouble("bonus_speed", 0.0);
        double bonusWeight = sec.getDouble("bonus_weight", 0.0);
        double bonusResilience = sec.getDouble("bonus_resilience", 0.0);
        double bonusMutationChance = sec.getDouble("bonus_mutation_chance", 0.0);

        return new FischEnchant(id, name, description, maxLevel,
                bonusLuck, bonusSpeed, bonusWeight, bonusResilience, bonusMutationChance);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FischEnchant that = (FischEnchant) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
