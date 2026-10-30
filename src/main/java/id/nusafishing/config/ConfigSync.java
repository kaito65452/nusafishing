package id.nusafishing.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Logger;

/**
 * Utilitas bersama untuk sinkronisasi konfigurasi aman (safe config synchronization).
 *
 * Mengikuti prinsip:
 * 1. Tidak menimpa nilai yang telah diubah pengguna / pemain.
 * 2. Menyalin key yang hilang dari resource bawaan.
 * 3. Menjaga komentar jika Paper Comments API tersedia.
 * 4. Membuat cadangan (backup) berputar maksimal 3 cadangan terakhir sebelum menulis.
 * 5. Mengisolasi versi skema (config_schema) dari hash commit build.
 * 6. Menjalankan migrasi key lama -> key baru secara terkontrol.
 */
public final class ConfigSync {

    private static final int MAX_BACKUPS = 3;
    private static final int MAX_LOGGED_KEYS = 20;

    // Registri migrasi key terdaftar: resourceName -> Map<oldKey, newKey>
    private static final Map<String, Map<String, String>> MIGRATIONS = new HashMap<>();

    static {
        // Kerangka daftar pemetaan migrasi per file konfigurasi
        MIGRATIONS.put("config.yml", new LinkedHashMap<>());
        MIGRATIONS.put("fishing.yml", new LinkedHashMap<>());
        MIGRATIONS.put("rewards.yml", new LinkedHashMap<>());
    }

    private ConfigSync() {
        // Utility class
    }

    /**
     * Daftarkan migrasi key lama -> baru (ekstensi di masa mendatang).
     *
     * @param resourceName Nama file resource (mis. config.yml)
     * @param oldKey Key lama
     * @param newKey Key baru
     */
    public static void registerMigration(String resourceName, String oldKey, String newKey) {
        MIGRATIONS.computeIfAbsent(resourceName, k -> new LinkedHashMap<>()).put(oldKey, newKey);
    }

    /**
     * Eksekusi sinkronisasi file konfigurasi server dengan resource bawaan plugin.
     *
     * @param plugin Instance plugin Bukkit
     * @param file File fisik di server
     * @param serverConfig Konfigurasi yang sedang dimuat di memori
     * @param resourceName Nama resource di dalam JAR (mis. "config.yml")
     * @return true jika file dimodifikasi dan disimpan ulang, false jika tidak ada perubahan
     */
    public static boolean sync(Plugin plugin, File file, FileConfiguration serverConfig, String resourceName) {
        if (plugin == null || file == null || serverConfig == null || resourceName == null) {
            return false;
        }

        Logger logger = plugin.getLogger();

        FileConfiguration defaultConfig = loadDefaultResource(plugin, resourceName);
        if (defaultConfig == null) {
            return false;
        }

        // Parse comments pada defaultConfig & serverConfig jika didukung Bukkit/Paper
        enableCommentParsing(defaultConfig);
        enableCommentParsing(serverConfig);

        boolean modified = false;

        // 1. Jalankan migrasi key lama -> baru sebelum sinkronisasi
        if (applyMigrations(serverConfig, resourceName, logger)) {
            modified = true;
        }

        // 2. Evaluasi config_schema dan config_version
        int defaultSchema = defaultConfig.getInt("config_schema", 1);
        int serverSchema = serverConfig.getInt("config_schema", -1);

        String defaultVersion = extractCleanVersion(defaultConfig.getString("config_version", "1.5"));
        String serverVersion = extractCleanVersion(serverConfig.getString("config_version", null));

        if (serverSchema == -1) {
            // File lama belum memiliki config_schema
            serverConfig.set("config_schema", defaultSchema);
            modified = true;
        } else if (serverSchema < defaultSchema) {
            serverConfig.set("config_schema", defaultSchema);
            modified = true;
        }

        if (serverVersion == null || !serverVersion.equals(defaultVersion)) {
            serverConfig.set("config_version", defaultVersion);
            modified = true;
        }

        // 3. Salin key yang hilang dari default ke server (TANPA menimpa nilai yang sudah ada)
        List<String> addedKeys = new ArrayList<>();
        Set<String> defaultKeys = defaultConfig.getKeys(true);

        for (String key : defaultKeys) {
            if (!serverConfig.contains(key)) {
                Object value = defaultConfig.get(key);
                // Hanya set nilai riil (bukan konfigurasi section murni tanpa value langsung)
                if (!(value instanceof ConfigurationSection)) {
                    serverConfig.set(key, value);
                    copyComments(defaultConfig, serverConfig, key);
                    addedKeys.add(key);
                    modified = true;
                }
            }
        }

        // Jika ada perubahan, buat cadangan dan simpan file fisik
        if (modified) {
            try {
                if (file.exists()) {
                    createRotatingBackup(file, defaultVersion, logger);
                }
                serverConfig.save(file);

                // Log hanya jika ada key yang benar-benar ditambahkan atau schema berubah
                if (!addedKeys.isEmpty()) {
                    logAddedKeys(logger, resourceName, addedKeys);
                } else {
                    logger.info("[ConfigSync] " + resourceName + " diperbarui ke skema " + defaultSchema + " (v" + defaultVersion + ").");
                }
            } catch (IOException e) {
                logger.severe("[ConfigSync] Gagal menyimpan " + file.getName() + " setelah sinkronisasi: " + e.getMessage());
                return false;
            }
        }

        return modified;
    }

