package id.nusafishing.integration.season;

import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Interface umum untuk mendapatkan informasi musim saat ini.
 * Mendukung integrasi dengan plugin eksternal seperti RealisticSeasons
 * atau fallback ke internal season clock.
 */
public interface SeasonHook {

    /**
     * Enum representasi empat musim.
     */
    enum Season {
        SPRING("Musim Semi", "§a"),
        SUMMER("Musim Panas", "§e"),
        AUTUMN("Musim Gugur", "§6"),
        WINTER("Musim Dingin", "§b");

        private final String displayName;
        private final String colorCode;

        Season(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }

        public String getColoredName() {
            return colorCode + displayName;
        }

        public static Season fromString(String name) {
            if (name == null || name.isBlank()) {
                return SPRING;
            }
            String upper = name.trim().toUpperCase();
            switch (upper) {
                case "SPRING":
                    return SPRING;
                case "SUMMER":
                    return SUMMER;
                case "AUTUMN":
                case "FALL":
                    return AUTUMN;
                case "WINTER":
                    return WINTER;
                default:
                    return SPRING;
            }
        }
    }

    /**
     * Memeriksa apakah provider RealisticSeasons aktif dan terhubung.
     *
     * @return true jika RealisticSeasons terhubung, false jika menggunakan internal clock
     */
    boolean isAvailable();

    /**
     * Mendapatkan musim saat ini di dunia tertentu.
     *
     * @param world Bukkit World
     * @return Season saat ini
     */
    Season getSeason(World world);

    /**
     * Mendapatkan musim saat ini berdasarkan lokasi player.
     *
     * @param player Pemain
     * @return Season saat ini
     */
    default Season getSeason(Player player) {
        if (player == null || player.getWorld() == null) {
            return Season.SPRING;
        }
        return getSeason(player.getWorld());
    }

    /**
     * Mendapatkan nama musim yang sudah diformat dengan warna.
     *
     * @param world Bukkit World
     * @return Nama musim berformat
     */
    default String getSeasonName(World world) {
        Season season = getSeason(world);
        return season != null ? season.getColoredName() : Season.SPRING.getColoredName();
    }

    /**
     * Mendapatkan sisa hari di musim saat ini.
     *
     * @param world Bukkit World
     * @return Sisa hari dalam integer
     */
    int getDaysLeftInSeason(World world);
}
