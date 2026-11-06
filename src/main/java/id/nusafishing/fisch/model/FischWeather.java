package id.nusafishing.fisch.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Enum model representing Fisch-style weather conditions and their environmental modifiers.
 */
public enum FischWeather {
    CLEAR("clear", "Cerah", "§e", "☀", 1.0, 1.0, null),
    RAIN("rain", "Hujan", "§9", "🌧", 1.15, 1.10, "SPLASH"),
    FOG("fog", "Berkabut", "§7", "🌫", 1.20, 1.25, "CAMPFIRE_COSY_SMOKE"),
    WINDY("windy", "Berangin", "§b", "💨", 1.25, 1.15, "CLOUD"),
    STORM("storm", "Badai", "§1", "⛈", 1.40, 1.50, "ELECTRIC_SPARK"),
    AURORA("aurora", "Aurora Borealis", "§a", "✨", 2.00, 2.50, "END_ROD"),
    ECLIPSE("eclipse", "Gerhana Matahari", "§5", "🌑", 2.50, 3.50, "PORTAL");

    private static final Map<String, FischWeather> BY_NAME;

    static {
        Map<String, FischWeather> map = new HashMap<>();
        for (FischWeather weather : values()) {
            map.put(weather.id.toLowerCase(), weather);
            map.put(weather.name().toLowerCase(), weather);
        }
        BY_NAME = Collections.unmodifiableMap(map);
    }

    private final String id;
    private final String displayName;
    private final String colorCode;
    private final String symbol;
    private final double luckModifier;
    private final double mutationModifier;
    private final String particleName;

    FischWeather(String id, String displayName, String colorCode, String symbol,
                 double luckModifier, double mutationModifier, String particleName) {
        this.id = id;
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.symbol = symbol;
        this.luckModifier = luckModifier;
        this.mutationModifier = mutationModifier;
        this.particleName = particleName;
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

    public double getLuckModifier() {
        return luckModifier;
    }

    public double getMutationModifier() {
        return mutationModifier;
    }

    public String getParticleName() {
        return particleName;
    }

    /**
     * Returns colored display with weather symbol, e.g. "§a✨ Aurora Borealis".
     */
    public String getFormattedName() {
        return colorCode + symbol + " " + displayName;
    }

    /**
     * Whether this weather is an extraordinary celestial event (Aurora or Eclipse).
     */
    public boolean isSpecial() {
        return this == AURORA || this == ECLIPSE;
    }

    /**
     * Whether this weather involves rain/storm in Minecraft terms.
     */
    public boolean isPrecipitation() {
        return this == RAIN || this == STORM;
    }

    /**
     * Safe lookup by identifier or enum name, defaulting to CLEAR if not found.
     */
    public static FischWeather fromString(String name) {
        if (name == null || name.isBlank()) {
            return CLEAR;
        }
        return BY_NAME.getOrDefault(name.trim().toLowerCase(), CLEAR);
    }
}
