package id.nusafishing.managers;

import id.nusafishing.NusaFishing;
import id.nusafishing.config.ConfigSync;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class RewardsManager {
    private final NusaFishing plugin;
    private FileConfiguration rewardsConfig;
    private File rewardsFile;
    
    public RewardsManager(NusaFishing plugin) {
        this.plugin = plugin;
        // Setup config
        loadRewardsConfig();
    }
    
    public void loadRewardsConfig() {
        if (rewardsFile == null) {
            rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        }
        
        // Create file if it doesn't exist
        if (!rewardsFile.exists()) {
            plugin.saveResource("rewards.yml", false);
        }
        
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);

        // Sinkronisasi key bawaan ke file fisik di disk
        ConfigSync.sync(plugin, rewardsFile, rewardsConfig, "rewards.yml");
    }
    
    public FileConfiguration getRewardsConfig() {
        if (rewardsConfig == null) {
            loadRewardsConfig();
        }
        return rewardsConfig;
    }
    
    public void saveRewardsConfig() {
        if (rewardsConfig == null || rewardsFile == null) {
            return;
        }
        
        try {
            getRewardsConfig().save(rewardsFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Tidak dapat menyimpan rewards config ke " + rewardsFile);
        }
    }
    
    public void reloadRewardsConfig() {
        loadRewardsConfig();
        plugin.getLogger().info("Konfigurasi rewards berhasil dimuat ulang!");
    }
    
    /**
     * Berikan hadiah pembuatan lokasi kepada pemain
     * @param player Pemain yang membuat lokasi
     * @param locationId ID lokasi yang dibuat
     * @param locationName Nama lokasi yang dibuat
     * @param radius Radius lokasi
     * @param x Koordinat X
     * @param z Koordinat Z
     */
    public void giveLocationCreationRewards(Player player, String locationId, String locationName, double radius, double x, double z) {
        if (!rewardsConfig.getBoolean("settings.enable_rewards", true)) {
            return;
        }
        
        // Berikan hadiah default
        giveRewardFromSection(player, "admin_rewards.default", locationId, locationName, x, z);
        
        // Berikan hadiah berdasarkan ukuran
        String sizeCategory = getSizeCategory(radius);
        giveRewardFromSection(player, "admin_rewards.by_size." + sizeCategory, locationId, locationName, x, z);
        
        // Tangani hadiah server-wide jika diaktifkan
        if (rewardsConfig.getBoolean("server_rewards.enable", true)) {
            String announceMsg = rewardsConfig.getString("server_rewards.announce_message", "");
            if (!announceMsg.isEmpty()) {
                announceMsg = replaceVariables(announceMsg, player.getName(), locationId, locationName, x, z);
                Bukkit.broadcastMessage(announceMsg);
            }
            
            // Eksekusi perintah server-wide
            List<String> commands = rewardsConfig.getStringList("server_rewards.broadcast_commands");
            for (String cmd : commands) {
                cmd = replaceVariables(cmd, player.getName(), locationId, locationName, x, z);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }
    
    /**
     * Tentukan kategori ukuran lokasi berdasarkan radius
     */
    private String getSizeCategory(double radius) {
        if (radius < 30) {
            return "small";
        } else if (radius < 80) {
            return "medium";
        } else {
            return "large";
        }
    }
    
    /**
     * Berikan hadiah dari bagian konfigurasi tertentu
     */
    private void giveRewardFromSection(Player player, String sectionPath, String locationId, String locationName, double x, double z) {
        ConfigurationSection section = rewardsConfig.getConfigurationSection(sectionPath);
        if (section == null) {
            return;
        }
        
        // Eksekusi perintah
        List<String> commands = section.getStringList("commands");
        for (String cmd : commands) {
            cmd = replaceVariables(cmd, player.getName(), locationId, locationName, x, z);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        
        // Berikan hadiah uang jika ekonomi diaktifkan
        double money = section.getDouble("money", 0);
        if (money > 0 && plugin.getEconomyManager().isEconomyEnabled()) {
            plugin.getEconomyManager().depositMoney(player, money);
            player.sendMessage("§a+" + plugin.getEconomyManager().formatMoney(money) + " §7(Reward lokasi memancing)");
        }
        
        // Berikan poin pengalaman
        int exp = section.getInt("exp", 0);
        if (exp > 0) {
            plugin.getSkillManager().addFishingExp(player, exp);
            player.sendMessage("§b+" + exp + " §7EXP Memancing (Reward lokasi)");
        }
    }
    
    /**
     * Ganti variabel placeholder dalam string perintah
     */
    private String replaceVariables(String text, String playerName, String locationId, String locationName, double x, double z) {
        return text.replace("%player%", playerName)
                  .replace("%admin%", playerName)
                  .replace("%location_id%", locationId)
                  .replace("%location_name%", locationName)
                  .replace("%x%", String.valueOf((int)x))
                  .replace("%z%", String.valueOf((int)z));
    }
}