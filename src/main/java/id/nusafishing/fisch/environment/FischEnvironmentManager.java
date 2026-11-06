package id.nusafishing.fisch.environment;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import id.nusafishing.NusaFishing;
import id.nusafishing.compat.CompatibilityHelper;
import id.nusafishing.fisch.model.FischSeason;
import id.nusafishing.fisch.model.FischTotem;
import id.nusafishing.fisch.model.FischWeather;

/**
 * Fisch Environment Manager.
 * <p>
 * Controls world weather, 4-season astronomical cycle, active totems,
 * lightweight visual particle effects, and provides O(1) cached fishing stat boosts.
 */
public class FischEnvironmentManager {
    private final NusaFishing plugin;

    // Active totems per world UUID
    private final Map<UUID, ActiveTotemSession> activeTotems = new ConcurrentHashMap<>();

    // Manual / forced weather overrides per world (if not from totem)
    private final Map<UUID, WeatherOverride> customWeathers = new ConcurrentHashMap<>();

    // O(1) Environment Stats Cache per world UUID
    private final Map<UUID, EnvironmentCache> cacheMap = new ConcurrentHashMap<>();

    private BukkitTask updateTask;
    private BukkitTask particleTask;

    public FischEnvironmentManager(NusaFishing plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        startTasks();
    }

    /**
     * Starts the periodic update task (every 20s) and lightweight totem particle task (every 2s).
     */
    private void startTasks() {
        // Scheduler running every 20 seconds (400 ticks) to monitor weather, seasons, and totem expirations
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllWorlds, 20L, 400L);

