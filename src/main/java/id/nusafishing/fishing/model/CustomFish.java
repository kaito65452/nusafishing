package id.nusafishing.fishing.model;

import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import id.nusafishing.util.ItemBuilder;
import id.nusafishing.NusaFishing;
import id.nusafishing.compat.CustomModelDataHelper;

public class CustomFish {
    // Definisikan tier ikan
    public enum FishTier {
        COMMON("§7", 1.0, "Biasa"),
        UNCOMMON("§a", 1.5, "Tidak Biasa"),
        RARE("§9", 2.0, "Langka"),
        EPIC("§5", 3.0, "Epik"),
        LEGENDARY("§6", 5.0, "Legendaris"),
        MYTHIC("§c", 8.0, "Mistik");
        
        private final String defaultColor;
        private final double priceMultiplier;
        private final String displayName;
        
        FishTier(String defaultColor, double priceMultiplier, String displayName) {
            this.defaultColor = defaultColor;
            this.priceMultiplier = priceMultiplier;
            this.displayName = displayName;
        }
        
        public String getDefaultColor() { return defaultColor; }

        public String getColor() {
            if (NusaFishing.getInstance() != null && NusaFishing.getInstance().getConfigManager() != null) {
                return NusaFishing.getInstance().getConfigManager().getFishTierColor(this);
            }
            return defaultColor;
        }

        public double getPriceMultiplier() { return priceMultiplier; }
        public String getDisplayName() { return displayName; }
    }
    
    private static final Random random = new Random();
    
    private String id;
    private String name;
    private String description;
    private Material material;
    private int customModelData;
    private String textureProvider; // "vanilla", "nexo", "oraxen", "itemsadder"
    private String textureId;       // Item ID in custom provider
    private double minWeight;
    private double maxWeight;
    private int minQuality;
    private int maxQuality;
    private double basePrice;
    private FishTier baseTier; // Tier dasar ikan
    
    // State for caught fish
    private double weight;
    private int quality;
    private FishTier actualTier; // Tier aktual saat tertangkap (bisa dipengaruhi bonus)
    
    public CustomFish(String id, String name, String description, Material material,
                     int customModelData, double minWeight, double maxWeight,
                     int minQuality, int maxQuality, double basePrice,
                     FishTier baseTier) {
        this(id, name, description, material, customModelData, "vanilla", null,
             minWeight, maxWeight, minQuality, maxQuality, basePrice, baseTier);
    }

