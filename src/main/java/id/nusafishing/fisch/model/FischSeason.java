package id.nusafishing.fisch.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enum model representing Fisch-style fishing seasons.
 * <p>
 * Cycle logic:
 * - 1 Minecraft Day = 24,000 ticks
 * - 1 Season = 7 Minecraft Days = 168,000 ticks
 * - 1 Annual Cycle (4 seasons) = 28 Minecraft Days = 672,000 ticks (~9.33 hours in real time at 20 TPS)
 */
public enum FischSeason {
    SPRING("spring", "Musim Semi", "§a", "🌸", 1.20, 1.00, 1.15, 1.10,
            "Ikan lebih aktif memakan umpan, peluang catch speed meningkat."),
    SUMMER("summer", "Musim Panas", "§6", "☀", 1.00, 1.30, 1.05, 1.00,
            "Sinar matahari memicu pertumbuhan ikan, bobot ikan yang didapat meningkat tajam."),
    AUTUMN("autumn", "Musim Gugur", "§c", "🍂", 1.10, 1.05, 1.30, 1.20,
            "Angin sejuk musim gugur menarik ikan langka, meningkatkan Fish Luck."),
    WINTER("winter", "Musim Dingin", "§b", "❄", 1.05, 1.00, 1.10, 1.45,
            "Suhu dingin ekstrem memicu mutasi biologis langka pada spesies ikan.");

    public static final long TICKS_PER_DAY = 24000L;
    public static final long DAYS_PER_SEASON = 7L;
    public static final long TICKS_PER_SEASON = DAYS_PER_SEASON * TICKS_PER_DAY; // 168,000
    public static final long TOTAL_CYCLE_TICKS = 4L * TICKS_PER_SEASON;           // 672,000

    private static final FischSeason[] SEASONS = values();
    private static final Map<String, FischSeason> BY_NAME;

    static {
        Map<String, FischSeason> map = new HashMap<>();
        for (FischSeason season : SEASONS) {
            map.put(season.id.toLowerCase(), season);
            map.put(season.name().toLowerCase(), season);
        }
        BY_NAME = Collections.unmodifiableMap(map);
    }

    private final String id;
    private final String displayName;
    private final String colorCode;
    private final String symbol;
    private final double catchSpeedMultiplier;
    private final double weightMultiplier;
    private final double luckMultiplier;
    private final double mutationMultiplier;
    private final String description;

    FischSeason(String id, String displayName, String colorCode, String symbol,
                double catchSpeedMultiplier, double weightMultiplier,
                double luckMultiplier, double mutationMultiplier, String description) {
        this.id = id;
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.symbol = symbol;
        this.catchSpeedMultiplier = catchSpeedMultiplier;
        this.weightMultiplier = weightMultiplier;
        this.luckMultiplier = luckMultiplier;
        this.mutationMultiplier = mutationMultiplier;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getCatchSpeedMultiplier() {
        return catchSpeedMultiplier;
    }

    public double getWeightMultiplier() {
        return weightMultiplier;
    }

    public double getLuckMultiplier() {
        return luckMultiplier;
    }

    public double getMutationMultiplier() {
        return mutationMultiplier;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Formatted string e.g. "§a🌸 Musim Semi".
     */
    public String getFormattedName() {
        return colorCode + symbol + " " + displayName;
    }

    /**
     * Get next season in rotation.
     */
    public FischSeason next() {
        return SEASONS[(this.ordinal() + 1) % SEASONS.length];
    }

    /**
     * Get previous season in rotation.
     */
    public FischSeason previous() {
        return SEASONS[(this.ordinal() + SEASONS.length - 1) % SEASONS.length];
    }

    /**
     * Calculates the season based on world.getFullTime().
     */
    public static FischSeason fromWorldTime(long fullTime) {
        long cycleTicks = Math.floorMod(fullTime, TOTAL_CYCLE_TICKS);
        int index = (int) (cycleTicks / TICKS_PER_SEASON);
        if (index < 0 || index >= SEASONS.length) {
            index = 0;
        }
        return SEASONS[index];
    }

    /**
     * Returns the day number within the current season (1 to 7).
     */
    public static int getDayOfSeason(long fullTime) {
        long cycleTicks = Math.floorMod(fullTime, TOTAL_CYCLE_TICKS);
        long ticksIntoSeason = cycleTicks % TICKS_PER_SEASON;
        return (int) (ticksIntoSeason / TICKS_PER_DAY) + 1;
    }

    /**
     * Returns the number of Minecraft days remaining until the next season.
     */
    public static int getDaysRemaining(long fullTime) {
        return (int) (DAYS_PER_SEASON - getDayOfSeason(fullTime));
    }

    /**
     * Safe lookup by id or name, defaulting to SPRING.
     */
    public static FischSeason fromString(String name) {
        if (name == null || name.isBlank()) {
            return SPRING;
        }
        return BY_NAME.getOrDefault(name.trim().toLowerCase(), SPRING);
    }
}
