package id.nusafishing.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import id.nusafishing.NusaFishing;
import id.nusafishing.economy.EconomyManager;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.util.ItemBuilder;

public class ShopGUI extends GUI {
    private final NusaFishing plugin;
    private final EconomyManager economy;
    
    // Shop sections
    private static final int SECTION_MAIN = 0;
    private static final int SECTION_BUY_RODS = 1;
    private static final int SECTION_BUY_BAITS = 2;
    private static final int SECTION_SELL_FISH = 3;
    
    private int currentSection = SECTION_MAIN;
    private int currentPage = 0;
    private int maxPage = 0;
    
    // Constants for pricing
    private static final double ROD_PRICE_MULTIPLIER = 1.5;
    private static final double BAIT_PRICE_MULTIPLIER = 0.8;
    
    /**
     * Create a new shop GUI
     * 
     * @param player The player viewing the shop
     * @param plugin The plugin instance
     */
    public ShopGUI(Player player, NusaFishing plugin) {
        super(player, "§8NusaFishing Shop", 6); // 6 rows of inventory
        this.plugin = plugin;
        this.economy = plugin.getEconomyManager();
        
        // Initialize the main menu
        showMainMenu();
    }
    
    /**
     * Show the main menu of the shop
     */
    private void showMainMenu() {
        currentSection = SECTION_MAIN;
        inventory.clear();
        clickHandlers.clear();
        
        // Fill the border with gray glass
        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        // Set the shop sections
        setItem(20, new ItemBuilder(Material.FISHING_ROD)
                .name("§b§lBeli Pancingan")
                .lore("§7Klik untuk melihat pancingan yang tersedia")
                .flag(ItemFlag.HIDE_ATTRIBUTES)
                .build(),
                () -> showRodShop(0));
        
        setItem(22, new ItemBuilder(Material.WHEAT_SEEDS)
                .name("§a§lBeli Umpan")
                .lore("§7Klik untuk melihat umpan yang tersedia")
                .customModelData(2001) // Assuming this is a bait model
                .build(),
                () -> showBaitShop(0));
        
        setItem(24, new ItemBuilder(Material.COD)
                .name("§e§lJual Ikan")
                .lore("§7Klik untuk menjual ikan yang kamu miliki")
                .build(),
                () -> showFishSellShop(0));
        
        // Show player's balance at the bottom
        updateBalanceDisplay();
    }
    
    /**
     * Show the rod shop section
     * 
     * @param page The page to show
     */
    private void showRodShop(int page) {
        currentSection = SECTION_BUY_RODS;
        currentPage = page;
        inventory.clear();
        clickHandlers.clear();
        
        // Fill the border with glass
        fillBorder(Material.BLUE_STAINED_GLASS_PANE);
        
        // Set the title
        setItem(4, new ItemBuilder(Material.FISHING_ROD)
                .name("§b§lToko Pancingan")
                .lore("§7Pilih pancingan untuk dibeli")
                .flag(ItemFlag.HIDE_ATTRIBUTES)
                .build());
        
        // Get all available rods
        List<FishingRod> availableRods = new ArrayList<>(plugin.getConfigManager().getFishingRods().values());
        
        // Calculate pages
        int itemsPerPage = 21; // 3 rows of 7 items
        maxPage = (availableRods.size() - 1) / itemsPerPage;
        currentPage = Math.max(0, Math.min(currentPage, maxPage));
        
        // Add rods to inventory
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableRods.size());
        
        int slot = 10; // Start at the second row, second column
        for (int i = startIndex; i < endIndex; i++) {
            FishingRod rod = availableRods.get(i);
            double price = calculateRodPrice(rod);
            
            ItemStack rodItem = rod.createDisplayItem(price, economy.hasMoney(player, price));
            
            // Add to inventory with click handler that buys the rod
            setItem(slot, rodItem, () -> {
                if (economy.hasMoney(player, price)) {
                    buyRod(rod.getId(), price);
                } else {
                    player.sendMessage("§cUang kamu tidak cukup untuk membeli ini!");
                }
            });
            
            // Move to the next slot
            slot++;
            if ((slot % 9) == 8) slot += 2; // Skip the border
        }
        
