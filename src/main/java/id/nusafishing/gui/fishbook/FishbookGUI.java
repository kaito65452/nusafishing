package id.nusafishing.gui.fishbook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import id.nusafishing.NusaFishing;
import id.nusafishing.event.ConfigEventFish;
import id.nusafishing.fishing.data.FishingStatisticsManager.PlayerFishRecord;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.gui.GUI;
import id.nusafishing.util.ItemBuilder;

public class FishbookGUI extends GUI {
    private final NusaFishing plugin;
    private final List<CustomFish> allFishes = new ArrayList<>();
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 45;

    public FishbookGUI(Player player, NusaFishing plugin) {
        super(player, "§1§l✦ Buku Ikan (Fishbook) ✦", 6);
        this.plugin = plugin;

        loadAndSortFishes();
        renderPage();
    }

    private void loadAndSortFishes() {
        allFishes.clear();
        Set<String> seenIds = new HashSet<>();

        // Ambil semua ikan reguler dari fishing.yml
        for (CustomFish fish : plugin.getConfigManager().getCustomFishes().values()) {
            if (seenIds.add(fish.getId())) {
                allFishes.add(fish);
            }
        }

        // Ambil ikan event jika diaktifkan di config
        if (plugin.getConfigManager().isFishbookIncludeEventFish()) {
            for (ConfigEventFish eventFish : ConfigEventFish.getEventFishMap().values()) {
                if (seenIds.add(eventFish.getId())) {
                    allFishes.add(eventFish.createTemplateFish());
                }
            }
        }

        // Urutkan berdasarkan tier lalu nama
        allFishes.sort(Comparator
                .comparing((CustomFish f) -> f.getBaseTier().ordinal())
                .thenComparing(f -> f.getName().toLowerCase())
        );
    }

    private void renderPage() {
        clickHandlers.clear();
        inventory.clear();

        int totalFishes = allFishes.size();
        int maxPage = Math.max(1, (int) Math.ceil((double) totalFishes / ITEMS_PER_PAGE));
        if (page >= maxPage) page = maxPage - 1;
        if (page < 0) page = 0;

        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalFishes);

        // Hitung total ikan unik yang sudah ditangkap pemain
        int caughtDistinctCount = 0;
        for (CustomFish fish : allFishes) {
            if (plugin.getStatisticsManager().hasCaughtFish(player.getUniqueId(), fish.getId())) {
                caughtDistinctCount++;
            }
        }

        for (int i = startIndex; i < endIndex; i++) {
            int slot = i - startIndex;
            CustomFish fish = allFishes.get(i);
            boolean caught = plugin.getStatisticsManager().hasCaughtFish(player.getUniqueId(), fish.getId());
            PlayerFishRecord record = plugin.getStatisticsManager().getFishRecord(player.getUniqueId(), fish.getId());

            ItemStack displayItem;
            if (caught && record != null) {
                // Ikan sudah pernah ditangkap: tampilkan ikon asli dan lore statistik
                ItemStack baseItem = fish.createItemStack();
                CustomFish.FishTier tier = fish.getBaseTier();

                List<String> lore = new ArrayList<>();
                lore.add("§8" + fish.getDescription());
                lore.add("");
                lore.add("§a✔ Sudah Pernah Ditangkap");
                lore.add("§7Total Ditangkap: §e" + record.getCount() + "x");
                lore.add("§7Rekor Berat: §f" + formatWeight(record.getMaxWeight()));
                lore.add("§7Rekor Kualitas: §f" + record.getMaxQuality() + "/100");
                lore.add("§7Tier: " + tier.getColor() + tier.getDisplayName());
                lore.add("§7Harga Dasar: §f" + String.format("%.2f", fish.getBasePrice()));
                lore.add("");
                lore.add("§8[Read-Only Catalog]");

                displayItem = new ItemBuilder(baseItem)
                        .name(tier.getColor() + fish.getName() + " §7[" + tier.getDisplayName() + "]")
                        .lore(lore)
                        .build();
            } else {
                // Ikan belum pernah ditangkap: tampilkan misteri (BLACK_CONCRETE, ???)
                displayItem = new ItemBuilder(Material.BLACK_CONCRETE)
                        .name("§8???")
                        .lore("§7???")
                        .lore("")
                        .lore("§8(Ikan ini belum pernah kamu tangkap)")
                        .build();
            }

            // Read-only catalog, interaksi dilarang
            setItem(slot, displayItem);
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

        // Info Progres
        double percentage = totalFishes > 0 ? ((double) caughtDistinctCount / totalFishes) * 100.0 : 0.0;
        ItemStack info = new ItemBuilder(Material.WRITTEN_BOOK)
                .name("§e§l✦ Koleksi Buku Ikan ✦")
                .lore("§7Terkumpul: §a" + caughtDistinctCount + "§7/§e" + totalFishes + " §7(" + String.format("%.1f", percentage) + "%)")
                .lore("§7Halaman: §f" + (page + 1) + "§7/§f" + maxPage)
                .lore("")
                .lore("§8Buku ini mencatat ikan yang sah ditangkap.")
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

    private String formatWeight(double weight) {
        if (weight < 1.0) {
            return String.format("%.2f g", weight * 1000);
        } else if (weight < 1000.0) {
            return String.format("%.2f kg", weight);
        } else {
            return String.format("%.2f ton", weight / 1000);
        }
    }
}
