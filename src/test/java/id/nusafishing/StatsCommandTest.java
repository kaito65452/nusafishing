package id.nusafishing;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import id.nusafishing.commands.NusaFishingCommand;
import id.nusafishing.fishing.data.FishingStatisticsManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import sun.misc.Unsafe;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class StatsCommandTest {
    private ServerMock server;
    private NusaFishing plugin;
    private FishingStatisticsManager statsManager;
    private NusaFishingCommand commandExecutor;

    @Before
    public void setUp() throws Exception {
        server = MockBukkit.mock();

        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);

        plugin = (NusaFishing) unsafe.allocateInstance(NusaFishing.class);
        File tempDir = Files.createTempDirectory("nusafishing_stats_test").toFile();
        tempDir.deleteOnExit();

        for (Class<?> c = NusaFishing.class; c != null; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                if (field.getType().equals(File.class) && (field.getName().contains("dataFolder") || field.getName().contains("Folder"))) {
                    field.set(plugin, tempDir);
                }
                if (field.getType().equals(Logger.class)) {
                    field.set(plugin, Logger.getLogger("NusaFishingStatsTest"));
                }
                if (field.getType().getSimpleName().equals("Server")) {
                    field.set(plugin, server);
                }
            }
        }

        statsManager = new FishingStatisticsManager(plugin);

        Field statsField = NusaFishing.class.getDeclaredField("statisticsManager");
        statsField.setAccessible(true);
        statsField.set(plugin, statsManager);

        commandExecutor = new NusaFishingCommand(plugin, null);
    }

    @After
    public void tearDown() {
        if (statsManager != null) {
            statsManager.shutdown();
        }
        if (server != null) {
            MockBukkit.unmock();
        }
    }

    /**
     * Uji tab complete "/nf " mengandung "stats"
     */
    @Test
    public void testTabCompleteMainCommandContainsStats() {
        PlayerMock player = server.addPlayer("Steve");

        // Tab completion dengan input kosong: /nf ""
        List<String> completions = commandExecutor.onTabComplete(player, null, "nf", new String[]{""});
        assertNotNull("Tab complete tidak boleh null", completions);
        assertTrue("Tab complete /nf harus mengandung 'stats'", completions.contains("stats"));

        // Tab completion dengan filter: /nf "stat"
        List<String> filteredCompletions = commandExecutor.onTabComplete(player, null, "nf", new String[]{"stat"});
        assertNotNull("Filtered tab complete tidak boleh null", filteredCompletions);
        assertTrue("Tab complete /nf stat harus mengandung 'stats'", filteredCompletions.contains("stats"));
    }

    /**
     * Uji tab complete "/nf stats " menghasilkan list semua player yang sedang online
     */
    @Test
    public void testTabCompleteStatsReturnsOnlinePlayers() {
        PlayerMock sender = server.addPlayer("Charlie");
        PlayerMock player1 = server.addPlayer("Alice");
        PlayerMock player2 = server.addPlayer("Bob");

        // Tab completion setelah "stats ": /nf stats ""
        List<String> playerCompletions = commandExecutor.onTabComplete(sender, null, "nf", new String[]{"stats", ""});
        assertNotNull("Tab complete list pemain tidak boleh null", playerCompletions);
        assertTrue("Tab complete harus mengandung Charlie", playerCompletions.contains("Charlie"));
        assertTrue("Tab complete harus mengandung Alice", playerCompletions.contains("Alice"));
        assertTrue("Tab complete harus mengandung Bob", playerCompletions.contains("Bob"));

        // Tab completion dengan filter nama pemain: /nf stats "al"
        List<String> filteredPlayerCompletions = commandExecutor.onTabComplete(sender, null, "nf", new String[]{"stats", "al"});
        assertNotNull("Filtered list pemain tidak boleh null", filteredPlayerCompletions);
        assertTrue("Harus mengandung Alice", filteredPlayerCompletions.contains("Alice"));
        assertFalse("Tidak boleh mengandung Bob", filteredPlayerCompletions.contains("Bob"));
        assertFalse("Tidak boleh mengandung Charlie", filteredPlayerCompletions.contains("Charlie"));
    }

    /**
     * Uji method statistik di FishingStatisticsManager dapat diakses dan mengembalikan nilai default untuk UUID baru
     */
    @Test
    public void testFishingStatisticsManagerDefaultValues() {
        UUID newPlayerUuid = UUID.randomUUID();

        assertEquals("Total fish caught default harus 0", 0, statsManager.getTotalFishCaught(newPlayerUuid));
        assertEquals("Legendary fish caught default harus 0", 0, statsManager.getLegendaryFishCaught(newPlayerUuid));
        assertEquals("Mythic fish caught default harus 0", 0, statsManager.getMythicFishCaught(newPlayerUuid));
        assertEquals("Event fish caught default harus 0", 0, statsManager.getEventFishCaught(newPlayerUuid));
        assertEquals("Largest fish weight default harus 0.0", 0.0, statsManager.getLargestFishWeight(newPlayerUuid), 0.001);
        assertEquals("Largest fish name default harus 'Tidak Ada'", "Tidak Ada", statsManager.getLargestFishName(newPlayerUuid));
    }

    /**
     * Uji eksekusi command /nf stats menampilkan ringkasan statistik pemain
     */
    @Test
    public void testStatsCommandExecution() {
        PlayerMock player = server.addPlayer("Fisherman");
        player.setOp(true);

        boolean result = commandExecutor.onCommand(player, null, "nf", new String[]{"stats"});
        assertTrue("Perintah /nf stats harus mengembalikan true", result);

        String message = player.nextMessage();
        assertNotNull("Pemain harus menerima pesan balasan", message);
        assertTrue("Pesan harus mengandung judul statistik memancing", message.contains("Statistik Memancing"));
    }
}
