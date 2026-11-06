package id.nusafishing.fisch.model;

import java.util.Objects;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Data model for daily/recurrent Fisch missions.
 * Players complete fishing tasks (catch specific fish, tiers, counts) to earn Rods, Baits, Totems, EXP, or Money.
 */
public class FischMission {

    public enum RewardType {
        ROD("Pancingan"),
        BAIT("Umpan"),
        TOTEM("Totem"),
        EXP("EXP Memancing"),
        MONEY("Uang");

        private final String displayName;

        RewardType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static RewardType fromString(String name) {
            if (name == null || name.isBlank()) return MONEY;
            try {
                return valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return MONEY;
            }
        }
    }

    private final String id;
    private final String title;
    private final String description;
    private final int requiredFishCount;
    private final String requiredSpecies; // null or "ANY" if any species matches
    private final String requiredTier;    // null or "ANY" if any tier matches
    private final RewardType rewardType;
    private final String rewardId;        // specific rod/bait/totem id, or null for EXP/MONEY
    private final double rewardAmount;

    public FischMission(String id, String title, String description, int requiredFishCount,
                        String requiredSpecies, String requiredTier, RewardType rewardType,
                        String rewardId, double rewardAmount) {
        this.id = Objects.requireNonNull(id, "id cannot be null").toLowerCase();
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.description = description != null ? description : "";
        this.requiredFishCount = Math.max(1, requiredFishCount);
        this.requiredSpecies = (requiredSpecies == null || requiredSpecies.equalsIgnoreCase("ANY") || requiredSpecies.isBlank()) 
                ? null : requiredSpecies.trim().toLowerCase();
        this.requiredTier = (requiredTier == null || requiredTier.equalsIgnoreCase("ANY") || requiredTier.isBlank()) 
                ? null : requiredTier.trim().toUpperCase();
        this.rewardType = rewardType != null ? rewardType : RewardType.MONEY;
        this.rewardId = rewardId != null ? rewardId.trim().toLowerCase() : "";
        this.rewardAmount = Math.max(1.0, rewardAmount);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getRequiredFishCount() {
        return requiredFishCount;
    }

    public String getRequiredSpecies() {
        return requiredSpecies;
    }

    public String getRequiredTier() {
        return requiredTier;
    }

    public RewardType getRewardType() {
        return rewardType;
    }

    public String getRewardId() {
        return rewardId;
    }

    public double getRewardAmount() {
        return rewardAmount;
    }

    /**
     * Checks if a caught fish matches the conditions for this mission.
     */
    public boolean matchesFish(String fishId, String fishTier) {
        if (requiredSpecies != null) {
            if (fishId == null || !requiredSpecies.equalsIgnoreCase(fishId.trim())) {
                return false;
            }
        }
        if (requiredTier != null) {
            if (fishTier == null || !requiredTier.equalsIgnoreCase(fishTier.trim())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Formats the mission reward in human-readable text.
     */
    public String formatReward() {
        switch (rewardType) {
            case MONEY:
                return String.format("$%.0f", rewardAmount);
            case EXP:
                return String.format("%.0f EXP", rewardAmount);
            case ROD:
                return String.format("%.0fx Joran (%s)", rewardAmount, rewardId);
            case BAIT:
                return String.format("%.0fx Umpan (%s)", rewardAmount, rewardId);
            case TOTEM:
                return String.format("%.0fx Totem (%s)", rewardAmount, rewardId);
            default:
                return String.format("%.0f %s", rewardAmount, rewardType.getDisplayName());
        }
    }

    /**
     * Deserializes a FischMission from a ConfigurationSection.
     */
    public static FischMission fromConfiguration(String id, ConfigurationSection sec) {
        if (sec == null) return null;

        String title = sec.getString("title", id);
        String description = sec.getString("description", "");
        int requiredFishCount = sec.getInt("required_fish_count", 5);
        String requiredSpecies = sec.getString("required_species", null);
        String requiredTier = sec.getString("required_tier", null);
        RewardType rewardType = RewardType.fromString(sec.getString("reward_type", "MONEY"));
        String rewardId = sec.getString("reward_id", "");
        double rewardAmount = sec.getDouble("reward_amount", 100.0);

        return new FischMission(id, title, description, requiredFishCount,
                requiredSpecies, requiredTier, rewardType, rewardId, rewardAmount);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FischMission that = (FischMission) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
