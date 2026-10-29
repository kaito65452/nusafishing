package id.nusafishing.fishing.afk;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import id.nusafishing.NusaFishing;
import id.nusafishing.compat.CompatibilityHelper;
import id.nusafishing.fishing.FishingListener;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;

public class AfkFishingManager implements Listener {
    private final NusaFishing plugin;
    private final Set<UUID> afkPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeAutoReelTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> activeRecastTasks = new ConcurrentHashMap<>();

    public AfkFishingManager(NusaFishing plugin) {
        this.plugin = plugin;
    }

    public boolean isAfkEnabledInConfig() {
        return plugin.getConfig().getBoolean("afk_fishing.enabled", true);
    }

    public double getLuckNerf() {
        Object val = plugin.getConfig().get("afk_fishing.luck-nerf", "20%");
        return parsePercent(val, 0.20);
    }

    public double getSpeedNerf() {
        Object val = plugin.getConfig().get("afk_fishing.speed-nerf", "30%");
        return parsePercent(val, 0.30);
    }

    public static double parsePercent(Object raw, double defaultVal) {
        if (raw == null) return defaultVal;
        String s = String.valueOf(raw).trim().replace("%", "");
        try {
            double parsed = Double.parseDouble(s);
            if (parsed > 1.0) {
                return parsed / 100.0;
            }
            return Math.max(0.0, parsed);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    public boolean isAfk(UUID uuid) {
        return afkPlayers.contains(uuid);
    }

    public boolean isAfk(Player player) {
        return player != null && isAfk(player.getUniqueId());
    }

    public boolean toggleAfk(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        if (isAfk(uuid)) {
            disableAfk(player, "Mode auto-fishing dinonaktifkan.");
            return false;
        } else {
            return enableAfk(player);
        }
    }

    public boolean enableAfk(Player player) {
        if (!isAfkEnabledInConfig()) {
            player.sendMessage("§c§l[AFK Fishing] §fFitur auto-fishing saat ini dinonaktifkan oleh server.");
            return false;
        }

        UUID uuid = player.getUniqueId();
        afkPlayers.add(uuid);
        initialLocations.put(uuid, player.getLocation().clone());

        // Cancel any active manual minigame cleanly
        if (plugin.getFishingListener() != null && plugin.getFishingListener().isPlayerInMinigame(player)) {
            player.sendMessage("§e[AFK Fishing] Menghentikan minigame manual...");
            plugin.getFishingListener().getStrikeMinigame().endMinigameAbort(player);
        }

        int luckNerfPct = (int) Math.round(getLuckNerf() * 100.0);
        int speedNerfPct = (int) Math.round(getSpeedNerf() * 100.0);

        player.sendMessage("§a§l[AFK Fishing] §fMode auto-fishing berhasil §a§lDIAKTIFKAN§f!");
        player.sendMessage("§e§l[NERF AKTIF] §7Keberuntungan: §c-" + luckNerfPct + "% §7| Kecepatan: §c-" + speedNerfPct + "%");
        player.sendMessage("§7Kail akan menarik ikan secara otomatis setiap ada gigitan. Ketik §f/nf afk §7lagi untuk menonaktifkan.");
        CompatibilityHelper.sendActionBar(player, "§a[AFK Fishing Aktif] Menunggu gigitan ikan...");
        return true;
    }

    public void disableAfk(Player player, String reason) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        afkPlayers.remove(uuid);
        initialLocations.remove(uuid);

        BukkitTask reelTask = activeAutoReelTasks.remove(uuid);
        if (reelTask != null) reelTask.cancel();

        BukkitTask recastTask = activeRecastTasks.remove(uuid);
        if (recastTask != null) recastTask.cancel();

        if (reason != null && !reason.isEmpty()) {
            player.sendMessage("§c§l[AFK Fishing] §f" + reason);
        }
    }

    /**
     * Start the automated reel-in process when a fish bites in AFK mode.
     */
    public void handleAfkBite(Player player, CustomFish fish, ItemStack rodItem, FishingRod customRod) {
        UUID uuid = player.getUniqueId();

        // Cancel previous tasks if running
        BukkitTask prevReel = activeAutoReelTasks.remove(uuid);
        if (prevReel != null) prevReel.cancel();
        BukkitTask prevRecast = activeRecastTasks.remove(uuid);
        if (prevRecast != null) prevRecast.cancel();

        // Speed nerf extends the reel-in duration
        double speedNerf = getSpeedNerf();
        int baseReelSeconds = 3;
        int totalReelSeconds = Math.max(3, (int) Math.round(baseReelSeconds * (1.0 + (speedNerf * 2.0))));

        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);
        player.sendTitle("§a§lIkan menggigit!", "§b[AFK] §fMenarik otomatis (" + totalReelSeconds + "s)...", 5, 30, 5);

        BukkitTask task = new BukkitRunnable() {
            private int secondsLeft = totalReelSeconds;

            @Override
            public void run() {
                if (!player.isOnline() || !isAfk(uuid)) {
                    cancel();
                    activeAutoReelTasks.remove(uuid);
                    return;
                }

                // Check distance
                Location initial = initialLocations.get(uuid);
                if (initial != null && (!player.getWorld().equals(initial.getWorld()) || player.getLocation().distanceSquared(initial) > 25.0)) {
                    disableAfk(player, "Kamu bergerak terlalu jauh! Auto-fishing dihentikan.");
                    cancel();
                    activeAutoReelTasks.remove(uuid);
                    return;
                }

                // Verify rod still in hand
                ItemStack currentHand = player.getInventory().getItemInMainHand();
                if (currentHand.getType() != Material.FISHING_ROD) {
                    disableAfk(player, "Kamu melepaskan pancingan! Auto-fishing dihentikan.");
                    cancel();
                    activeAutoReelTasks.remove(uuid);
                    return;
                }

                if (secondsLeft <= 0) {
                    cancel();
                    activeAutoReelTasks.remove(uuid);
                    completeAfkCatch(player, fish, rodItem, customRod);
                    return;
                }

                // Visual & sound feedback during reel-in
                player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_STEP, 0.6f, 1.2f);
                CompatibilityHelper.sendActionBar(player, "§b[AFK Fishing] Menarik " + fish.getName() + " §7(" + secondsLeft + "s)");
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeAutoReelTasks.put(uuid, task);
    }

    private void completeAfkCatch(Player player, CustomFish fish, ItemStack rodItem, FishingRod customRod) {
        UUID uuid = player.getUniqueId();
        if (!player.isOnline() || !isAfk(uuid)) {
            return;
        }

        // Add fish to inventory or drop naturally if full
        ItemStack fishItem = fish.createItemStack();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(fishItem);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            player.sendMessage("§e§lPerhatian! §fInventory penuh, hasil pancingan dijatuhkan ke tanah.");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.sendTitle("§a§l[AFK Catch]", "§fMendapatkan §b" + fish.getName() + " §7(" + fish.getFormattedWeight() + ")", 5, 40, 10);

        // Record statistics
        plugin.getStatisticsManager().recordFishCaught(uuid, fish);

        // Record event fish
        if (fish.getId().startsWith("event_")) {
            plugin.getEventManager().handleEventFishCaught(player, fish);
        }

        // Record tournament
        if (plugin.getTournamentManager().isTournamentActive()) {
            plugin.getTournamentManager().recordCatch(player, fish);
        }

        // Add NusaFishing skill XP
        int xpGain = (int) (5 + (fish.getWeight() * fish.getQuality() / 20.0));
        plugin.getSkillManager().addFishingExp(player, xpGain);

        // Add AuraSkills XP
        plugin.getAuraSkillsHook().addFishingXp(player, fish);

        ItemStack currentRod = player.getInventory().getItemInMainHand();
        if (currentRod.getType() != Material.FISHING_ROD) {
            currentRod = player.getInventory().getItemInOffHand();
        }
        FishingRod activeCustomRod = (plugin.getFishingListener() != null && currentRod.getType() == Material.FISHING_ROD)
            ? plugin.getFishingListener().getCustomFishingRod(currentRod)
            : customRod;

        // Consume stamina
        plugin.getStaminaManager().consumeStaminaForFish(player, fish, activeCustomRod);

        // Reduce durability
        if (activeCustomRod != null && currentRod != null) {
            FishingListener.reduceDurability(player, currentRod, FishingListener.DURABILITY_LOSS_ON_CATCH);
        }

        // Remove any lingering hooks
        removeHook(player);

        // Schedule auto-recast if player remains in AFK mode
        scheduleAutoRecast(player, currentRod, activeCustomRod);
    }

    /**
     * Schedule an automated recast after a catch
     */
    public void scheduleAutoRecast(Player player, ItemStack rodItem, FishingRod customRod) {
        UUID uuid = player.getUniqueId();
        if (!isAfk(uuid)) return;

        double speedNerf = getSpeedNerf();
        long recastDelayTicks = Math.max(40L, (long) Math.round(60L * (1.0 + speedNerf)));

        BukkitTask recastTask = new BukkitRunnable() {
            @Override
            public void run() {
                activeRecastTasks.remove(uuid);
                if (!player.isOnline() || !isAfk(uuid)) return;

                ItemStack currentRod = player.getInventory().getItemInMainHand();
                if (currentRod.getType() != Material.FISHING_ROD) {
                    disableAfk(player, "Pancingan tidak dipegang di tangan utama. Auto-fishing berhenti.");
                    return;
                }

                // Resolve current custom rod freshly from the held item to avoid stale closure references
                FishingRod currentCustomRod = (plugin.getFishingListener() != null)
                        ? plugin.getFishingListener().getCustomFishingRod(currentRod)
                        : null;

                // Check stamina
                if (!plugin.getStaminaManager().hasEnoughStamina(player, FishingListener.CAST_STAMINA_COST)) {
                    disableAfk(player, "Stamina tidak cukup untuk memancing! Auto-fishing dihentikan.");
                    return;
                }

                // Check durability
                if (currentCustomRod != null) {
                    int dur = FishingListener.getRodDurability(currentRod);
                    if (dur <= 0) {
                        disableAfk(player, "Pancingan kamu rusak! Auto-fishing dihentikan.");
                        return;
                    }
                }

                // Check location distance
                Location initial = initialLocations.get(uuid);
                if (initial != null && (!player.getWorld().equals(initial.getWorld()) || player.getLocation().distanceSquared(initial) > 25.0)) {
                    disableAfk(player, "Kamu bergerak terlalu jauh dari lokasi awal! Auto-fishing dihentikan.");
                    return;
                }

                // Check and apply bait from inventory
                FishingBait bait = null;
                if (plugin.getFishingListener() != null) {
                    bait = plugin.getFishingListener().selectBaitFromInventory(player);
                    if (bait != null) {
                        plugin.getFishingListener().setActiveBait(uuid, bait);
                    }
                }

                // Auto launch hook
                try {
                    FishHook hook = player.launchProjectile(FishHook.class);
                    if (hook != null) {
                        hook.setVelocity(player.getLocation().getDirection().multiply(1.25));

                        // Calculate lure level from rod and bait
                        int baseLureLevel = currentCustomRod != null ? currentCustomRod.getLureLevel() : 0;
                        int extraLure = (bait != null) ? (int) Math.max(0, Math.min(5, Math.round(bait.getAttractionModifier() - 1.0))) : 0;
                        int totalLure = baseLureLevel + extraLure;

                        // Base wait time with lure boost, then lengthened by speed nerf
                        int baseMin = Math.max(20, 100 - (totalLure * 15));
                        int baseMax = Math.max(baseMin + 20, 600 - (totalLure * 60));
                        int nerfedMin = (int) Math.round(baseMin * (1.0 + speedNerf));
                        int nerfedMax = (int) Math.round(baseMax * (1.0 + speedNerf));
                        try {
                            hook.setWaitTime(nerfedMin, nerfedMax);
                        } catch (Throwable t) {
                            try {
                                hook.setMinWaitTime(nerfedMin);
                                hook.setMaxWaitTime(nerfedMax);
                                hook.setWaitTime((nerfedMin + nerfedMax) / 2);
                            } catch (Throwable ignored) {}
                        }

                        // Consume stamina on cast
                        plugin.getStaminaManager().consumeStamina(player, FishingListener.CAST_STAMINA_COST, currentCustomRod);

                        // Reduce durability on cast
                        if (currentCustomRod != null) {
                            FishingListener.reduceDurability(player, currentRod, FishingListener.DURABILITY_LOSS_ON_CAST);
                        }

                        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.0f);
                        String baitInfo = (bait != null) ? " §7(Umpan: " + bait.getName() + "§7)" : "";
                        CompatibilityHelper.sendActionBar(player, "§a[AFK Fishing] Kail dilemparkan kembali..." + baitInfo);
                    }
                } catch (Throwable t) {
                    player.sendMessage("§7[AFK Fishing] Lemparkan kail pancing untuk melanjutkan auto-fishing.");
                }
            }
        }.runTaskLater(plugin, recastDelayTicks);

        activeRecastTasks.put(uuid, recastTask);
    }

    private void removeHook(Player player) {
        if (player == null) return;
        try {
            FishHook hook = player.getFishHook();
            if (hook != null && hook.isValid()) {
                hook.remove();
            }
        } catch (Throwable ignored) {}
    }

    public void cleanup() {
        for (BukkitTask task : activeAutoReelTasks.values()) {
            if (task != null) task.cancel();
        }
        for (BukkitTask task : activeRecastTasks.values()) {
            if (task != null) task.cancel();
        }
        activeAutoReelTasks.clear();
        activeRecastTasks.clear();
        afkPlayers.clear();
        initialLocations.clear();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        disableAfk(event.getPlayer(), null);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        disableAfk(event.getEntity(), "Kamu meninggal dunia! Auto-fishing dihentikan.");
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!isAfk(uuid)) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Location initial = initialLocations.get(uuid);
        if (initial != null && (!to.getWorld().equals(initial.getWorld()) || to.distanceSquared(initial) > 25.0)) {
            disableAfk(player, "Kamu bergerak terlalu jauh! Auto-fishing dihentikan.");
        }
    }
}
