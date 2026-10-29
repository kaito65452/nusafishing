package id.nusafishing.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.entity.FishHook;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import id.nusafishing.NusaFishing;
import id.nusafishing.config.ConfigManager;
import id.nusafishing.fishing.data.PlayerSkillManager;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.event.FishingEventManager;
import id.nusafishing.event.ConfigEventFish;
import id.nusafishing.event.EventFish;
import id.nusafishing.event.EventType;

public class FishingManager {
    private final NusaFishing plugin;
    private final ConfigManager configManager;
    private final Random random = new Random();
    
    // Data for special fishing locations
    private static class SpecialLocation {
        String id;
        String name;
        String description;
        String worldName;
        double centerX;
        double centerZ;
        double radius;
        double fishMultiplier;
        double rareFishChance;
        List<String> specialFish;
        
        public boolean isInLocation(Location location) {
            if (location == null) return false;
            if (worldName != null && location.getWorld() != null && !location.getWorld().getName().equalsIgnoreCase(worldName)) {
                return false;
            }
            double dx = location.getX() - centerX;
            double dz = location.getZ() - centerZ;
            return (dx * dx + dz * dz) <= (radius * radius);
        }
    }
    
    private final Map<String, SpecialLocation> specialLocations = new HashMap<>();
    
    public FishingManager(NusaFishing plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        
        // Load special locations from config
        loadSpecialLocations();
    }
    
    /**
     * Load special locations from the configuration
     */
    private void loadSpecialLocations() {
        specialLocations.clear();
        
        ConfigurationSection locationsSection = plugin.getConfigManager().getFishingConfig()
                .getConfigurationSection("special_locations");
        
        if (locationsSection == null) {
            return;
        }
        
        for (String key : locationsSection.getKeys(false)) {
            SpecialLocation location = new SpecialLocation();
            location.id = key;
            location.name = locationsSection.getString(key + ".name", key);
            location.description = locationsSection.getString(key + ".description", "");
            location.worldName = locationsSection.getString(key + ".world", null);
            location.centerX = locationsSection.getDouble(key + ".center_x");
            location.centerZ = locationsSection.getDouble(key + ".center_z");
            location.radius = locationsSection.getDouble(key + ".radius", 50.0);
            location.fishMultiplier = locationsSection.getDouble(key + ".fish_multiplier", 1.0);
            location.rareFishChance = locationsSection.getDouble(key + ".rare_fish_chance", 0.1);
            location.specialFish = locationsSection.getStringList(key + ".special_fish");
            
            specialLocations.put(key, location);
        }
        
        plugin.getLogger().info("Loaded " + specialLocations.size() + " special fishing locations");
    }
    
    /**
     * Get a fishing rod by its custom model data
     * @param modelData The custom model data to look for
     * @return The matching FishingRod, or null if none found
     */
    public FishingRod getFishingRodByModelData(int modelData) {
        for (FishingRod rod : configManager.getFishingRods().values()) {
            if (rod.getCustomModelData() == modelData) {
                return rod;
            }
        }
        return null;
    }
    
    /**
     * Get a fishing rod by its ID
     * @param rodId The rod ID to look for
     * @return The matching FishingRod, or null if none found
     */
    public FishingRod getRodById(String rodId) {
        return configManager.getFishingRods().get(rodId);
    }
    
