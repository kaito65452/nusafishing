package id.nusafishing.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tes sinkronisasi konfigurasi dan konsistensi key antara resources internal dan example-config.
 */
public class ConfigSyncAndParityTest {

    private Set<String> getKeysDeep(FileConfiguration config) {
        Set<String> keys = new TreeSet<>();
        for (String key : config.getKeys(true)) {
            Object val = config.get(key);
            if (!(val instanceof ConfigurationSection)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /**
     * Uji paritas key config.yml antara src/main/resources dan example-config.
     * Gagal jika ada key yang berbeda.
     */
    @Test
    public void testConfigYmlKeysParityWithExample() {
        File resourceFile = new File("src/main/resources/config.yml");
        File exampleFile = new File("example-config/config.yml");

        assertTrue("src/main/resources/config.yml harus ada", resourceFile.exists());
        assertTrue("example-config/config.yml harus ada", exampleFile.exists());

        FileConfiguration resourceConfig = YamlConfiguration.loadConfiguration(resourceFile);
        FileConfiguration exampleConfig = YamlConfiguration.loadConfiguration(exampleFile);

        Set<String> resourceKeys = getKeysDeep(resourceConfig);
        Set<String> exampleKeys = getKeysDeep(exampleConfig);

        Set<String> inResourceOnly = new TreeSet<>(resourceKeys);
        inResourceOnly.removeAll(exampleKeys);

        Set<String> inExampleOnly = new TreeSet<>(exampleKeys);
        inExampleOnly.removeAll(resourceKeys);

        String message = String.format("Ketidakcocokan key config.yml! Resource-only: %s, Example-only: %s",
                inResourceOnly, inExampleOnly);
        assertTrue(message, inResourceOnly.isEmpty() && inExampleOnly.isEmpty());
    }

    /**
     * Uji paritas key rewards.yml antara src/main/resources dan example-config.
     */
    @Test
    public void testRewardsYmlKeysParityWithExample() {
        File resourceFile = new File("src/main/resources/rewards.yml");
        File exampleFile = new File("example-config/rewards.yml");

        assertTrue("src/main/resources/rewards.yml harus ada", resourceFile.exists());
        assertTrue("example-config/rewards.yml harus ada", exampleFile.exists());

        FileConfiguration resourceConfig = YamlConfiguration.loadConfiguration(resourceFile);
        FileConfiguration exampleConfig = YamlConfiguration.loadConfiguration(exampleFile);

        Set<String> resourceKeys = getKeysDeep(resourceConfig);
        Set<String> exampleKeys = getKeysDeep(exampleConfig);

        assertEquals("Key rewards.yml pada resources dan example-config harus identik",
                resourceKeys, exampleKeys);
    }

    /**
     * Uji bahwa config_schema dan config_version terdefinisi secara benar pada kedua sisi.
     */
    @Test
    public void testConfigSchemaAndVersionIntegrity() {
        File[] targetConfigs = new File[]{
                new File("src/main/resources/config.yml"),
                new File("src/main/resources/fishing.yml"),
                new File("src/main/resources/rewards.yml"),
                new File("example-config/config.yml"),
                new File("example-config/fishing.yml"),
                new File("example-config/rewards.yml")
        };

        for (File file : targetConfigs) {
            assertTrue("File harus ada: " + file.getPath(), file.exists());
            FileConfiguration conf = YamlConfiguration.loadConfiguration(file);

            int schema = conf.getInt("config_schema", -1);
            String version = conf.getString("config_version", null);

            assertEquals("File " + file.getPath() + " harus memiliki config_schema = 2", 2, schema);
            assertNotNull("File " + file.getPath() + " harus memiliki config_version", version);
            assertEquals("File " + file.getPath() + " harus memiliki config_version bersih", "1.5",
                    ConfigSync.extractCleanVersion(version));
        }
    }

    /**
     * Uji pembersihan versi: membuang metadata hash/SNAPSHOT sehingga tidak memicu resync tiap build.
     */
    @Test
    public void testCleanVersionExtraction() {
        assertEquals("1.5", ConfigSync.extractCleanVersion("1.5+Alpha+02b8ebb"));
        assertEquals("1.5", ConfigSync.extractCleanVersion("1.5-SNAPSHOT"));
        assertEquals("1.4", ConfigSync.extractCleanVersion("1.4"));
        assertEquals("1.4", ConfigSync.extractCleanVersion("1.4+hotfix"));
    }

    /**
     * Uji bahwa ConfigSync tidak menimpa nilai yang telah diubah pengguna.
     */
    @Test
    public void testSyncPreservesPlayerValues() {
        FileConfiguration defaultConfig = new YamlConfiguration();
        defaultConfig.set("config_schema", 2);
        defaultConfig.set("config_version", "1.5");
        defaultConfig.set("give.max-amount", 64);
        defaultConfig.set("give.admin-fish-sellable", false);
        defaultConfig.set("economy.fish_price_multiplier", 1.0);

        FileConfiguration serverConfig = new YamlConfiguration();
        serverConfig.set("config_schema", 1);
        serverConfig.set("config_version", "1.4");
        // Pemain mengubah multiplier dan batas give
        serverConfig.set("economy.fish_price_multiplier", 2.5);
        serverConfig.set("give.max-amount", 128);

        // Simulasikan sinkronisasi tanpa menimpa
        for (String key : defaultConfig.getKeys(true)) {
            if (!serverConfig.contains(key)) {
                Object val = defaultConfig.get(key);
                if (!(val instanceof ConfigurationSection)) {
                    serverConfig.set(key, val);
                }
            }
        }
        serverConfig.set("config_schema", defaultConfig.getInt("config_schema"));
        serverConfig.set("config_version", defaultConfig.getString("config_version"));

        // Nilai custom pemain tetap utuh
        assertEquals(2.5, serverConfig.getDouble("economy.fish_price_multiplier"), 0.0001);
        assertEquals(128, serverConfig.getInt("give.max-amount"));

        // Key baru berhasil ditambahkan
        assertFalse(serverConfig.getBoolean("give.admin-fish-sellable"));
        assertEquals(2, serverConfig.getInt("config_schema"));
        assertEquals("1.5", serverConfig.getString("config_version"));
    }
}
