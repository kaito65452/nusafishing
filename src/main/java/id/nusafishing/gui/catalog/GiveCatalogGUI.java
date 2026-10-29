package id.nusafishing.gui.catalog;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import id.nusafishing.NusaFishing;
import id.nusafishing.event.ConfigEventFish;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.gui.GUI;
import id.nusafishing.managers.GiveManager.GiveType;
import id.nusafishing.util.ItemBuilder;

public class GiveCatalogGUI extends GUI {
    private final NusaFishing plugin;
    private final GiveType giveType;
    private final List<String> itemIds = new ArrayList<>();
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 45;

    public GiveCatalogGUI(Player player, NusaFishing plugin, GiveType giveType) {
        super(player, "§8Give: §0" + giveType.getDisplayName().toUpperCase(), 6);
        this.plugin = plugin;
        this.giveType = giveType;

        loadItemIds();
        renderPage();
    }

    private void loadItemIds() {
        itemIds.clear();
        switch (giveType) {
            case FISH:
                itemIds.addAll(plugin.getConfigManager().getCustomFishes().keySet());
                for (String eventFishId : ConfigEventFish.getEventFishMap().keySet()) {
                    if (!itemIds.contains(eventFishId)) {
                        itemIds.add(eventFishId);
                    }
                }
                break;
            case BAIT:
                itemIds.addAll(plugin.getConfigManager().getFishingBaits().keySet());
                break;
            case ROD:
                itemIds.addAll(plugin.getConfigManager().getFishingRods().keySet());
                break;
        }
    }

    private boolean checkPermission() {
        if (player == null) return false;
        if (player.hasPermission("nusafishing.admin") || player.hasPermission("nusafishing.give")) {
            return true;
        }
        switch (giveType) {
            case FISH:
                return player.hasPermission("nusafishing.give.fish");
            case BAIT:
                return player.hasPermission("nusafishing.give.bait") || player.hasPermission("nusafishing.bait");
            case ROD:
                return player.hasPermission("nusafishing.give.rod") || player.hasPermission("nusafishing.rod");
            default:
                return false;
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
            ItemStack displayItem = createDisplayItem(id);

            if (displayItem != null) {
                setItem(slot, displayItem, () -> handleItemClick(id));
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
        ItemStack info = new ItemBuilder(Material.COMPASS)
                .name("§b§lKatalog Give")
                .lore("§7Tipe: §e" + giveType.getDisplayName())
                .lore("§7Halaman: §f" + (page + 1) + "§7/§f" + maxPage)
                .lore("§7Total Item: §f" + totalItems)
                .lore("")
                .lore("§a• Klik Kiri: §fDapatkan 1 item")
                .lore("§e• Shift + Klik Kiri: §fDapatkan 1 stack")
                .build();
        setItem(49, info);

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

    private void handleItemClick(String id) {
        // Validasi izin dinamis pada setiap klik
        if (!checkPermission()) {
            close();
            player.sendMessage("§cIzin dicabut! Kamu tidak lagi memiliki akses untuk memberikan item ini.");
            return;
        }

        ClickType clickType = lastClickEvent != null ? lastClickEvent.getClick() : ClickType.LEFT;
        int maxAmount = plugin.getConfigManager().getGiveMaxAmount();
        int maxStack = (giveType == GiveType.ROD) ? 1 : 64;
        int amount = clickType.isShiftClick() ? Math.min(maxStack, maxAmount) : 1;

        switch (giveType) {
            case FISH:
                plugin.getGiveManager().giveFish(player, player, id, amount);
                break;
            case BAIT:
                plugin.getGiveManager().giveBait(player, player, id, amount);
                break;
            case ROD:
                plugin.getGiveManager().giveRod(player, player, id, amount);
                break;
        }
    }

    private ItemStack createDisplayItem(String id) {
        switch (giveType) {
            case FISH: {
                CustomFish fish = plugin.getConfigManager().getCustomFishes().get(id);
                if (fish == null) {
                    ConfigEventFish ev = ConfigEventFish.getEventFish(id);
                    if (ev != null) fish = ev.createTemplateFish();
                }
                if (fish == null) return null;

                ItemStack item = fish.createItemStack();
                List<String> lore = item.getItemMeta() != null && item.getItemMeta().getLore() != null
                        ? new ArrayList<>(item.getItemMeta().getLore())
                        : new ArrayList<>();
                lore.add("");
                lore.add("§a• Klik Kiri: §fBeri 1x");
                lore.add("§e• Shift+Klik: §fBeri 1 stack");
                lore.add("§8ID: " + id);
                return new ItemBuilder(item).lore(lore).build();
            }
            case BAIT: {
                FishingBait bait = plugin.getConfigManager().getFishingBaits().get(id);
                if (bait == null) return null;
                ItemStack item = bait.createItemStack(1);
                List<String> lore = item.getItemMeta() != null && item.getItemMeta().getLore() != null
                        ? new ArrayList<>(item.getItemMeta().getLore())
                        : new ArrayList<>();
                lore.add("");
                lore.add("§a• Klik Kiri: §fBeri 1x");
                lore.add("§e• Shift+Klik: §fBeri 1 stack");
                return new ItemBuilder(item).lore(lore).build();
            }
            case ROD: {
                FishingRod rod = plugin.getConfigManager().getFishingRods().get(id);
                if (rod == null) return null;
                ItemStack item = rod.createItemStack();
                List<String> lore = item.getItemMeta() != null && item.getItemMeta().getLore() != null
                        ? new ArrayList<>(item.getItemMeta().getLore())
                        : new ArrayList<>();
                lore.add("");
                lore.add("§a• Klik Kiri: §fBeri 1x");
                lore.add("§e• Shift+Klik: §fBeri 1 stack");
                return new ItemBuilder(item).lore(lore).build();
            }
            default:
                return null;
        }
    }
}
