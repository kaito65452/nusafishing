package id.nusafishing.integration.auraskills;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;

/**
 * Safe manager for AuraSkills integration.
 * Prevents NoClassDefFoundError if AuraSkills is not installed on the server.
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
     *
     * @param player The player who caught the fish
     * @param fish   The fish that was caught
     */
    public void addFishingXp(Player player, CustomFish fish) {
        if (!isAvailable() || !isEnabledInConfig() || player == null || fish == null) {
            return;
        }
        try {
            handler.addFishingXp(player, fish);
        } catch (Throwable t) {
            plugin.getLogger().warning("Error executing AuraSkills addFishingXp: " + t.getMessage());
        }
    }

    /**
     * Add direct AuraSkills fishing experience (e.g. from tournaments).
     */
    public void addXp(Player player, double amount) {
        if (!isAvailable() || !isEnabledInConfig() || player == null || amount <= 0) {
            return;
        }
        try {
            handler.addXp(player, amount);
        } catch (Throwable ignored) {}
    }
}