    /**
     * Memuat resource bawaan dari JAR.
     */
    private static FileConfiguration loadDefaultResource(Plugin plugin, String resourceName) {
        try (InputStream stream = plugin.getResource(resourceName)) {
            if (stream == null) {
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            plugin.getLogger().warning("[ConfigSync] Tidak dapat membaca resource internal: " + resourceName);
            return null;
        }
    }

    /**
     * Menjalankan pemetaan key lama -> baru sesuai registri migrasi.
     */
    private static boolean applyMigrations(FileConfiguration serverConfig, String resourceName, Logger logger) {
        Map<String, String> migrationMap = MIGRATIONS.get(resourceName);
        if (migrationMap == null || migrationMap.isEmpty()) {
            return false;
        }

        boolean anyMigrated = false;
        for (Map.Entry<String, String> entry : migrationMap.entrySet()) {
            String oldKey = entry.getKey();
            String newKey = entry.getValue();

            if (serverConfig.contains(oldKey) && !serverConfig.contains(newKey)) {
                Object val = serverConfig.get(oldKey);
                serverConfig.set(newKey, val);
                serverConfig.set(oldKey, null); // Hapus key lama
                logger.info("[ConfigSync] Migrasi konfigurasi " + resourceName + ": key '" + oldKey + "' dipindahkan ke '" + newKey + "'");
                anyMigrated = true;
            }
        }
        return anyMigrated;
    }

    /**
     * Memotong metadata build (+SNAPSHOT, hash) sehingga hanya menyisakan versi bersih (misal 1.5).
     */
    public static String extractCleanVersion(String rawVersion) {
        if (rawVersion == null || rawVersion.trim().isEmpty()) {
            return "1.5";
        }
        String cleaned = rawVersion.trim();
        int plusIdx = cleaned.indexOf('+');
        if (plusIdx != -1) {
            cleaned = cleaned.substring(0, plusIdx);
        }
        int dashIdx = cleaned.indexOf("-SNAPSHOT");
        if (dashIdx != -1) {
            cleaned = cleaned.substring(0, dashIdx);
        }
        return cleaned.isEmpty() ? "1.5" : cleaned;
    }

    /**
     * Buat cadangan berputar <nama>.bak-<versi>.yml dengan retensi maksimal 3 file cadangan terakhir.
     */
    public static void createRotatingBackup(File file, String version, Logger logger) {
        File parent = file.getParentFile();
        if (parent == null || !file.exists()) {
            return;
        }

        String baseName = file.getName(); // mis. "config.yml"
        String prefix = baseName + ".bak-";

        // Cari semua file cadangan yang ada untuk file ini
        File[] existingBackups = parent.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".yml"));

        // Format nama cadangan baru dengan timestamp untuk memastikan keunikan
        long now = System.currentTimeMillis();
        String backupName = String.format("%s.bak-%s-%d.yml", baseName, version, now);
        File backupFile = new File(parent, backupName);

        try {
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("[ConfigSync] Gagal membuat cadangan untuk " + baseName + ": " + e.getMessage());
            }
            return;
        }

        // Rotasi: pertahankan maksimal MAX_BACKUPS (termasuk yang baru dibuat)
        File[] allBackups = parent.listFiles((dir, name) -> name.startsWith(prefix) && name.endsWith(".yml"));
        if (allBackups != null && allBackups.length > MAX_BACKUPS) {
            Arrays.sort(allBackups, Comparator.comparingLong(File::lastModified));
            int toDelete = allBackups.length - MAX_BACKUPS;
            for (int i = 0; i < toDelete; i++) {
                try {
                    Files.deleteIfExists(allBackups[i].toPath());
                } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Log daftar key yang ditambahkan (maksimal 20, sisanya 'dan N lainnya').
     */
    private static void logAddedKeys(Logger logger, String resourceName, List<String> addedKeys) {
        int total = addedKeys.size();
        int displayed = Math.min(total, MAX_LOGGED_KEYS);

        StringBuilder sb = new StringBuilder();
        sb.append("[ConfigSync] ").append(resourceName).append(" ditambahkan ").append(total).append(" key baru: ");
        for (int i = 0; i < displayed; i++) {
            if (i > 0) sb.append(", ");
            sb.append(addedKeys.get(i));
        }

        if (total > MAX_LOGGED_KEYS) {
            sb.append(" dan ").append(total - MAX_LOGGED_KEYS).append(" lainnya");
        }

        logger.info(sb.toString());
    }

    /**
     * Aktifkan parse comments via reflection jika platform mendukungnya.
     */
    private static void enableCommentParsing(FileConfiguration config) {
        try {
            Object options = config.options();
            Method parseCommentsMethod = options.getClass().getMethod("parseComments", boolean.class);
            parseCommentsMethod.invoke(options, true);
        } catch (Throwable ignored) {
            // Paper Comments API tidak tersedia atau versi lama
        }
    }

    /**
     * Salin header komentar & inline komentar menggunakan Paper API via reflection jika ada.
     */
    private static void copyComments(FileConfiguration source, FileConfiguration target, String path) {
        try {
            Method getComments = source.getClass().getMethod("getComments", String.class);
            Method setComments = target.getClass().getMethod("setComments", String.class, List.class);
            @SuppressWarnings("unchecked")
            List<String> comments = (List<String>) getComments.invoke(source, path);
            if (comments != null && !comments.isEmpty()) {
                setComments.invoke(target, path, comments);
            }

            Method getInlineComments = source.getClass().getMethod("getInlineComments", String.class);
            Method setInlineComments = target.getClass().getMethod("setInlineComments", String.class, List.class);
            @SuppressWarnings("unchecked")
            List<String> inlineComments = (List<String>) getInlineComments.invoke(source, path);
            if (inlineComments != null && !inlineComments.isEmpty()) {
                setInlineComments.invoke(target, path, inlineComments);
            }
        } catch (Throwable ignored) {
            // Abaikan jika Paper Comments API belum tersedia
        }
    }
}
