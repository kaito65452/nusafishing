package id.nusafishing.fishing.minigame;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.FishingListener;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingRod;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class StrikeMinigame {
    private final NusaFishing plugin;
    private final Map<UUID, BukkitTask> activeTasks;
    private final Map<UUID, CustomFish> pendingFish;
    private final Map<UUID, ItemStack> pendingRods;
    private final Map<UUID, Integer> pendingDurabilityLoss;
    
    // Minigame state data
    private final Map<UUID, Integer> requiredClicks;
    private final Map<UUID, Integer> currentClicks;
    
    private final Random random = new Random();
    
    // Constants for minigame
    private static final int MIN_REQUIRED_CLICKS = 8;
    private static final int MAX_REQUIRED_CLICKS = 15;
    private static final int MAX_TIME_SECONDS = 10;
    
    public StrikeMinigame(NusaFishing plugin) {
        this.plugin = plugin;
        this.activeTasks = new HashMap<>();
        this.pendingFish = new HashMap<>();
        this.pendingRods = new HashMap<>();
        this.pendingDurabilityLoss = new HashMap<>();
        this.requiredClicks = new HashMap<>();
        this.currentClicks = new HashMap<>();
    }
    
    /**
     * Start a new clicking minigame for player
     */
    public void startStrikeGame(Player player, CustomFish fish, ItemStack fishingRod, int durabilityLoss) {
        UUID playerId = player.getUniqueId();
        
        // Cancel existing game if any
        cancelActiveGame(player);
        
        // Store the fish and rod that will be used
        pendingFish.put(playerId, fish);
        pendingRods.put(playerId, fishingRod);
        pendingDurabilityLoss.put(playerId, durabilityLoss);
        
        // Calculate number of clicks based on fish weight
        int baseClicks = 8; // Basis minimal klik
        
        // Skala berdasarkan berat
        // Rumus: baseClicks + (weight ^ 0.5) / 2
        double weightFactor = Math.sqrt(fish.getWeight()) / 2;
        int clicks = baseClicks + (int)Math.ceil(weightFactor);
        
        // Batasi jumlah klik maksimal (tidak perlu terlalu banyak agar tidak membosankan)
        int maxClicks = 50;
        clicks = Math.min(clicks, maxClicks);
        
        // Simpan jumlah klik yang diperlukan
        requiredClicks.put(playerId, clicks);
        
        // Set starting click count
        currentClicks.put(playerId, 0);
        
        // Play splash sound
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);
        
        // Show instructions to player using title/subtitle
        String weightDisplay = fish.getFormattedWeight();
        player.sendTitle(
            "§a§lIkan menggigit! §f(" + weightDisplay + ")", 
            "§fKlik kiri untuk menarik ikan (§e" + clicks + " kali§f)", 
            10, 40, 10
        );
        
        // Display initial progress
        updateProgressDisplay(player);
        
        // Start countdown timer
        BukkitTask task = new BukkitRunnable() {
            private int secondsLeft = MAX_TIME_SECONDS;
            
            @Override
            public void run() {
                if (secondsLeft <= 0) {
                    // Time's up, fish got away
                    endMinigame(player, false);
                    return;
                }
                
                // Update countdown with current progress
                UUID playerId = player.getUniqueId();
                int current = currentClicks.getOrDefault(playerId, 0);
                int required = requiredClicks.getOrDefault(playerId, 10);
                
                // Build progress bar
                String progressBar = buildProgressBar(current, required);
                
                // Show progress in title and timer in subtitle
                String progressTitle = "§b" + current + "§f/§e" + required;
                String timerSubtitle = progressBar + " §7(" + secondsLeft + "s)";
                
                player.sendTitle(progressTitle, timerSubtitle, 0, 20, 5);
                
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
        
        activeTasks.put(playerId, task);
    }
    
    /**
     * Handle player left click to progress the minigame
     */
    public boolean handleClick(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Check if player is in an active minigame
        if (!isPlayerInMinigame(playerId)) {
            return false;
        }
        
        // Increment click count
        int currentClickCount = currentClicks.getOrDefault(playerId, 0) + 1;
        int requiredClicksCount = requiredClicks.getOrDefault(playerId, 10);
        
        // Check if player has completed the required clicks
        if (currentClickCount >= requiredClicksCount) {
            currentClicks.put(playerId, currentClickCount);
            // Success! Player caught the fish
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            endMinigame(player, true);
            return true;
        }

        currentClicks.put(playerId, currentClickCount);
        
        // Just update progress
        player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_STEP, 0.5f, 1.0f);
        updateProgressDisplay(player);
        return true;
    }
    
    /**
     * End the minigame with result
     */
    private void endMinigame(Player player, boolean success) {
        UUID playerId = player.getUniqueId();
        
        // Cancel active game task first to prevent concurrent timer expiration
        cancelActiveGame(player);

        // Get the pending data
        CustomFish fish = pendingFish.remove(playerId);
        ItemStack fishingRod = pendingRods.remove(playerId);
        Integer durabilityLoss = pendingDurabilityLoss.remove(playerId);
        
        // Clear minigame state
        requiredClicks.remove(playerId);
        currentClicks.remove(playerId);
        
        // Make sure to remove player's minigame metadata
        player.removeMetadata("NF_InMinigame", plugin);
        
        // Remove any active fishing hooks from the player to force re-casting
        removePlayerFishingHook(player);
        
        if (success && fish != null) {
            // Award the fish to player
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(fish.createItemStack());
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                player.sendMessage("§e§lPerhatian! §fInventory kamu penuh, ikan dijatuhkan ke tanah.");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            
            // Record the catch in statistics
            plugin.getStatisticsManager().recordFishCaught(player.getUniqueId(), fish);
            
            // Check for event fish catch
            if (fish.getId().startsWith("event_")) {
                plugin.getEventManager().handleEventFishCaught(player, fish);
            }

            // Record untuk turnamen juga jika sedang berlangsung
            if (plugin.getTournamentManager().isTournamentActive()) {
                plugin.getTournamentManager().recordCatch(player, fish);
            }
            
            // Use titles instead of messages
            player.sendTitle(
                "§a§lBerhasil!",
                "§fKamu mendapatkan §b" + fish.getName() + " §f(" + String.format("%.2f", fish.getWeight()) + " kg)", 
                10, 70, 20
            );
            
            // Add fishing XP based on fish quality and weight
            int xpGain = (int)(5 + (fish.getWeight() * fish.getQuality() / 20));
            plugin.getSkillManager().addFishingExp(player, xpGain);

            // Add AuraSkills XP if enabled
            plugin.getAuraSkillsHook().addFishingXp(player, fish);
            
            // Reduce durability on successful catch
            FishingRod customRod = null;
            if (fishingRod != null) {
                ItemMeta meta = fishingRod.getItemMeta();
                if (meta != null) {
                    NamespacedKey cleanRodKey = NamespacedKey.fromString("nusafishing:rod_id");
                    NamespacedKey dottedRodKey = NamespacedKey.fromString("nusafishing:nusafishing.rod_id");
                    String rodId = null;
                    if (cleanRodKey != null && meta.getPersistentDataContainer().has(cleanRodKey, PersistentDataType.STRING)) {
                        rodId = meta.getPersistentDataContainer().get(cleanRodKey, PersistentDataType.STRING);
                    } else if (dottedRodKey != null && meta.getPersistentDataContainer().has(dottedRodKey, PersistentDataType.STRING)) {
                        rodId = meta.getPersistentDataContainer().get(dottedRodKey, PersistentDataType.STRING);
                    }
                    if (rodId != null) {
                        customRod = plugin.getFishingManager().getRodById(rodId);
                    }
                }
                if (durabilityLoss != null) {
                    FishingListener.reduceDurability(player, fishingRod, durabilityLoss);
                }
            }

            // Consume stamina based on fish weight
            plugin.getStaminaManager().consumeStaminaForFish(player, fish, customRod);
        } else {
            // Fish got away
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            player.sendTitle("§c§lGagal!", "§fIkan berhasil lolos!", 10, 70, 20);
            
            // Reduce durability by 1 on failure (less than success)
            if (fishingRod != null) {
                FishingListener.reduceDurability(player, fishingRod, 1);
            }
        }
    }
    
    /**
     * Remove all fishing hooks from the player
     * @param player The player to remove hooks from
     */
    private void removePlayerFishingHook(Player player) {
        if (player == null) return;
        try {
            org.bukkit.entity.FishHook hook = player.getFishHook();
            if (hook != null && hook.isValid()) {
                hook.remove();
                return;
            }
        } catch (Throwable ignored) {
        }
        // Check all entities in the world for fishing hooks belonging to this player
        player.getWorld().getEntitiesByClass(org.bukkit.entity.FishHook.class).stream()
            .filter(hook -> hook.getShooter() instanceof Player)
            .filter(hook -> ((Player) hook.getShooter()).getUniqueId().equals(player.getUniqueId()))
            .forEach(org.bukkit.entity.Entity::remove);
    }
    
    /**
     * End the minigame when player aborts by opening inventory
     * @param player The player who opened inventory
     */
    public void endMinigameAbort(Player player) {
        // Fish got away - use false for failure
        endMinigame(player, false);
        
        // Send specific failure message about opening inventory
        player.playSound(player.getLocation(), Sound.ENTITY_SALMON_FLOP, 1.0f, 0.5f);
        player.sendTitle("§c§lGagal!", "§fIkan lolos karena kamu membuka inventory!", 10, 70, 20);
        
        // Remove fishing hook
        removePlayerFishingHook(player);
    }
    
    /**
     * Update progress display for player
     */
    private void updateProgressDisplay(Player player) {
        UUID playerId = player.getUniqueId();
        int current = currentClicks.getOrDefault(playerId, 0);
        int required = requiredClicks.getOrDefault(playerId, 10);
        
        String progressBar = buildProgressBar(current, required);
        
        // Use title/subtitle to show progress
        String progressTitle = "§b" + current + "§f/§e" + required;
        player.sendTitle(progressTitle, progressBar, 0, 15, 5);
    }
    
    /**
     * Helper method to build progress bar string
     */
    private String buildProgressBar(int current, int required) {
        StringBuilder progressBar = new StringBuilder();
        progressBar.append("§e[");
        
        int progressLength = 20; // 20 character progress bar
        int filledChars = (int) Math.floor((double) current / required * progressLength);
        
        for (int i = 0; i < progressLength; i++) {
            if (i < filledChars) {
                progressBar.append("§a|");
            } else {
                progressBar.append("§7|");
            }
        }
        progressBar.append("§e]");
        
        return progressBar.toString();
    }
    
    /**
     * Helper method to send action bar message
     */
    private void sendActionBarMessage(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
    
    /**
     * Cancel active game for player
     */
    private void cancelActiveGame(Player player) {
        UUID playerId = player.getUniqueId();
        
        // Cancel running task if any
        BukkitTask task = activeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
    
    /**
     * Check if player is in an active minigame
     */
    private boolean isPlayerInMinigame(UUID playerId) {
        return activeTasks.containsKey(playerId);
    }
    
    /**
     * Clean up resources when a player quits
     */
    public void cleanupPlayer(Player player) {
        cancelActiveGame(player);
        UUID playerId = player.getUniqueId();
        pendingFish.remove(playerId);
        pendingRods.remove(playerId);
        pendingDurabilityLoss.remove(playerId);
        requiredClicks.remove(playerId);
        currentClicks.remove(playerId);
        
        // Also make sure to remove player's minigame metadata
        player.removeMetadata("NF_InMinigame", plugin);
    }
    
    /**
     * Handle player right click actions (not used in current minigame implementation)
     * This is just a placeholder to prevent errors
     */
    public boolean handleStrike(Player player) {
        // The current minigame only uses left-clicks
        // This method is here just to satisfy references from FishingListener
        return false;
    }
}