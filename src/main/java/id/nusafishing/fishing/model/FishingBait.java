package id.nusafishing.fishing.model;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import id.nusafishing.NusaFishing;
import id.nusafishing.compat.CustomModelDataHelper;
import id.nusafishing.util.ItemBuilder;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

public class FishingBait {
    private String id;
    private String name;
    private String description;
    private Material material;
    private int customModelData;
    private List<String> validFish;
    private double attractionModifier;
    private int tierBoost; // Tambahan: Boost tier
    private int usesPerItem; // Tambahan: Jumlah penggunaan per item
    private double price; // Harga kustom
    private double qualityBoost;  // Multiplier untuk kualitas ikan
    private double weightBoost;   // Multiplier untuk berat ikan
    
    public FishingBait(String id, String name, String description, 
                     Material material, int customModelData,
                     List<String> validFish, double attractionModifier,
                     int tierBoost, int usesPerItem, double price,
                     double qualityBoost, double weightBoost) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.material = material;
        this.customModelData = customModelData;
        this.validFish = validFish;
        this.attractionModifier = attractionModifier;
        this.tierBoost = tierBoost;
        this.usesPerItem = usesPerItem;
        this.price = price;
        this.qualityBoost = qualityBoost;
        this.weightBoost = weightBoost;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Material getMaterial() { return material; }
    public int getCustomModelData() { return customModelData; }
    public List<String> getValidFish() { return validFish; }
    public double getAttractionModifier() { return attractionModifier; }
    public int getTierBoost() { return tierBoost; }
    public int getUsesPerItem() { return usesPerItem; }
    public double getPrice() { return price; }
    public double getQualityBoost() { return qualityBoost; }
    public double getWeightBoost() { return weightBoost; }
    
    public boolean canCatchFish(String fishId) {
        // If validFish is empty or null, it can catch any fish
        if (validFish == null || validFish.isEmpty()) {
            return true;
        }
        return validFish.contains(fishId);
    }

    /**
     * Creates an ItemStack representing this bait with given amount.
     */
    public ItemStack createItemStack(int amount) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("§8" + description);
        lore.add("");
        lore.add("§e§l✦ Efek Umpan:");
        lore.add("  §7• Daya tarik: §a+" + (int)((attractionModifier - 1.0) * 100) + "%");
        lore.add("  §7• Boost kualitas: §a+" + (int)((qualityBoost - 1.0) * 100) + "%");
        lore.add("  §7• Boost berat: §a+" + (int)((weightBoost - 1.0) * 100) + "%"); 
        lore.add("  §7• Tier boost: §a+" + (tierBoost * 2) + " §7tingkat");
        lore.add("");
        lore.add("§b§l✦ Ikan Target:");
        lore.add("  §7" + (validFish == null || validFish.isEmpty() ? "Semua ikan" : formatFishNames(validFish)));
        lore.add("  §7(§dBonus 10x §7untuk ikan yang sesuai)");
        lore.add("");
        lore.add("§7Sisa penggunaan: §e" + usesPerItem + "/" + usesPerItem);
        lore.add("§8ID: " + id);

        ItemStack baitItem = new ItemBuilder(material != null ? material : Material.WHEAT_SEEDS)
                .name("§a" + name)
                .lore(lore)
                .amount(Math.max(1, amount))
                .customModelData(customModelData)
                .enchant(Enchantment.LUCK_OF_THE_SEA, 1)
                .flag(ItemFlag.HIDE_ENCHANTS)
                .flag(ItemFlag.HIDE_ATTRIBUTES)
                .build();

        ItemMeta meta = baitItem.getItemMeta();
        if (meta != null) {
            PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(NamespacedKey.fromString("nusafishing:bait_id"), PersistentDataType.STRING, id);
            container.set(NamespacedKey.fromString("nusafishing:nusafishing.bait_id"), PersistentDataType.STRING, id);
            container.set(NamespacedKey.fromString("nusafishing:bait_uses"), PersistentDataType.INTEGER, usesPerItem);
            baitItem.setItemMeta(meta);
        }
        return baitItem;
    }

    public ItemStack createItemStack() {
        return createItemStack(1);
    }

    private String formatFishNames(List<String> fishIds) {
        if (fishIds == null || fishIds.isEmpty()) return "Semua ikan";
        List<String> names = new java.util.ArrayList<>();
        if (NusaFishing.getInstance() != null && NusaFishing.getInstance().getConfigManager() != null) {
            Map<String, CustomFish> fishes = NusaFishing.getInstance().getConfigManager().getCustomFishes();
            for (String fid : fishIds) {
                CustomFish f = fishes.get(fid);
                names.add(f != null ? f.getName() : fid);
            }
        } else {
            names.addAll(fishIds);
        }
        return String.join(", ", names);
    }
    
    /**
     * Checks if the given ItemStack represents this bait.
     */
    public boolean isThisBait(ItemStack item) {
        if (item == null || item.getType() != material) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey cleanKey = NamespacedKey.fromString("nusafishing:bait_id");
        NamespacedKey dottedKey = NamespacedKey.fromString("nusafishing:nusafishing.bait_id");

        String storedId = null;
        if (cleanKey != null && container.has(cleanKey, PersistentDataType.STRING)) {
            storedId = container.get(cleanKey, PersistentDataType.STRING);
        } else if (dottedKey != null && container.has(dottedKey, PersistentDataType.STRING)) {
            storedId = container.get(dottedKey, PersistentDataType.STRING);
        }

        if (storedId != null) {
            return id.equalsIgnoreCase(storedId);
        }

        int model = CustomModelDataHelper.getCustomModelData(meta);
        return model > 0 && model == customModelData;
    }

    /**
     * Use one charge of the bait from the player's off hand or inventory
     * @param player The player using the bait
     * @return true if successful, false if no more uses or no bait
     */
    public boolean consumeUse(Player player) {
        if (player == null) return false;

        // Try off hand first
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem != null && isThisBait(offHandItem)) {
            return consumeFromItem(player, offHandItem, -1);
        }

        // Search player inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && isThisBait(item)) {
                return consumeFromItem(player, item, i);
            }
        }

        return false;
    }

    private boolean consumeFromItem(Player player, ItemStack item, int slot) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        NamespacedKey usesKey = NamespacedKey.fromString("nusafishing:bait_uses");
        PersistentDataContainer container = meta.getPersistentDataContainer();

        int remainingUses = container.getOrDefault(usesKey, PersistentDataType.INTEGER, usesPerItem);
        remainingUses--;

        if (remainingUses <= 0) {
            int amount = item.getAmount();
            if (amount > 1) {
                container.set(usesKey, PersistentDataType.INTEGER, usesPerItem);
                item.setItemMeta(meta);
                item.setAmount(amount - 1);
                if (slot == -1) {
                    player.getInventory().setItemInOffHand(item);
                } else {
                    player.getInventory().setItem(slot, item);
                }
            } else {
                if (slot == -1) {
                    player.getInventory().setItemInOffHand(null);
                } else {
                    player.getInventory().setItem(slot, null);
                }
            }
        } else {
            container.set(usesKey, PersistentDataType.INTEGER, remainingUses);
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (int i = 0; i < lore.size(); i++) {
                    String line = lore.get(i);
                    if (line.contains("Sisa penggunaan:") || line.contains("Penggunaan:") || line.contains("Uses:")) {
                        lore.set(i, "§7Sisa penggunaan: §e" + remainingUses + "/" + usesPerItem);
                        break;
                    }
                }
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
            if (slot == -1) {
                player.getInventory().setItemInOffHand(item);
            } else {
                player.getInventory().setItem(slot, item);
            }
        }

        return true;
    }
}