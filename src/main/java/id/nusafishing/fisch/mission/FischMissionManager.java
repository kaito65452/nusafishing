package id.nusafishing.fisch.mission;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import id.nusafishing.NusaFishing;
import id.nusafishing.fisch.model.FischMission;
import id.nusafishing.fisch.model.FischTotem;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;

/**
 * Fisch Mission Manager.
 * Manages player active missions, progress tracking upon fish catch,
 * distributing rewards (Rods into Virtual Inventory, Baits, Totems, EXP, Money),
 * and debounced async saving to prevent any server freeze.
 */
public class FischMissionManager {
    private final NusaFishing plugin;
    private final File missionDataFile;
    private FileConfiguration missionDataConfig;

    // Cache: Player UUID -> (Mission ID -> Progress count)
    private final Map<UUID, Map<String, Integer>> playerProgress = new ConcurrentHashMap<>();
    // Cache: Player UUID -> Active Mission ID
    private final Map<UUID, String> playerActiveMission = new ConcurrentHashMap<>();

    private volatile boolean dirty = false;
    private BukkitTask debounceSaveTask = null;

    public FischMissionManager(NusaFishing plugin) {
        this.plugin = plugin;
        this.missionDataFile = new File(plugin.getDataFolder(), "fisch_missions_data.yml");
        loadData();
    }

