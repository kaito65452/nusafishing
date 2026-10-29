package id.nusafishing.gui.catalog;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import id.nusafishing.NusaFishing;
import id.nusafishing.event.ConfigEventFish;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.gui.GUI;
import id.nusafishing.managers.GiveManager.GiveType;
import id.nusafishing.util.ItemBuilder;

public class ViewCatalogGUI extends GUI {
    private final NusaFishing plugin;
    private final GiveType viewType;
    private final List<String> itemIds = new ArrayList<>();
    private final String filterId;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 45;

    public ViewCatalogGUI(Player player, NusaFishing plugin, GiveType viewType, String filterId) {
        super(player, "§8Katalog: §1" + viewType.getDisplayName().toUpperCase(), 6);
        this.plugin = plugin;
        this.viewType = viewType;
        this.filterId = (filterId != null && !filterId.isBlank()) ? filterId.trim().toLowerCase() : null;

        loadItemIds();
        renderPage();
    }

    public ViewCatalogGUI(Player player, NusaFishing plugin, GiveType viewType) {
        this(player, plugin, viewType, null);
    }

    private void loadItemIds() {
        itemIds.clear();
        switch (viewType) {
            case FISH:
                if (filterId != null) {
                    for (String key : plugin.getConfigManager().getCustomFishes().keySet()) {
                        if (key.equalsIgnoreCase(filterId)) {
                            itemIds.add(key);
                            return;
                        }
                    }
                    for (String evKey : ConfigEventFish.getEventFishMap().keySet()) {
                        if (evKey.equalsIgnoreCase(filterId)) {
                            itemIds.add(evKey);
                            return;
                        }
                    }
                } else {
                    itemIds.addAll(plugin.getConfigManager().getCustomFishes().keySet());
                    for (String evId : ConfigEventFish.getEventFishMap().keySet()) {
                        if (!itemIds.contains(evId)) {
                            itemIds.add(evId);
                        }
                    }
                }
                break;
            case BAIT:
                if (filterId != null) {
                    for (String key : plugin.getConfigManager().getFishingBaits().keySet()) {
                        if (key.equalsIgnoreCase(filterId)) {
                            itemIds.add(key);
                            return;
                        }
                    }
                } else {
                    itemIds.addAll(plugin.getConfigManager().getFishingBaits().keySet());
                }
                break;
            case ROD:
                if (filterId != null) {
                    for (String key : plugin.getConfigManager().getFishingRods().keySet()) {
                        if (key.equalsIgnoreCase(filterId)) {
                            itemIds.add(key);
                            return;
                        }
                    }
                } else {
                    itemIds.addAll(plugin.getConfigManager().getFishingRods().keySet());
                }
                break;
        }
    }

    private void renderPage() {
        clickHandlers.clear();
        inventory.clear();

        int totalItems = itemIds.size();
        int maxPage = Math.max(1, (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE));
        if (page >= maxPage) page = maxPage - 1;
        if (page < 0) page = 0;

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            String id = itemIds.get(i);
            ItemStack displayItem = createDetailedDisplayItem(id);

            if (displayItem != null) {
                // Read-only catalog: item tidak dapat diambil sama sekali
                setItem(slot, displayItem);
            }
        }

