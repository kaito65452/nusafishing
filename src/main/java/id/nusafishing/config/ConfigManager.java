package id.nusafishing.config;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;

public class ConfigManager {
    private final NusaFishing plugin;
    private FileConfiguration fishingConfig;
    private File fishingConfigFile;
    
    private Map<String, FishingRod> fishingRods;
    private Map<String, FishingBait> fishingBaits;
    private Map<String, CustomFish> customFishes;
    private Map<CustomFish.FishTier, String> fishTierColors;
    private Map<FishingRod.RodTier, String> rodTierColors;
    
    public ConfigManager(NusaFishing plugin) {
        this.plugin = plugin;
        this.fishingRods = new HashMap<>();
        this.fishingBaits = new HashMap<>();
        this.customFishes = new HashMap<>();
        this.fishTierColors = new EnumMap<>(CustomFish.FishTier.class);
        this.rodTierColors = new EnumMap<>(FishingRod.RodTier.class);
        
        // Initialize configs
        autoUpdateMainConfig();
        loadTierColors();
        createFishingConfig();
        autoUpdateFishingConfig();
        loadFishingConfig();
    }
    
    public void autoUpdateMainConfig() {
        FileConfiguration config = plugin.getConfig();
        if (config == null) return;
        
        try {
            config.options().parseComments(true);
        } catch (Throwable ignored) {}

        FileConfiguration defaultConfig = null;
        try (java.io.InputStream defStream = plugin.getResource("config.yml")) {
            if (defStream != null) {
                defaultConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {}

        boolean modified = false;

        // Sync missing defaults from bundled config.yml
        if (defaultConfig != null) {
            for (String key : defaultConfig.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaultConfig.get(key));
                    modified = true;
                }
            }
        }

        String currentVersion = plugin.getDescription().getVersion();
        String configVersion = config.getString("config_version", null);

        if (configVersion == null || !configVersion.equalsIgnoreCase(currentVersion)) {
            config.set("config_version", currentVersion);
            modified = true;
        }

        if (modified) {
            plugin.saveConfig();
            plugin.getLogger().info("[ConfigManager] config.yml berhasil diperbarui secara otomatis ke versi " + currentVersion + "!");
        }
    }