    private void loadData() {
        if (!missionDataFile.exists()) {
            missionDataFile.getParentFile().mkdirs();
            try {
                missionDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create fisch_missions_data.yml: " + e.getMessage());
            }
        }
        this.missionDataConfig = YamlConfiguration.loadConfiguration(missionDataFile);
        ConfigurationSection sec = missionDataConfig.getConfigurationSection("players");
        if (sec != null) {
            for (String uuidStr : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String active = sec.getString(uuidStr + ".active_mission");
                    if (active != null) {
                        playerActiveMission.put(uuid, active);
                    }
                    ConfigurationSection progSec = sec.getConfigurationSection(uuidStr + ".progress");
                    if (progSec != null) {
                        Map<String, Integer> progMap = new HashMap<>();
                        for (String mId : progSec.getKeys(false)) {
                            progMap.put(mId, progSec.getInt(mId, 0));
                        }
                        playerProgress.put(uuid, progMap);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public synchronized void scheduleSave() {
        dirty = true;
        if (debounceSaveTask != null) {
            debounceSaveTask.cancel();
        }
        debounceSaveTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            saveNow();
        }, 100L); // 5s debounce
    }

    public synchronized void saveNow() {
        if (!dirty) return;
        dirty = false;
        try {
            FileConfiguration config = new YamlConfiguration();
            for (Map.Entry<UUID, String> entry : playerActiveMission.entrySet()) {
                String path = "players." + entry.getKey().toString();
                config.set(path + ".active_mission", entry.getValue());
                Map<String, Integer> prog = playerProgress.get(entry.getKey());
                if (prog != null) {
                    for (Map.Entry<String, Integer> pEntry : prog.entrySet()) {
                        config.set(path + ".progress." + pEntry.getKey(), pEntry.getValue());
                    }
                }
            }
            config.save(missionDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save fisch_missions_data.yml: " + e.getMessage());
        }
    }

    public FischMission getActiveMission(Player player) {
        if (player == null || plugin.getMissionRegistry() == null) return null;
        String mId = playerActiveMission.get(player.getUniqueId());
        if (mId == null) {
            // Assign random mission from registry if not set
            FischMission randomMission = plugin.getMissionRegistry().getRandomMission();
            if (randomMission != null) {
                playerActiveMission.put(player.getUniqueId(), randomMission.getId());
                scheduleSave();
                return randomMission;
            }
            return null;
        }
        return plugin.getMissionRegistry().getMission(mId);
    }

    public int getProgress(Player player, String missionId) {
        if (player == null || missionId == null) return 0;
        Map<String, Integer> map = playerProgress.get(player.getUniqueId());
        return (map != null) ? map.getOrDefault(missionId, 0) : 0;
    }

    /**
     * Called whenever a fish is caught. Checks requirements and delivers rewards.
     */
    public void onFishCaught(Player player, CustomFish fish) {
        if (player == null || fish == null || plugin.getMissionRegistry() == null) return;
        FischMission mission = getActiveMission(player);
        if (mission == null) return;

        // Check species match
        if (mission.getRequiredSpecies() != null && !mission.getRequiredSpecies().equalsIgnoreCase("ANY")) {
            if (!mission.getRequiredSpecies().equalsIgnoreCase(fish.getId())) {
                return;
            }
        }

        // Check tier match
        if (mission.getRequiredTier() != null && !mission.getRequiredTier().equalsIgnoreCase("ANY")) {
            if (!mission.getRequiredTier().equalsIgnoreCase(fish.getActualTier().name())) {
                return;
            }
        }

        // Increment progress
        Map<String, Integer> map = playerProgress.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        int current = map.getOrDefault(mission.getId(), 0) + 1;
        map.put(mission.getId(), current);
        scheduleSave();

        // Check completion
        if (current >= mission.getRequiredFishCount()) {
            completeMission(player, mission);
        } else {
            player.sendMessage("§e[Misi Fisch] §7Progress: §b" + current + "/" + mission.getRequiredFishCount() + " §7(" + mission.getTitle() + ")");
        }
    }

    private void completeMission(Player player, FischMission mission) {
        player.sendTitle("§a§lMISI SELESAI!", "§f" + mission.getTitle(), 10, 60, 20);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendMessage("§a§l✦ SELAMAT! §fKamu menyelesaikan misi: §e" + mission.getTitle());

        // Grant reward based on rewardType
        switch (mission.getRewardType()) {
            case ROD:
                if (mission.getRewardId() != null) {
                    if (plugin.getVirtualRodManager() != null) {
                        plugin.getVirtualRodManager().addRodToPlayer(player, mission.getRewardId());
                        player.sendMessage("§aHadiah Pancingan §e" + mission.getRewardId() + " §atelah ditambahkan ke Koleksi Virtual Rod kamu! (/nf rods)");
                    }
                }
                break;
            case BAIT:
                if (mission.getRewardId() != null && plugin.getConfigManager() != null) {
                    FishingBait bait = plugin.getConfigManager().getFishingBaits().get(mission.getRewardId());
                    if (bait != null) {
                        int amount = (int) Math.max(1, mission.getRewardAmount());
                        player.getInventory().addItem(bait.createItemStack(amount));
                        player.sendMessage("§aHadiah: §f" + amount + "x " + bait.getName());
                    }
                }
                break;
            case TOTEM:
                if (mission.getRewardId() != null && plugin.getTotemRegistry() != null) {
                    FischTotem totem = plugin.getTotemRegistry().getTotem(mission.getRewardId());
                    if (totem != null) {
                        int amount = (int) Math.max(1, mission.getRewardAmount());
                        player.getInventory().addItem(totem.createItemStack(amount));
                        player.sendMessage("§aHadiah: §f" + amount + "x " + totem.getName());
                    }
                }
                break;
            case EXP:
                int exp = (int) mission.getRewardAmount();
                if (plugin.getSkillManager() != null) {
                    plugin.getSkillManager().addFishingExp(player, exp);
                    player.sendMessage("§aHadiah: §b+" + exp + " EXP Memancing!");
                }
                break;
            case MONEY:
                double money = mission.getRewardAmount();
                if (plugin.getEconomyManager() != null && plugin.getEconomyManager().isEconomyEnabled()) {
                    plugin.getEconomyManager().addMoney(player, money);
                    player.sendMessage("§aHadiah: §6+$" + String.format("%.2f", money));
                }
                break;
        }

        // Reset progress for this mission and assign a new random mission
        Map<String, Integer> map = playerProgress.get(player.getUniqueId());
        if (map != null) {
            map.remove(mission.getId());
        }
        FischMission newMission = plugin.getMissionRegistry().getRandomMission();
        if (newMission != null) {
            playerActiveMission.put(player.getUniqueId(), newMission.getId());
            player.sendMessage("§7Misi berikutnya: §e" + newMission.getTitle() + " §8(/nf mission)");
        }
        scheduleSave();
    }
}
