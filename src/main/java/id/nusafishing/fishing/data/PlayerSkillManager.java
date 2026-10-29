package id.nusafishing.fishing.data;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import id.nusafishing.NusaFishing;

public class PlayerSkillManager {
    private final NusaFishing plugin;
    private final Map<UUID, Integer> fishingLevels;
    private final Map<UUID, Integer> fishingExperience;
    private final File dataFile;
    private final FileConfiguration dataConfig;
    
    public PlayerSkillManager(NusaFishing plugin) {
        this.plugin = plugin;
        this.fishingLevels = new HashMap<>();
        this.fishingExperience = new HashMap<>();
        
        this.dataFile = new File(plugin.getDataFolder(), "player_data.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player_data.yml: " + e.getMessage());
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }
    
    public void loadPlayerData(Player player) {
        UUID playerId = player.getUniqueId();
        String path = playerId.toString();
        
        int level = dataConfig.getInt(path + ".fishing_level", 1);
        int exp = dataConfig.getInt(path + ".fishing_exp", 0);
        
        fishingLevels.put(playerId, level);
        fishingExperience.put(playerId, exp);
    }
    
    public void savePlayerData(Player player) {
        UUID playerId = player.getUniqueId();
        String path = playerId.toString();
        
        dataConfig.set(path + ".name", player.getName());
        dataConfig.set(path + ".fishing_level", fishingLevels.getOrDefault(playerId, 1));
        dataConfig.set(path + ".fishing_exp", fishingExperience.getOrDefault(playerId, 0));
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player data for " + player.getName() + ": " + e.getMessage());
        }
    }
    
    public void saveAllData() {
        Map<UUID, Integer> levelsSnapshot = new HashMap<>(fishingLevels);
        Map<UUID, Integer> expSnapshot = new HashMap<>(fishingExperience);
        for (UUID playerId : levelsSnapshot.keySet()) {
            String path = playerId.toString();
            dataConfig.set(path + ".fishing_level", levelsSnapshot.get(playerId));
            dataConfig.set(path + ".fishing_exp", expSnapshot.get(playerId));
        }
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player data: " + e.getMessage());
        }
    }
    
    public int getFishingLevel(Player player) {
        return fishingLevels.getOrDefault(player.getUniqueId(), 1);
    }
    
    public int getFishingExperience(Player player) {
        return fishingExperience.getOrDefault(player.getUniqueId(), 0);
    }
    
    public int getFishingLevel(UUID playerId) {
        if (fishingLevels.containsKey(playerId)) {
            return fishingLevels.get(playerId);
        }
        return dataConfig.getInt(playerId.toString() + ".fishing_level", 1);
    }

    public int getFishingExperience(UUID playerId) {
        if (fishingExperience.containsKey(playerId)) {
            return fishingExperience.get(playerId);
        }
        return dataConfig.getInt(playerId.toString() + ".fishing_exp", 0);
    }
    
    public void addFishingExp(Player player, int expAmount) {
        if (player == null || expAmount <= 0) return;
        UUID playerId = player.getUniqueId();
        int currentExp = fishingExperience.getOrDefault(playerId, 0);
        int currentLevel = fishingLevels.getOrDefault(playerId, 1);
        
        int newExp = currentExp + expAmount;
        int expForNextLevel = getExpForNextLevel(currentLevel);
        
        int initialLevel = currentLevel;

        // Check for level up
        while (newExp >= expForNextLevel) {
            newExp -= expForNextLevel;
            currentLevel++;
            expForNextLevel = getExpForNextLevel(currentLevel);
        }

        fishingExperience.put(playerId, newExp);
        fishingLevels.put(playerId, currentLevel);

        // Send consolidated level up message and sound once to avoid spam
        if (currentLevel > initialLevel) {
            player.sendMessage("§a§lLevel Naik! §fSkill memancing kamu naik menjadi level §e" + currentLevel + "§f!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        
        // Inform player about exp gain
        player.sendMessage("§b+" + expAmount + " Exp Memancing §7(" + newExp + "/" + expForNextLevel + ")");
    }
    
    private int getExpForNextLevel(int currentLevel) {
        // Simple formula: 100 * level
        return 100 * currentLevel;
    }
    
    // Get fishing success chance based on player level (0.0 - 1.0)
    public double getFishingSuccessChance(Player player) {
        int level = getFishingLevel(player);
        // Base 50% + 0.5% per level, max 95% at level 90
        return Math.min(0.95, 0.50 + 0.005 * level);
    }
    
    // Get fish quality modifier based on player level (1.0 - 2.0)
    public double getQualityModifier(Player player) {
        int level = getFishingLevel(player);
        // 1.0 + 0.01 per level, max 2.0 at level 100
        return Math.min(2.0, 1.0 + 0.01 * level);
    }
}