        // Filler baris bawah
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int s = 45; s < 54; s++) {
            setItem(s, filler.clone());
        }

        // Tombol Sebelumnya
        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.ARROW)
                    .name("§e◄ Halaman Sebelumnya")
                    .lore("§7Klik untuk ke halaman " + page)
                    .build();
            setItem(45, prev, () -> {
                page--;
                renderPage();
            });
        }

        // Info Halaman
        ItemBuilder infoBuilder = new ItemBuilder(Material.BOOK)
                .name("§6§lKatalog Informasi")
                .lore("§7Tipe: §e" + viewType.getDisplayName())
                .lore("§7Halaman: §f" + (page + 1) + "§7/§f" + maxPage)
                .lore("§7Total Item: §f" + totalItems);
        if (filterId != null) {
            infoBuilder.lore("§7Filter: §a" + filterId);
        }
        infoBuilder.lore("")
                .lore("§8Mode Read-Only: Item hanya untuk dilihat.");
        setItem(49, infoBuilder.build());

        // Tombol Tutup
        ItemStack close = new ItemBuilder(Material.BARRIER)
                .name("§c§lTutup")
                .build();
        setItem(51, close, this::close);

        // Tombol Berikutnya
        if (page < maxPage - 1) {
            ItemStack next = new ItemBuilder(Material.ARROW)
                    .name("§eHalaman Berikutnya ►")
                    .lore("§7Klik untuk ke halaman " + (page + 2))
                    .build();
            setItem(53, next, () -> {
                page++;
                renderPage();
            });
        }
    }

    private ItemStack createDetailedDisplayItem(String id) {
        switch (viewType) {
            case FISH: {
                CustomFish fish = plugin.getConfigManager().getCustomFishes().get(id);
                boolean isEvent = false;
                if (fish == null) {
                    ConfigEventFish ev = ConfigEventFish.getEventFish(id);
                    if (ev != null) {
                        fish = ev.createTemplateFish();
                        isEvent = true;
                    }
                }
                if (fish == null) return null;

                ItemStack baseItem = fish.createItemStack();
                List<String> lore = new ArrayList<>();
                lore.add("§8" + fish.getDescription());
                lore.add("");
                lore.add("§e§l✦ Informasi Spesifikasi:");
                lore.add("  §7• ID: §f" + fish.getId());
                lore.add("  §7• Tier: " + fish.getBaseTier().getColor() + fish.getBaseTier().getDisplayName());
                lore.add("  §7• Rentang Berat: §f" + String.format("%.2f", fish.getMinWeight()) + " - " + String.format("%.2f", fish.getMaxWeight()) + " kg");
                lore.add("  §7• Rentang Kualitas: §f" + fish.getMinQuality() + " - " + fish.getMaxQuality() + "/100");
                lore.add("  §7• Harga Dasar: §f" + String.format("%.2f", fish.getBasePrice()));
                lore.add("  §7• Tipe: §f" + (isEvent ? "§dIkan Event" : "§aIkan Reguler"));
                if (fish.getTextureProvider() != null && !fish.getTextureProvider().equalsIgnoreCase("vanilla")) {
                    lore.add("  §7• Texture: §d" + fish.getTextureProvider() + " §7(" + fish.getTextureId() + ")");
                }
                lore.add("");
                lore.add("§8[Read-Only Catalog - Tidak Dapat Diambil]");

                return new ItemBuilder(baseItem)
                        .name(fish.getBaseTier().getColor() + fish.getName())
                        .lore(lore)
                        .build();
            }
            case BAIT: {
                FishingBait bait = plugin.getConfigManager().getFishingBaits().get(id);
                if (bait == null) return null;

                ItemStack baseItem = bait.createItemStack(1);
                List<String> lore = new ArrayList<>();
                lore.add("§8" + bait.getDescription());
                lore.add("");
                lore.add("§e§l✦ Informasi Umpan:");
                lore.add("  §7• ID: §f" + bait.getId());
                lore.add("  §7• Daya Tarik: §a+" + (int) ((bait.getAttractionModifier() - 1.0) * 100) + "%");
                lore.add("  §7• Boost Kualitas: §a+" + (int) ((bait.getQualityBoost() - 1.0) * 100) + "%");
                lore.add("  §7• Boost Berat: §a+" + (int) ((bait.getWeightBoost() - 1.0) * 100) + "%");
                lore.add("  §7• Boost Tier: §a+" + bait.getTierBoost() + " tingkat");
                lore.add("  §7• Penggunaan/Item: §e" + bait.getUsesPerItem() + " kali");
                lore.add("  §7• Harga: §f" + String.format("%.2f", bait.getPrice()));
                lore.add("");
                lore.add("§8[Read-Only Catalog - Tidak Dapat Diambil]");

                return new ItemBuilder(baseItem)
                        .name("§a" + bait.getName())
                        .lore(lore)
                        .build();
            }
            case ROD: {
                FishingRod rod = plugin.getConfigManager().getFishingRods().get(id);
                if (rod == null) return null;

                ItemStack baseItem = rod.createItemStack();
                List<String> lore = new ArrayList<>();
                lore.add("§8" + rod.getDescription());
                lore.add("");
                lore.add("§e§l✦ Informasi Pancingan:");
                lore.add("  §7• ID: §f" + rod.getId());
                lore.add("  §7• Tier: " + rod.getTier().getColor() + rod.getTier().getDisplayName());
                lore.add("  §7• Durability Maksimal: §f" + rod.getDurability());
                lore.add("  §7• Modifikasi Stamina: §f" + (rod.getStaminaModifier() < 1.0 ?
                        "-" + (int)((1.0 - rod.getStaminaModifier()) * 100) + "%" :
                        "+" + (int)((rod.getStaminaModifier() - 1.0) * 100) + "%"));
                lore.add("  §7• Boost Kualitas: §f+" + (int)((rod.getQualityBoost() - 1.0) * 100) + "%");
                lore.add("  §7• Enchant Lure: §f" + rod.getLureLevel());
                lore.add("  §7• Kapasitas Berat: §f" + String.format("%.2f kg", rod.getMaxWeightCapacity()));
                if (rod.hasSpecialAbility() && rod.getSpecialAbilityDescription() != null) {
                    lore.add("  §7• Kemampuan Khusus: §e" + rod.getSpecialAbilityDescription());
                }
                lore.add("");
                lore.add("§8[Read-Only Catalog - Tidak Dapat Diambil]");

                return new ItemBuilder(baseItem)
                        .name(rod.getTier().getColor() + rod.getName())
                        .lore(lore)
                        .build();
            }
            default:
                return null;
        }
    }
}
