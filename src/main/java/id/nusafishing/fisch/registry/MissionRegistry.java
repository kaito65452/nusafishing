package id.nusafishing.fisch.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;

import id.nusafishing.fisch.model.FischMission;

/**
 * Registry for Fisch Missions and Quests.
 * Manages daily mission pools, custom missions loaded from YAML, and random selection.
 */
public class MissionRegistry {
    private final Map<String, FischMission> missions = new ConcurrentHashMap<>();

    public MissionRegistry() {
        registerDefaults();
    }

    /**
     * Registers default missions providing diverse rewards:
     * Money, EXP, Bait, Totem, Rod.
     */
    private void registerDefaults() {
        register(new FischMission("daily_warmup", "Pemanasan Nelayan",
                "Tangkap 5 ikan jenis apa saja.",
                5, null, null,
                FischMission.RewardType.MONEY, null, 1500.0));

        register(new FischMission("rare_hunter", "Pemburu Ikan Langka",
                "Tangkap 3 ikan bertier RARE ke atas.",
                3, null, "RARE",
                FischMission.RewardType.EXP, null, 500.0));

        register(new FischMission("bait_collector", "Pencari Umpan Alami",
                "Tangkap 8 ikan untuk ditukarkan dengan pasokan umpan segar.",
                8, null, null,
                FischMission.RewardType.BAIT, "cacing_tanah", 5.0));

        register(new FischMission("totem_ritual", "Ritual Cuaca Purba",
                "Tangkap 2 ikan bertier EPIC atau lebih tinggi untuk membangkitkan kekuatan totem.",
                2, null, "EPIC",
                FischMission.RewardType.TOTEM, "wind_totem", 1.0));

        register(new FischMission("master_angler", "Tantangan Angler Sejati",
                "Tangkap 1 ikan bertier LEGENDARY untuk membuktikan keahlianmu.",
                1, null, "LEGENDARY",
                FischMission.RewardType.MONEY, null, 10000.0));
    }

    public void register(FischMission mission) {
        if (mission == null) return;
        missions.put(mission.getId(), mission);
    }

    public FischMission getMission(String id) {
        if (id == null) return null;
        return missions.get(id.trim().toLowerCase());
    }

    public Map<String, FischMission> getAll() {
        return Collections.unmodifiableMap(missions);
    }

    /**
     * Clears and reloads missions from a ConfigurationSection.
     */
    public synchronized void loadFromConfig(ConfigurationSection section) {
        if (section == null) return;

        missions.clear();
        for (String key : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(key);
            if (sec != null) {
                FischMission mission = FischMission.fromConfiguration(key.toLowerCase(), sec);
                if (mission != null) {
                    missions.put(mission.getId(), mission);
                }
            }
        }

        if (missions.isEmpty()) {
            registerDefaults();
        }
    }

    /**
     * Picks up to 'count' random unique missions from the registered pool.
     */
    public List<FischMission> getRandomMissions(int count) {
        if (missions.isEmpty() || count <= 0) {
            return Collections.emptyList();
        }

        List<FischMission> pool = new ArrayList<>(missions.values());
        Collections.shuffle(pool);
        return pool.subList(0, Math.min(count, pool.size()));
    }

    public FischMission getRandomMission() {
        List<FischMission> list = getRandomMissions(1);
        return list.isEmpty() ? null : list.get(0);
    }
}
