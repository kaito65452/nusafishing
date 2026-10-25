package id.nusafishing.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.FishHook;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
// text componennt
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatMessageType;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.data.PlayerSkillManager;
import id.nusafishing.fishing.data.StaminaManager;
import id.nusafishing.fishing.minigame.StrikeMinigame;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.util.ItemBuilder;

public class FishingListener implements Listener {
    private final NusaFishing plugin;
    private final FishingManager fishingManager;
    private final StaminaManager staminaManager;
    private final PlayerSkillManager skillManager;
    private final StrikeMinigame strikeMinigame;
    private final Map<UUID, Long> lastCastTime;
    private final Map<UUID, FishingBait> activeBaits = new HashMap<>();
    
    // Keys for persistent data
    private static final String ROD_ID_KEY = "nusafishing.rod_id";
    private static final String ROD_DURABILITY_KEY = "nusafishing.durability";
    
    // Constants for fishing
    public static final int MIN_CAST_COOLDOWN_MS = 2000; // 2 seconds between casts
    public static final double CAST_STAMINA_COST = 10.0;
    public static final int DURABILITY_LOSS_ON_CAST = 1; // Durability loss when casting
    public static final int DURABILITY_LOSS_ON_CATCH = 2; // Durability loss when catching a fish

    public FishingListener(NusaFishing plugin, FishingManager fishingManager,
                         StaminaManager staminaManager, PlayerSkillManager skillManager) {
        this.plugin = plugin;
        this.fishingManager = fishingManager;
        this.staminaManager = staminaManager;
        this.skillManager = skillManager;
        this.strikeMinigame = new StrikeMinigame(plugin);
        this.lastCastTime = new HashMap<>();
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        staminaManager.initializePlayer(player);
        skillManager.loadPlayerData(player);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        staminaManager.removePlayer(player);
        skillManager.savePlayerData(player);
        strikeMinigame.cleanupPlayer(player);
        activeBaits.remove(playerId);
        lastCastTime.remove(playerId);
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        // Handle left-click during minigame
        if (event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_AIR || 
            event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            
            if (heldItem.getType() == Material.FISHING_ROD) {
                if (strikeMinigame.handleClick(player)) {
                    event.setCancelled(true);
                }
            }
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        
        // IMMEDIATELY cancel ANY fishing events if player is in a minigame - check first before anything else
        if (isPlayerInMinigame(player)) {
            event.setCancelled(true);
            return;
        }
        
        ItemStack fishingRod = player.getInventory().getItemInMainHand();
        
        if (fishingRod.getType() != Material.FISHING_ROD) {
            return;
        }
        
        // Check if using custom fishing rod
        FishingRod customRod = getCustomFishingRod(fishingRod);
        // Allow vanilla rods (customRod will be null but we'll handle that)
        
        if (event.getState() == State.FISHING) {
            // Player cast their line
            handleFishingCast(event, player, fishingRod, customRod);
        } else if (event.getState() == State.BITE) {
            // Fish is biting - start minigame
            handleFishBite(event, player, fishingRod, customRod);
        } else if (event.getState() == State.CAUGHT_FISH) {
            // Default fish caught - replace with custom mechanics
            event.setCancelled(true);
            
            // If we get here during a minigame, let the minigame handle it
            if (isPlayerInMinigame(player)) {
                return;
            }

            UUID playerId = player.getUniqueId();
            FishingBait bait = activeBaits.remove(playerId);
            if (bait != null) {
                bait.consumeUse(player);
            }

            // Check if player is in AFK auto-fishing mode
            if (plugin.getAfkFishingManager().isAfk(player)) {
                double luckNerf = plugin.getAfkFishingManager().getLuckNerf();
                CustomFish fish = fishingManager.selectRandomFish(player, customRod, bait, luckNerf);
                if (fish != null) {
                    removePlayerFishingHook(player);
                    plugin.getAfkFishingManager().handleAfkBite(player, fish, fishingRod, customRod);
                }
                return;
            }

            // Process catch directly (vanilla rod or custom rod when minigame was not active)
            CustomFish fish = fishingManager.selectRandomFish(player, customRod, bait);
            if (fish != null) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(fish.createItemStack());
                if (!leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                    player.sendMessage("§e§lPerhatian! §fInventory kamu penuh, ikan dijatuhkan ke tanah.");
                }
                player.sendMessage("§aKamu menangkap §f" + fish.getName() + "§a!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                
                // Record the catch in statistics
                plugin.getStatisticsManager().recordFishCaught(player.getUniqueId(), fish);

                // Check for event fish catch
                if (fish.getId().startsWith("event_")) {
                    plugin.getEventManager().handleEventFishCaught(player, fish);
                }

                // Record catch in active tournament
                if (plugin.getTournamentManager().isTournamentActive()) {
                    plugin.getTournamentManager().recordCatch(player, fish);
                }

                // Add fishing XP based on fish quality and weight
                int xpGain = (int)(5 + (fish.getWeight() * fish.getQuality() / 20));
                plugin.getSkillManager().addFishingExp(player, xpGain);

                // Add AuraSkills XP if enabled
                plugin.getAuraSkillsHook().addFishingXp(player, fish);

                // Consume stamina based on fish weight
                staminaManager.consumeStaminaForFish(player, fish, customRod);

                // Reduce durability if custom rod
                if (customRod != null) {
                    reduceDurability(player, fishingRod, DURABILITY_LOSS_ON_CATCH);
                }

                // TAMBAHAN: Hapus fishing hook setelah berhasil menangkap ikan
                removePlayerFishingHook(player);
            }
        } else if (event.getState() == State.REEL_IN || event.getState() == State.FAILED_ATTEMPT || event.getState() == State.IN_GROUND) {
            activeBaits.remove(player.getUniqueId());
        }
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        
        // If the player is in a minigame, end it (fish escapes)
        if (isPlayerInMinigame(player)) {
            player.sendMessage("§c§lIkan lolos! §fKamu membuka inventory saat memancing.");
            strikeMinigame.endMinigameAbort(player);
            removePlayerFishingHook(player);
        }
    }
    
    private void handleFishingCast(PlayerFishEvent event, Player player, ItemStack fishingRod, FishingRod customRod) {
        UUID playerId = player.getUniqueId();
        
        // Check for cooldown
        long now = System.currentTimeMillis();
        long lastCast = lastCastTime.getOrDefault(playerId, 0L);
        if (now - lastCast < MIN_CAST_COOLDOWN_MS) {
            event.setCancelled(true);
            player.sendMessage("§cTunggu sebentar sebelum melempar kembali!");
            return;
        }
        
        // Check for stamina
        if (!staminaManager.hasEnoughStamina(player, CAST_STAMINA_COST)) {
            event.setCancelled(true);
            player.sendMessage("§cStamina tidak cukup untuk memancing!");
            return;
        }
        
        // Check rod durability
        if (customRod != null) {
            int currentDurability = getRodDurability(fishingRod);
            if (currentDurability <= 0) {
                event.setCancelled(true);
                player.sendMessage("§cPancingan kamu rusak! Perbaiki menggunakan /nusafishing repair");
                return;
            }
            
            // Reduce durability on cast
            reduceDurability(player, fishingRod, DURABILITY_LOSS_ON_CAST);
        }
        
        // Cek umpan di inventory dan pilih secara otomatis
        FishingBait selectedBait = selectBaitFromInventory(player);
        if (selectedBait != null) {
            activeBaits.put(playerId, selectedBait);
            
            // Terapkan boost lure ke fishing hook
            FishHook hook = event.getHook();
            int baseLureLevel = customRod != null ? customRod.getLureLevel() : 0;
            int extraLure = (int) Math.max(0, Math.min(5, Math.round(selectedBait.getAttractionModifier() - 1.0)));
            int totalLure = baseLureLevel + extraLure;
            
            if (hook != null && totalLure > 0) {
                int minWait = Math.max(20, 100 - (totalLure * 15));
                int maxWait = Math.max(minWait + 20, 600 - (totalLure * 60));
                if (plugin.getAfkFishingManager().isAfk(player)) {
                    double speedNerf = plugin.getAfkFishingManager().getSpeedNerf();
                    minWait = (int) Math.round(minWait * (1.0 + speedNerf));
                    maxWait = (int) Math.round(maxWait * (1.0 + speedNerf));
                }
                try {
                    hook.setWaitTime(minWait, maxWait);
                } catch (Throwable t1) {
                    try {
                        hook.setMinWaitTime(minWait);
                        hook.setMaxWaitTime(maxWait);
                        hook.setWaitTime((minWait + maxWait) / 2);
                    } catch (Throwable ignored) {
                    }
                }
            }
            
            // Beri tahu pemain umpan yang aktif dengan pesan yang SANGAT JELAS
            String attractionText = String.format("§e+%.0f%%", (selectedBait.getAttractionModifier() - 1.0) * 100);
            String lureBoost = extraLure > 0 ? " §7(+"+extraLure+" Lure)" : "";
            
            // Efek visual LEBIH DRAMATIS
            if (id.nusafishing.compat.CompatibilityHelper.BUBBLE != null) {
                player.getWorld().spawnParticle(
                    id.nusafishing.compat.CompatibilityHelper.BUBBLE, 
                    player.getLocation().add(0, 1.0, 0), 
                    50, 0.7, 0.7, 0.7, 0.1
                );
            }
            if (id.nusafishing.compat.CompatibilityHelper.SPLASH != null) {
                player.getWorld().spawnParticle(
                    id.nusafishing.compat.CompatibilityHelper.SPLASH,
                    event.getHook().getLocation(),
                    30, 0.3, 0.0, 0.3, 0.2
                );
            }
            
            // Efek suara LEBIH JELAS
            player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.5f, 1.8f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        
        // Consume stamina and update last cast time
        staminaManager.consumeStamina(player, CAST_STAMINA_COST, customRod);
        lastCastTime.put(playerId, now);
    }
    
    public FishingBait selectBaitFromInventory(Player player) {
        // Prioritaskan umpan yang ada di off hand
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        FishingBait offHandBait = fishingManager.getBaitFromItem(offHandItem);
        if (offHandBait != null) {
            return offHandBait;
        }
        
        // Jika tidak ada di off hand, cek seluruh inventory
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            FishingBait bait = fishingManager.getBaitFromItem(item);
            if (bait != null) {
                return bait;
            }
        }
        
        return null;
    }

    public void setActiveBait(UUID playerId, FishingBait bait) {
        if (playerId != null && bait != null) {
            activeBaits.put(playerId, bait);
        }
    }

    public FishingBait getActiveBait(UUID playerId) {
        return playerId != null ? activeBaits.get(playerId) : null;
    }

    public FishingBait removeActiveBait(UUID playerId) {
        return playerId != null ? activeBaits.remove(playerId) : null;
    }
    
    private void handleFishBite(PlayerFishEvent event, Player player, ItemStack fishingRod, FishingRod customRod) {
        // Cancel vanilla behavior
        event.setCancelled(true);
        
        // Don't start a new minigame if player is already in one
        if (isPlayerInMinigame(player)) {
            player.sendMessage("§cKamu sedang dalam permainan memancing!");
            return;
        }
        
        // Check rod durability only for custom rods
        if (customRod != null) {
            int currentDurability = getRodDurability(fishingRod);
            if (currentDurability <= DURABILITY_LOSS_ON_CATCH) {
                player.sendMessage("§cPancingan kamu hampir rusak! Perbaiki segera dengan /nusafishing repair");
            }
        }
        
        // Get active bait
        UUID playerId = player.getUniqueId();
        FishingBait bait = activeBaits.remove(playerId);
        
        // Consume bait if available
        if (bait != null) {
            boolean consumed = bait.consumeUse(player);
            
            if (consumed) {
                if (id.nusafishing.compat.CompatibilityHelper.BUBBLE != null) {
                    player.spawnParticle(id.nusafishing.compat.CompatibilityHelper.BUBBLE, 
                        player.getLocation().add(0, 0.5, 0), 
                        10, 0.2, 0.2, 0.2, 0.1);
                }
            }
        }
        
        // Check if player is in AFK auto-fishing mode
        if (plugin.getAfkFishingManager().isAfk(player)) {
            double luckNerf = plugin.getAfkFishingManager().getLuckNerf();
            CustomFish fish = fishingManager.selectRandomFish(player, customRod, bait, luckNerf);
            if (fish == null) {
                player.sendMessage("§c§oTidak bisa menemukan ikan yang cocok untuk ditangkap.");
                return;
            }
            removePlayerFishingHook(player);
            plugin.getAfkFishingManager().handleAfkBite(player, fish, fishingRod, customRod);
            return;
        }

        // Select a random fish based on player's location, rod, and active bait
        CustomFish fish = fishingManager.selectRandomFish(player, customRod, bait);
        
        // Debug message to help diagnose the issue
        if (fish == null) {
            player.sendMessage("§c§oTidak bisa menemukan ikan yang cocok untuk ditangkap.");
            return;
        }
        
        // TAMBAHAN: Hapus fishing hook segera setelah ikan menggigit
        // sehingga pemain harus melempar kembali setelah gigitan
        removePlayerFishingHook(player);
        
        // Memulai minigame tangkap ikan
        int durabilityLoss = customRod != null ? DURABILITY_LOSS_ON_CATCH : 0;
        
        // Start the fishing minigame with the selected fish immediately
        strikeMinigame.startStrikeGame(player, fish, fishingRod, durabilityLoss);
        // Set metadata to mark player as in minigame
        player.setMetadata("NF_InMinigame", new FixedMetadataValue(plugin, true));
    }
    
    /**
     * Cancel all active fishing hooks for a player
     * @param player The player whose hooks should be cancelled
     */
    private void cancelAllFishingHooks(Player player) {
        // Get all hooks in the world and cancel those belonging to this player
        player.getWorld().getEntitiesByClass(org.bukkit.entity.FishHook.class).stream()
                .filter(hook -> hook.getShooter() instanceof Player)
                .filter(hook -> ((Player)hook.getShooter()).getUniqueId().equals(player.getUniqueId()))
                .forEach(org.bukkit.entity.Entity::remove);
    }
    
    /**
     * Remove all fishing hooks from the player
     * @param player The player to remove hooks from
     */
    private void removePlayerFishingHook(Player player) {
        if (player == null) return;
        try {
            org.bukkit.entity.FishHook hook = player.getFishHook();
            if (hook != null && hook.isValid()) {
                hook.remove();
                return;
            }
        } catch (Throwable ignored) {
        }
        // Check all entities in the world for fishing hooks belonging to this player
        player.getWorld().getEntitiesByClass(org.bukkit.entity.FishHook.class).stream()
            .filter(hook -> hook.getShooter() instanceof Player)
            .filter(hook -> ((Player) hook.getShooter()).getUniqueId().equals(player.getUniqueId()))
            .forEach(org.bukkit.entity.Entity::remove);
    }
    
    /**
     * Get the current durability of a fishing rod
     * @param fishingRod The fishing rod item
     * @return The current durability, or 0 if not found
     */
    public static int getRodDurability(ItemStack fishingRod) {
        if (fishingRod == null || fishingRod.getType() != Material.FISHING_ROD) {
            return 0;
        }
        
        ItemMeta meta = fishingRod.getItemMeta();
        if (meta == null) {
            return 0;
        }
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey cleanKey = NamespacedKey.fromString("nusafishing:durability");
        NamespacedKey legacyKey = NamespacedKey.fromString("nusafishing:" + ROD_DURABILITY_KEY);
        
        if (cleanKey != null && container.has(cleanKey, PersistentDataType.INTEGER)) {
            return container.get(cleanKey, PersistentDataType.INTEGER);
        } else if (container.has(legacyKey, PersistentDataType.INTEGER)) {
            return container.get(legacyKey, PersistentDataType.INTEGER);
        }
        
        return 0;
    }
    
    /**
     * Reduce the durability of a fishing rod
     * @param player The player using the rod
     * @param fishingRod The fishing rod item
     * @param amount The amount to reduce
     * @return The new durability
     */
    public static int reduceDurability(Player player, ItemStack fishingRod, int amount) {
        if (fishingRod == null || fishingRod.getType() != Material.FISHING_ROD) {
            return 0;
        }
        
        ItemMeta meta = fishingRod.getItemMeta();
        if (meta == null) {
            return 0;
        }
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey cleanDurabilityKey = NamespacedKey.fromString("nusafishing:durability");
        NamespacedKey legacyDurabilityKey = NamespacedKey.fromString("nusafishing:" + ROD_DURABILITY_KEY);
        NamespacedKey cleanRodIdKey = NamespacedKey.fromString("nusafishing:rod_id");
        NamespacedKey legacyRodIdKey = NamespacedKey.fromString("nusafishing:" + ROD_ID_KEY);
        
        String rodId = null;
        if (cleanRodIdKey != null && container.has(cleanRodIdKey, PersistentDataType.STRING)) {
            rodId = container.get(cleanRodIdKey, PersistentDataType.STRING);
        } else if (container.has(legacyRodIdKey, PersistentDataType.STRING)) {
            rodId = container.get(legacyRodIdKey, PersistentDataType.STRING);
        }

        if (rodId == null) {
            return 0;
        }

        int currentDurability = 0;
        if (cleanDurabilityKey != null && container.has(cleanDurabilityKey, PersistentDataType.INTEGER)) {
            currentDurability = container.get(cleanDurabilityKey, PersistentDataType.INTEGER);
        } else if (container.has(legacyDurabilityKey, PersistentDataType.INTEGER)) {
            currentDurability = container.get(legacyDurabilityKey, PersistentDataType.INTEGER);
        }
        
        // Get rod info for max durability
        FishingRod rod = NusaFishing.getInstance().getFishingManager().getRodById(rodId);
        if (rod == null) {
            return currentDurability;
        }
        
        // Calculate new durability
        int newDurability = Math.max(0, currentDurability - amount);
        
        // Update persistent data on both clean and legacy keys
        if (cleanDurabilityKey != null) {
            container.set(cleanDurabilityKey, PersistentDataType.INTEGER, newDurability);
        }
        container.set(legacyDurabilityKey, PersistentDataType.INTEGER, newDurability);
        
        // Update lore
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                if (line.contains("Ketahanan:")) {
                    lore.set(i, "§7Ketahanan: §f" + newDurability + "/" + rod.getDurability());
                    break;
                }
            }
            meta.setLore(lore);
        }
        
