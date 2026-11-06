package id.nusafishing.integration.auraskills;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;

/**
 * Safe manager for AuraSkills integration.
 * Prevents NoClassDefFoundError if AuraSkills is not installed on the server.
 * Automatically fallbacks to internal PlayerSkillManager when AuraSkills is unavailable.
 */
public class AuraSkillsHook {
    private final NusaFishing plugin;
    private boolean available = false;
    private AuraSkillsHandler handler = null;

    public AuraSkillsHook(NusaFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
    }

    public void checkAvailability() {
        handler = null;
        available = false;
        try {
            if (Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) {
                Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
                Class.forName("dev.aurelium.auraskills.api.skill.Skill");
                Class<?> handlerClass = Class.forName("id.nusafishing.integration.auraskills.RealAuraSkillsHandler");
                this.handler = (AuraSkillsHandler) handlerClass.getConstructor(NusaFishing.class).newInstance(plugin);
                available = true;
                plugin.getLogger().info("AuraSkills terdeteksi dan berhasil dihubungkan!");
            }
        } catch (Throwable t) {
            available = false;
            handler = null;
        }
    }

    public boolean isAvailable() {
        return available && handler != null;
    }

    public boolean isEnabledInConfig() {
        return plugin.getConfig().getBoolean("auraskills.enabled", true);
    }

    /**
     * Add AuraSkills fishing experience to player upon catching a fish.
     * Fallback automatically to internal PlayerSkillManager if absent.
     *
     * @param player The player who caught the fish
     * @param fish   The fish that was caught
     */
    public void addFishingXp(Player player, CustomFish fish) {
        if (player == null || fish == null) {
            return;
        }
        if (!isAvailable() || !isEnabledInConfig()) {
            if (plugin.getSkillManager() != null) {
                int xpGain = (int) (5 + (fish.getWeight() * fish.getQuality() / 20.0));
                plugin.getSkillManager().addFishingExp(player, xpGain);
            }
            return;
        }
        try {
            handler.addFishingXp(player, fish);
        } catch (Throwable t) {
            plugin.getLogger().warning("Error executing AuraSkills addFishingXp: " + t.getMessage());
            if (plugin.getSkillManager() != null) {
                int xpGain = (int) (5 + (fish.getWeight() * fish.getQuality() / 20.0));
                plugin.getSkillManager().addFishingExp(player, xpGain);
            }
        }
    }

    /**
     * Add direct AuraSkills fishing experience (e.g. from tournaments).
     */
    public void addXp(Player player, double amount) {
        if (player == null || amount <= 0) {
            return;
        }
        if (!isAvailable() || !isEnabledInConfig()) {
            if (plugin.getSkillManager() != null) {
                plugin.getSkillManager().addFishingExp(player, (int) Math.round(amount));
            }
            return;
        }
        try {
            handler.addXp(player, amount);
        } catch (Throwable ignored) {
            if (plugin.getSkillManager() != null) {
                plugin.getSkillManager().addFishingExp(player, (int) Math.round(amount));
            }
        }
    }

    /**
     * Get player's AuraSkills fishing level by UUID.
     */
    public int getAuraFishingLevel(java.util.UUID uuid) {
        if (uuid == null) return 0;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return getFishingLevel(player);
        }
        return 0;
    }

    /**
     * Get fishing level with seamless fallback.
     */
    public int getFishingLevel(Player player) {
        if (player == null) return 1;
        if (isAvailable() && isEnabledInConfig()) {
            try {
                return handler.getFishingLevel(player);
            } catch (Throwable ignored) {}
        }
        if (plugin.getSkillManager() != null) {
            return plugin.getSkillManager().getFishingLevel(player);
        }
        return 1;
    }
}