    public CustomFish(String id, String name, String description, Material material,
                     int customModelData, String textureProvider, String textureId,
                     double minWeight, double maxWeight,
                     int minQuality, int maxQuality, double basePrice,
                     FishTier baseTier) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.material = material != null ? material : Material.COD;
        this.customModelData = customModelData;
        this.textureProvider = (textureProvider == null || textureProvider.isBlank()) ? "vanilla" : textureProvider.trim().toLowerCase();
        this.textureId = textureId;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.minQuality = minQuality;
        this.maxQuality = maxQuality;
        this.basePrice = basePrice;
        this.baseTier = baseTier == null ? FishTier.COMMON : baseTier;
    }
    
    // Create a new caught fish instance with random weight and quality
    public CustomFish createCaughtInstance(int tierBoost, double qualityBoost, double weightBoost, double maxWeight) {
        CustomFish fish = new CustomFish(id, name, description, material, customModelData,
                textureProvider, textureId, minWeight, maxWeight, minQuality, maxQuality, basePrice, baseTier);
        
        // Generate random weight with boost applied
        double weightRange = Math.min(this.maxWeight, maxWeight) - minWeight;
        double boostedMaxWeight = minWeight + (weightRange * weightBoost);
        
        double randomValue = Math.pow(random.nextDouble(), 0.7);
        fish.weight = minWeight + (randomValue * (boostedMaxWeight - minWeight));
        
        // Apply quality boost
        int boostedMaxQuality = Math.min(100, (int)(maxQuality * qualityBoost));
        fish.quality = minQuality + random.nextInt(boostedMaxQuality - minQuality + 1);
        
        // Calculate actual tier and ENSURE it's never null
        FishTier calculatedTier = calculateActualTier(fish.quality, fish.weight, tierBoost);
        // Safety check - if calculation returns null, use the base tier
        fish.actualTier = (calculatedTier != null) ? calculatedTier : baseTier;
        
        return fish;
    }

    // Overload untuk kompatibilitas ke belakang
    public CustomFish createCaughtInstance(int tierBoost, double qualityBoost, double weightBoost) {
        return createCaughtInstance(tierBoost, qualityBoost, weightBoost, maxWeight);
    }

    // Overload untuk kompatibilitas ke belakang
    public CustomFish createCaughtInstance(int tierBoost, double qualityBoost) {
        return createCaughtInstance(tierBoost, qualityBoost, 1.0, maxWeight);
    }
    
    // Enhance calculateActualTier to ensure it never returns null
    private FishTier calculateActualTier(int quality, double weight, int tierBoost) {
        try {
            // Base tier index
            int tierIndex = baseTier.ordinal();
            
            // Adjust for high quality
            if (quality > 70) {
                tierIndex += (quality - 70) / 25;
            }
            
            // Adjust for exceptional weight
            double weightRange = maxWeight - minWeight;
            if (weightRange > 0) { // Prevent division by zero
                double weightPercentile = (weight - minWeight) / weightRange;
                if (weightPercentile > 0.85) {
                    tierIndex++;
                }
            }
            
            // Add tier boost with diminishing returns
            if (tierBoost > 0) {
                tierIndex += 1 + (int) ((tierBoost - 1) * 0.5);
            }
            
            // Chance for mythic or legendary
            if (random.nextInt(100) < (1.5 * tierBoost) && tierBoost > 0) {
                if (tierIndex >= FishTier.EPIC.ordinal()) {
                    return FishTier.MYTHIC;
                } else if (tierIndex >= FishTier.RARE.ordinal()) {
                    return FishTier.LEGENDARY;
                }
            }
            
            // Ensure tierIndex is within valid range
            if (tierIndex < 0) {
                tierIndex = 0;
            }
            
            // Cap at LEGENDARY tier
            int maxTierIndex = FishTier.values().length - 1;
            tierIndex = Math.min(tierIndex, maxTierIndex);
            
            return FishTier.values()[tierIndex];
        } catch (Exception e) {
            // Fallback to base tier if any error occurs
            return baseTier;
        }
    }
    
    // Get the actual price based on weight, quality, and tier
    public double getPrice() {
        if (id != null && id.startsWith("event_")) {
            return getEventFishPrice();
        }

        // 1. Nilai dasar berdasarkan kualitas dan tier
        double avgQuality = (double) (minQuality + maxQuality) / 2.0;
        double qualityFactor = avgQuality > 0.0 ? (quality / avgQuality) : 1.0;
        double tierFactor = actualTier.getPriceMultiplier();
        double baseValue = basePrice * qualityFactor * tierFactor;
        
        // 2. Faktor berat dengan diminishing returns
        // - Linear sampai 10kg
        // - Logaritmik dari 10kg-100kg
        // - Akar kuadrat di atas 100kg (drastis mengurangi returns)
        double weightFactor;
        if (weight <= 10.0) {
            // Untuk ikan kecil, faktor linier (normal)
            weightFactor = weight / ((minWeight + Math.min(10.0, maxWeight)) / 2);
        } else if (weight <= 100.0) {
            // Untuk ikan medium, logaritmik (pertumbuhan melambat)
            double baseWeight = 10.0;
            weightFactor = 1.0 + (Math.log10(weight / baseWeight) * 2.0);
        } else {
            // Untuk ikan besar, akar kuadrat (drastis melambat)
            double baseWeight = 100.0;
            weightFactor = 3.0 + (Math.sqrt(weight / baseWeight) * 0.5);
        }
        
        // 3. Faktor kesulitan penangkapan - ikan dengan weight mendekati max capacity lebih berharga
        // Hal ini mendorong pemain untuk menangkap ikan pada kapasitas rod mereka
        double difficultyBonus = 1.0;
        
        // 4. Biaya pemeliharaan - mengurangi keuntungan bersih
        // Ikan besar lebih cepat merusak peralatan
        double maintenanceCost = Math.min(weight * 0.01, 10.0); // Maksimal 10 coin
        
        // 5. Hitung total harga dengan formula yang lebih kompleks
        double rawPrice = baseValue * weightFactor * difficultyBonus;
        double finalPrice = Math.max(1.0, rawPrice - maintenanceCost);
        
        // 6. Terapkan market saturation untuk mencegah peternakan ikan
        // (Diterapkan di EconomyManager melalui dynamic pricing)
        
        // 7. Pastikan ikan langka tetap berharga
        if (actualTier == FishTier.MYTHIC) {
            finalPrice = Math.max(finalPrice, basePrice * 3);
        } else if (actualTier == FishTier.LEGENDARY || actualTier == FishTier.EPIC) {
            finalPrice = Math.max(finalPrice, basePrice * 2);
        }
        
        return finalPrice;
    }
    
    /**
     * Balanced price formula for event fish with logarithmic scaling and price caps
     */
    private double getEventFishPrice() {
        double baseValue = basePrice;
        double qualityFactor = 0.5 + (Math.sqrt(quality) / 10.0);
        double weightFactor;
        if (weight < 100.0) {
            weightFactor = 0.8 + (Math.log10(1 + weight) / 5.0);
        } else if (weight < 1000.0) {
            weightFactor = 1.0 + (Math.log10(weight / 100.0) / 3.0);
        } else {
            weightFactor = 1.2 + (Math.log10(weight / 1000.0) / 5.0);
        }
        double tierMultiplier;
        switch (getActualTier()) {
            case MYTHIC: tierMultiplier = 2.0; break;
            case LEGENDARY: tierMultiplier = 1.5; break;
            case EPIC: tierMultiplier = 1.3; break;
            case RARE: tierMultiplier = 1.1; break;
            default: tierMultiplier = 1.0;
        }
        double rawPrice = baseValue * qualityFactor * weightFactor * tierMultiplier;
        double priceCap = 1000000.0;
        if (rawPrice > priceCap) {
            double excess = rawPrice - priceCap;
            double cappedExcess = Math.sqrt(excess) * 1000;
            rawPrice = priceCap + cappedExcess;
            rawPrice = Math.min(rawPrice, 2000000.0);
        }
        return rawPrice;
    }
    
    // Tambahkan method baru untuk format berat:
    public String getFormattedWeight() {
        if (weight < 1.0) {
            return String.format("%.2f g", weight * 1000); // gram
        } else if (weight < 1000.0) {
            return String.format("%.2f kg", weight);       // kilogram
        } else {
            return String.format("%.2f ton", weight / 1000); // ton
        }
    }

    // Create an item stack for this fish with tier coloring
    public ItemStack createItemStack() {
        ItemStack fishItem;
        if (NusaFishing.getInstance() != null && NusaFishing.getInstance().getItemProviderManager() != null) {
            fishItem = NusaFishing.getInstance().getItemProviderManager()
                    .resolveBaseItem(textureProvider, textureId, material, customModelData);
        } else {
            fishItem = new ItemStack(material != null ? material : Material.COD);
            ItemMeta meta = fishItem.getItemMeta();
            if (meta != null && customModelData > 0) {
                CustomModelDataHelper.applyCustomModelData(meta, customModelData);
                fishItem.setItemMeta(meta);
            }
        }

        ItemMeta meta = fishItem.getItemMeta();
        if (meta == null) {
            meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(fishItem.getType());
        }

        meta.setDisplayName(actualTier.getColor() + name + " " + actualTier.getDisplayName());
        List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Berat: §f" + getFormattedWeight());
        lore.add("§7Kualitas: §f" + quality + "/100");
        lore.add("§7Tier: " + actualTier.getColor() + actualTier.getDisplayName());
        lore.add("§7Nilai: §f" + String.format("%.2f", getPrice()));
        lore.add("");
        lore.add("§8" + description);
        meta.setLore(lore);
                
        // Simpan data ikan
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(NamespacedKey.fromString("nusafishing:fish_id"), PersistentDataType.STRING, id);
        container.set(NamespacedKey.fromString("nusafishing:fish_weight"), PersistentDataType.DOUBLE, weight);
        container.set(NamespacedKey.fromString("nusafishing:fish_quality"), PersistentDataType.INTEGER, quality);
        container.set(NamespacedKey.fromString("nusafishing:fish_tier"), PersistentDataType.INTEGER, actualTier.ordinal());
        fishItem.setItemMeta(meta);
        
        return fishItem;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Material getMaterial() { return material; }
    public int getCustomModelData() { return customModelData; }
    public String getTextureProvider() { return textureProvider; }
    public String getTextureId() { return textureId; }
    public double getMinWeight() { return minWeight; }
    public double getMaxWeight() { return maxWeight; }
    public int getMinQuality() { return minQuality; }
    public int getMaxQuality() { return maxQuality; }
    public double getBasePrice() { return basePrice; }
    public FishTier getBaseTier() { return baseTier; }
    public double getWeight() { return weight; }
    public int getQuality() { return quality; }
    public FishTier getActualTier() {
        // Never return null - fall back to base tier if actual tier is null
        return actualTier != null ? actualTier : baseTier;
    }

    /**
     * Set caught fish properties safely without requiring reflection.
     */
    public void applyCaughtData(double weight, int quality, FishTier actualTier) {
        this.weight = weight;
        this.quality = quality;
        this.actualTier = actualTier != null ? actualTier : this.baseTier;
    }
}