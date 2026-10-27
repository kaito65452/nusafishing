package id.nusafishing.placeholder;

import java.util.Map;
import java.util.UUID;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.data.FishingStatisticsManager;

public class NusaFishingPlaceholders extends PlaceholderExpansion {
    private final NusaFishing plugin;
    private final FishingStatisticsManager statsManager;
    
    public NusaFishingPlaceholders(NusaFishing plugin) {
        this.plugin = plugin;
        this.statsManager = plugin.getStatisticsManager();
    }
    
    @Override
    public @NotNull String getIdentifier() {
        return "nusafishing";
    }
    
    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }
    
    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }
    
    @Override
    public boolean persist() {
        return true; // Tetap terdaftar sampai server mati
    }
    
    @Override
    public boolean canRegister() {
        return true;
    }
    
    @Override
    public String onRequest(OfflinePlayer player, @NotNull String identifier) {
        // Statistik untuk pemain individu
        if (player != null) {
            // Jumlah ikan yang tertangkap
            if (identifier.equals("fish_caught")) {
                return String.valueOf(statsManager.getTotalFishCaught(player.getUniqueId()));
            }
            
            if (identifier.equals("legendary_caught")) {
                return String.valueOf(statsManager.getLegendaryFishCaught(player.getUniqueId()));
            }

            if (identifier.equals("mythic_caught")) {
                return String.valueOf(statsManager.getMythicFishCaught(player.getUniqueId()));
            }
            
            if (identifier.equals("event_fish_caught")) {
                return String.valueOf(statsManager.getEventFishCaught(player.getUniqueId()));
            }
            
            // Experience dan level
            if (identifier.equals("fishing_level")) {
                return String.valueOf(plugin.getSkillManager().getFishingLevel(player.getUniqueId()));
            }
            
            if (identifier.equals("fishing_exp")) {
                return String.valueOf(plugin.getSkillManager().getFishingExperience(player.getUniqueId()));
            }

            // Stamina
            if (identifier.equals("stamina")) {
                return String.format("%.1f", plugin.getStaminaManager().getStamina(player.getUniqueId()));
            }

            if (identifier.equals("max_stamina")) {
                return String.format("%.1f", plugin.getStaminaManager().getMaxStamina(player.getUniqueId()));
            }
            
            // Ikan terbesar
            if (identifier.equals("largest_fish_weight")) {
                return String.format("%.2f", statsManager.getLargestFishWeight(player.getUniqueId()));
            }
            
            if (identifier.equals("largest_fish_name")) {
                return statsManager.getLargestFishName(player.getUniqueId());
            }
        }
        
        // Global/leaderboard placeholders
        if (identifier.startsWith("top_fisher_")) {
            // Fix for top_fisher_count_X placeholders
            if (identifier.startsWith("top_fisher_count_")) {
                try {
                    String rankStr = identifier.substring("top_fisher_count_".length());
                    int rank = Integer.parseInt(rankStr.trim());
                    Map.Entry<UUID, Integer> entry = statsManager.getTopFisher(rank);
                    if (entry != null) {
                        return String.valueOf(entry.getValue());
                    }
                    return "0";
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid rank format in placeholder: " + identifier);
                    return "0";
                }
            } else {
                // Regular top_fisher_X placeholders
                try {
                    String rankStr = identifier.substring("top_fisher_".length());
                    int rank = Integer.parseInt(rankStr.trim());
                    Map.Entry<UUID, Integer> entry = statsManager.getTopFisher(rank);
                    if (entry != null) {
                        String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                        return playerName != null ? playerName : "Unknown";
                    }
                    return "Tidak Ada";
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid rank format in placeholder: " + identifier);
                    return "Tidak Ada";
                }
            }
        }
        
        if (identifier.startsWith("top_largest_fish_weight_")) {
            try {
                String rankStr = identifier.substring("top_largest_fish_weight_".length());
                int rank = Integer.parseInt(rankStr.trim());
                Map.Entry<UUID, Double> entry = statsManager.getTopLargestFish(rank);
                if (entry != null) {
                    return String.format("%.2f", entry.getValue());
                }
                return "0.0";
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid rank format in placeholder: " + identifier);
                return "0.0";
            }
        }
        
        if (identifier.startsWith("top_largest_fish_")) {
            try {
                int rank = Integer.parseInt(identifier.substring("top_largest_fish_".length()));
                Map.Entry<UUID, Double> entry = statsManager.getTopLargestFish(rank);
                if (entry != null) {
                    String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    return playerName != null ? playerName : "Unknown";
                }
                return "Tidak Ada";
            } catch (NumberFormatException e) {
                return "Rank Invalid";
            }
        }
        
        return null; // Placeholder tidak dikenali
    }
}