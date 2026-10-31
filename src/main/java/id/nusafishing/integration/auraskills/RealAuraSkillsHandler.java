package id.nusafishing.integration.auraskills;

import org.bukkit.entity.Player;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;

/**
 * Concrete implementation of AuraSkillsHandler interacting with AuraSkills API.
 * This class is only loaded dynamically if AuraSkills is present and enabled.
 */
public class RealAuraSkillsHandler implements AuraSkillsHandler {
    private final NusaFishing plugin;

    public RealAuraSkillsHandler(NusaFishing plugin) {
        this.plugin = plugin;
    }

    @Override
    public void addFishingXp(Player player, CustomFish fish) {
        try {
            if (!Skills.FISHING.isEnabled()) {
                return;
            }

            AuraSkillsApi api = AuraSkillsApi.get();
            if (api == null) return;

            SkillsUser user = api.getUser(player.getUniqueId());
            if (user == null || !user.isLoaded()) {
                return;
            }

            double baseXp = plugin.getConfig().getDouble("auraskills.base_xp", 15.0);
            
            // Tier multiplier
            String tierKey = fish.getActualTier() != null ? fish.getActualTier().name() : "COMMON";
            double tierMultiplier = plugin.getConfig().getDouble("auraskills.tier_xp_multipliers." + tierKey, 1.0);
            
            // Weight & quality scaling
            double qualityFactor = Math.max(0.5, fish.getQuality() / 50.0);
            double weightFactor = Math.min(3.0, 1.0 + Math.log10(Math.max(1.0, fish.getWeight())));
            
            double totalXp = (baseXp + (weightFactor * qualityFactor * 2.0)) * tierMultiplier;
            
            user.addSkillXp(Skills.FISHING, totalXp);
        } catch (Throwable t) {
            plugin.getLogger().warning("Gagal menambahkan XP AuraSkills: " + t.getMessage());
        }
    }

    @Override
    public void addXp(Player player, double amount) {
        try {
            if (!Skills.FISHING.isEnabled()) {
                return;
            }

            AuraSkillsApi api = AuraSkillsApi.get();
            if (api == null) return;

            SkillsUser user = api.getUser(player.getUniqueId());
            if (user != null && user.isLoaded()) {
                user.addSkillXp(Skills.FISHING, amount);
            }
        } catch (Throwable ignored) {}
    }
}
