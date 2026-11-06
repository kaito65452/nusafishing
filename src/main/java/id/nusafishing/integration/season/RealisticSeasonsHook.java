package id.nusafishing.integration.season;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.World;

import id.nusafishing.NusaFishing;

/**
 * Implementasi SeasonHook dengan dukungan RealisticSeasons melalui refleksi
 * dan fallback otomatis ke internal season clock.
 */
public class RealisticSeasonsHook implements SeasonHook {

    private final NusaFishing plugin;
    private boolean available = false;

    // Reflection targets for RealisticSeasons
    private Object seasonsApiInstance;
    private Method getSeasonMethod;
    private Method getDaysLeftMethod;

    public RealisticSeasonsHook(NusaFishing plugin) {
        this.plugin = plugin;
        checkAvailability();
    }

    /**
     * Memeriksa ketersediaan plugin RealisticSeasons dan mempersiapkan method refleksi.
     */
    public void checkAvailability() {
        this.available = false;
        this.seasonsApiInstance = null;
        this.getSeasonMethod = null;
        this.getDaysLeftMethod = null;

        if (!Bukkit.getPluginManager().isPluginEnabled("RealisticSeasons")) {
            plugin.getLogger().info("[RealisticSeasons] Plugin tidak ditemukan, menggunakan internal season clock.");
            return;
        }

        try {
            // Coba SeasonsAPI.getInstance().getSeason(world)
            Class<?> seasonsApiClass = Class.forName("me.casperge.realisticseasons.api.SeasonsAPI");
            Method getInstanceMethod = seasonsApiClass.getMethod("getInstance");
            this.seasonsApiInstance = getInstanceMethod.invoke(null);

            if (this.seasonsApiInstance != null) {
                try {
                    this.getSeasonMethod = seasonsApiClass.getMethod("getSeason", World.class);
                } catch (NoSuchMethodException e) {
                    this.getSeasonMethod = seasonsApiClass.getMethod("getSeason", String.class);
                }

                try {
                    this.getDaysLeftMethod = seasonsApiClass.getMethod("getDaysLeft", World.class);
                } catch (Throwable ignored) {
                    // Method getDaysLeft mungkin bernama lain atau tidak tersedia di versi tertentu
                }

                this.available = true;
                plugin.getLogger().info("[RealisticSeasons] Terdeteksi dan berhasil dihubungkan via SeasonsAPI!");
                return;
            }
        } catch (Throwable t) {
            // Coba alternatif RealisticSeasonsAPI
            try {
                Class<?> legacyApiClass = Class.forName("me.casperge.realisticseasons.RealisticSeasonsAPI");
                this.getSeasonMethod = legacyApiClass.getMethod("getSeason", World.class);
                this.available = true;
                plugin.getLogger().info("[RealisticSeasons] Terdeteksi dan berhasil dihubungkan via RealisticSeasonsAPI!");
                return;
            } catch (Throwable ignored) {
                plugin.getLogger().warning("[RealisticSeasons] Plugin aktif tetapi API tidak dapat diinisialisasi: " + t.getMessage() + ". Beralih ke internal clock.");
            }
        }

        this.available = false;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    /**
     * Mendapatkan musim saat ini. Jika RealisticSeasons tersedia, ambil dari RealisticSeasons.
     * Jika tidak atau jika terjadi kesalahan, fallback ke internal season clock.
     */
    @Override
    public Season getSeason(World world) {
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }

        if (available && getSeasonMethod != null) {
            try {
                Object result;
                if (getSeasonMethod.getParameterTypes()[0] == String.class) {
                    result = getSeasonMethod.invoke(seasonsApiInstance, world != null ? world.getName() : "world");
                } else {
                    result = getSeasonMethod.invoke(seasonsApiInstance, world);
                }

                if (result != null) {
                    return Season.fromString(result.toString());
                }
            } catch (Throwable t) {
                // Jangan spam console jika terjadi glitch sesaat, fallback aman
            }
        }

        return getInternalSeason(world);
    }

    @Override
    public int getDaysLeftInSeason(World world) {
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }

        if (available && getDaysLeftMethod != null) {
            try {
                Object result = getDaysLeftMethod.invoke(seasonsApiInstance, world);
                if (result instanceof Number) {
                    return ((Number) result).intValue();
                }
            } catch (Throwable ignored) {}
        }

        return getInternalDaysLeft(world);
    }

    /**
     * Internal season clock kalkulasi berbasis world time atau system time.
     * Secara default 7 hari in-game per musim (dapat disesuaikan di config).
     */
    public Season getInternalSeason(World world) {
        int daysPerSeason = getDaysPerSeasonConfig();
        long currentDay = getCurrentDay(world);
        long seasonIndex = (currentDay / daysPerSeason) % 4;

        switch ((int) seasonIndex) {
            case 0:
                return Season.SPRING;
            case 1:
                return Season.SUMMER;
            case 2:
                return Season.AUTUMN;
            case 3:
                return Season.WINTER;
            default:
                return Season.SPRING;
        }
    }

    /**
     * Menghitung sisa hari dalam musim berdasarkan internal clock.
     */
    public int getInternalDaysLeft(World world) {
        int daysPerSeason = getDaysPerSeasonConfig();
        long currentDay = getCurrentDay(world);
        int dayInSeason = (int) (currentDay % daysPerSeason);
        return Math.max(1, daysPerSeason - dayInSeason);
    }

    private long getCurrentDay(World world) {
        if (world != null) {
            return Math.max(0, world.getFullTime() / 24000L);
        }
        // Fallback: system time (20 menit per in-game day = 1.200.000 ms)
        return (System.currentTimeMillis() / 1200000L);
    }

    private int getDaysPerSeasonConfig() {
        if (plugin != null && plugin.getConfig() != null) {
            return Math.max(1, plugin.getConfig().getInt("seasons.days_per_season", 7));
        }
        return 7;
    }
}