    /**
     * Get a bait by its persistent data or custom model data
     * @param item The item to check for bait properties
     * @return The matching FishingBait, or null if not a bait
     */
    public FishingBait getBaitFromItem(ItemStack item) {
        if (item == null || item.getType() == null) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        // Check PersistentDataContainer first
        org.bukkit.persistence.PersistentDataContainer container = meta.getPersistentDataContainer();
        org.bukkit.NamespacedKey cleanKey = org.bukkit.NamespacedKey.fromString("nusafishing:bait_id");
        org.bukkit.NamespacedKey dottedKey = new org.bukkit.NamespacedKey(plugin, "nusafishing.bait_id");
        org.bukkit.NamespacedKey simpleKey = new org.bukkit.NamespacedKey(plugin, "bait_id");

        String baitId = null;
        if (cleanKey != null && container.has(cleanKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            baitId = container.get(cleanKey, org.bukkit.persistence.PersistentDataType.STRING);
        } else if (container.has(dottedKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            baitId = container.get(dottedKey, org.bukkit.persistence.PersistentDataType.STRING);
        } else if (container.has(simpleKey, org.bukkit.persistence.PersistentDataType.STRING)) {
            baitId = container.get(simpleKey, org.bukkit.persistence.PersistentDataType.STRING);
        }

        if (baitId != null) {
            FishingBait bait = configManager.getFishingBaits().get(baitId);
            if (bait != null) {
                return bait;
            }
        }
        
        // Fallback: Check custom model data using compatibility helper
        int modelData = id.nusafishing.compat.CustomModelDataHelper.getCustomModelData(meta);
        if (modelData > 0) {
            for (FishingBait bait : configManager.getFishingBaits().values()) {
                if (bait.getMaterial() == item.getType() && 
                    bait.getCustomModelData() == modelData) {
                    return bait;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Find the special location a player is currently in, if any
     * @param player The player to check
     * @return The special location, or null if not in any
     */
    public SpecialLocation getPlayerSpecialLocation(Player player) {
        Location location = player.getLocation();
        
        for (SpecialLocation specialLocation : specialLocations.values()) {
            if (specialLocation.isInLocation(location)) {
                return specialLocation;
            }
        }
        
        return null;
    }
    
    /**
     * Check if the player is in a special fishing location
     * @param player The player to check
     * @return The name of the special location, or null if not in one
     */
    public String getSpecialLocationName(Player player) {
        SpecialLocation location = getPlayerSpecialLocation(player);
        return location != null ? location.name : null;
    }
    
    /**
     * Check if an event fish can be caught and return it
     * @param player The player fishing
     * @param location The fishing location
     * @return The event fish if one can be caught, otherwise null
     */
    public CustomFish checkForEventFish(Player player, Location location) {
        return checkForEventFish(player, location, null);
    }

    /**
     * Check if an event fish can be caught with explicit active bait
     * @param player The player fishing
     * @param location The fishing location
     * @param bait The active bait being used (or null to search inventory)
     * @return The event fish if one can be caught, otherwise null
     */
    public CustomFish checkForEventFish(Player player, Location location, FishingBait bait) {
        // Get the event manager
        FishingEventManager eventManager = plugin.getEventManager();
        
        // Check if an event is active and this location is valid
        if (!eventManager.isEventActive()) {
            return null;
        }
        
        // Check biome compatibility
        Biome biome = location.getBlock().getBiome();
        if (!eventManager.isEventActiveForBiome(biome)) {
            return null;
        }
        
        // Calculate chance based on time elapsed in event - make it slower
        int eventTimeRemaining = eventManager.getEventTimeRemaining();
        int eventDuration = plugin.getConfig().getInt("events.duration_minutes", 30) * 60; // in seconds
        int timeElapsed = eventDuration - eventTimeRemaining;
        
        // Much lower initial chance (0.1%) with slower growth rate
        // Increase up to only 5% at the end of the event
        double baseChance = 0.001; // 0.1% initial
        double maxChance = 0.05;  // 5% maximum (was 10%)
        
        // Lower the progression rate by using square root function
        double progressFactor = Math.sqrt(Math.min(1.0, timeElapsed / (double)eventDuration));
        double currentChance = baseChance + progressFactor * (maxChance - baseChance);
        
        // Factor in fishing rod quality more significantly
        ItemStack fishingRod = player.getInventory().getItemInMainHand();
        ItemMeta meta = fishingRod.getItemMeta();
        double rodBonus = 0.0;
        
        if (meta != null) {
            FishingRod rod = null;
            org.bukkit.persistence.PersistentDataContainer rodContainer = meta.getPersistentDataContainer();
            org.bukkit.NamespacedKey rodKey = org.bukkit.NamespacedKey.fromString("nusafishing:rod_id");
            if (rodKey != null && rodContainer.has(rodKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                rod = getRodById(rodContainer.get(rodKey, org.bukkit.persistence.PersistentDataType.STRING));
            }
            if (rod == null) {
                int cmd = id.nusafishing.compat.CustomModelDataHelper.getCustomModelData(meta);
                if (cmd > 0) {
                    rod = getFishingRodByModelData(cmd);
                }
            }

            if (rod != null) {
                // More significant rod bonuses based on tier
                switch (rod.getTier()) {
                    case MYTHIC:
                        rodBonus = 0.05; // +5%
                        break;
                    case MASTER:
                        rodBonus = 0.03; // +3%
                        break;
                    case EXPERT:
                        rodBonus = 0.02; // +2%
                        break;
                    case ADVANCED:
                        rodBonus = 0.01; // +1%
                        break;
                    case INTERMEDIATE:
                        rodBonus = 0.005; // +0.5%
                        break;
                    default:
                        rodBonus = 0.0;
                }
            }
        }
        
        // Factor in bait quality
        if (bait == null) {
            ItemStack offHandItem = player.getInventory().getItemInOffHand();
            bait = getBaitFromItem(offHandItem);
            if (bait == null) {
                for (ItemStack invItem : player.getInventory().getContents()) {
                    if (invItem == null) continue;
                    bait = getBaitFromItem(invItem);
                    if (bait != null) break;
                }
            }
        }

        double baitBonus = 0.0;
        if (bait != null) {
            // Base bonus from tier boost
            baitBonus = bait.getTierBoost() * 0.005; // Each tier is +0.5%
            
            // Premium bait gets extra boost
            if (bait.getId().equals("rare_bait")) {
                baitBonus *= 2; // Double the bonus for premium bait
            }
        }
        
        // Factor in fishing skill
        int fishingLevel = plugin.getSkillManager().getFishingLevel(player);
        double skillBonus = fishingLevel * 0.0005; // 0.05% per level (reduced from 0.1%)
        
        // Calculate total chance with all factors
        double totalChance = currentChance + rodBonus + baitBonus + skillBonus;
        
        // Show chance in action bar (this is the new addition)
        String chanceMessage = String.format("§6§lEvent: §e%.2f%% §7peluang mendapat ikan mistik §8(+%.1f%% alat, +%.1f%% umpan, +%.1f%% skill)",
                totalChance * 100, rodBonus * 100, baitBonus * 100, skillBonus * 100);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(chanceMessage));
        
        // Random check with the calculated chance
        if (random.nextDouble() <= totalChance) {
            // Got lucky! Check if event is active and has a fish type
            EventType activeEventType = eventManager.getActiveEventType();
            if (activeEventType == null) {
                plugin.getLogger().warning("Active event type is null when trying to get event fish");
                return null;
            }
            
            ConfigEventFish eventFish = activeEventType.getRandomEventFish();
            if (eventFish == null) {
                plugin.getLogger().warning("No event fish found for event type: " + activeEventType.name());
                return null;
            }
            
            // Create and return the custom fish
            return eventFish.createCustomFish();
        }
        
        return null;
    }

    /**
     * Select a random fish based on player's location, time, weather, etc.
     * @param player The player fishing
     * @param rod The fishing rod being used, can be null for default rod
     * @return A randomly selected fish suitable for the conditions, or null if no fish can be caught
     */
    public CustomFish selectRandomFish(Player player, FishingRod rod) {
        return selectRandomFish(player, rod, null);
    }

    /**
     * Select a random fish with active bait
     * @param player The player fishing
     * @param rod The fishing rod being used
     * @param activeBait The active bait being used
     * @return A randomly selected fish
     */
    public CustomFish selectRandomFish(Player player, FishingRod rod, FishingBait activeBait) {
        return selectRandomFish(player, rod, activeBait, 0.0);
    }

    public CustomFish selectRandomFish(Player player, FishingRod rod, FishingBait activeBait, double luckNerf) {
        // Resolve active bait if not explicitly passed
        if (activeBait == null) {
            ItemStack offHandItem = player.getInventory().getItemInOffHand();
            activeBait = getBaitFromItem(offHandItem);
            if (activeBait == null) {
                for (ItemStack invItem : player.getInventory().getContents()) {
                    if (invItem == null) continue;
                    activeBait = getBaitFromItem(invItem);
                    if (activeBait != null) break;
                }
            }
        }

        // Check for event fish first (highest priority) - reduced chance under luck nerf
        if (luckNerf <= 0 || random.nextDouble() >= luckNerf) {
            CustomFish eventFish = checkForEventFish(player, player.getLocation(), activeBait);
            if (eventFish != null) {
                return eventFish;
            }
        }
        
        // Get location and environment information
        Location location = player.getLocation();
        World world = location.getWorld();
        Biome biome = location.getBlock().getBiome();
        
        // Calculate tier boost from rod and bait
        int totalTierBoost = 0;
        double qualityBoost = 1.0;
        double weightBoost = 1.0;

        if (rod != null) {
            totalTierBoost += rod.getTier().getTierBoost();
            qualityBoost *= rod.getQualityBoost();
        } else {
            // Default values for vanilla rod - reduced penalty to help players
            totalTierBoost = 0;
            qualityBoost = 0.9;
        }

        // Apply bait boosts
        if (activeBait != null) {
            totalTierBoost += activeBait.getTierBoost();
            qualityBoost *= activeBait.getQualityBoost();
            weightBoost *= activeBait.getWeightBoost();
        }

        // Apply luck nerf to tier, quality, and weight boosts
        if (luckNerf > 0.0) {
            totalTierBoost = (int) Math.round(totalTierBoost * (1.0 - luckNerf));
            qualityBoost = Math.max(0.5, qualityBoost * (1.0 - (luckNerf * 0.5)));
            weightBoost = Math.max(0.5, weightBoost * (1.0 - (luckNerf * 0.5)));
        }

        // Add the missing section to populate availableFish
        List<CustomFish> availableFish = new ArrayList<>();
        List<Double> fishWeights = new ArrayList<>();
        double totalWeight = 0.0;
        
        // Get all fish from config
        Map<String, CustomFish> allFish = configManager.getCustomFishes();
        
        // Get player's special location if any
        SpecialLocation specialLocation = getPlayerSpecialLocation(player);
        
        // Get current time of day and weather
        String timeOfDay = getTimeOfDay(world.getTime());
        String weather = getWeatherCondition(world);
        
        // Calculate weight for each fish and add to available list if catchable
        for (CustomFish fish : allFish.values()) {
            // Skip event fish in normal fishing
            if (fish.getId().startsWith("event_")) continue;
            
            // Calculate weight based on various factors
            double weight = calculateFishWeight(fish, activeBait, specialLocation, timeOfDay);
            
            // Apply luck nerf to higher-tier fish weights
            if (luckNerf > 0.0 && weight > 0) {
                switch (fish.getBaseTier()) {
                    case MYTHIC -> weight *= Math.max(0.05, 1.0 - (luckNerf * 1.5));
                    case LEGENDARY -> weight *= Math.max(0.10, 1.0 - (luckNerf * 1.25));
                    case EPIC -> weight *= Math.max(0.20, 1.0 - luckNerf);
                    case RARE -> weight *= Math.max(0.35, 1.0 - (luckNerf * 0.75));
                    default -> {}
                }
            }
            
            // If weight > 0, fish can be caught in current conditions
            if (weight > 0) {
                availableFish.add(fish);
                fishWeights.add(weight);
                totalWeight += weight;
            }
        }

        double maxPossibleWeight = rod != null ? rod.getMaxWeightCapacity() : 100.0;

        // IMPORTANT: If no fish matches criteria, return at least a common fish
        // This ensures vanilla rods always catch something
        if (availableFish.isEmpty()) {
            CustomFish defaultFish = configManager.getCustomFishes().get("small_carp");
            if (defaultFish == null && !configManager.getCustomFishes().isEmpty()) {
                defaultFish = configManager.getCustomFishes().values().iterator().next();
            }
            if (defaultFish != null) {
                return defaultFish.createCaughtInstance(totalTierBoost, qualityBoost, weightBoost, maxPossibleWeight);
            }
        }
        
        // Select a random fish using weighted probability
        if (!availableFish.isEmpty() && totalWeight > 0) {
            double randomValue = random.nextDouble() * totalWeight;
            double cumulativeWeight = 0;
            
            for (int i = 0; i < availableFish.size(); i++) {
                cumulativeWeight += fishWeights.get(i);
                if (randomValue <= cumulativeWeight) {
                    return availableFish.get(i).createCaughtInstance(totalTierBoost, qualityBoost, weightBoost, maxPossibleWeight);
                }
            }
        }
        
        // Fallback to return a basic fish
        CustomFish fallbackFish = configManager.getCustomFishes().get("small_carp");
        if (fallbackFish == null && !configManager.getCustomFishes().isEmpty()) {
            fallbackFish = configManager.getCustomFishes().values().iterator().next();
        }
        if (fallbackFish != null) {
            return fallbackFish.createCaughtInstance(totalTierBoost, qualityBoost, weightBoost, maxPossibleWeight);
        }
        return null;
    }
    
    /**
     * Calculate weight (selection probability) for a fish based on various factors
     */
    private double calculateFishWeight(CustomFish fish, FishingBait bait, SpecialLocation location, String timeOfDay) {
        // Base weight calculation - make rarer fish MUCH harder to catch
        // Ikan dengan tier tinggi lebih sulit ditangkap secara eksponensial
        double baseWeight;
        switch (fish.getBaseTier()) {
            case MYTHIC:
                baseWeight = 4.0 / (1 + fish.getBasePrice() * 2.5); // 25x harder than regular formula
                break;
            case LEGENDARY:
                baseWeight = 10.0 / (1 + fish.getBasePrice() * 2); // 10x harder than regular formula
                break;
            case EPIC:
                baseWeight = 25.0 / (1 + fish.getBasePrice() * 1.5); // 4x harder
                break; 
            case RARE:
                baseWeight = 50.0 / (1 + fish.getBasePrice() * 1.2); // 2x harder
                break;
            default:
                baseWeight = 100.0 / (1 + fish.getBasePrice()); // Original formula
        }
        
        // Apply bait attraction modifier with less impact for higher tiers
        double baitModifier = 1.0;
        if (bait != null) {
            // Apply attraction modifier based on tier
            switch (fish.getBaseTier()) {
                case MYTHIC:
                    // Bait only 15% as effective for mythic
                    baitModifier = 1.0 + ((bait.getAttractionModifier() - 1.0) * 0.15);
                    break;
                case LEGENDARY:
                    // Bait only 25% as effective for legendary
                    baitModifier = 1.0 + ((bait.getAttractionModifier() - 1.0) * 0.25);
                    break;
                case EPIC:
                    // Bait only 50% as effective for epic
                    baitModifier = 1.0 + ((bait.getAttractionModifier() - 1.0) * 0.5);
                    break;
                case RARE:
                    // Bait 75% as effective for rare
                    baitModifier = 1.0 + ((bait.getAttractionModifier() - 1.0) * 0.75);
                    break;
                default:
                    // Full effectiveness for common/uncommon
                    baitModifier = bait.getAttractionModifier();
            }
            
            // Apply bonus for matching fish, but with diminishing returns by tier
            if (!bait.getValidFish().isEmpty() && bait.getValidFish().contains(fish.getId())) {
                switch (fish.getBaseTier()) {
                    case MYTHIC:
                        baitModifier *= 2.0;  // 2x bonus
                        break;
                    case LEGENDARY:
                        baitModifier *= 3.0;  // 3x bonus (instead of 10x)
                        break;
                    case EPIC:
                        baitModifier *= 5.0;  // 5x bonus
                        break;
                    case RARE:
                        baitModifier *= 7.0;  // 7x bonus
                        break;
                    default:
                        baitModifier *= 10.0; // Original 10x bonus
                }
            }
        }
        
        // Location modifier remains the same
        double locationModifier = (location != null) ? location.fishMultiplier : 1.0;
        
        // Add time-based factor - legendary, mythic and epic fish more active at night/sunset
        double timeModifier = 1.0;
        if (fish.getBaseTier() == CustomFish.FishTier.MYTHIC || fish.getBaseTier() == CustomFish.FishTier.LEGENDARY || fish.getBaseTier() == CustomFish.FishTier.EPIC) {
            if ("NIGHT".equalsIgnoreCase(timeOfDay) || "SUNSET".equalsIgnoreCase(timeOfDay)) {
                timeModifier = 1.25;
            }
        }
        
        return baseWeight * baitModifier * locationModifier * timeModifier;
    }
    
    /**
     * Get the time of day as a string (DAY, SUNSET, NIGHT, SUNRISE)
     * @param time The tick time of the world
     * @return A string representing the time of day
     */
    private String getTimeOfDay(long time) {
        // Minecraft day is 24000 ticks long
        // Day: 0-12000, Sunset: 12000-14000, Night: 14000-22000, Sunrise: 22000-24000
        if (time < 12000) {
            return "DAY";
        } else if (time < 14000) {
            return "SUNSET";
        } else if (time < 22000) {
            return "NIGHT";
        } else {
            return "SUNRISE";
        }
    }
    
    /**
     * Get the current weather condition as a string (CLEAR, RAIN, THUNDER)
     * @param world The world to check weather in
     * @return A string representing the weather
     */
    private String getWeatherCondition(World world) {
        if (world.isThundering()) {
            return "THUNDER";
        } else if (world.hasStorm()) {
            return "RAIN";
        } else {
            return "CLEAR";
        }
    }
    
    /**
     * Reload all fishing-related configurations
     */
    public void reloadConfig() {
        loadSpecialLocations();
    }
    
    /**
     * Get access to the special locations map
     */
    public Map<String, Object> getSpecialLocations() {
        ConfigurationSection locationsSection = plugin.getConfigManager().getFishingConfig()
                .getConfigurationSection("special_locations");
        
        if (locationsSection == null) {
            return new HashMap<>();
        }
        
        return locationsSection.getValues(false);
    }
}