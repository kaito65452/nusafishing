package id.nusafishing.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;

public class FishingEventManager {
    private final NusaFishing plugin;
    private final Random random = new Random();
    
    // Event state
    private boolean eventActive = false;
    private EventType activeEventType = null;
    private long eventStartTime = 0;
    private long eventEndTime = 0;
    private long lastEventTime = 0;
    private UUID winnerUUID = null;
    private String winnerName = null;
    private String caughtFishName = null;
    private double caughtFishWeight = 0;
    
    // Event settings (configurable)
    private int minEventCooldownMinutes = 30; // Minimum time between events
    private int maxEventCooldownMinutes = 120; // Maximum time between events
    private int eventDurationMinutes = 30; // How long events last
    private double eventChancePercent = 10; // Chance for an event to occur when checked
    
    // Valid biomes for events
    private final List<Biome> validBiomes = new ArrayList<>();
    
    // Scheduled tasks
    private BukkitTask eventCheckTask = null;
    private BukkitTask eventEndTask = null;
    
    // Biome to event type mapping
    private final Map<Biome, List<EventType>> biomeEvents = new HashMap<>();
    
    public FishingEventManager(NusaFishing plugin) {
        this.plugin = plugin;
        loadConfiguration();
        setupBiomeEvents();
        startEventChecker();
    }
    
    /**
     * Load event configuration from config file
     */
    private void loadConfiguration() {
        // Load from config
        minEventCooldownMinutes = plugin.getConfig().getInt("events.min_cooldown_minutes", 30);
        maxEventCooldownMinutes = plugin.getConfig().getInt("events.max_cooldown_minutes", 120);
        eventDurationMinutes = plugin.getConfig().getInt("events.duration_minutes", 30);
        eventChancePercent = plugin.getConfig().getDouble("events.chance_percent", 10.0);
        
        // Load valid biomes from config or use defaults
        List<String> biomeNames = plugin.getConfig().getStringList("events.valid_biomes");
        if (biomeNames.isEmpty()) {
            // Default water biomes if not configured
            biomeNames = Arrays.asList(
                "OCEAN", "DEEP_OCEAN", "WARM_OCEAN", "LUKEWARM_OCEAN", 
                "COLD_OCEAN", "DEEP_WARM_OCEAN", "DEEP_LUKEWARM_OCEAN", 
                "DEEP_COLD_OCEAN", "FROZEN_OCEAN", "DEEP_FROZEN_OCEAN",
                "RIVER", "FROZEN_RIVER", "BEACH", "SNOWY_BEACH"
            );
        }
        
        // Convert string names to Biome enums
        for (String biomeName : biomeNames) {
            if (biomeName == null || biomeName.isBlank()) continue;
            try {
                Biome biome = Biome.valueOf(biomeName.trim().toUpperCase());
                validBiomes.add(biome);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid biome in config: " + biomeName);
            }
        }
        
        plugin.getLogger().info("Fishing event system configured with " + validBiomes.size() + " valid biomes");
    }
    
    /**
     * Map biomes to specific event types
     */
    private void setupBiomeEvents() {
        // Ocean biomes can have all events
        for (Biome biome : validBiomes) {
            List<EventType> biomeEventTypes = new ArrayList<>();
            
            if (biome.toString().contains("OCEAN")) {
                // All ocean biomes can have these events
                biomeEventTypes.add(EventType.LEVIATHAN_HUNT);
                biomeEventTypes.add(EventType.ANCIENT_GUARDIAN);
                biomeEventTypes.add(EventType.ABYSSAL_HORROR);
                biomeEventTypes.add(EventType.GOLDEN_CATCH);
                biomeEventTypes.add(EventType.COASTAL_TREASURE);
            } else if (biome.toString().contains("RIVER")) {
                // River events
                biomeEventTypes.add(EventType.RIVER_TITAN);
                biomeEventTypes.add(EventType.GOLDEN_CATCH);
            } else if (biome.toString().contains("BEACH")) {
                // Beach events
                biomeEventTypes.add(EventType.COASTAL_TREASURE);
                biomeEventTypes.add(EventType.GOLDEN_CATCH);
            }
            
            // Add the biome to the map if it has any event types
            if (!biomeEventTypes.isEmpty()) {
                biomeEvents.put(biome, biomeEventTypes);
            }
        }
    }
    
