package id.nusafishing.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;

public class ConfigEventFish {
    private static final Random random = new Random();
    private static final Map<String, ConfigEventFish> eventFishMap = new HashMap<>();
    private static final Map<EventType, List<ConfigEventFish>> eventTypeFishMap = new HashMap<>();
    
    private final String id;
    private final String name;
    private final String description;
    private final Material material;
    private final int modelData;
    private final String textureProvider;
    private final String textureId;
    private final double minWeight;
    private final double maxWeight;
    private final int minQuality;
    private final int maxQuality;
    private final double basePrice;
    private final CustomFish.FishTier tier;
    private final List<EventType> eventTypes;
    
    public ConfigEventFish(String id, String name, String description, Material material, int modelData,
                          double minWeight, double maxWeight, int minQuality, int maxQuality,
                          double basePrice, CustomFish.FishTier tier, List<EventType> eventTypes) {
        this(id, name, description, material, modelData, "vanilla", null,
             minWeight, maxWeight, minQuality, maxQuality, basePrice, tier, eventTypes);
    }

    public ConfigEventFish(String id, String name, String description, Material material, int modelData,
                          String textureProvider, String textureId,
                          double minWeight, double maxWeight, int minQuality, int maxQuality,
                          double basePrice, CustomFish.FishTier tier, List<EventType> eventTypes) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.material = material;
        this.modelData = modelData;
        this.textureProvider = (textureProvider == null || textureProvider.isBlank()) ? "vanilla" : textureProvider.trim().toLowerCase();
        this.textureId = textureId;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.minQuality = minQuality;
        this.maxQuality = maxQuality;
        this.basePrice = basePrice;
        this.tier = tier;
        this.eventTypes = eventTypes;
    }
    
    /**
     * Load all event fish from config
     */
    public static void loadFromConfig(NusaFishing plugin) {
        // Clear existing maps
        eventFishMap.clear();
        eventTypeFishMap.clear();
        
        // Initialize the event type map with empty lists
        for (EventType eventType : EventType.values()) {
            eventTypeFishMap.put(eventType, new ArrayList<>());
        }
        
        // Get the event fish section from config
        ConfigurationSection eventFishSection = plugin.getConfig().getConfigurationSection("event_fish");
        if (eventFishSection == null) {
            plugin.getLogger().warning("No event_fish section found in config.yml!");
            return;
        }
        
        // Load each fish
        for (String fishId : eventFishSection.getKeys(false)) {
            ConfigurationSection fishSection = eventFishSection.getConfigurationSection(fishId);
            if (fishSection == null) continue;
            
            try {
                // Get basic properties
                String name = fishSection.getString("name", fishId);
                String description = fishSection.getString("description", "");
                
                // Get material (default to PUFFERFISH if invalid)
                Material material;
                try {
                    material = Material.valueOf(fishSection.getString("material", "PUFFERFISH"));
                } catch (IllegalArgumentException e) {
                    material = Material.PUFFERFISH;
                    plugin.getLogger().warning("Invalid material for event fish " + fishId + ", using PUFFERFISH");
                }
                
                int customModelData = fishSection.getInt("custom_model_data", 0);
                String textureProvider = fishSection.getString("texture_provider", "vanilla");
                String textureId = fishSection.getString("texture_id", null);
                double minWeight = fishSection.getDouble("min_weight", 1.0);
                double maxWeight = fishSection.getDouble("max_weight", 10.0);
                int minQuality = fishSection.getInt("min_quality", 1);
                int maxQuality = fishSection.getInt("max_quality", 100);
                double basePrice = fishSection.getDouble("base_price", 100.0);
                
                // Get tier (default to LEGENDARY if invalid)
                CustomFish.FishTier tier;
                try {
                    tier = CustomFish.FishTier.valueOf(fishSection.getString("tier", "LEGENDARY"));
                } catch (IllegalArgumentException e) {
                    tier = CustomFish.FishTier.LEGENDARY;
                    plugin.getLogger().warning("Invalid tier for event fish " + fishId + ", using LEGENDARY");
                }
                
                // Get event types
                List<String> eventTypeNames = fishSection.getStringList("event_types");
                List<EventType> eventTypes = new ArrayList<>();
                
                for (String typeName : eventTypeNames) {
                    try {
                        EventType eventType = EventType.valueOf(typeName);
                        eventTypes.add(eventType);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid event type '" + typeName + "' for fish " + fishId);
                    }
                }
                
                // Create the event fish
                ConfigEventFish eventFish = new ConfigEventFish(fishId, name, description, material, customModelData,
                                                          textureProvider, textureId,
                                                          minWeight, maxWeight, minQuality, maxQuality,
                                                          basePrice, tier, eventTypes);
                
                // Add to maps
                eventFishMap.put(fishId, eventFish);
                
                // Add to event type lists
                for (EventType eventType : eventTypes) {
                    List<ConfigEventFish> fishList = eventTypeFishMap.get(eventType);
                    if (fishList != null) {
                        fishList.add(eventFish);
                    }
                }
                
                plugin.getLogger().info("Loaded event fish: " + name + " for " + eventTypes.size() + " event types");
                
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading event fish " + fishId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        plugin.getLogger().info("Loaded " + eventFishMap.size() + " event fish from config");
    }
    
    /**
     * Get a random event fish for a specific event type
     * @param eventType The event type
     * @return A random event fish for this event type, or null if none available
     */
    public static ConfigEventFish getRandomFishForEvent(EventType eventType) {
        List<ConfigEventFish> fishList = eventTypeFishMap.get(eventType);
        if (fishList == null || fishList.isEmpty()) {
            return null;
        }
        
        return fishList.get(random.nextInt(fishList.size()));
    }

    /**
     * Get all loaded event fish.
     */
    public static Map<String, ConfigEventFish> getEventFishMap() {
        return java.util.Collections.unmodifiableMap(eventFishMap);
    }

    /**
     * Get an event fish configuration by ID.
     */
    public static ConfigEventFish getEventFish(String id) {
        if (id == null) return null;
        return eventFishMap.get(id);
    }
    
    /**
     * Create a CustomFish template instance from this event fish.
     * @return A base CustomFish instance without randomized caught stats
     */
    public CustomFish createTemplateFish() {
        return new CustomFish(
            id, name, description, material, modelData,
            textureProvider, textureId,
            minWeight, maxWeight, minQuality, maxQuality,
            basePrice, tier
        );
    }
    
    /**
     * Create a CustomFish instance from this event fish
     * @return A new CustomFish instance
     */
    public CustomFish createCustomFish() {
        CustomFish fish = createTemplateFish();
        return fish.createCaughtInstance(2, 1.5, 1.5);
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Material getMaterial() { return material; }
    public int getModelData() { return modelData; }
    public String getTextureProvider() { return textureProvider; }
    public String getTextureId() { return textureId; }
    public double getMinWeight() { return minWeight; }
    public double getMaxWeight() { return maxWeight; }
    public int getMinQuality() { return minQuality; }
    public int getMaxQuality() { return maxQuality; }
    public double getBasePrice() { return basePrice; }
    public CustomFish.FishTier getTier() { return tier; }
    public List<EventType> getEventTypes() { return eventTypes; }
}