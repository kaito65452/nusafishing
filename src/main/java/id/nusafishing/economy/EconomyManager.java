package id.nusafishing.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingRod;

public class EconomyManager {
    private final NusaFishing plugin;
    private Economy economy;
    private boolean economyEnabled = false;
    
    // Settings from config
    private double repairCostPerDurability = 1.0;
    private double fishPriceMultiplier = 1.0;
    private boolean dynamicPricing = true;
    private double priceFluctuation = 0.2;
    
    // Cache for dynamic prices (fish id -> current price multiplier)
    private Map<String, Double> priceDynamics = new java.util.concurrent.ConcurrentHashMap<>();
    private final Random random = new Random();
    
    public EconomyManager(NusaFishing plugin) {
        this.plugin = plugin;
        this.economyEnabled = setupEconomy();
        
        // Load config
        loadConfig();
        
        if (this.economyEnabled) {
            plugin.getLogger().info("Vault economy integration enabled!");
        } else {
            plugin.getLogger().warning("Vault economy integration failed. Economy features disabled.");
        }
        
        // Initialize dynamic price factors for all fish
        if (dynamicPricing) {
            initializeDynamicPrices();
        }
    }
    
    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        
        economy = rsp.getProvider();
        return economy != null;
    }
    
    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        
        // Create default economy config if not exists
        if (!config.contains("economy")) {
            config.set("economy.repair_cost_per_durability", repairCostPerDurability);
            config.set("economy.fish_price_multiplier", fishPriceMultiplier);
            config.set("economy.dynamic_pricing", dynamicPricing);
            config.set("economy.price_fluctuation", priceFluctuation);
            plugin.saveConfig();
        }
        
        // Load values from config
        repairCostPerDurability = config.getDouble("economy.repair_cost_per_durability", 1.0);
        fishPriceMultiplier = config.getDouble("economy.fish_price_multiplier", 1.0);
        dynamicPricing = config.getBoolean("economy.dynamic_pricing", true);
        priceFluctuation = config.getDouble("economy.price_fluctuation", 0.2);
        
        // Clamp fluctuation to valid range
        priceFluctuation = Math.min(1.0, Math.max(0.0, priceFluctuation));
    }
    
    private void initializeDynamicPrices() {
        // Initialize random price factors for all fish
        for (CustomFish fish : plugin.getConfigManager().getCustomFishes().values()) {
            double factor = 1.0 - priceFluctuation + (random.nextDouble() * priceFluctuation * 2);
            priceDynamics.put(fish.getId(), factor);
        }
    }
    
    /**
     * Calculate the actual price for a fish with all modifiers applied
     * @param fish The fish to calculate price for
     * @return The final price
     */
    public double calculateFishPrice(CustomFish fish) {
        double basePrice = fish.getPrice();
        
        // Apply global fish price multiplier
        double price = basePrice * fishPriceMultiplier;
        
        // Apply dynamic pricing if enabled
        if (dynamicPricing && priceDynamics.containsKey(fish.getId())) {
            price *= priceDynamics.get(fish.getId());
        }
        
        return price;
    }
    
    /**
     * Get the repair cost based on durability lost and rod tier
     * @param durabilityLost Amount of durability to repair
     * @param rodTier Tier of the fishing rod
     * @return The cost to repair
     */
    public double getRepairCost(int durabilityLost, FishingRod.RodTier rodTier) {
        double baseCost = durabilityLost * repairCostPerDurability;
        double multiplier = 1.0; // Default multiplier
        
        // Get multiplier based on tier from config
        if (rodTier != null) {
            multiplier = plugin.getConfig().getDouble(
                "economy.repair_cost_multipliers." + rodTier.name(), 1.0);
        }
        
        return baseCost * multiplier;
    }

    /**
     * Maintain original method for backward compatibility
     */
    public double getRepairCost(int durabilityLost) {
        return durabilityLost * repairCostPerDurability;
    }
    
    /**
     * Update dynamic price for a fish after it is sold
     * @param fishId The ID of the fish that was sold
     * @param amount Number of fish sold
     */
    public void updateDynamicPrice(String fishId, int amount) {
        if (!dynamicPricing) return;
        
        // Lebih agresif mengurangi harga berdasarkan tier ikan
        CustomFish fish = plugin.getConfigManager().getCustomFishes().get(fishId);
        if (fish == null) return;
        
        double currentFactor = priceDynamics.getOrDefault(fishId, 1.0);
        double reduction;
        
        // Higher tier fish lose value faster when market gets saturated
        switch (fish.getBaseTier()) {
            case MYTHIC:
                reduction = 0.04 * amount; // 4% per fish
                break;
            case LEGENDARY:
                reduction = 0.03 * amount; // 3% per fish
                break;
            case EPIC:
                reduction = 0.025 * amount; // 2.5% per fish
                break;
            case RARE:
                reduction = 0.02 * amount; // 2% per fish
                break;
            case UNCOMMON:
                reduction = 0.015 * amount; // 1.5% per fish
                break;
            default: // COMMON
                reduction = 0.01 * amount; // 1% per fish
                break;
        }
        
        // Calculate new factor with minimum of 30% of original price (harsher limit)
        double newFactor = Math.max(0.3, currentFactor - reduction);
        priceDynamics.put(fishId, newFactor);
        
        // Log if price changes significantly
        if ((currentFactor - newFactor) > 0.1) {
            plugin.getLogger().info("Fish market price for " + fishId + " dropped by " + 
                String.format("%.1f%%", (currentFactor - newFactor) * 100) + 
                " due to market saturation.");
        }
    }
    
    /**
     * Regenerate prices over time
     * This would be called by a scheduler
     */
    public void regeneratePrices() {
        if (!dynamicPricing) return;
        
        for (Map.Entry<String, Double> entry : priceDynamics.entrySet()) {
            String fishId = entry.getKey();
            double currentFactor = entry.getValue();
            
            if (Math.abs(currentFactor - 1.0) > 0.0001) {
                // Get fish tier
                CustomFish fish = plugin.getConfigManager().getCustomFishes().get(fishId);
                double recoveryRate;
                
                if (fish != null) {
                    // Recovery rate based on tier
                    switch (fish.getBaseTier()) {
                        case MYTHIC:
                            recoveryRate = 0.005; // 0.5% recovery per cycle
                            break;
                        case LEGENDARY:
                            recoveryRate = 0.01; // 1% recovery per cycle
                            break;
                        case EPIC:
                            recoveryRate = 0.015; // 1.5% recovery
                            break;
                        case RARE:
                            recoveryRate = 0.02; // 2% recovery
                            break;
                        case UNCOMMON:
                            recoveryRate = 0.025; // 2.5% recovery
                            break;
                        default: // COMMON
                            recoveryRate = 0.03; // 3% recovery
                            break;
                    }
                } else {
                    recoveryRate = 0.02; // Default 2%
                }
                
                // Gradually move price back toward normal equilibrium (1.0)
                double newFactor;
                if (currentFactor < 1.0) {
                    newFactor = Math.min(1.0, currentFactor + recoveryRate);
                } else {
                    newFactor = Math.max(1.0, currentFactor - recoveryRate);
                }
                priceDynamics.put(fishId, newFactor);
            }
        }
    }
    
    // Vault API wrapper methods
    
    public boolean isEconomyEnabled() {
        return economyEnabled && economy != null;
    }
    
    public boolean hasMoney(OfflinePlayer player, double amount) {
        if (!isEconomyEnabled()) return false;
        return economy.has(player, amount);
    }
    
    public boolean withdrawMoney(OfflinePlayer player, double amount) {
        if (!isEconomyEnabled()) return false;
        
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }
    
    public boolean depositMoney(OfflinePlayer player, double amount) {
        if (!isEconomyEnabled()) return false;
        
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }
    
    public String formatMoney(double amount) {
        if (!isEconomyEnabled()) return String.format("%.1f", amount);
        return economy.format(amount);
    }
    
    public double getBalance(OfflinePlayer player) {
        if (!isEconomyEnabled()) return 0;
        return economy.getBalance(player);
    }
    
    public String getCurrencyNameSingular() {
        if (!isEconomyEnabled()) return "coin";
        return economy.currencyNameSingular();
    }
    
    public String getCurrencyNamePlural() {
        if (!isEconomyEnabled()) return "coins";
        return economy.currencyNamePlural();
    }
}