        // Apply changes
        fishingRod.setItemMeta(meta);
        
        // Notify player if durability is low
        if (newDurability <= 10 && newDurability > 0) {
            player.sendMessage("§c§lPeringatan! §fPancingan kamu sudah hampir rusak (" + newDurability + "/" + rod.getDurability() + ")");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f);
        } else if (newDurability <= 0) {
            player.sendMessage("§c§lPancingan rusak! §fGunakan /nusafishing repair untuk memperbaikinya");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
        
        return newDurability;
    }
    
    /**
     * Repair a fishing rod to full durability
     * @param player The player repairing the rod
     * @param fishingRod The fishing rod item
     * @return true if repair successful
     */
    public static boolean repairRod(Player player, ItemStack fishingRod) {
        if (fishingRod == null || fishingRod.getType() != Material.FISHING_ROD) {
            return false;
        }
        
        ItemMeta meta = fishingRod.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey cleanDurabilityKey = NamespacedKey.fromString("nusafishing:durability");
        NamespacedKey legacyDurabilityKey = NamespacedKey.fromString("nusafishing:" + ROD_DURABILITY_KEY);
        NamespacedKey cleanRodIdKey = NamespacedKey.fromString("nusafishing:rod_id");
        NamespacedKey legacyRodIdKey = NamespacedKey.fromString("nusafishing:" + ROD_ID_KEY);
        
        String rodId = null;
        if (cleanRodIdKey != null && container.has(cleanRodIdKey, PersistentDataType.STRING)) {
            rodId = container.get(cleanRodIdKey, PersistentDataType.STRING);
        } else if (container.has(legacyRodIdKey, PersistentDataType.STRING)) {
            rodId = container.get(legacyRodIdKey, PersistentDataType.STRING);
        }

        if (rodId == null) {
            return false;
        }
        
        // Get rod info for max durability
        FishingRod rod = NusaFishing.getInstance().getFishingManager().getRodById(rodId);
        if (rod == null) {
            return false;
        }
        
        // Reset durability to max on both keys
        if (cleanDurabilityKey != null) {
            container.set(cleanDurabilityKey, PersistentDataType.INTEGER, rod.getDurability());
        }
        container.set(legacyDurabilityKey, PersistentDataType.INTEGER, rod.getDurability());
        
        // Update lore
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                if (line.contains("Ketahanan:")) {
                    lore.set(i, "§7Ketahanan: §f" + rod.getDurability() + "/" + rod.getDurability());
                    break;
                }
            }
            meta.setLore(lore);
        }
        
        // Apply changes
        fishingRod.setItemMeta(meta);
        return true;
    }
    
    private FishingRod getCustomFishingRod(ItemStack item) {
        if (item == null) {
            return null;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        
        // 1. Check persistent data container for rod ID
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey rodIdKey = NamespacedKey.fromString("nusafishing:rod_id");
        if (container.has(rodIdKey, PersistentDataType.STRING)) {
            String rodId = container.get(rodIdKey, PersistentDataType.STRING);
            FishingRod rod = fishingManager.getRodById(rodId);
            if (rod != null) {
                return rod;
            }
        }
        NamespacedKey legacyRodIdKey = NamespacedKey.fromString("nusafishing:nusafishing.rod_id");
        if (container.has(legacyRodIdKey, PersistentDataType.STRING)) {
            String rodId = container.get(legacyRodIdKey, PersistentDataType.STRING);
            FishingRod rod = fishingManager.getRodById(rodId);
            if (rod != null) {
                return rod;
            }
        }
        
        // 2. Check for custom model data fallback
        if (meta.hasCustomModelData()) {
            int modelData = meta.getCustomModelData();
            return fishingManager.getFishingRodByModelData(modelData);
        }
        
        return null;
    }
    
    // Helper method to check if player is in minigame
    public boolean isPlayerInMinigame(Player player) {
        return player.getMetadata("NF_InMinigame").stream()
               .anyMatch(metaValue -> metaValue.asBoolean());
    }

    public StrikeMinigame getStrikeMinigame() {
        return strikeMinigame;
    }
    
    /**
     * Display visual and sound effects when a player is using bait
     * @param player The player
     * @param bait The bait being used
     */
    private void showBaitEffect(Player player, FishingBait bait) {
        // Schedule a repeating visual effect
        new BukkitRunnable() {
            int count = 0;
            ItemStack lastBaitItem = null;
            
            @Override
            public void run() {
                // Check if player still has bait in off hand
                ItemStack offHandItem = player.getInventory().getItemInOffHand();
                if (offHandItem == null || offHandItem.getType() != bait.getMaterial() || 
                    count >= 5) {
                    this.cancel();
                    return;
                }
                
                // Only update if the bait has changed
                if (!offHandItem.equals(lastBaitItem)) {
                    // Update reference
                    lastBaitItem = offHandItem.clone();
                    
                    // Show particles
                    if (id.nusafishing.compat.CompatibilityHelper.BUBBLE != null) {
                        player.getWorld().spawnParticle(
                            id.nusafishing.compat.CompatibilityHelper.BUBBLE, 
                            player.getLocation().add(0, 0.5, 0), 
                            5, 0.2, 0.2, 0.2, 0
                        );
                    }
                    
                    // Send action bar message
                    String tierBoostText = bait.getTierBoost() > 0 ? 
                        " §7(+" + bait.getTierBoost() + " Tier)" : "";
                    player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR, 
                        TextComponent.fromLegacyText("§aUmpan aktif: §f" + bait.getName() + 
                                                  " §7- §f+" + 
                                                  (int)((bait.getAttractionModifier() - 1.0) * 100) + 
                                                  "% §7daya tarik" + tierBoostText)
                    );
                }
                count++;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
    
    // Tambahkan method baru untuk memformat nama-nama ikan
    private String formatFishNamesForBait(List<String> fishIds) {
        if (fishIds == null || fishIds.isEmpty()) {
            return "semua ikan";
        }
        
        List<String> names = new ArrayList<>();
        for (String fishId : fishIds) {
            CustomFish fish = plugin.getConfigManager().getCustomFishes().get(fishId);
            if (fish != null) {
                names.add(fish.getName());
            }
        }
        
        return String.join(", ", names);
    }
}