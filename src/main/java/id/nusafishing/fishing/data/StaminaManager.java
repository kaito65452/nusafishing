package id.nusafishing.fishing.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.fishing.model.CustomFish;

public class StaminaManager {
    private final NusaFishing plugin;
    private final Map<UUID, Double> playerStamina;
    private BukkitTask globalRegenTask;
    
    private double maxStamina = 100.0;
    private double regenAmount = 1.5;
    private long regenDelay = 20L;
    
    public StaminaManager(NusaFishing plugin) {
        this.plugin = plugin;
        this.playerStamina = new HashMap<>();
        loadConfig();
        startGlobalRegeneration();
    }

    public void loadConfig() {
        if (plugin.getConfig() != null) {
            this.maxStamina = plugin.getConfig().getDouble("player.max_stamina", 100.0);
            this.regenAmount = plugin.getConfig().getDouble("player.stamina_regen", 1.5);
            this.regenDelay = Math.max(1L, plugin.getConfig().getLong("player.stamina_regen_delay", 20L));
        }
        startGlobalRegeneration();
    }
    
    public void initializePlayer(Player player) {
        UUID playerId = player.getUniqueId();
        // Preserve stamina across reconnects, default to maxStamina for new players
        playerStamina.putIfAbsent(playerId, maxStamina);
    }
    
    public void removePlayer(Player player) {
        // Keep stamina in playerStamina cache so reconnecting does not exploit free 100% stamina
    }
    
    public void shutdown() {
        if (globalRegenTask != null) {
            globalRegenTask.cancel();
            globalRegenTask = null;
        }
    }
    
    public boolean hasEnoughStamina(Player player, double amount) {
        return getStamina(player) >= amount;
    }
    
    public void consumeStamina(Player player, double amount, FishingRod rod) {
        UUID playerId = player.getUniqueId();
        double staminaCost = amount;
        
        // Apply rod's stamina modifier if present
        if (rod != null) {
            staminaCost *= rod.getStaminaModifier();
        }
        
        double currentStamina = playerStamina.getOrDefault(playerId, maxStamina);
        double newStamina = Math.max(0, currentStamina - staminaCost);
        
        playerStamina.put(playerId, newStamina);
        
        // Show action bar message only when stamina is low/depleted
        if (newStamina <= 20) {
            String staminaMessage;
            if (newStamina <= 0) {
                staminaMessage = "§c§lStamina habis! §cTunggu stamina regenerasi...";
            } else {
                staminaMessage = "§e§lStamina rendah! §e" + String.format("%.1f", newStamina) + "%";
            }
            sendActionBar(player, staminaMessage);
        }
    }
    
    public void restoreStamina(Player player, double amount) {
        UUID playerId = player.getUniqueId();
        double currentStamina = playerStamina.getOrDefault(playerId, maxStamina);
        double newStamina = Math.min(maxStamina, currentStamina + amount);
        
        playerStamina.put(playerId, newStamina);
    }
    
    public double getStamina(Player player) {
        if (player == null) return maxStamina;
        return getStamina(player.getUniqueId());
    }

    public double getStamina(UUID playerId) {
        if (playerId == null) return maxStamina;
        return playerStamina.getOrDefault(playerId, maxStamina);
    }

    public double getMaxStamina() {
        return maxStamina;
    }

    public double getMaxStamina(UUID playerId) {
        return maxStamina;
    }
    
    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
    
    private void startGlobalRegeneration() {
        if (globalRegenTask != null) {
            globalRegenTask.cancel();
        }
        
        globalRegenTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID playerId = player.getUniqueId();
                    double stamina = playerStamina.getOrDefault(playerId, maxStamina);
                    if (stamina < maxStamina) {
                        restoreStamina(player, regenAmount);
                        double clampedStamina = Math.min(maxStamina, stamina + regenAmount);
                        
                        // Show recovery message if stamina is still low
                        if (clampedStamina < 20) {
                            sendActionBar(player, "§e§lStamina: §e" + String.format("%.1f", clampedStamina) + "%");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, regenDelay, regenDelay);
    }
    
    /**
     * Konsumsi stamina berdasarkan berat ikan yang ditangkap
     * @param player Pemain yang menangkap ikan
     * @param fish Ikan yang ditangkap
     * @param rod Pancingan yang digunakan
     */
    public void consumeStaminaForFish(Player player, CustomFish fish, FishingRod rod) {
        // Base stamina cost is 10 + weight-based cost
        double fishWeight = fish.getWeight();
        double weightFactor;
        
        // Skala berdasarkan berat
        if (fishWeight < 10) {
            weightFactor = fishWeight / 2; // 0-5 stamina
        } else if (fishWeight < 100) {
            weightFactor = 5 + (fishWeight - 10) / 10; // 5-14 stamina
        } else if (fishWeight < 1000) {
            weightFactor = 15 + (fishWeight - 100) / 100; // 15-24 stamina
        } else {
            weightFactor = 25 + (fishWeight - 1000) / 1000; // 25+ stamina
        }
        
        // Cap the base stamina cost at 50 to avoid excessive penalties
        double staminaCost = Math.min(50, 10 + weightFactor);
        
        // Consume stamina (consumeStamina applies rod's stamina modifier once)
        consumeStamina(player, staminaCost, rod);
        
        // Notify player if significant stamina loss
        double actualCost = (rod != null) ? (staminaCost * rod.getStaminaModifier()) : staminaCost;
        if (actualCost > 20) {
            player.sendMessage("§e§lMelelahkan! §fMenangkap ikan seberat §e" + 
                fish.getFormattedWeight() + " §fmenguras §e" + 
                String.format("%.1f", actualCost) + " §fstamina.");
        }
    }
}