        // Lightweight particle emitter running every 2 seconds (40 ticks) for active totems
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::renderTotemParticles, 40L, 40L);
    }

    /**
     * Shuts down all tasks and cleans up resources.
     */
    public void shutdown() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }
        if (particleTask != null && !particleTask.isCancelled()) {
            particleTask.cancel();
            particleTask = null;
        }
        activeTotems.clear();
        customWeathers.clear();
        cacheMap.clear();
    }

    /**
     * Periodic update checking all loaded worlds.
     */
    public void updateAllWorlds() {
        long now = System.currentTimeMillis();

        // 1. Process active totem expirations
        for (Map.Entry<UUID, ActiveTotemSession> entry : activeTotems.entrySet()) {
            ActiveTotemSession session = entry.getValue();
            if (session.isExpired(now)) {
                UUID worldUuid = entry.getKey();
                activeTotems.remove(worldUuid);

                World world = Bukkit.getWorld(worldUuid);
                if (world != null) {
                    notifyTotemExpired(world, session.getTotem());
                }
            }
        }

        // 2. Process manual weather expirations
        for (Map.Entry<UUID, WeatherOverride> entry : customWeathers.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                customWeathers.remove(entry.getKey());
            }
        }

        // 3. Rebuild O(1) cache for each world
        for (World world : Bukkit.getWorlds()) {
            refreshCache(world);
        }
    }

    /**
     * Rebuilds O(1) cache for a specific world.
     */
    public EnvironmentCache refreshCache(World world) {
        if (world == null) return null;

        UUID worldUuid = world.getUID();
        long now = System.currentTimeMillis();

        // 1. Resolve Active Totem
        ActiveTotemSession session = activeTotems.get(worldUuid);
        if (session != null && session.isExpired(now)) {
            activeTotems.remove(worldUuid);
            session = null;
        }
        FischTotem activeTotem = (session != null) ? session.getTotem() : null;

        // 2. Resolve Weather
        FischWeather weather;
        if (activeTotem != null && activeTotem.getWeatherTarget() != null) {
            weather = activeTotem.getWeatherTarget();
        } else {
            WeatherOverride override = customWeathers.get(worldUuid);
            if (override != null && !override.isExpired(now)) {
                weather = override.getWeather();
            } else if (world.isThundering()) {
                weather = FischWeather.STORM;
            } else if (world.hasStorm()) {
                weather = FischWeather.RAIN;
            } else {
                weather = FischWeather.CLEAR;
            }
        }

        // 3. Resolve Season
        FischSeason season;
        if (activeTotem != null && activeTotem.getSeasonTarget() != null) {
            season = activeTotem.getSeasonTarget();
        } else {
            season = FischSeason.fromWorldTime(world.getFullTime());
        }

        // 4. Compute Environmental Stat Boosts
        // Base modifiers from weather & season relative to 1.0 baseline
        double luckBoost = (weather.getLuckModifier() - 1.0) + (season.getLuckMultiplier() - 1.0);
        double mutationBoost = (weather.getMutationModifier() - 1.0) + (season.getMutationMultiplier() - 1.0);

        if (activeTotem != null) {
            luckBoost += activeTotem.getLuckBoost();
            mutationBoost += activeTotem.getMutationBoost();
        }

        EnvironmentCache newCache = new EnvironmentCache(weather, season, activeTotem, luckBoost, mutationBoost, now);
        cacheMap.put(worldUuid, newCache);
        return newCache;
    }

    /**
     * O(1) lookup for current weather in a world.
     */
    public FischWeather getCurrentWeather(World world) {
        if (world == null) return FischWeather.CLEAR;
        EnvironmentCache cached = cacheMap.get(world.getUID());
        if (cached != null) {
            return cached.getWeather();
        }
        return refreshCache(world).getWeather();
    }

    /**
     * O(1) lookup for current season in a world.
     */
    public FischSeason getCurrentSeason(World world) {
        if (world == null) return FischSeason.SPRING;
        EnvironmentCache cached = cacheMap.get(world.getUID());
        if (cached != null) {
            return cached.getSeason();
        }
        return refreshCache(world).getSeason();
    }

    /**
     * O(1) lookup for active totem in a world (or null if none active).
     */
    public FischTotem getActiveTotem(World world) {
        if (world == null) return null;
        EnvironmentCache cached = cacheMap.get(world.getUID());
        if (cached != null) {
            return cached.getActiveTotem();
        }
        return refreshCache(world).getActiveTotem();
    }

    /**
     * O(1) lookup for total environmental luck boost in a world.
     */
    public double getEnvironmentLuckBoost(World world) {
        if (world == null) return 0.0;
        EnvironmentCache cached = cacheMap.get(world.getUID());
        if (cached != null) {
            return cached.getLuckBoost();
        }
        return refreshCache(world).getLuckBoost();
    }

    /**
     * O(1) lookup for total environmental mutation boost in a world.
     */
    public double getEnvironmentMutationBoost(World world) {
        if (world == null) return 0.0;
        EnvironmentCache cached = cacheMap.get(world.getUID());
        if (cached != null) {
            return cached.getMutationBoost();
        }
        return refreshCache(world).getMutationBoost();
    }

    /**
     * Activates a Fisch Totem in the specified world.
     *
     * @param world      Target Bukkit World
     * @param totem      FischTotem definition
     * @param location   Activation location (for visuals and particles)
     * @param activator  Player who placed/activated the totem (nullable for console/admin)
     * @return true if activation succeeded
     */
    public boolean activateTotem(World world, FischTotem totem, Location location, Player activator) {
        if (world == null || totem == null) return false;

        UUID worldUuid = world.getUID();
        long expireTime = System.currentTimeMillis() + (totem.getDurationSeconds() * 1000L);
        UUID activatorUuid = activator != null ? activator.getUniqueId() : null;
        String activatorName = activator != null ? activator.getName() : "Sistem";

        ActiveTotemSession session = new ActiveTotemSession(totem, expireTime, location, activatorUuid, activatorName);
        activeTotems.put(worldUuid, session);

        // Synchronize vanilla world weather if needed
        applyWorldWeather(world, totem.getWeatherTarget());

        // Audio-visual activation burst
        Location fxLoc = location != null ? location : (activator != null ? activator.getLocation() : null);
        if (fxLoc != null && fxLoc.getWorld() != null) {
            try {
                fxLoc.getWorld().playSound(fxLoc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                fxLoc.getWorld().playSound(fxLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
            } catch (Throwable ignored) {}

            spawnSafeParticle(fxLoc.clone().add(0, 1.0, 0), Particle.PORTAL, 40, 0.5, 0.5, 0.5, 0.1);
        }

        // World broadcast announcement
        String message = String.format("§a[NusaFishing] §e%s §7telah mengaktifkan %s §7di perairan ini! (Durasi: §e%d detik§7)",
                activatorName, totem.getName(), totem.getDurationSeconds());
        for (Player p : world.getPlayers()) {
            p.sendMessage(message);
        }

        // Immediate O(1) cache update
        refreshCache(world);
        return true;
    }

    /**
     * Cancels any active totem in the specified world.
     */
    public boolean cancelTotem(World world) {
        if (world == null) return false;
        ActiveTotemSession removed = activeTotems.remove(world.getUID());
        if (removed != null) {
            notifyTotemExpired(world, removed.getTotem());
            refreshCache(world);
            return true;
        }
        return false;
    }

    /**
     * Overrides weather for a given duration without a totem.
     */
    public void setCustomWeather(World world, FischWeather weather, long durationSeconds) {
        if (world == null || weather == null) return;
        long expireTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        customWeathers.put(world.getUID(), new WeatherOverride(weather, expireTime));
        applyWorldWeather(world, weather);
        refreshCache(world);
    }

    private void applyWorldWeather(World world, FischWeather weather) {
        if (world == null || weather == null) return;
        try {
            if (weather == FischWeather.STORM) {
                world.setStorm(true);
                world.setThundering(true);
            } else if (weather == FischWeather.RAIN) {
                world.setStorm(true);
                world.setThundering(false);
            } else if (weather == FischWeather.CLEAR) {
                world.setStorm(false);
                world.setThundering(false);
            }
        } catch (Throwable ignored) {}
    }

    private void notifyTotemExpired(World world, FischTotem totem) {
        if (world == null || totem == null) return;
        String message = String.format("§c[NusaFishing] §7Efek %s §7telah berakhir. Cuaca dan kondisi perairan kembali normal.", totem.getName());
        for (Player p : world.getPlayers()) {
            p.sendMessage(message);
        }
    }

    /**
     * Lightweight particle spawner for active totems (every 2s).
     */
    private void renderTotemParticles() {
        if (activeTotems.isEmpty()) return;

        for (ActiveTotemSession session : activeTotems.values()) {
            Location loc = session.getLocation();
            if (loc == null || loc.getWorld() == null) continue;

            World w = loc.getWorld();
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            if (!w.isChunkLoaded(chunkX, chunkZ)) continue;

            Particle particle = resolveParticleForTotem(session.getTotem());
            if (particle != null) {
                spawnSafeParticle(loc.clone().add(0, 1.2, 0), particle, 8, 0.4, 0.6, 0.4, 0.02);
            }
        }
    }

    private Particle resolveParticleForTotem(FischTotem totem) {
        if (totem == null) return Particle.PORTAL;
        switch (totem.getEffectType()) {
            case AURORA_TOTEM:
                return Particle.END_ROD;
            case ECLIPSE_TOTEM:
                return Particle.PORTAL;
            case TEMPEST_TOTEM:
                try {
                    return Particle.valueOf("ELECTRIC_SPARK");
                } catch (Throwable t) {
                    return Particle.PORTAL;
                }
            case SMOKESCREEN_TOTEM:
                try {
                    return Particle.valueOf("CAMPFIRE_COSY_SMOKE");
                } catch (Throwable t) {
                    return Particle.CLOUD;
                }
            case WIND_TOTEM:
                return Particle.CLOUD;
            case SUNDIAL_TOTEM:
            default:
                return CompatibilityHelper.TOTEM != null ? CompatibilityHelper.TOTEM : Particle.PORTAL;
        }
    }

    private void spawnSafeParticle(Location loc, Particle particle, int count, double ox, double oy, double oz, double speed) {
        if (loc == null || loc.getWorld() == null || particle == null) return;
        try {
            loc.getWorld().spawnParticle(particle, loc, count, ox, oy, oz, speed);
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // Inner Data Containers
    // =========================================================================

    public static class ActiveTotemSession {
        private final FischTotem totem;
        private final long expireTimeMillis;
        private final Location location;
        private final UUID activatorUUID;
        private final String activatorName;

        public ActiveTotemSession(FischTotem totem, long expireTimeMillis, Location location,
                                  UUID activatorUUID, String activatorName) {
            this.totem = totem;
            this.expireTimeMillis = expireTimeMillis;
            this.location = location != null ? location.clone() : null;
            this.activatorUUID = activatorUUID;
            this.activatorName = activatorName;
        }

        public FischTotem getTotem() {
            return totem;
        }

        public long getExpireTimeMillis() {
            return expireTimeMillis;
        }

        public Location getLocation() {
            return location != null ? location.clone() : null;
        }

        public UUID getActivatorUUID() {
            return activatorUUID;
        }

        public String getActivatorName() {
            return activatorName;
        }

        public boolean isExpired(long nowMillis) {
            return nowMillis >= expireTimeMillis;
        }

        public long getRemainingSeconds(long nowMillis) {
            return Math.max(0L, (expireTimeMillis - nowMillis) / 1000L);
        }
    }

    public static class EnvironmentCache {
        private final FischWeather weather;
        private final FischSeason season;
        private final FischTotem activeTotem;
        private final double luckBoost;
        private final double mutationBoost;
        private final long lastCalculatedTime;

        public EnvironmentCache(FischWeather weather, FischSeason season, FischTotem activeTotem,
                                double luckBoost, double mutationBoost, long lastCalculatedTime) {
            this.weather = weather != null ? weather : FischWeather.CLEAR;
            this.season = season != null ? season : FischSeason.SPRING;
            this.activeTotem = activeTotem;
            this.luckBoost = luckBoost;
            this.mutationBoost = mutationBoost;
            this.lastCalculatedTime = lastCalculatedTime;
        }

        public FischWeather getWeather() {
            return weather;
        }

        public FischSeason getSeason() {
            return season;
        }

        public FischTotem getActiveTotem() {
            return activeTotem;
        }

        public double getLuckBoost() {
            return luckBoost;
        }

        public double getMutationBoost() {
            return mutationBoost;
        }

        public long getLastCalculatedTime() {
            return lastCalculatedTime;
        }
    }

    private static class WeatherOverride {
        private final FischWeather weather;
        private final long expireTimeMillis;

        public WeatherOverride(FischWeather weather, long expireTimeMillis) {
            this.weather = weather;
            this.expireTimeMillis = expireTimeMillis;
        }

        public FischWeather getWeather() {
            return weather;
        }

        public boolean isExpired(long now) {
            return now >= expireTimeMillis;
        }
    }
}
