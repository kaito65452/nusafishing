package id.nusafishing.integration.auraskills;

import org.bukkit.entity.Player;
import id.nusafishing.fishing.model.CustomFish;

/**
 * Interface decoupling AuraSkills API calls from core plugin loading.
 */
public interface AuraSkillsHandler {

    /**
     * Add AuraSkills fishing experience to player upon catching a fish.
     *
     * @param player The player who caught the fish
     * @param fish   The fish that was caught
     */
    void addFishingXp(Player player, CustomFish fish);

    /**
     * Add direct AuraSkills fishing experience (e.g. from tournaments).
     *
     * @param player The player to receive experience
     * @param amount The experience amount
     */
    void addXp(Player player, double amount);

    /**
     * Get player's AuraSkills fishing level.
     */
    int getFishingLevel(Player player);
}
