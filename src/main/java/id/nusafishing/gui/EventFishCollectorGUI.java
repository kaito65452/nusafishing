package id.nusafishing.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import id.nusafishing.NusaFishing;
import id.nusafishing.economy.EconomyManager;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.util.ItemBuilder;
import id.nusafishing.event.EventFish;
import id.nusafishing.event.ConfigEventFish;
import id.nusafishing.fishing.model.FishingBait;

public class EventFishCollectorGUI extends GUI {
    private final NusaFishing plugin;
    private final EconomyManager economy;
    private final Random random = new Random();
    
    // Bonus rewards tracking
    private static final Map<UUID, Long> lastEventFishSale = new HashMap<>();
    private static final Map<UUID, Integer> consecutiveSales = new HashMap<>();
    
    // Constants for the GUI
    private static final String GUI_TITLE = "§d§l✦ Kolektor Ikan Mistik ✦";
    
    // Price multiplier for event fish (higher than regular fish)
    private static final double EVENT_FISH_PRICE_MULTIPLIER = 1.5; // Reduced from 3.0
    
    // Possible bonus rewards
    private enum BonusReward {
        EXP("§b+%d EXP Memancing Bonus", 0),
        MONEY("§a+%d%% Bonus Uang", 1),
        BAIT("§eUmpan Premium Bonus", 2),
        ROD_REPAIR("§6Perbaikan Pancingan Gratis", 3);
        
        private final String format;
        private final int id;
        
        BonusReward(String format, int id) {
            this.format = format;
            this.id = id;
        }
        
        public String getFormatted(int value) {
            return String.format(format, value);
        }
        
        public static BonusReward fromId(int id) {
            for (BonusReward reward : values()) {
                if (reward.id == id) return reward;
            }
            return EXP;
        }
    }
    
    /**
     * Create a new event fish collector GUI
     * @param player The player viewing the GUI
     * @param plugin The plugin instance
     */
    public EventFishCollectorGUI(Player player, NusaFishing plugin) {
        super(player, GUI_TITLE, 6); // 6 rows inventory
        this.plugin = plugin;
        this.economy = plugin.getEconomyManager();
        
        // Initialize the GUI
        setupGui();
    }
    
    /**
     * Setup the GUI layout
     */
    private void setupGui() {
        // Reset the inventory
        inventory.clear();
        clickHandlers.clear();
        
        // Create a fancy border
        fillBorder(Material.PURPLE_STAINED_GLASS_PANE);
        
        // Set decorative corners
        setItem(0, new ItemBuilder(Material.END_CRYSTAL)
                .name("§d§l✦ Kolektor Ikan Mistik ✦")
                .build());
        
        setItem(8, new ItemBuilder(Material.END_CRYSTAL)
                .name("§d§l✦ Kolektor Ikan Mistik ✦")
                .build());
        
        setItem(45, new ItemBuilder(Material.END_CRYSTAL)
                .name("§d§l✦ Kolektor Ikan Mistik ✦")
                .build());
        
        setItem(53, new ItemBuilder(Material.END_CRYSTAL)
                .name("§d§l✦ Kolektor Ikan Mistik ✦")
                .build());
        
        // Add the collector NPC "character" in the middle
        setItem(4, new ItemBuilder(Material.PLAYER_HEAD)
                .name("§e§lAhli Biologi Laut")
                .lore("§7\"Saya akan membayar mahal untuk")
                .lore("§7spesimen ikan mistik langka!\"")
                .build());
        
        // Add sell all button
        setItem(49, new ItemBuilder(Material.GOLD_BLOCK)
                .name("§e§lJual Semua Ikan Mistik")
                .lore("§7Jual semua ikan event untuk")
                .lore("§7mendapatkan harga premium dan")
                .lore("§7bonus istimewa!")
                .lore("")
                .lore("§d✦ §fHarga 3x lipat dari normal")
                .lore("§d✦ §fKesempatan mendapat bonus")
                .build(), () -> sellAllEventFish());
        
        // Show player information and streak
        updatePlayerInfo();
        
        // Display event fish from player inventory
        displayEventFish();
    }
    
    /**
     * Update the player info display
     */
    private void updatePlayerInfo() {
        // Get player streak
        int streak = consecutiveSales.getOrDefault(player.getUniqueId(), 0);
        
        // Get balance
        String balanceText = economy.formatMoney(economy.getBalance(player));
        
        // Create info item
        ItemStack infoItem = new ItemBuilder(Material.BOOK)
                .name("§e§l" + player.getName())
                .lore("§7Saldo: §f" + balanceText)
                .lore("§7Streak Penjualan: §f" + streak)
                .lore("")
                .lore("§d✦ §fStreak meningkatkan bonus yang kamu dapatkan!")
                .build();
        
        setItem(8, infoItem);
    }
    