    public void autoUpdateFishingConfig() {
        if (fishingConfig == null) return;

        FileConfiguration defaultFishingConfig = null;
        try (java.io.InputStream defStream = plugin.getResource("fishing.yml")) {
            if (defStream != null) {
                defaultFishingConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (Throwable ignored) {}

        boolean modified = false;

        // Sync missing keys from bundled fishing.yml
        if (defaultFishingConfig != null) {
            for (String key : defaultFishingConfig.getKeys(true)) {
                if (!fishingConfig.contains(key)) {
                    fishingConfig.set(key, defaultFishingConfig.get(key));
                    modified = true;
                }
            }
        }

        String currentVersion = plugin.getDescription().getVersion();
        String configVersion = fishingConfig.getString("config_version", null);

        if (configVersion == null || !configVersion.equalsIgnoreCase(currentVersion)) {
            fishingConfig.set("config_version", currentVersion);
            modified = true;
        }

        if (modified) {
            saveConfig();
            plugin.getLogger().info("[ConfigManager] fishing.yml berhasil diperbarui secara otomatis ke versi " + currentVersion + "!");
        }
    }
    
    private void createFishingConfig() {
        fishingConfigFile = new File(plugin.getDataFolder(), "fishing.yml");
        if (!fishingConfigFile.exists()) {
            fishingConfigFile.getParentFile().mkdirs();
            plugin.saveResource("fishing.yml", false);
        }
        
        fishingConfig = YamlConfiguration.loadConfiguration(fishingConfigFile);
        try {
            fishingConfig.options().parseComments(true);
        } catch (Throwable ignored) {}
    }
    
    public void loadFishingConfig() {
        // Load fishing rods
        ConfigurationSection rodsSection = fishingConfig.getConfigurationSection("fishing_rods");
        if (rodsSection != null) {
            for (String key : rodsSection.getKeys(false)) {
                String name = rodsSection.getString(key + ".name", key);
                String description = rodsSection.getString(key + ".description", "");
                int customModelData = rodsSection.getInt(key + ".custom_model_data", 0);
                int durability = rodsSection.getInt(key + ".durability", 100);
                double staminaModifier = rodsSection.getDouble(key + ".stamina_modifier", 1.0);
                
                // Baca tier dan special ability
                String tierName = rodsSection.getString(key + ".tier", "BASIC");
                FishingRod.RodTier tier;
                try {
                    tier = FishingRod.RodTier.valueOf(tierName);
                } catch (IllegalArgumentException e) {
                    tier = FishingRod.RodTier.BASIC;
                }
                
                boolean hasSpecialAbility = rodsSection.getBoolean(key + ".special_ability", false);
                String abilityDescription = rodsSection.getString(key + ".ability_description", "");
                
                String materialName = rodsSection.getString(key + ".material", "FISHING_ROD");
                Material material = Material.getMaterial(materialName != null ? materialName.toUpperCase() : "FISHING_ROD");
                if (material == null) {
                    material = Material.FISHING_ROD;
                }
                String textureProvider = rodsSection.getString(key + ".texture_provider", "vanilla");
                String textureId = rodsSection.getString(key + ".texture_id", null);

                // Baca price dan lure_level
                double price = rodsSection.getDouble(key + ".price", -1); // Default -1 berarti gunakan kalkulasi
                int lureLevel = rodsSection.getInt(key + ".lure_level", tier.ordinal() + 1); // Default berdasarkan tier
                
                // Baca max_weight_capacity (kapasitas berat ikan maksimal)
                double maxWeightCapacity = rodsSection.getDouble(key + ".max_weight_capacity", 
                        switch(tier) {
                            case BASIC -> 25.0;
                            case INTERMEDIATE -> 100.0;
                            case ADVANCED -> 500.0;
                            case EXPERT -> 2500.0;
                            case MASTER -> 10000.0;
                            case MYTHIC -> 50000.0;
                            default -> 25.0;
                        });
                
                FishingRod rod = new FishingRod(key, name, description, material, customModelData, 
                                              textureProvider, textureId,
                                              durability, staminaModifier, tier,
                                              hasSpecialAbility, abilityDescription,
                                              price, lureLevel, maxWeightCapacity);
                fishingRods.put(key, rod);
            }
        }
        
        // Load fishing baits
        ConfigurationSection baitsSection = fishingConfig.getConfigurationSection("fishing_baits");
        if (baitsSection != null) {
            for (String key : baitsSection.getKeys(false)) {
                String name = baitsSection.getString(key + ".name", key);
                String description = baitsSection.getString(key + ".description", "");
                int customModelData = baitsSection.getInt(key + ".custom_model_data", 0);
                Material material = Material.getMaterial(baitsSection.getString(key + ".material", "COD"));
                List<String> validFish = baitsSection.getStringList(key + ".valid_fish");
                double attractionModifier = baitsSection.getDouble(key + ".attraction_modifier", 1.0);
                
                int tierBoost = baitsSection.getInt(key + ".tier_boost", 0);
                int usesPerItem = baitsSection.getInt(key + ".uses_per_item", 1);
                double price = baitsSection.getDouble(key + ".price", -1);
                
                // Baca nilai boosts baru dengan default 1.0 (tidak ada boost)
                double qualityBoost = baitsSection.getDouble(key + ".quality_boost", 1.0);
                double weightBoost = baitsSection.getDouble(key + ".weight_boost", 1.0);
                
                FishingBait bait = new FishingBait(key, name, description, material, 
                                                 customModelData, validFish, attractionModifier,
                                                 tierBoost, usesPerItem, price,
                                                 qualityBoost, weightBoost);
                fishingBaits.put(key, bait);
            }
        }
        
        // Load custom fish
        ConfigurationSection fishesSection = fishingConfig.getConfigurationSection("fishes");
        if (fishesSection != null) {
            for (String key : fishesSection.getKeys(false)) {
                String name = fishesSection.getString(key + ".name", key);
                String description = fishesSection.getString(key + ".description", "");
                String materialName = fishesSection.getString(key + ".material", "COD");
                Material material = Material.getMaterial(materialName != null ? materialName.toUpperCase() : "COD");
                if (material == null) {
                    material = Material.COD;
                }
                int customModelData = fishesSection.getInt(key + ".custom_model_data", 0);
                String textureProvider = fishesSection.getString(key + ".texture_provider", "vanilla");
                String textureId = fishesSection.getString(key + ".texture_id", null);
                double minWeight = fishesSection.getDouble(key + ".min_weight", 0.1);
                double maxWeight = fishesSection.getDouble(key + ".max_weight", 1.0);
                int minQuality = fishesSection.getInt(key + ".min_quality", 1);
                int maxQuality = fishesSection.getInt(key + ".max_quality", 100);
                double basePrice = fishesSection.getDouble(key + ".base_price", 10.0);
                
                // Baca tier ikan
                String tierName = fishesSection.getString(key + ".tier", "COMMON");
                CustomFish.FishTier tier;
                try {
                    tier = CustomFish.FishTier.valueOf(tierName);
                } catch (IllegalArgumentException e) {
                    tier = CustomFish.FishTier.COMMON;
                }
                
                CustomFish fish = new CustomFish(key, name, description, material, customModelData,
                                                textureProvider, textureId,
                                                minWeight, maxWeight, minQuality, maxQuality, 
                                                basePrice, tier);
                customFishes.put(key, fish);
            }
        }
    }
    
    public void saveConfig() {
        try {
            fishingConfig.save(fishingConfigFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save fishing.yml: " + e.getMessage());
        }
    }
    
    public void reloadConfigs() {
        // Reload plugin config
        plugin.reloadConfig();
        autoUpdateMainConfig();
        loadTierColors();
        
        // Reload fishing config
        reloadFishingConfig();
        autoUpdateFishingConfig();
        
        // Reload the rewards config
        plugin.getRewardsManager().reloadRewardsConfig();
        
        // Log message
        plugin.getLogger().info("Semua konfigurasi berhasil dimuat ulang!");
    }

    public void loadTierColors() {
        fishTierColors.clear();
        rodTierColors.clear();

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection tierColorsSection = config != null ? config.getConfigurationSection("tier_colors") : null;

        ConfigurationSection fishColors = tierColorsSection != null ? tierColorsSection.getConfigurationSection("fish") : null;
        for (CustomFish.FishTier tier : CustomFish.FishTier.values()) {
            String colorCode = null;
            if (fishColors != null) {
                colorCode = fishColors.getString(tier.name());
            }
            if (colorCode == null && tierColorsSection != null) {
                colorCode = tierColorsSection.getString(tier.name());
            }
            if (colorCode != null && !colorCode.isEmpty()) {
                fishTierColors.put(tier, ChatColor.translateAlternateColorCodes('&', colorCode));
            } else {
                fishTierColors.put(tier, tier.getDefaultColor());
            }
        }

        ConfigurationSection rodColors = tierColorsSection != null ? tierColorsSection.getConfigurationSection("rod") : null;
        for (FishingRod.RodTier tier : FishingRod.RodTier.values()) {
            String colorCode = null;
            if (rodColors != null) {
                colorCode = rodColors.getString(tier.name());
            }
            if (colorCode == null && tierColorsSection != null) {
                colorCode = tierColorsSection.getString(tier.name());
            }
            if (colorCode != null && !colorCode.isEmpty()) {
                rodTierColors.put(tier, ChatColor.translateAlternateColorCodes('&', colorCode));
            } else {
                rodTierColors.put(tier, tier.getDefaultColor());
            }
        }
    }

    public String getFishTierColor(CustomFish.FishTier tier) {
        if (tier == null) return "§7";
        return fishTierColors.getOrDefault(tier, tier.getDefaultColor());
    }

    public String getRodTierColor(FishingRod.RodTier tier) {
        if (tier == null) return "§7";
        return rodTierColors.getOrDefault(tier, tier.getDefaultColor());
    }

    public void reloadFishingConfig() {
        createFishingConfig();
        loadFishingConfig();
    }
    
    public FileConfiguration getFishingConfig() {
        return fishingConfig;
    }
    
    public Map<String, FishingRod> getFishingRods() {
        return fishingRods;
    }
    
    public Map<String, FishingBait> getFishingBaits() {
        return fishingBaits;
    }
    
    public Map<String, CustomFish> getCustomFishes() {
        return customFishes;
    }
}