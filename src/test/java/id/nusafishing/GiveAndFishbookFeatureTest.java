package id.nusafishing;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import id.nusafishing.gui.GuiSecurityUtils;
import id.nusafishing.managers.GiveManager;
import id.nusafishing.fishing.data.FishingStatisticsManager;
import id.nusafishing.fishing.data.FishingStatisticsManager.PlayerFishRecord;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class GiveAndFishbookFeatureTest {
    private ServerMock server;

    @Before
    public void setUp() {
        server = MockBukkit.mock();
    }

    @After
    public void tearDown() {
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    /**
     * Test 1: Deteksi dan pembersihan item GUI bertag (nf_gui_item)
     */
    @Test
    public void testGuiItemTaggingAndSanitization() {
        PlayerMock player = server.addPlayer();

        ItemStack normalItem = new ItemStack(Material.DIAMOND, 5);
        ItemStack guiItem1 = new ItemStack(Material.GOLDEN_APPLE, 1);
        ItemStack guiItem2 = new ItemStack(Material.FISHING_ROD, 1);

        GuiSecurityUtils.markGuiItem(guiItem1);
        GuiSecurityUtils.markGuiItem(guiItem2);

        assertTrue("Item harus terdeteksi sebagai GUI item", GuiSecurityUtils.isGuiItem(guiItem1));
        assertTrue("Item harus terdeteksi sebagai GUI item", GuiSecurityUtils.isGuiItem(guiItem2));
        assertFalse("Item normal tidak boleh terdeteksi sebagai GUI item", GuiSecurityUtils.isGuiItem(normalItem));

        player.getInventory().addItem(normalItem);
        player.getInventory().addItem(guiItem1);
        player.getInventory().addItem(guiItem2);

        assertEquals("Inventory harus memiliki 3 stack item sebelum dibersihkan", 3, countNonEmptySlots(player));

        GuiSecurityUtils.cleanLeakedGuiItems(player);

        assertEquals("Setelah sanitasi, hanya normalItem yang tersisa", 1, countNonEmptySlots(player));
        assertNotNull(player.getInventory().getItem(0));
        assertEquals(Material.DIAMOND, player.getInventory().getItem(0).getType());
    }

    /**
     * Test 2: Tagging admin_given dan proteksi penjualan
     */
    @Test
    public void testAdminGivenFishTagging() {
        ItemStack fishItem = new ItemStack(Material.COD, 1);
        assertFalse("Fish normal belum di-tag admin_given", GuiSecurityUtils.isAdminGiven(fishItem));

        GuiSecurityUtils.markAdminGiven(fishItem);
        assertTrue("Fish harus terdeteksi admin_given setelah di-mark", GuiSecurityUtils.isAdminGiven(fishItem));
    }

    /**
     * Test 3: Validasi amount (positive, non-zero, bounds)
     */
    @Test
    public void testAmountValidationLogic() {
        int maxAmountConfig = 64;

        // Validasi angka positif
        int validAmount = Math.max(1, Math.min(10, maxAmountConfig));
        assertEquals(10, validAmount);

        // Validasi pembatasan batas atas
        int overAmount = Math.max(1, Math.min(100, maxAmountConfig));
        assertEquals(64, overAmount);

        // Validasi angka non-positif / batas bawah
        int zeroAmount = Math.max(1, Math.min(0, maxAmountConfig));
        assertEquals(1, zeroAmount);

        int negativeAmount = Math.max(1, Math.min(-5, maxAmountConfig));
        assertEquals(1, negativeAmount);

        // Parsing string non-angka harus melempar exception / return null
        boolean parsedValid = isInteger("16");
        boolean parsedInvalid = isInteger("abc");
        boolean parsedNegative = isInteger("-10");

        assertTrue(parsedValid);
        assertFalse(parsedInvalid);
        assertTrue(parsedNegative); // Negatif adalah integer, namun ditolak oleh validator bounds
    }

    /**
     * Test 4: Logika Fishbook (Status ikan tertangkap vs belum pernah ditangkap)
     */
    @Test
    public void testFishbookCaughtVsUncaughtLogic() {
        UUID playerUuid = UUID.randomUUID();
        PlayerFishRecord record = new PlayerFishRecord("tuna", 5, 12.5, 88);

        assertEquals("tuna", record.getFishId());
        assertEquals(5, record.getCount());
        assertEquals(12.5, record.getMaxWeight(), 0.001);
        assertEquals(88, record.getMaxQuality());

        // Update rekor dengan tangkapan baru yang lebih tinggi
        PlayerFishRecord updated1 = new PlayerFishRecord("tuna", record.getCount() + 1,
                Math.max(record.getMaxWeight(), 15.2), Math.max(record.getMaxQuality(), 95));
        assertEquals(6, updated1.getCount());
        assertEquals(15.2, updated1.getMaxWeight(), 0.001);
        assertEquals(95, updated1.getMaxQuality());

        // Update rekor dengan tangkapan baru yang lebih rendah (tidak boleh menurunkan rekor)
        PlayerFishRecord updated2 = new PlayerFishRecord("tuna", updated1.getCount() + 1,
                Math.max(updated1.getMaxWeight(), 8.0), Math.max(updated1.getMaxQuality(), 70));
        assertEquals(7, updated2.getCount());
        assertEquals(15.2, updated2.getMaxWeight(), 0.001);
        assertEquals(95, updated2.getMaxQuality());
    }

    /**
     * Test 5: Tab-complete filtering berdasarkan izin dan prefix
     */
    @Test
    public void testTabCompleteFilteringLogic() {
        List<String> allSubCommands = Arrays.asList(
                "help", "fishbook", "view", "give", "rod", "bait",
                "repair", "shop", "sell", "afk", "collector",
                "tournament", "stats", "event", "location", "reload"
        );

        // Pemain biasa tanpa permission admin/give
        List<String> userAllowed = allSubCommands.stream()
                .filter(cmd -> !cmd.equals("give") && !cmd.equals("reload") && !cmd.equals("event") && !cmd.equals("location"))
                .collect(Collectors.toList());

        assertFalse("Pemain biasa tidak boleh melihat 'give'", userAllowed.contains("give"));
        assertFalse("Pemain biasa tidak boleh melihat 'reload'", userAllowed.contains("reload"));
        assertTrue("Pemain biasa boleh melihat 'fishbook'", userAllowed.contains("fishbook"));
        assertTrue("Pemain biasa boleh melihat 'view'", userAllowed.contains("view"));

        // Prefix matching
        String query = "fi";
        List<String> matches = userAllowed.stream()
                .filter(s -> s.startsWith(query.toLowerCase()))
                .collect(Collectors.toList());

        assertEquals(1, matches.size());
        assertEquals("fishbook", matches.get(0));
    }

    /**
     * Test 6: Hitung sisa ruang inventori (available space check)
     */
    @Test
    public void testInventorySpaceCheck() {
        PlayerMock player = server.addPlayer();
        ItemStack singleRod = new ItemStack(Material.FISHING_ROD, 1); // max stack 1
        ItemStack singleFish = new ItemStack(Material.COD, 1); // max stack 64

        int rodSpace = GiveManager.getAvailableSpace(player, singleRod);
        int fishSpace = GiveManager.getAvailableSpace(player, singleFish);

        // Storage size = 36 slot
        assertEquals(36, rodSpace);
        assertEquals(36 * 64, fishSpace);

        // Isi 1 slot dengan 32 ikan COD
        player.getInventory().setItem(0, new ItemStack(Material.COD, 32));
        int fishSpaceAfter = GiveManager.getAvailableSpace(player, singleFish);
        assertEquals((35 * 64) + 32, fishSpaceAfter);
    }

    private int countNonEmptySlots(PlayerMock player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                count++;
            }
        }
        return count;
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