    /**
     * Start the scheduled task that checks if an event should start
     */
    public void startEventChecker() {
        // Cancel existing task if any
        if (eventCheckTask != null) {
            eventCheckTask.cancel();
        }
        
        // Schedule a repeating check every 5 minutes
        eventCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Only check for new events if no event is currently active
                if (!eventActive) {
                    // Check if enough time has passed since the last event
                    long currentTime = System.currentTimeMillis();
                    long minCooldown = minEventCooldownMinutes * 60 * 1000; // Convert to ms
                    
                    if (lastEventTime == 0 || (currentTime - lastEventTime) >= minCooldown) {
                        // Roll the dice to see if an event should start
                        if (random.nextDouble() * 100 < eventChancePercent) {
                            startRandomEvent();
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20 * 60, 20 * 60 * 5); // Initial 1 minute delay, then every 5 minutes
    }
    
    /**
     * Start a random fishing event
     */
    public void startRandomEvent() {
        if (eventActive) return;
        
        // Choose a random event type from all available types
        List<EventType> allEventTypes = new ArrayList<>();
        for (List<EventType> events : biomeEvents.values()) {
            for (EventType event : events) {
                if (!allEventTypes.contains(event)) {
                    allEventTypes.add(event);
                }
            }
        }
        
        if (allEventTypes.isEmpty()) {
            plugin.getLogger().warning("No event types available to start an event!");
            return;
        }
        
        EventType chosenEvent = allEventTypes.get(random.nextInt(allEventTypes.size()));
        startEvent(chosenEvent);
    }
    
    /**
     * Start a specific event
     * @param eventType The type of event to start
     */
    public void startEvent(EventType eventType) {
        if (eventActive) return;
        
        // Set event state
        eventActive = true;
        activeEventType = eventType;
        eventStartTime = System.currentTimeMillis();
        eventEndTime = eventStartTime + (eventDurationMinutes * 60 * 1000);
        winnerUUID = null;
        winnerName = null;
        caughtFishName = null;
        
        // Broadcast event start
        String eventTitle = "§6§l⚡ EVENT MEMANCING DIMULAI ⚡";
        String eventMessage = "§e" + eventType.getDisplayName() + " §ftelah dimulai! Berlangsung selama §a" + 
                eventDurationMinutes + " menit§f! Cepat cari ikan legendaris di " + 
                formatBiomeList(eventType) + "!";
        
        broadcastEventMessage(eventTitle, eventMessage);
        
        // Play sound for all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.0f);
        }
        
        // Schedule event end
        eventEndTask = new BukkitRunnable() {
            @Override
            public void run() {
                endEvent(false);
            }
        }.runTaskLater(plugin, 20 * 60 * eventDurationMinutes);
        
        plugin.getLogger().info("Started fishing event: " + eventType.name());
    }
    
    /**
     * End the current event
     * @param caught Whether the event fish was caught
     */
    public void endEvent(boolean caught) {
        if (!eventActive) return;
        
        // Cancel the end task if it's still running
        if (eventEndTask != null) {
            eventEndTask.cancel();
            eventEndTask = null;
        }
        
        // Update event state
        lastEventTime = System.currentTimeMillis();
        eventActive = false;
        
        // Broadcast event end
        String endTitle;
        String endMessage;
        
        if (caught && winnerUUID != null) {
            // Someone caught the fish - they win!
            endTitle = "§a§l⚡ EVENT SELESAI ⚡";
            endMessage = "§e" + winnerName + " §fberhasil menangkap §b" + caughtFishName + 
                    " §f(" + formatWeight(caughtFishWeight) + ") dan memenangkan event!";
        } else {
            // Time ran out - nobody won
            endTitle = "§c§l⚡ EVENT BERAKHIR ⚡";
            endMessage = "§fTidak ada yang berhasil menangkap ikan legendaris. Event berakhir!";
        }
        
        broadcastEventMessage(endTitle, endMessage);
        
        // Schedule next event check with random cooldown
        int nextCheckMinutes = minEventCooldownMinutes + 
                random.nextInt(maxEventCooldownMinutes - minEventCooldownMinutes + 1);
        
        // Announce when the next event might occur
        Bukkit.getServer().broadcastMessage("§7Event memancing selanjutnya dalam waktu §e" + 
                nextCheckMinutes + " menit §7atau lebih...");
        
        // Reset active event
        activeEventType = null;
        
        plugin.getLogger().info("Ended fishing event. Next check in " + nextCheckMinutes + " minutes");
    }
    
    /**
     * Check if an event is active for a specific location/biome
     * @param biome The biome to check
     * @return true if an event is active for this biome
     */
    public boolean isEventActiveForBiome(Biome biome) {
        if (!eventActive || activeEventType == null) {
            return false;
        }
        
        // Check if this biome has the active event type
        List<EventType> biomeEventTypes = biomeEvents.get(biome);
        return biomeEventTypes != null && biomeEventTypes.contains(activeEventType);
    }
    
    /**
     * Check if a special event fish can be caught
     * @param player The player fishing
     * @param location The location they're fishing at
     * @return The ConfigEventFish that can be caught, or null
     */
    public ConfigEventFish canCatchEventFish(Player player, Location location) {
        if (!eventActive || activeEventType == null) {
            return null;
        }
        
        // Check if the biome is valid for the event
        Biome biome = location.getBlock().getBiome();
        if (!isEventActiveForBiome(biome)) {
            return null;
        }
        
        // Get appropriate fish for the event
        return activeEventType.getRandomEventFish();
    }
    
    /**
     * Handle a player catching an event fish
     * @param player The player who caught the fish
     * @param fish The custom fish that was caught
     * @return true if this was an event fish catch
     */
    public boolean handleEventFishCaught(Player player, CustomFish fish) {
        if (!eventActive || activeEventType == null) {
            return false;
        }
        
        // Check if this is a legitimate event fish (should match ID pattern)
        String fishId = fish.getId();
        if (!fishId.startsWith("event_")) {
            return false;
        }
        
        // Record the winner information
        winnerUUID = player.getUniqueId();
        winnerName = player.getName();
        caughtFishName = fish.getName();
        caughtFishWeight = fish.getWeight();
        
        // End the event with a winner
        endEvent(true);
        
        // Reward the player (beyond just getting the valuable fish)
        giveEventRewards(player);
        
        return true;
    }
    
    /**
     * Give additional rewards to the event winner
     * @param player The winning player
     */
    private void giveEventRewards(Player player) {
        // Play victory effects
        player.getWorld().strikeLightningEffect(player.getLocation());
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        
        // Title message
        player.sendTitle(
            "§6§l✦ PEMENANG EVENT ✦",
            "§fKamu memenangkan event memancing!",
            10, 100, 20
        );
        
        // Add special rewards here if desired
        // For example:
        // - Give fishing XP
        plugin.getSkillManager().addFishingExp(player, 1000);
        // - Give special items
        // - Give currency rewards
        plugin.getEconomyManager().depositMoney(player, 25000);
    }
    
    /**
     * Format a list of relevant biomes for an event
     */
    private String formatBiomeList(EventType eventType) {
        List<String> relevantBiomes = new ArrayList<>();
        
        for (Map.Entry<Biome, List<EventType>> entry : biomeEvents.entrySet()) {
            if (entry.getValue().contains(eventType)) {
                relevantBiomes.add(formatBiomeName(entry.getKey().toString()));
            }
        }
        
        // Return a formatted list like "Ocean, Deep Ocean, or River"
        if (relevantBiomes.isEmpty()) {
            return "perairan";
        } else if (relevantBiomes.size() == 1) {
            return relevantBiomes.get(0);
        } else {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < relevantBiomes.size() - 1; i++) {
                result.append(relevantBiomes.get(i)).append(", ");
            }
            result.append("atau ").append(relevantBiomes.get(relevantBiomes.size() - 1));
            return result.toString();
        }
    }
    
