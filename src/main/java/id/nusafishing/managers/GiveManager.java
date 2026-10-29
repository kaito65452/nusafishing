package id.nusafishing.managers;

import java.util.HashMap;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import id.nusafishing.NusaFishing;
import id.nusafishing.event.ConfigEventFish;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.gui.GuiSecurityUtils;

public class GiveManager {
    private final NusaFishing plugin;

    public GiveManager(NusaFishing plugin) {
        this.plugin = plugin;
    }

    public enum GiveType {
        FISH("ikan"),
        BAIT("umpan"),
        ROD("pancingan");

        private final String displayName;

        GiveType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static GiveType fromString(String name) {
            if (name == null) return null;
            try {
                return valueOf(name.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Hitung sisa kapasitas inventori pemain untuk jenis ItemStack tertentu.
     */
    public static int getAvailableSpace(Player player, ItemStack item) {
        if (player == null || item == null) return 0;
        int space = 0;
        int maxStack = item.getMaxStackSize();
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (ItemStack invItem : storage) {
            if (invItem == null || invItem.getType() == Material.AIR) {
                space += maxStack;
            } else if (invItem.isSimilar(item)) {
                space += Math.max(0, maxStack - invItem.getAmount());
            }
        }
        return space;
    }

    /**
     * Berikan ikan custom ke pemain dengan validasi berat, kualitas, dan tag admin_given.
     */
    public boolean giveFish(CommandSender sender, Player target, String fishId, int amount) {
        if (!validateTarget(sender, target)) return false;

        CustomFish template = plugin.getConfigManager().getCustomFishes().get(fishId);
        if (template == null) {
            ConfigEventFish eventFish = ConfigEventFish.getEventFish(fishId);
            if (eventFish != null) {
                template = eventFish.createTemplateFish();
            }
        }

        if (template == null) {
            sender.sendMessage("§cIkan dengan ID '" + fishId + "' tidak ditemukan!");
            return false;
        }

        int maxAmount = plugin.getConfigManager().getGiveMaxAmount();
        int finalAmount = Math.max(1, Math.min(amount, maxAmount));

        // Buat ikan dengan berat dan kualitas strictly di dalam rentang config
        double minWeight = template.getMinWeight();
        double maxWeight = template.getMaxWeight();
        double weight = minWeight + (maxWeight - minWeight) * 0.5;
        weight = Math.max(minWeight, Math.min(maxWeight, weight));

        int minQuality = template.getMinQuality();
        int maxQuality = template.getMaxQuality();
        int quality = minQuality + (maxQuality - minQuality) / 2;
        quality = Math.max(minQuality, Math.min(maxQuality, quality));

        CustomFish fish = template.createCaughtInstance(0, 1.0, 1.0, maxWeight);
        fish.applyCaughtData(weight, quality, template.getBaseTier());

        ItemStack fishItem = fish.createItemStack();
        // Beri tag admin_given agar tunduk pada config give.admin-fish-sellable
        GuiSecurityUtils.markAdminGiven(fishItem);

        String display = fish.getActualTier().getColor() + fish.getName();
        return dispatchItems(sender, target, fishItem, finalAmount, display, GiveType.FISH, fishId);
    }

    /**
     * Berikan umpan custom ke pemain.
     */
    public boolean giveBait(CommandSender sender, Player target, String baitId, int amount) {
        if (!validateTarget(sender, target)) return false;

        FishingBait bait = plugin.getConfigManager().getFishingBaits().get(baitId);
        if (bait == null) {
            sender.sendMessage("§cUmpan dengan ID '" + baitId + "' tidak ditemukan!");
            return false;
        }

        int maxAmount = plugin.getConfigManager().getGiveMaxAmount();
        int finalAmount = Math.max(1, Math.min(amount, maxAmount));

        ItemStack baitItem = bait.createItemStack(1);
        String display = "§a" + bait.getName();
        return dispatchItems(sender, target, baitItem, finalAmount, display, GiveType.BAIT, baitId);
    }

    /**
     * Berikan pancingan custom ke pemain.
     */
    public boolean giveRod(CommandSender sender, Player target, String rodId, int amount) {
        if (!validateTarget(sender, target)) return false;

        FishingRod rod = plugin.getConfigManager().getFishingRods().get(rodId);
        if (rod == null) {
            sender.sendMessage("§cPancingan dengan ID '" + rodId + "' tidak ditemukan!");
            return false;
        }

        int maxAmount = plugin.getConfigManager().getGiveMaxAmount();
        int finalAmount = Math.max(1, Math.min(amount, maxAmount));

        ItemStack rodItem = rod.createItemStack();
        String display = rod.getTier().getColor() + rod.getName();
        return dispatchItems(sender, target, rodItem, finalAmount, display, GiveType.ROD, rodId);
    }

    private boolean validateTarget(CommandSender sender, Player target) {
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPemain tidak ditemukan atau sedang offline!");
            return false;
        }
        return true;
    }

    /**
     * Eksekusi pemberian item ke inventori atau drop ke tanah jika penuh sesuai config.
     */
    private boolean dispatchItems(CommandSender sender, Player target, ItemStack singleItem, int totalAmount,
                                  String displayName, GiveType type, String id) {
        boolean dropIfFull = plugin.getConfigManager().isGiveDropIfFull();
        int availableSpace = getAvailableSpace(target, singleItem);

        if (availableSpace < totalAmount && !dropIfFull) {
            sender.sendMessage("§cInventori " + target.getName() + " penuh! Tidak dapat memberikan item.");
            if (!sender.equals(target)) {
                target.sendMessage("§cInventori kamu penuh saat menerima item dari admin!");
            }
            return false;
        }

        int remaining = totalAmount;
        int maxStack = singleItem.getMaxStackSize();
        boolean droppedAny = false;

        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStack);
            ItemStack stack = singleItem.clone();
            stack.setAmount(stackSize);

            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                for (ItemStack leftoverItem : leftover.values()) {
                    if (leftoverItem != null && leftoverItem.getType() != Material.AIR) {
                        target.getWorld().dropItemNaturally(target.getLocation(), leftoverItem);
                        droppedAny = true;
                    }
                }
            }
            remaining -= stackSize;
        }

        // Audit log ke konsol server
        plugin.getLogger().info(String.format(
            "[AUDIT] %s memberikan %dx %s (%s) kepada %s",
            sender.getName(), totalAmount, type.name(), id, target.getName()
        ));

        // Notifikasi pemain
        target.sendMessage("§aKamu menerima §f" + totalAmount + "x " + displayName + "§a!");
        target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        if (droppedAny) {
            target.sendMessage("§eSebagian item dijatuhkan ke tanah karena inventori penuh!");
        }

        if (!sender.equals(target)) {
            sender.sendMessage("§aBerhasil memberikan §f" + totalAmount + "x " + displayName + " §akepada §e" + target.getName() + "§a.");
        }

        return true;
    }
}