    /**
     * Fill the border of the inventory with a specific material
     * @param material The material to use
     */
    private void fillBorder(Material material) {
        ItemStack item = new ItemBuilder(material).name(" ").build();
        
        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            setItem(i, item);
            setItem(45 + i, item);
        }
        
        // Left and right borders
        for (int i = 1; i < 5; i++) {
            setItem(i * 9, item);
            setItem(i * 9 + 8, item);
        }
    }
    
    /**
     * Display all event fish from player's inventory in the GUI
     */
    private void displayEventFish() {
        List<ItemStack> eventFishItems = new ArrayList<>();
        Map<Integer, ItemStack> slotToItem = new HashMap<>();
        
        // Find all event fish in the player's inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isEventFish(item)) {
                eventFishItems.add(item);
                slotToItem.put(i, item);
            }
        }
        
        // If no event fish found, show a message
        if (eventFishItems.isEmpty()) {
            setItem(22, new ItemBuilder(Material.BARRIER)
                    .name("§c§lTidak Ada Ikan Mistik")
                    .lore("§7Kamu belum memiliki ikan event mistik.")
                    .lore("§7Dapatkan dari event memancing spesial!")
                    .build());
            return;
        }
        
        // Otherwise, display all event fish
        int slot = 10;
        for (Map.Entry<Integer, ItemStack> entry : slotToItem.entrySet()) {
            int invSlot = entry.getKey();
            ItemStack fishItem = entry.getValue();
            CustomFish fish = getFishFromItem(fishItem);
            
            if (fish != null) {
                // Calculate the premium price
                double basePrice = fish.getPrice();
                double premiumPrice = basePrice * EVENT_FISH_PRICE_MULTIPLIER;
                
                // Create a display item with sell button
                ItemStack displayItem = new ItemBuilder(fishItem.clone())
                        .lore("")
                        .lore("§e§lHarga Premium: §f" + economy.formatMoney(premiumPrice))
                        .lore("§7(§f" + fishItem.getAmount() + "x§7 = §f" + 
                                economy.formatMoney(premiumPrice * fishItem.getAmount()) + "§7)")
                        .lore("")
                        .lore("§aKlik untuk jual!")
                        .build();
                
                // Set the item in the GUI with a click handler
                final int currentGuiSlot = slot;
                setItem(slot, displayItem, () -> sellEventFish(currentGuiSlot, invSlot, fishItem, fish, premiumPrice));
                
                // Move to next slot
                slot++;
                if ((slot % 9) == 8) slot += 2; // Skip the border
                if (slot >= 45) break; // Don't overflow the GUI
            }
        }
    }
    
    /**
     * Check if an item is an event fish
     * @param item The item to check
     * @return true if the item is an event fish
     */
    private boolean isEventFish(ItemStack item) {
        if (item == null) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey cleanFishIdKey = NamespacedKey.fromString("nusafishing:fish_id");
        NamespacedKey legacyFishIdKey = new NamespacedKey(plugin, "fish_id");
        
        String fishId = null;
        if (cleanFishIdKey != null && container.has(cleanFishIdKey, PersistentDataType.STRING)) {
            fishId = container.get(cleanFishIdKey, PersistentDataType.STRING);
        } else if (container.has(legacyFishIdKey, PersistentDataType.STRING)) {
            fishId = container.get(legacyFishIdKey, PersistentDataType.STRING);
        }
        return fishId != null && fishId.startsWith("event_");
    }
    
    /**
     * Get a CustomFish from an ItemStack
     * @param item The ItemStack to check
     * @return The CustomFish, or null if not a fish
     */
    private CustomFish getFishFromItem(ItemStack item) {
        if (item == null) return null;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey cleanFishIdKey = NamespacedKey.fromString("nusafishing:fish_id");
        NamespacedKey legacyFishIdKey = new NamespacedKey(plugin, "fish_id");
        
        String fishId = null;
        if (cleanFishIdKey != null && container.has(cleanFishIdKey, PersistentDataType.STRING)) {
            fishId = container.get(cleanFishIdKey, PersistentDataType.STRING);
        } else if (container.has(legacyFishIdKey, PersistentDataType.STRING)) {
            fishId = container.get(legacyFishIdKey, PersistentDataType.STRING);
        }
        
        if (fishId == null) return null;

        NamespacedKey cleanWeightKey = NamespacedKey.fromString("nusafishing:fish_weight");
        NamespacedKey legacyWeightKey = new NamespacedKey(plugin, "fish_weight");
        NamespacedKey cleanQualityKey = NamespacedKey.fromString("nusafishing:fish_quality");
        NamespacedKey legacyQualityKey = new NamespacedKey(plugin, "fish_quality");
        NamespacedKey cleanTierKey = NamespacedKey.fromString("nusafishing:fish_tier");
        NamespacedKey legacyTierKey = new NamespacedKey(plugin, "fish_tier");

        CustomFish templateFish = null;
        
        // 1. Check ConfigEventFish (from config.yml)
        ConfigEventFish configEventFish = ConfigEventFish.getEventFish(fishId);
        if (configEventFish != null) {
            templateFish = configEventFish.createTemplateFish();
        }

        // 2. Check EventFish enum constants
        if (templateFish == null && fishId.startsWith("event_")) {
            String enumName = fishId.substring(6);
            for (EventFish eventFish : EventFish.values()) {
                if (eventFish.name().equalsIgnoreCase(enumName)) {
                    templateFish = eventFish.createCustomFish();
                    break;
                }
            }
        }

        // 3. Fallback to regular CustomFishes from configManager
        if (templateFish == null) {
            templateFish = plugin.getConfigManager().getCustomFishes().get(fishId);
        }

        if (templateFish == null) return null;

        // Create new instance from template
        CustomFish fish = new CustomFish(
            templateFish.getId(), 
            templateFish.getName(), 
            templateFish.getDescription(),
            templateFish.getMaterial(),
            templateFish.getCustomModelData(),
            templateFish.getTextureProvider(),
            templateFish.getTextureId(),
            templateFish.getMinWeight(),
            templateFish.getMaxWeight(),
            templateFish.getMinQuality(),
            templateFish.getMaxQuality(),
            templateFish.getBasePrice(),
            templateFish.getBaseTier()
        );

        // Extract caught stats from ItemStack PersistentDataContainer
        double weight = templateFish.getMinWeight();
        if (cleanWeightKey != null && container.has(cleanWeightKey, PersistentDataType.DOUBLE)) {
            weight = container.get(cleanWeightKey, PersistentDataType.DOUBLE);
        } else if (container.has(legacyWeightKey, PersistentDataType.DOUBLE)) {
            weight = container.get(legacyWeightKey, PersistentDataType.DOUBLE);
        }

        int quality = templateFish.getMinQuality();
        if (cleanQualityKey != null && container.has(cleanQualityKey, PersistentDataType.INTEGER)) {
            quality = container.get(cleanQualityKey, PersistentDataType.INTEGER);
        } else if (container.has(legacyQualityKey, PersistentDataType.INTEGER)) {
            quality = container.get(legacyQualityKey, PersistentDataType.INTEGER);
        }

        int tierOrdinal = templateFish.getBaseTier().ordinal();
        if (cleanTierKey != null && container.has(cleanTierKey, PersistentDataType.INTEGER)) {
            tierOrdinal = container.get(cleanTierKey, PersistentDataType.INTEGER);
        } else if (container.has(legacyTierKey, PersistentDataType.INTEGER)) {
            tierOrdinal = container.get(legacyTierKey, PersistentDataType.INTEGER);
        }
        if (tierOrdinal < 0 || tierOrdinal >= CustomFish.FishTier.values().length) {
            tierOrdinal = templateFish.getBaseTier().ordinal();
        }
        CustomFish.FishTier actualTier = CustomFish.FishTier.values()[tierOrdinal];

        fish.applyCaughtData(weight, quality, actualTier);
        return fish;
    }
    
    /**
     * Sell a specific event fish
     * @param guiSlot The slot in the GUI
     * @param inventorySlot The inventory slot of the fish
     * @param fishItem The ItemStack of the fish
     * @param fish The CustomFish data
     * @param unitPrice The price per fish
     */
    private void sellEventFish(int guiSlot, int inventorySlot, ItemStack fishItem, CustomFish fish, double unitPrice) {
        // Immediately remove click handler and clear GUI slot to prevent double-click dupe
        clickHandlers.remove(guiSlot);
        inventory.setItem(guiSlot, null);

        // Verify the item is still actually in the player's inventory slot
        ItemStack currentItem = player.getInventory().getItem(inventorySlot);
        if (currentItem == null || !isEventFish(currentItem)) {
            player.sendMessage("§cIkan tidak ditemukan di inventory!");
            setupGui();
            return;
        }

        int amount = currentItem.getAmount();
        double totalPrice = unitPrice * amount;
        
        // Remove the fish from the player's inventory
        player.getInventory().setItem(inventorySlot, null);
        
        // Play collection animation
        playCollectionAnimation();
        
        // Add money to the player
        economy.depositMoney(player, totalPrice);
        
        // Increment the player's streak
        incrementStreak(player.getUniqueId());
        
        // Notify the player
        player.sendMessage("§d§l[KOLEKTOR MISTIK] §fKamu menjual §b" + amount + "x " + fish.getName() + 
                " §fdengan harga premium §e" + economy.formatMoney(totalPrice) + "§f!");
        
        // Check for bonus rewards based on streak
        if (random.nextDouble() < getRewardChance(player.getUniqueId())) {
            giveRandomBonus(totalPrice);
        }
        
        // Refresh the GUI immediately
        setupGui();
    }
    
    /**
     * Sell all event fish in the player's inventory
     */
    private void sellAllEventFish() {
        // Prevent spam clicking the sell-all button
        clickHandlers.remove(49);

        // Get all event fish from inventory
        List<Integer> eventFishSlots = new ArrayList<>();
        Map<String, Integer> fishCounts = new HashMap<>();
        
        // Find all slots containing event fish
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isEventFish(item)) {
                eventFishSlots.add(i);
                
                // Track fish types for summary
                CustomFish fish = getFishFromItem(item);
                if (fish != null) {
                    fishCounts.put(fish.getName(), fishCounts.getOrDefault(fish.getName(), 0) + item.getAmount());
                }
            }
        }
        
        // If no event fish found
        if (eventFishSlots.isEmpty()) {
            player.sendMessage("§c§l[KOLEKTOR MISTIK] §fKamu tidak memiliki ikan mistik untuk dijual!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }
        
        // Calculate total
        double totalEarnings = 0;
        int totalFishSold = 0;
        
        // Process each event fish
        for (int slot : eventFishSlots) {
            ItemStack fishItem = player.getInventory().getItem(slot);
            if (fishItem != null) {
                CustomFish fish = getFishFromItem(fishItem);
                if (fish != null) {
                    double basePrice = fish.getPrice();
                    double premiumPrice = basePrice * EVENT_FISH_PRICE_MULTIPLIER;
                    
                    totalEarnings += premiumPrice * fishItem.getAmount();
                    totalFishSold += fishItem.getAmount();
                    
                    // Remove the fish
                    player.getInventory().setItem(slot, null);
                }
            }
        }
        
        // Add money to the player
        economy.depositMoney(player, totalEarnings);
        
        // Increment the player's streak
        incrementStreak(player.getUniqueId());
        
        // Play collection animation (more dramatic for bulk sale)
        playBulkCollectionAnimation();
        
        // Create a summary message
        StringBuilder summary = new StringBuilder();
        summary.append("§d§l[KOLEKTOR MISTIK] §fDaftar Ikan Terjual:\n");
        
        for (Map.Entry<String, Integer> entry : fishCounts.entrySet()) {
            summary.append("§e• §b").append(entry.getKey()).append(" §fx").append(entry.getValue()).append("\n");
        }
        
        summary.append("\n§fTotal pendapatan: §e").append(economy.formatMoney(totalEarnings)).append("§f!");
        
        // Show to player
        player.sendMessage(summary.toString());
        
        // Always give a bonus when selling all
        giveRandomBonus(totalEarnings);
        
        // Refresh the GUI immediately
        setupGui();
    }
    
    /**
     * Play a collection animation when selling a fish
     */
    private void playCollectionAnimation() {
        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        
        // Spawn particles
        player.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
    }
    
    /**
     * Play a more dramatic animation for bulk sales
     */
    private void playBulkCollectionAnimation() {
        // Play multiple sounds
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0f, 1.0f);
        
        // Create a particle animation sequence
        new BukkitRunnable() {
            int ticks = 0;
            
            @Override
            public void run() {
                if (ticks >= 20) {
                    this.cancel();
                    return;
                }
                
                // Spiral particles
                double angle = ticks * Math.PI / 10;
                double radius = ticks * 0.1;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                
                player.spawnParticle(Particle.END_ROD, player.getLocation().add(x, 1, z), 5, 0.1, 0.1, 0.1, 0.05);
                
                if (ticks % 5 == 0) {
                    if (id.nusafishing.compat.CompatibilityHelper.TOTEM != null) {
                        player.spawnParticle(id.nusafishing.compat.CompatibilityHelper.TOTEM, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                    }
                }
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    /**
     * Increment player's streak counter
     * @param playerId The player's UUID
     */
    private void incrementStreak(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        long lastSale = lastEventFishSale.getOrDefault(playerId, 0L);
        
        // Check if this is within streak time (24 hours)
        if (currentTime - lastSale < 24 * 60 * 60 * 1000 || lastSale == 0) {
            // Increment streak
            int currentStreak = consecutiveSales.getOrDefault(playerId, 0);
            consecutiveSales.put(playerId, currentStreak + 1);
        } else {
            // Reset streak
            consecutiveSales.put(playerId, 1);
        }
        
        // Update last sale time
        lastEventFishSale.put(playerId, currentTime);
    }
    
    /**
     * Get the chance of receiving a reward based on the player's streak
     * @param playerId The player's UUID
     * @return The chance (0.0-1.0) of getting a bonus
     */
    private double getRewardChance(UUID playerId) {
        int streak = consecutiveSales.getOrDefault(playerId, 0);
        
        // Base chance is 30%, increasing by 5% per streak up to 80%
        return Math.min(0.3 + (streak * 0.05), 0.8);
    }
    
    /**
     * Give a random bonus to the player based on transaction value
     */
    private void giveRandomBonus(double transactionTotal) {
        // Choose a random bonus type
        BonusReward bonusType = BonusReward.fromId(random.nextInt(4));
        
        // Get player streak for bonus calculation
        int streak = consecutiveSales.getOrDefault(player.getUniqueId(), 1);
        
        switch (bonusType) {
            case EXP:
                // Give fishing XP bonus
                int expBonus = 100 * streak; // 100 base XP per streak level
                plugin.getSkillManager().addFishingExp(player, expBonus);
                
                // Notify player
                player.sendMessage("§d§l[BONUS MISTIK] §f" + bonusType.getFormatted(expBonus));
                break;
                
            case MONEY:
                // Give money bonus as percentage of this sale transaction
                int percentBonus = 5 + (streak * 2); // 5% + 2% per streak
                double bonus = Math.max(10.0, transactionTotal * (percentBonus / 100.0));
                
                // Add the bonus money
                economy.depositMoney(player, bonus);
                
                // Notify player
                player.sendMessage("§d§l[BONUS MISTIK] §f" + bonusType.getFormatted(percentBonus) + 
                                   " §7(" + economy.formatMoney(bonus) + ")");
                break;
                
            case BAIT:
                // Give premium bait based on streak level
                int baitAmount = Math.min(3, streak);
                
                // Get bait from configuration
                FishingBait bait = plugin.getConfigManager().getFishingBaits().get("rare_bait");
                if (bait != null) {
                    // Run command to give bait
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                            "nusafishing bait rare_bait " + player.getName() + " " + baitAmount);
                    
                    // Notify player
                    player.sendMessage("§d§l[BONUS MISTIK] §f" + bonusType.getFormatted(0) + 
                                       " §7(" + baitAmount + "x)");
                }
                break;
                
            case ROD_REPAIR:
                // Find the fishing rod in player's inventory
                ItemStack rod = player.getInventory().getItemInMainHand();
                if (rod.getType() != Material.FISHING_ROD) {
                    // If not holding a rod, check whole inventory
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.getType() == Material.FISHING_ROD) {
                            rod = item;
                            break;
                        }
                    }
                }
                
                // If we found a rod, repair it
                if (rod != null && rod.getType() == Material.FISHING_ROD) {
                    // Run repair command
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), 
                            "nusafishing repair " + player.getName());
                    
                    // Notify player
                    player.sendMessage("§d§l[BONUS MISTIK] §f" + bonusType.getFormatted(0));
                } else {
                    // Fallback to EXP if no rod found
                    int fallbackExp = 150 * streak;
                    plugin.getSkillManager().addFishingExp(player, fallbackExp);
                    player.sendMessage("§d§l[BONUS MISTIK] §f" + BonusReward.EXP.getFormatted(fallbackExp) + 
                                      " §7(tidak ditemukan pancingan)");
                }
                break;
        }
        
        // Play bonus effect
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        if (id.nusafishing.compat.CompatibilityHelper.WITCH != null) {
            player.spawnParticle(id.nusafishing.compat.CompatibilityHelper.WITCH, player.getLocation().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.1);
        }
    }
}