    /**
     * Format a biome name for display
     * @param biomeName The internal biome name
     * @return A formatted biome name
     */
    private String formatBiomeName(String biomeName) {
        biomeName = biomeName.replace("_", " ").toLowerCase();
        String[] words = biomeName.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(word.substring(0, 1).toUpperCase())
                  .append(word.substring(1))
                  .append(" ");
        }
        return result.toString().trim();
    }
    
    /**
     * Broadcast a formatted event message to all players
     */
    private void broadcastEventMessage(String title, String message) {
        // Server-wide title message
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(title, "§f" + message, 10, 70, 20);
        }
        
        // Chat message with fancy formatting
        String chatMessage = "\n§8§l§m------------------------------§r\n" +
                             "§r " + title + "\n" +
                             "§r " + message + "\n" +
                             "§8§l§m------------------------------§r";
        
        Bukkit.getServer().broadcastMessage(chatMessage);
    }
    
    /**
     * Format weight with appropriate units
     */
    private String formatWeight(double weight) {
        if (weight < 1.0) {
            return String.format("%.2f gram", weight * 1000);
        } else if (weight < 1000.0) {
            return String.format("%.2f kg", weight);
        } else {
            return String.format("%.2f ton", weight / 1000);
        }
    }
    
    /**
     * Check if an event is currently active
     * @return true if an event is active
     */
    public boolean isEventActive() {
        return eventActive;
    }
    
    /**
     * Get the current active event type
     * @return The active event type, or null if no event is active
     */
    public EventType getActiveEventType() {
        return activeEventType;
    }
    
    /**
     * Get the time remaining for the current event in seconds
     * @return Seconds remaining, or 0 if no event is active
     */
    public int getEventTimeRemaining() {
        if (!eventActive) {
            return 0;
        }
        
        long currentTime = System.currentTimeMillis();
        long remainingMillis = Math.max(0, eventEndTime - currentTime);
        return (int) (remainingMillis / 1000);
    }
    
    /**
     * Force start a specific event (admin command)
     * @param eventTypeName The event type to start
     * @return true if the event was started
     */
    public boolean forceStartEvent(String eventTypeName) {
        try {
            EventType eventType = EventType.valueOf(eventTypeName.toUpperCase());
            startEvent(eventType);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Force end the current event (admin command)
     */
    public void forceEndEvent() {
        if (eventActive) {
            endEvent(false);
        }
    }
    
    /**
     * Stop event checker and cleanup
     */
    public void shutdown() {
        if (eventCheckTask != null) {
            eventCheckTask.cancel();
            eventCheckTask = null;
        }
        
        if (eventEndTask != null) {
            eventEndTask.cancel();
            eventEndTask = null;
        }
    }
}