        // Add navigation buttons
        addNavigationButtons();
        
        // Show player's balance at the bottom
        updateBalanceDisplay();
    }
    
    /**
     * Show the bait shop section
     * 
     * @param page The page to show
     */
    private void showBaitShop(int page) {
        currentSection = SECTION_BUY_BAITS;
        currentPage = page;
        inventory.clear();
        clickHandlers.clear();
        
        // Fill the border with glass
        fillBorder(Material.GREEN_STAINED_GLASS_PANE);
        
        // Set the title
        setItem(4, new ItemBuilder(Material.WHEAT_SEEDS)
                .name("§a§lToko Umpan")
                .lore("§7Pilih umpan untuk dibeli")
                .customModelData(2001) // Assuming this is a bait model
                .build());
        
        // Get all available baits
        List<FishingBait> availableBaits = new ArrayList<>(plugin.getConfigManager().getFishingBaits().values());
        
        // Calculate pages
        int itemsPerPage = 21; // 3 rows of 7 items
        maxPage = (availableBaits.size() - 1) / itemsPerPage;
        currentPage = Math.max(0, Math.min(currentPage, maxPage));
        
        // Add baits to inventory
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, availableBaits.size());
        
        int slot = 10; // Start at the second row, second column
        for (int i = startIndex; i < endIndex; i++) {
            FishingBait bait = availableBaits.get(i);
            double price = calculateBaitPrice(bait);
            
            ItemStack baitItem = new ItemBuilder(bait.getMaterial())
                    .name("§a" + bait.getName())
                    .lore("§8" + bait.getDescription())
                    .lore("")
                    .lore("§7Daya tarik: §f+" + (int)((bait.getAttractionModifier() - 1.0) * 100) + "%")
                    .lore("§7Cocok untuk: §f" + formatFishNames(bait.getValidFish()))
                    .lore("")
                    .lore("§7Harga: §f" + economy.formatMoney(price) + " / item")
                    .lore("")
                    .lore(economy.hasMoney(player, price) ? 
                            "§a► Klik untuk membeli (1x)" : "§c✘ Uang tidak cukup")
                    .lore("§a► Shift+Klik untuk membeli (5x)")
                    .customModelData(bait.getCustomModelData())
                    .build();
            
            // Add to inventory with click handler that buys the bait
            setItem(slot, baitItem, () -> {
                // If shift-clicking, try to buy 5
                int amount = player.isSneaking() ? 5 : 1;
                double totalPrice = price * amount;
                
                if (economy.hasMoney(player, totalPrice)) {
                    buyBait(bait.getId(), amount, totalPrice);
                } else {
                    player.sendMessage("§cUang kamu tidak cukup untuk membeli ini!");
                }
            });
            
            // Move to the next slot
            slot++;
            if ((slot % 9) == 8) slot += 2; // Skip the border
        }
        
        // Add navigation buttons
        addNavigationButtons();
        
        // Show player's balance at the bottom
        updateBalanceDisplay();
    }
    
    /**
     * Show the fish selling section
     * 
     * @param page The page to show
     */
    private void showFishSellShop(int page) {
        currentSection = SECTION_SELL_FISH;
        currentPage = page;
        inventory.clear();
        clickHandlers.clear();
        
        // Fill the border with glass
        fillBorder(Material.YELLOW_STAINED_GLASS_PANE);
        
        // Set the title
        setItem(4, new ItemBuilder(Material.COD)
                .name("§e§lJual Ikan")
                .lore("§7Pilih ikan untuk dijual")
                .build());
        
        // Add a "Sell All" button
        setItem(49, new ItemBuilder(Material.GOLD_INGOT)
                .name("§e§lJual Semua Ikan")
                .lore("§7Klik untuk menjual semua ikan di inventory")
                .build(), () -> {
                    sellAllFish();
                });
        
        // Collect all fish from player's inventory
        List<Integer> fishSlotIndices = new ArrayList<>();
        List<ItemStack> fishItems = new ArrayList<>();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack item = storage[i];
            if (item != null && isFish(item)) {
                fishSlotIndices.add(i);
                fishItems.add(item);
            }
        }
        
        // Calculate pages
        int itemsPerPage = 21; // 3 rows of 7 items
        maxPage = (fishItems.size() - 1) / itemsPerPage;
        currentPage = Math.max(0, Math.min(currentPage, maxPage));
        
        // Add fish to inventory
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, fishItems.size());
        
        int slot = 10; // Start at the second row, second column
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack fishItem = fishItems.get(i);
            int storageSlot = fishSlotIndices.get(i);
            CustomFish fish = getFishFromItem(fishItem);
            
            if (fish != null) {
                double price = economy.calculateFishPrice(fish);
                
                List<String> lore = new ArrayList<>();
                lore.add("§8" + fish.getDescription());
                lore.add("");
                lore.add("§7Kualitas: §f" + fish.getQuality() + "/100 §7| Berat: §f" + String.format("%.2f", fish.getWeight()) + " kg");
                lore.add("§7Harga: §f" + economy.formatMoney(price * fishItem.getAmount()) + " §7(" + economy.formatMoney(price) + " x " + fishItem.getAmount() + ")");
                lore.add("");
                lore.add("§a► Klik untuk menjual");
                
                ItemStack displayItem = new ItemBuilder(fishItem)
                        .lore(lore)
                        .build();
                
                // Add to inventory with click handler that sells the fish
                setItem(slot, displayItem, () -> {
                    sellFish(storageSlot, fishItem, fish, price);
                    // Refresh the inventory after selling
                    showFishSellShop(currentPage);
                });
            }
            
            // Move to the next slot
            slot++;
            if ((slot % 9) == 8) slot += 2; // Skip the border
        }
        
        // Add navigation buttons
        addNavigationButtons();
        
        // Show player's balance at the bottom
        updateBalanceDisplay();
    }
    
    /**
     * Add navigation buttons to the current inventory
     */
    private void addNavigationButtons() {
        // Back button
        setItem(45, new ItemBuilder(Material.BARRIER)
                .name("§c§lKembali")
                .lore("§7Klik untuk kembali ke menu utama")
                .build(),
                this::showMainMenu);
        
        // Previous page button (if not on the first page)
        if (currentPage > 0) {
            setItem(48, new ItemBuilder(Material.ARROW)
                    .name("§7§lHalaman Sebelumnya")
                    .lore("§7Klik untuk ke halaman " + currentPage)
                    .build(),
                    () -> showCurrentSection(currentPage - 1));
        } else {
            setItem(48, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }
        
        // Current page indicator
        setItem(49, new ItemBuilder(Material.PAPER)
                .name("§f§lHalaman " + (currentPage + 1) + "/" + (maxPage + 1))
                .build());
        
        // Next page button (if not on the last page)
        if (currentPage < maxPage) {
            setItem(50, new ItemBuilder(Material.ARROW)
                    .name("§7§lHalaman Selanjutnya")
                    .lore("§7Klik untuk ke halaman " + (currentPage + 2))
                    .build(),
                    () -> showCurrentSection(currentPage + 1));
        } else {
            setItem(50, new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
        }
    }
    
    /**
     * Show the current section at a specific page
     * 
     * @param page The page number to show
     */
    private void showCurrentSection(int page) {
        switch (currentSection) {
            case SECTION_MAIN:
                showMainMenu();
                break;
            case SECTION_BUY_RODS:
                showRodShop(page);
                break;
            case SECTION_BUY_BAITS:
                showBaitShop(page);
                break;
            case SECTION_SELL_FISH:
                showFishSellShop(page);
                break;
        }
    }
    
    /**
     * Fill the border of the inventory with a specific material
     * 
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
     * Update the balance display in the inventory
     */
    private void updateBalanceDisplay() {
        // Show player's balance at the bottom
        String balanceText = economy.formatMoney(economy.getBalance(player));
        
        setItem(53, new ItemBuilder(Material.GOLD_INGOT)
                .name("§e§lSaldo: §f" + balanceText)
                .lore("§7" + player.getName())
                .build());
    }
    
    /**
     * Buy a fishing rod
     * 
     * @param rodId The ID of the rod to buy
     * @param price The price to pay
     */
    private void buyRod(String rodId, double price) {
        if (economy.withdrawMoney(player, price)) {
            // Get the rod data
            FishingRod rod = plugin.getConfigManager().getFishingRods().get(rodId);
            if (rod == null) {
                player.sendMessage("§cTerjadi kesalahan saat membeli pancingan!");
                return;
            }
            
            // Create the fishing rod item
            ItemStack rodItem = rod.createItemStack();
            
            // Give to player
            player.getInventory().addItem(rodItem);
            player.sendMessage("§aKamu berhasil membeli §b" + rod.getName() + "§a!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            
            // Refresh the inventory
            updateBalanceDisplay();
        } else {
            player.sendMessage("§cUang kamu tidak cukup untuk membeli ini!");
        }
    }
    
    /**
     * Buy a fishing bait
     * 
     * @param baitId The ID of the bait to buy
     * @param amount The amount to buy
     * @param price The total price to pay
     */
    private void buyBait(String baitId, int amount, double price) {
        if (economy.withdrawMoney(player, price)) {
            // Get the bait data
            FishingBait bait = plugin.getConfigManager().getFishingBaits().get(baitId);
            if (bait == null) {
                player.sendMessage("§cTerjadi kesalahan saat membeli umpan!");
                return;
            }
            
            // Create the bait item
            ItemStack baitItem = bait.createItemStack(amount);
            
            // Give to player
            player.getInventory().addItem(baitItem);
            
            player.sendMessage("§aKamu berhasil membeli §f" + amount + "x §a" + bait.getName() + "§a!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            
            // Refresh the inventory
            updateBalanceDisplay();
        } else {
            player.sendMessage("§cUang kamu tidak cukup untuk membeli ini!");
        }
    }
    
    /**
     * Sell a specific fish
     * 
     * @param itemIndex The index of the fish in the player's inventory
     * @param fishItem The ItemStack of the fish
     * @param fish The CustomFish data
     * @param unitPrice The price per fish
     */
    private void sellFish(int storageSlot, ItemStack fishItem, CustomFish fish, double unitPrice) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        if (storageSlot < 0 || storageSlot >= storage.length) {
            player.sendMessage("§cIkan tidak ditemukan di inventory kamu!");
            return;
        }

        ItemStack currentItem = storage[storageSlot];
        if (currentItem == null || !isFish(currentItem)) {
            player.sendMessage("§cIkan tidak ditemukan di inventory kamu!");
            return;
        }

        CustomFish currentFish = getFishFromItem(currentItem);
        if (currentFish == null || !currentFish.getId().equals(fish.getId())) {
            player.sendMessage("§cIkan di inventory telah berubah!");
            return;
        }

        int amount = currentItem.getAmount();
        double totalPrice = unitPrice * amount;

        // Remove the item directly from the storage slot
        player.getInventory().setItem(storageSlot, null);

        // Add money to the player
        economy.depositMoney(player, totalPrice);

        // Update dynamic price for this fish
        economy.updateDynamicPrice(fish.getId(), amount);

        // Notify the player
        player.sendMessage("§aKamu menjual " + amount + "x " + fish.getName() + " seharga " 
                + economy.formatMoney(totalPrice));

        // Refresh the inventory
        updateBalanceDisplay();
    }
    
    /**
     * Sell all fish in the player's inventory
     */
    private void sellAllFish() {
        ItemStack[] contents = player.getInventory().getStorageContents();
        Map<String, Integer> fishSold = new HashMap<>();
        double totalEarnings = 0;
        int totalFishSold = 0;
        
        // Process each item in the inventory
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null) continue;
            
            CustomFish fish = getFishFromItem(item);
            if (fish == null) continue;
            
            double price = economy.calculateFishPrice(fish);
            double earnings = price * item.getAmount();
            totalEarnings += earnings;
            totalFishSold += item.getAmount();
            
            // Keep track of fish types sold for price dynamics
            fishSold.put(fish.getId(), fishSold.getOrDefault(fish.getId(), 0) + item.getAmount());
            
            // Remove the fish from inventory
            player.getInventory().setItem(i, null);
        }
        
        if (totalFishSold == 0) {
            player.sendMessage("§cTidak ada ikan yang dapat dijual!");
            return;
        }
        
        // Update dynamic prices for all fish sold
        for (Map.Entry<String, Integer> entry : fishSold.entrySet()) {
            economy.updateDynamicPrice(entry.getKey(), entry.getValue());
        }
        
        // Add money to player
        economy.depositMoney(player, totalEarnings);
        
        // Notify player
        player.sendMessage("§a§lBerhasil! §fKamu menjual §b" + totalFishSold + " ikan§f dengan total " 
                + "§a" + economy.formatMoney(totalEarnings) + "§f.");
        
        // Refresh the inventory
        showFishSellShop(0);
    }
    
    /**
     * Check if an item is a fish
     * 
     * @param item The item to check
     * @return true if the item is a fish
     */
    private boolean isFish(ItemStack item) {
        return getFishFromItem(item) != null;
    }
    
    /**
     * Get a CustomFish from an ItemStack
     * 
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
        
        CustomFish templateFish = plugin.getConfigManager().getCustomFishes().get(fishId);
        
        if (templateFish == null) return null;
        
        // Buat instance baru dengan nilai spesifik
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
            templateFish.getBaseTier() // Pass the base tier from template fish
        );
        
        // Set nilai berat dan kualitas dari data yang tersimpan
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
        
        // Baca data tier yang tersimpan (atau gunakan base tier jika tidak ada)
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
     * Format fish names from IDs
     * 
     * @param fishIds The fish IDs to format
     * @return A formatted string of fish names
     */
    private String formatFishNames(List<String> fishIds) {
        List<String> formattedNames = new ArrayList<>();
        for (String fishId : fishIds) {
            if (plugin.getConfigManager().getCustomFishes().containsKey(fishId)) {
                formattedNames.add(plugin.getConfigManager().getCustomFishes().get(fishId).getName());
            } else {
                formattedNames.add(fishId);
            }
        }
        return String.join(", ", formattedNames);
    }
    
    /**
     * Calculate the price of a fishing rod
     * 
     * @param rod The rod to calculate the price for
     * @return The price
     */
    private double calculateRodPrice(FishingRod rod) {
        // Gunakan harga dari config jika ada, atau hitung jika tidak ada
        if (rod.getPrice() > 0) {
            return rod.getPrice();
        }
        // Fallback ke kalkulasi lama
        return rod.getDurability() * ROD_PRICE_MULTIPLIER / rod.getStaminaModifier();
    }
    
    /**
     * Calculate the price of a fishing bait
     * 
     * @param bait The bait to calculate the price for
     * @return The price
     */
    private double calculateBaitPrice(FishingBait bait) {
        // Gunakan harga dari config jika ada, atau hitung jika tidak ada
        if (bait.getPrice() > 0) {
            return bait.getPrice();
        }
        // Fallback ke kalkulasi lama
        return bait.getAttractionModifier() * 10 * BAIT_PRICE_MULTIPLIER;
    }
}