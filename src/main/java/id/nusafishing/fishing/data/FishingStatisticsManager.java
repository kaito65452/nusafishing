package id.nusafishing.fishing.data;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;

public class FishingStatisticsManager {
    private final NusaFishing plugin;
    private final File statsFile;
    private FileConfiguration statsConfig;
    private org.bukkit.scheduler.BukkitTask autosaveTask;
    
    // Cache untuk statistik
    private final Map<UUID, Integer> fishCaughtCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> legendaryFishCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> mythicFishCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> eventFishCache = new ConcurrentHashMap<>();
    private final Map<UUID, Double> largestFishWeightCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> largestFishNameCache = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, PlayerFishRecord>> playerFishRecords = new ConcurrentHashMap<>();
    
    // Data tersortir untuk leaderboard
    private Map<UUID, Integer> sortedFishCaught = new HashMap<>();
    private Map<UUID, Double> sortedLargestFish = new HashMap<>();
    private long lastLeaderboardUpdate = 0;
    private static final long LEADERBOARD_UPDATE_INTERVAL = 5 * 60 * 1000; // 5 menit
    
    public FishingStatisticsManager(NusaFishing plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "fishing_stats.yml");
        
        // Buat file jika belum ada
        if (!statsFile.exists()) {
            statsFile.getParentFile().mkdirs();
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Tidak bisa membuat fishing_stats.yml: " + e.getMessage());
            }
        }
        
        // Load konfigurasi
        this.statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        
        // Load data ke cache
        loadCaches();

        // Autosave periodik setiap 5 menit
        this.autosaveTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            saveAllStats();
        }, 20 * 60 * 5, 20 * 60 * 5);
    }
    
    public static class PlayerFishRecord {
        private final String fishId;
        private final int count;
        private final double maxWeight;
        private final int maxQuality;

        public PlayerFishRecord(String fishId, int count, double maxWeight, int maxQuality) {
            this.fishId = fishId;
            this.count = count;
            this.maxWeight = maxWeight;
            this.maxQuality = maxQuality;
        }

        public PlayerFishRecord(int count, double maxWeight, int maxQuality) {
            this(null, count, maxWeight, maxQuality);
        }

        public String getFishId() { return fishId; }
        public int getCount() { return count; }
        public double getMaxWeight() { return maxWeight; }
        public int getMaxQuality() { return maxQuality; }
    }

    private void loadCaches() {
        // Load data dari config ke cache
        for (String uuidString : statsConfig.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);

                fishCaughtCache.put(uuid, statsConfig.getInt(uuidString + ".fish_caught", 0));
                legendaryFishCache.put(uuid, statsConfig.getInt(uuidString + ".legendary_fish", 0));
                mythicFishCache.put(uuid, statsConfig.getInt(uuidString + ".mythic_fish", 0));
                eventFishCache.put(uuid, statsConfig.getInt(uuidString + ".event_fish", 0));
                largestFishWeightCache.put(uuid, statsConfig.getDouble(uuidString + ".largest_fish_weight", 0.0));
                String storedName = statsConfig.getString(uuidString + ".largest_fish_name");
                largestFishNameCache.put(uuid, storedName != null ? storedName : "Tidak Ada");

                // Load caught fish per-id (migrasi aman jika section caught_fish belum ada)
                org.bukkit.configuration.ConfigurationSection caughtSection =
                        statsConfig.getConfigurationSection(uuidString + ".caught_fish");
                if (caughtSection != null) {
                    Map<String, PlayerFishRecord> fishMap = new ConcurrentHashMap<>();
                    for (String fishId : caughtSection.getKeys(false)) {
                        int count = caughtSection.getInt(fishId + ".count", 0);
                        double maxWeight = caughtSection.getDouble(fishId + ".max_weight", 0.0);
                        int maxQuality = caughtSection.getInt(fishId + ".max_quality", 0);
                        fishMap.put(fishId, new PlayerFishRecord(fishId, count, maxWeight, maxQuality));
                    }
                    playerFishRecords.put(uuid, fishMap);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID tidak valid di fishing_stats.yml: " + uuidString);
            }
        }

        // Sortir leaderboard pertama kali
        updateLeaderboards();
    }
    
    /**
     * Update leaderboard jika data sudah kadaluarsa
     */
    private void updateLeaderboards() {
        long now = System.currentTimeMillis();
        
        if (now - lastLeaderboardUpdate > LEADERBOARD_UPDATE_INTERVAL) {
            // Sortir berdasarkan jumlah ikan
            sortedFishCaught = fishCaughtCache.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
            
            // Sortir berdasarkan berat ikan terbesar
            sortedLargestFish = largestFishWeightCache.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
            
            lastLeaderboardUpdate = now;
        }
    }
    
    /**
     * Catat ikan yang tertangkap oleh pemain
     * @param playerId UUID pemain
     * @param fish Ikan yang tertangkap
     */
    public void recordFishCaught(UUID playerId, CustomFish fish) {
        if (playerId == null || fish == null) return;

        // Tambah total ikan
        int currentCount = fishCaughtCache.getOrDefault(playerId, 0);
        fishCaughtCache.put(playerId, currentCount + 1);

        // Cek ikan mythic
        if (fish.getActualTier() == CustomFish.FishTier.MYTHIC) {
            int mythicCount = mythicFishCache.getOrDefault(playerId, 0);
            mythicFishCache.put(playerId, mythicCount + 1);
        }

        // Cek ikan legendary atau mythic
        if (fish.getActualTier() == CustomFish.FishTier.LEGENDARY || fish.getActualTier() == CustomFish.FishTier.MYTHIC) {
            int legendaryCount = legendaryFishCache.getOrDefault(playerId, 0);
            legendaryFishCache.put(playerId, legendaryCount + 1);
        }

        // Cek ikan event
        if (fish.getId() != null && fish.getId().startsWith("event_")) {
            int eventCount = eventFishCache.getOrDefault(playerId, 0);
            eventFishCache.put(playerId, eventCount + 1);
        }

        // Cek ikan terbesar
        double fishWeight = fish.getWeight();
        double currentLargest = largestFishWeightCache.getOrDefault(playerId, 0.0);

        if (fishWeight > currentLargest) {
            largestFishWeightCache.put(playerId, fishWeight);
            String fishName = fish.getName() != null ? fish.getName() : "Ikan Tanpa Nama";
            largestFishNameCache.put(playerId, fishName);
        }

        // Catat statistik per-ID ikan untuk fishbook
        if (fish.getId() != null && !fish.getId().isBlank()) {
            final String fishId = fish.getId();
            final double currentWeight = fish.getWeight();
            final int currentQuality = fish.getQuality();
            playerFishRecords.compute(playerId, (k, currentMap) -> {
                Map<String, PlayerFishRecord> map = currentMap != null ? currentMap : new ConcurrentHashMap<>();
                PlayerFishRecord existing = map.get(fishId);
                int newCount = (existing != null ? existing.getCount() : 0) + 1;
                double newMaxWeight = existing != null ? Math.max(existing.getMaxWeight(), currentWeight) : currentWeight;
                int newMaxQuality = existing != null ? Math.max(existing.getMaxQuality(), currentQuality) : currentQuality;
                map.put(fishId, new PlayerFishRecord(fishId, newCount, newMaxWeight, newMaxQuality));
                return map;
            });
        }

        // Stats cached in memory, periodically saved to disk
    }

    /**
     * Simpan statistik pemain ke file konfigurasi
     * @param playerId UUID pemain
     */
    private synchronized void savePlayerStats(UUID playerId) {
        String path = playerId.toString();

        statsConfig.set(path + ".fish_caught", fishCaughtCache.getOrDefault(playerId, 0));
        statsConfig.set(path + ".legendary_fish", legendaryFishCache.getOrDefault(playerId, 0));
        statsConfig.set(path + ".mythic_fish", mythicFishCache.getOrDefault(playerId, 0));
        statsConfig.set(path + ".event_fish", eventFishCache.getOrDefault(playerId, 0));
        statsConfig.set(path + ".largest_fish_weight", largestFishWeightCache.getOrDefault(playerId, 0.0));
        statsConfig.set(path + ".largest_fish_name", largestFishNameCache.getOrDefault(playerId, "Tidak Ada"));

        Map<String, PlayerFishRecord> fishMap = playerFishRecords.get(playerId);
        if (fishMap != null) {
            for (Map.Entry<String, PlayerFishRecord> entry : fishMap.entrySet()) {
                String fishPath = path + ".caught_fish." + entry.getKey();
                statsConfig.set(fishPath + ".count", entry.getValue().getCount());
                statsConfig.set(fishPath + ".max_weight", entry.getValue().getMaxWeight());
                statsConfig.set(fishPath + ".max_quality", entry.getValue().getMaxQuality());
            }
        }

        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Tidak bisa menyimpan fishing_stats.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        // Save synchronously on shutdown
        saveAllStatsDirect();
    }

    /**
     * Simpan semua statistik pemain sekaligus dengan snapshot atomic thread-safe.
     */
    public synchronized void saveAllStats() {
        saveAllStatsDirect();
    }

    private synchronized void saveAllStatsDirect() {
        YamlConfiguration configToSave = new YamlConfiguration();
        Map<UUID, Integer> fishCaughtSnapshot = new HashMap<>(fishCaughtCache);
        Map<UUID, Integer> legendarySnapshot = new HashMap<>(legendaryFishCache);
        Map<UUID, Integer> mythicSnapshot = new HashMap<>(mythicFishCache);
        Map<UUID, Integer> eventSnapshot = new HashMap<>(eventFishCache);
        Map<UUID, Double> weightSnapshot = new HashMap<>(largestFishWeightCache);
        Map<UUID, String> nameSnapshot = new HashMap<>(largestFishNameCache);

        Set<UUID> allPlayers = new HashSet<>(fishCaughtSnapshot.keySet());
        allPlayers.addAll(playerFishRecords.keySet());

        for (UUID playerId : allPlayers) {
            String path = playerId.toString();
            configToSave.set(path + ".fish_caught", fishCaughtSnapshot.getOrDefault(playerId, 0));
            configToSave.set(path + ".legendary_fish", legendarySnapshot.getOrDefault(playerId, 0));
            configToSave.set(path + ".mythic_fish", mythicSnapshot.getOrDefault(playerId, 0));
            configToSave.set(path + ".event_fish", eventSnapshot.getOrDefault(playerId, 0));
            configToSave.set(path + ".largest_fish_weight", weightSnapshot.getOrDefault(playerId, 0.0));
            configToSave.set(path + ".largest_fish_name", nameSnapshot.getOrDefault(playerId, "Tidak Ada"));

            Map<String, PlayerFishRecord> fishMap = playerFishRecords.get(playerId);
            if (fishMap != null) {
                for (Map.Entry<String, PlayerFishRecord> entry : fishMap.entrySet()) {
                    String fishPath = path + ".caught_fish." + entry.getKey();
                    configToSave.set(fishPath + ".count", entry.getValue().getCount());
                    configToSave.set(fishPath + ".max_weight", entry.getValue().getMaxWeight());
                    configToSave.set(fishPath + ".max_quality", entry.getValue().getMaxQuality());
                }
            }
        }
        try {
            configToSave.save(statsFile);
            this.statsConfig = configToSave;
        } catch (IOException e) {
            plugin.getLogger().severe("Tidak bisa menyimpan fishing_stats.yml: " + e.getMessage());
        }
    }
    
    // Getter untuk placeholder
    
    public int getTotalFishCaught(UUID playerId) {
        return fishCaughtCache.getOrDefault(playerId, 0);
    }
    
    public int getLegendaryFishCaught(UUID playerId) {
        return legendaryFishCache.getOrDefault(playerId, 0);
    }

    public int getMythicFishCaught(UUID playerId) {
        return mythicFishCache.getOrDefault(playerId, 0);
    }
    
    public int getEventFishCaught(UUID playerId) {
        return eventFishCache.getOrDefault(playerId, 0);
    }
    
    public double getLargestFishWeight(UUID playerId) {
        return largestFishWeightCache.getOrDefault(playerId, 0.0);
    }
    
    public String getLargestFishName(UUID playerId) {
        return largestFishNameCache.getOrDefault(playerId, "Tidak Ada");
    }
    
    // Method untuk leaderboard
    
    public Map.Entry<UUID, Integer> getTopFisher(int rank) {
        updateLeaderboards();
        
        // Konversi ke list untuk akses indeks
        List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(sortedFishCaught.entrySet());
        
        if (rank > 0 && rank <= entries.size()) {
            return entries.get(rank - 1);
        }
        
        return null;
    }
    
    public Map.Entry<UUID, Double> getTopLargestFish(int rank) {
        updateLeaderboards();

        // Konversi ke list untuk akses indeks
        List<Map.Entry<UUID, Double>> entries = new ArrayList<>(sortedLargestFish.entrySet());

        if (rank > 0 && rank <= entries.size()) {
            return entries.get(rank - 1);
        }

        return null;
    }

    // Methods untuk fishbook
    public boolean hasCaughtFish(UUID playerId, String fishId) {
        if (playerId == null || fishId == null) return false;
        Map<String, PlayerFishRecord> records = playerFishRecords.get(playerId);
        return records != null && records.containsKey(fishId) && records.get(fishId).getCount() > 0;
    }

    public PlayerFishRecord getFishRecord(UUID playerId, String fishId) {
        if (playerId == null || fishId == null) return null;
        Map<String, PlayerFishRecord> records = playerFishRecords.get(playerId);
        return records != null ? records.get(fishId) : null;
    }

    public Map<String, PlayerFishRecord> getPlayerFishRecords(UUID playerId) {
        if (playerId == null) return Collections.emptyMap();
        Map<String, PlayerFishRecord> records = playerFishRecords.get(playerId);
        return records != null ? Collections.unmodifiableMap(records) : Collections.emptyMap();
    }

    public int getDistinctFishCaughtCount(UUID playerId) {
        if (playerId == null) return 0;
        Map<String, PlayerFishRecord> records = playerFishRecords.get(playerId);
        return records != null ? records.size() : 0;
    }
}