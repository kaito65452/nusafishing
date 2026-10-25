package id.nusafishing.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;  // Add this import
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.FishingManager;
import id.nusafishing.fishing.FishingListener;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.fishing.model.FishingBait;
import id.nusafishing.fishing.model.FishingRod;
import id.nusafishing.util.ItemBuilder;
import id.nusafishing.economy.EconomyManager;
import id.nusafishing.gui.ShopGUI;
import id.nusafishing.gui.EventFishCollectorGUI;
import id.nusafishing.event.EventFish;
import id.nusafishing.event.EventType;
import id.nusafishing.tournament.TournamentManager;
import id.nusafishing.tournament.model.Tournament;
import id.nusafishing.util.TimeUtils;
import id.nusafishing.tournament.model.TournamentType;


public class NusaFishingCommand implements CommandExecutor, TabCompleter {
    private final NusaFishing plugin;
    private final FishingManager fishingManager;
    
    // Keys for persistent data
    private static final String ROD_ID_KEY = "nusafishing.rod_id";
    private static final String ROD_DURABILITY_KEY = "nusafishing.durability";
    private static final String BAIT_ID_KEY = "nusafishing.bait_id";
    
    public NusaFishingCommand(NusaFishing plugin, FishingManager fishingManager) {
        this.plugin = plugin;
        this.fishingManager = fishingManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§b=== NusaFishing Commands ===");
            sender.sendMessage("§f/nusafishing help - Tampilkan bantuan");
            sender.sendMessage("§f/nusafishing rod <id> [jumlah] - Dapatkan pancingan");
            sender.sendMessage("§f/nusafishing bait <id> [jumlah] - Dapatkan umpan");
            sender.sendMessage("§f/nusafishing reload - Muat ulang konfigurasi");
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "help":
                // Tampilkan bantuan
                sender.sendMessage("§b=== NusaFishing Commands ===");
                sender.sendMessage("§f/nusafishing rod <id> [jumlah] - Dapatkan pancingan");
                sender.sendMessage("§f/nusafishing bait <id> [jumlah] - Dapatkan umpan");
                sender.sendMessage("§f/nusafishing repair - Perbaiki pancingan di tangan");
                sender.sendMessage("§f/nusafishing shop - Buka toko untuk beli dan jual");
                sender.sendMessage("§f/nusafishing afk - Aktifkan/nonaktifkan mode auto-fishing");
                
                if (sender.hasPermission("nusafishing.sell")) {
                    sender.sendMessage("§f/nusafishing sell - Jual ikan di tangan");
                    sender.sendMessage("§f/nusafishing sell all - Jual semua ikan di inventory");
                }
                
                if (sender.hasPermission("nusafishing.admin")) {
                    sender.sendMessage("§f/nusafishing reload - Muat ulang konfigurasi");
                    sender.sendMessage("§f/nusafishing location list - Lihat lokasi spesial");
                    sender.sendMessage("§f/nusafishing location tp <id> - Teleport ke lokasi spesial");
                    sender.sendMessage("§f/nusafishing location create <id> <radius> - Buat lokasi spesial baru");
                    sender.sendMessage("§f/nusafishing location delete <id> - Hapus lokasi spesial");
                }
                return true;
                
            case "rod":
                // Berikan pancingan
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cCommand ini hanya dapat digunakan oleh player!");
                    return true;
                }
                
                if (!sender.hasPermission("nusafishing.rod")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk menggunakan perintah ini!");
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage("§cPenggunaan: /nusafishing rod <id> [jumlah]");
                    return true;
                }
                
                String rodId = args[1].toLowerCase();
                int rodAmount = 1;
                
                if (args.length > 2) {
                    try {
                        rodAmount = Integer.parseInt(args[2]);
                        if (rodAmount < 1) rodAmount = 1;
                        if (rodAmount > 64) rodAmount = 64;
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cJumlah harus berupa angka!");
                        return true;
                    }
                }
                
                return giveRod((Player) sender, rodId, rodAmount);
                
            case "bait":
                // Berikan umpan
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cCommand ini hanya dapat digunakan oleh player!");
                    return true;
                }
                
                if (!sender.hasPermission("nusafishing.bait")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk menggunakan perintah ini!");
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage("§cPenggunaan: /nusafishing bait <id> [jumlah]");
                    return true;
                }
                
                String baitId = args[1].toLowerCase();
                int baitAmount = 1;
                
                if (args.length > 2) {
                    try {
                        baitAmount = Integer.parseInt(args[2]);
                        if (baitAmount < 1) baitAmount = 1;
                        if (baitAmount > 64) baitAmount = 64;
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cJumlah harus berupa angka!");
                        return true;
                    }
                }
                
                return giveBait((Player) sender, baitId, baitAmount);
                
            case "reload":
                if (!sender.hasPermission("nusafishing.admin.reload")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk melakukan ini!");
                    return true;
                }
                
                sender.sendMessage("§aMereload konfigurasi plugin...");
                
                // Use the new method that properly reloads from disk
                plugin.getConfigManager().reloadConfigs();
                
                // Also reload other dependent systems
                fishingManager.reloadConfig();
                
                sender.sendMessage("§a§lBerhasil! §fKonfigurasi plugin telah dimuat ulang.");
                return true;
                
            case "location":
                // Handle location management commands
                if (!sender.hasPermission("nusafishing.admin")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk menggunakan perintah ini!");
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage("§cPenggunaan: /nusafishing location <list|tp|create|delete>");
                    return true;
                }
                
                String locationAction = args[1].toLowerCase();
                
                switch (locationAction) {
                    case "list":
                        // List all special fishing locations
                        Map<String, Object> locations = fishingManager.getSpecialLocations();
                        
                        if (locations.isEmpty()) {
                            sender.sendMessage("§cBelum ada lokasi memancing spesial yang terdaftar.");
                            return true;
                        }
                        
                        sender.sendMessage("§b=== Lokasi Memancing Spesial ===");
                        for (String locId : locations.keySet()) {
                            ConfigurationSection locSection = plugin.getConfigManager().getFishingConfig()
                                    .getConfigurationSection("special_locations." + locId);
                            String name = locSection.getString("name", locId);
                            double centerX = locSection.getDouble("center_x");
                            double centerZ = locSection.getDouble("center_z");
                            double radius = locSection.getDouble("radius");
                            
                            sender.sendMessage(String.format("§f- §b%s §f(§7%s§f): §7X: %.0f, Z: %.0f, Radius: %.0f", 
                                    name, locId, centerX, centerZ, radius));
                        }
                        return true;
                        
                    case "tp":
                        // Teleport to a special location
                        if (!(sender instanceof Player)) {
                            sender.sendMessage("§cCommand ini hanya dapat digunakan oleh player!");
                            return true;
                        }
                        
                        if (args.length < 3) {
                            sender.sendMessage("§cPenggunaan: /nusafishing location tp <id>");
                            return true;
                        }
                        
                        return teleportToLocation((Player) sender, args[2]);
                        
                    case "create":
                        // Create a new special location
                        if (!(sender instanceof Player)) {
                            sender.sendMessage("§cCommand ini hanya dapat digunakan oleh player!");
                            return true;
                        }
                        
                        if (args.length < 3) {
                            sender.sendMessage("§cPenggunaan: /nusafishing location create <id> [radius] [name]");
                            return true;
                        }
                        
                        String newLocationId = args[2].toLowerCase();
                        double radius = 50.0; // Default radius
                        String name = newLocationId;
                        
                        // Parse radius if provided
                        if (args.length > 3) {
                            try {
                                radius = Double.parseDouble(args[3]);
                                if (radius < 1) radius = 1;
                                if (radius > 500) radius = 500;
                            } catch (NumberFormatException e) {
                                sender.sendMessage("§cRadius harus berupa angka!");
                                return true;
                            }
                        }
                        
                        // Parse name if provided
                        if (args.length > 4) {
                            StringBuilder nameBuilder = new StringBuilder();
                            for (int i = 4; i < args.length; i++) {
                                if (i > 4) nameBuilder.append(" ");
                                nameBuilder.append(args[i]);
                            }
                            name = nameBuilder.toString();
                        }
                        
                        return createLocation((Player) sender, newLocationId, radius, name);
                        
                    case "delete":
                        // Delete a special location
                        if (args.length < 3) {
                            sender.sendMessage("§cPenggunaan: /nusafishing location delete <id>");
                            return true;
                        }
                        
                        return deleteLocation(sender, args[2]);
                        
                    default:
                        sender.sendMessage("§cSubcommand lokasi tidak valid! Gunakan list, tp, create, atau delete.");
                        return true;
                }
                
            case "repair":
                // Repair fishing rod
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cCommand ini hanya dapat digunakan oleh player!");
                    return true;
                }
                
                // Check permission
                if (!sender.hasPermission("nusafishing.repair")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk menggunakan perintah ini!");
                    return true;
                }
                
                return repairRod((Player) sender);
                
            case "sell":
                // Sell fish from inventory
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cCommand ini hanya dapat digunakan oleh player!");
                    return true;
                }
                
                if (!sender.hasPermission("nusafishing.sell")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk menggunakan perintah ini!");
                    return true;
                }
                
                if (!plugin.getEconomyManager().isEconomyEnabled()) {
                    sender.sendMessage("§cFitur ekonomi tidak aktif! Konfirmasi Vault terpasang dan ada plugin ekonomi.");
                    return true;
                }
                
                // Check for "all" parameter
                boolean sellAll = args.length > 1 && args[1].equalsIgnoreCase("all");
                
                return sellFish((Player) sender, sellAll);
                
            case "shop":
                // Open shop GUI
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cCommand ini hanya dapat digunakan oleh player!");
                    return true;
                }
                
                if (!sender.hasPermission("nusafishing.shop")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk menggunakan perintah ini!");
                    return true;
                }
                
                if (!plugin.getEconomyManager().isEconomyEnabled()) {
                    sender.sendMessage("§cFitur ekonomi tidak aktif! Konfirmasi Vault terpasang dan ada plugin ekonomi.");
                    return true;
                }
                
                new ShopGUI((Player) sender, plugin).open();
                return true;
                
            case "event":
                // Require admin permission
                if (!sender.hasPermission("nusafishing.admin.event")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk melakukan itu!");
                    return true;
                }
                
                if (args.length < 2) {
                    sender.sendMessage("§cPenggunaan: /nusafishing event <start|stop|info> [event_type]");
                    return true;
                }
                
                String eventSubCommand = args[1].toLowerCase();
                
                switch (eventSubCommand) {
                    case "start":
                        if (args.length < 3) {
                            sender.sendMessage("§cPenggunaan: /nusafishing event start <event_type>");
                            sender.sendMessage("§cJenis event: LEVIATHAN_HUNT, ANCIENT_GUARDIAN, ABYSSAL_HORROR, RIVER_TITAN, GOLDEN_CATCH, COASTAL_TREASURE");
                            return true;
                        }
                        
                        String eventType = args[2].toUpperCase();
                        if (plugin.getEventManager().forceStartEvent(eventType)) {
                            sender.sendMessage("§aMemulai event " + eventType + "!");
                        } else {
                            sender.sendMessage("§cJenis event tidak valid! Gunakan: LEVIATHAN_HUNT, ANCIENT_GUARDIAN, dll.");
                        }
                        break;
                        
                    case "stop":
                        plugin.getEventManager().forceEndEvent();
                        sender.sendMessage("§aMenghentikan event yang sedang berjalan.");
                        break;
                        
                    case "info":
                        if (plugin.getEventManager().isEventActive()) {
                            EventType activeEventType = plugin.getEventManager().getActiveEventType();
                            int timeRemaining = plugin.getEventManager().getEventTimeRemaining();
                            int eventDuration = plugin.getConfig().getInt("events.duration_minutes", 30) * 60;
                            int timeElapsed = eventDuration - timeRemaining;
                            
                            // Calculate current chance
                            double baseChance = 0.01; // 1%
                            double maxChance = 0.10;  // 10%
                            double progressFactor = Math.min(1.0, timeElapsed / (double)eventDuration);
                            double currentChance = baseChance + progressFactor * (maxChance - baseChance);
                            
                            // Add skill bonus
                            if (sender instanceof Player) {
                                Player player = (Player) sender;
                                int fishingLevel = plugin.getSkillManager().getFishingLevel(player);
                                double skillBonus = fishingLevel * 0.001; // 0.1% per level
                                currentChance += skillBonus;
                            }
                            
                            sender.sendMessage("§6§l=== Info Event ===");
                            sender.sendMessage("§fEvent aktif: §e" + activeEventType.getDisplayName());
                            sender.sendMessage("§fDeskripsi: §7" + activeEventType.getDescription());
                            sender.sendMessage("§fWaktu tersisa: §e" + formatTime(timeRemaining));
                            sender.sendMessage("§fPersentase peluang saat ini: §e" + String.format("%.2f%%", currentChance * 100));
                        } else {
                            sender.sendMessage("§cTidak ada event yang sedang berlangsung.");
                        }
                        break;
                        
                    default:
                        sender.sendMessage("§cCommand tidak dikenali. Gunakan: /nusafishing event <start|stop|info>");
                }
                
                return true;
                
            case "afk":
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cCommand ini hanya dapat digunakan oleh player!");
                    return true;
                }
                
                if (!sender.hasPermission("nusafishing.afk")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk menggunakan perintah ini!");
                    return true;
                }

                Player afkPlayer = (Player) sender;
                plugin.getAfkFishingManager().toggleAfk(afkPlayer);
                return true;

            case "collector":
            case "kolektor":
                // Check permissions
                if (!sender.hasPermission("nusafishing.collector")) {
                    sender.sendMessage("§cKamu tidak memiliki izin untuk menggunakan perintah ini!");
                    return true;
                }
                
                // Make sure it's a player
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cPerintah ini hanya dapat digunakan oleh pemain!");
                    return true;
                }
                
                // Open the collector GUI
                new EventFishCollectorGUI((Player) sender, plugin).open();
                return true;
                
            case "tournament":
            case "turnamen":
                if (args.length < 2) {
                    sendTournamentHelp(sender);
                    return true;
                }
                
                String tournamentSubCommand = args[1].toLowerCase();
                switch (tournamentSubCommand) {
                    case "start":
                        if (!sender.hasPermission("nusafishing.admin.tournament")) {
                            sender.sendMessage("§cAnda tidak memiliki izin untuk menjalankan perintah ini.");
                            return true;
                        }
                        return startTournament(sender, args.length > 2 ? args[2] : null);
                        
                    case "end":
                        if (!sender.hasPermission("nusafishing.admin.tournament")) {
                            sender.sendMessage("§cAnda tidak memiliki izin untuk menjalankan perintah ini.");
                            return true;
                        }
                        return endTournament(sender);
                        
                    case "join":
                        if (!(sender instanceof Player)) {
                            sender.sendMessage("§cPerintah ini hanya dapat digunakan oleh pemain.");
                            return true;
                        }
                        return joinTournament((Player) sender);
                        
                    case "leaderboard":
                    case "lb":
                        return showTournamentLeaderboard(sender);
                        
                    case "info":
                        return showTournamentInfo(sender);
                        
                    case "schedule":
                        return showTournamentSchedule(sender);
                        
                    default:
                        sendTournamentHelp(sender);
                        return true;
                }
                
            default:
                sender.sendMessage("§cCommand tidak valid! Gunakan /nusafishing help untuk bantuan.");
                return true;
        }
    }
    
    /**
     * Teleport a player to a special fishing location
     * @param player The player to teleport
     * @param locationId The ID of the location
     * @return true if successful
     */
    private boolean teleportToLocation(Player player, String locationId) {
        ConfigurationSection locSection = plugin.getConfigManager().getFishingConfig()
                .getConfigurationSection("special_locations." + locationId);
        
        if (locSection == null) {
            player.sendMessage("§cLokasi dengan ID '" + locationId + "' tidak ditemukan!");
            return false;
        }
        
        double centerX = locSection.getDouble("center_x");
        double centerZ = locSection.getDouble("center_z");
        String name = locSection.getString("name", locationId);
        
        // Get current world and find suitable Y coordinate (surface)
        World world = player.getWorld();
        int centerY = world.getHighestBlockYAt((int) centerX, (int) centerZ) + 1;
        
        // Create location and teleport
        Location location = new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
        player.teleport(location);
        
        player.sendMessage("§aTeleport ke lokasi memancing '§b" + name + "§a'!");
        return true;
    }
    
    /**
     * Create a new special fishing location
     * @param player The player creating the location
     * @param locationId The ID for the new location
     * @param radius The radius of the location
     * @param name The name of the location
     * @return true if successful
     */
    private boolean createLocation(Player player, String locationId, double radius, String name) {
        // Check if location ID already exists
        if (plugin.getConfigManager().getFishingConfig().contains("special_locations." + locationId)) {
            player.sendMessage("§cLokasi dengan ID '" + locationId + "' sudah ada!");
            return false;
        }
        
        // Get player's current position
        Location playerLocation = player.getLocation();
        double centerX = playerLocation.getBlockX();
        double centerZ = playerLocation.getBlockZ();
        
        // Add new location to config
        ConfigurationSection specialLocationsSection = plugin.getConfigManager().getFishingConfig()
                .getConfigurationSection("special_locations");
        
        if (specialLocationsSection == null) {
            specialLocationsSection = plugin.getConfigManager().getFishingConfig().createSection("special_locations");
        }
        
        ConfigurationSection newLocationSection = specialLocationsSection.createSection(locationId);
        newLocationSection.set("name", name);
        newLocationSection.set("description", "Lokasi memancing spesial");
        newLocationSection.set("center_x", centerX);
        newLocationSection.set("center_z", centerZ);
        newLocationSection.set("radius", radius);
        newLocationSection.set("fish_multiplier", 1.5);
        newLocationSection.set("rare_fish_chance", 0.2);
        newLocationSection.set("special_fish", new ArrayList<String>());
        
        // Save and reload
        plugin.getConfigManager().saveConfig();
        fishingManager.reloadConfig();
        
        // Give rewards for location creation
        plugin.getRewardsManager().giveLocationCreationRewards(player, locationId, name, radius, centerX, centerZ);
        
        player.sendMessage("§aLokasi memancing spesial '§b" + name + "§a' (ID: " + locationId + ") berhasil dibuat!");
        player.sendMessage("§7Pusat: X: " + centerX + ", Z: " + centerZ + ", Radius: " + radius);
        player.sendMessage("§7Gunakan command: /nusafishing location list untuk melihat semua lokasi");
        return true;
    }
    
    /**
     * Delete a special fishing location
     * @param sender The command sender
     * @param locationId The ID of the location
     * @return true if successful
     */
    private boolean deleteLocation(CommandSender sender, String locationId) {
        // Check if location exists
        if (!plugin.getConfigManager().getFishingConfig().contains("special_locations." + locationId)) {
            sender.sendMessage("§cLokasi dengan ID '" + locationId + "' tidak ditemukan!");
            return false;
        }
        
        // Get location name before deletion
        String name = plugin.getConfigManager().getFishingConfig()
                .getString("special_locations." + locationId + ".name", locationId);
        
        // Remove from config
        plugin.getConfigManager().getFishingConfig().set("special_locations." + locationId, null);
        
        // Save and reload
        plugin.getConfigManager().saveConfig();
        fishingManager.reloadConfig();
        
        sender.sendMessage("§aLokasi memancing spesial '§b" + name + "§a' (ID: " + locationId + ") berhasil dihapus!");
        return true;
    }
    
    /**
     * Give a player a custom fishing rod
     * @param player The player to give the rod to
     * @param rodId The ID of the rod in the configuration
     * @param amount The amount of rods to give
     * @return true if successful
     */
    private boolean giveRod(Player player, String rodId, int amount) {
        FishingRod rod = plugin.getConfigManager().getFishingRods().get(rodId);
        if (rod == null) {
            player.sendMessage("§cPancingan dengan ID '" + rodId + "' tidak ditemukan!");
            return false;
        }
        
        // Create the fishing rod item
        ItemStack rodItem = rod.createItemStack();
        
        // Set amount and give to player
        rodItem.setAmount(amount);
        player.getInventory().addItem(rodItem);
        
        player.sendMessage("§aKamu mendapatkan §f" + amount + "x " + rod.getTier().getColor() + rod.getName() + "§a!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        return true;
    }
    
    /**
     * Give a player a custom bait
     * @param player The player to give the bait to
     * @param baitId The ID of the bait in the configuration
     * @param amount The amount of baits to give
     * @return true if successful
     */
    private boolean giveBait(Player player, String baitId, int amount) {
        FishingBait bait = plugin.getConfigManager().getFishingBaits().get(baitId);
        if (bait == null) {
            player.sendMessage("§cUmpan dengan ID '" + baitId + "' tidak ditemukan!");
            return false;
        }
        
        // Create the bait item and give to player
        ItemStack baitItem = bait.createItemStack(amount);
        player.getInventory().addItem(baitItem);
        
        player.sendMessage("§aKamu mendapatkan §f" + amount + "x §a" + bait.getName() + "§a!");
        return true;
    }
    
    /**
     * Format fish IDs into display names
     * @param fishIds List of fish IDs
     * @return List of formatted fish names
     */
    private List<String> formatFishNames(List<String> fishIds) {
        List<String> formattedNames = new ArrayList<>();
        for (String fishId : fishIds) {
            if (plugin.getConfigManager().getCustomFishes().containsKey(fishId)) {
                formattedNames.add(plugin.getConfigManager().getCustomFishes().get(fishId).getName());
            } else {
                formattedNames.add(fishId);
            }
        }
        return formattedNames;
    }
    
    /**
     * Repair the fishing rod in player's hand
     * @param player The player repairing the rod
     * @return true if repair successful
     */
    private boolean repairRod(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        
        // Check if player is holding a fishing rod
        if (heldItem.getType() != Material.FISHING_ROD) {
            player.sendMessage("§cKamu harus memegang pancingan di tangan!");
            return false;
        }
        
        // Check if it's a custom rod
        ItemMeta meta = heldItem.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) {
            player.sendMessage("§cIni bukan pancingan custom!");
            return false;
        }
        
        // Get rod info from persistent data
        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey cleanRodKey = NamespacedKey.fromString("nusafishing:rod_id");
        NamespacedKey dottedRodKey = new NamespacedKey(plugin, ROD_ID_KEY);
        NamespacedKey cleanDurabilityKey = NamespacedKey.fromString("nusafishing:durability");
        NamespacedKey dottedDurabilityKey = new NamespacedKey(plugin, ROD_DURABILITY_KEY);
        
        String rodId = null;
        if (cleanRodKey != null && container.has(cleanRodKey, PersistentDataType.STRING)) {
            rodId = container.get(cleanRodKey, PersistentDataType.STRING);
        } else if (container.has(dottedRodKey, PersistentDataType.STRING)) {
            rodId = container.get(dottedRodKey, PersistentDataType.STRING);
        }

        if (rodId == null) {
            player.sendMessage("§cIni bukan pancingan custom yang valid!");
            return false;
        }

        int currentDurability = 0;
        if (cleanDurabilityKey != null && container.has(cleanDurabilityKey, PersistentDataType.INTEGER)) {
            currentDurability = container.get(cleanDurabilityKey, PersistentDataType.INTEGER);
        } else if (container.has(dottedDurabilityKey, PersistentDataType.INTEGER)) {
            currentDurability = container.get(dottedDurabilityKey, PersistentDataType.INTEGER);
        }
        
        FishingRod rod = plugin.getFishingManager().getRodById(rodId);
        if (rod == null) {
            player.sendMessage("§cTidak dapat menemukan informasi pancingan!");
            return false;
        }
        
        // Calculate repair cost based on how damaged the rod is
        int maxDurability = rod.getDurability();
        int durabilityLost = maxDurability - currentDurability;
        
        if (durabilityLost <= 0) {
            player.sendMessage("§cPancingan ini tidak perlu diperbaiki!");
            return false;
        }
        
        // Calculate tier-based repair cost
        EconomyManager economy = plugin.getEconomyManager();
        double repairCost = economy.getRepairCost(durabilityLost, rod.getTier());
        
        // Check if economy is enabled and player has enough money
        if (economy.isEconomyEnabled()) {
            if (!economy.hasMoney(player, repairCost)) {
                player.sendMessage("§cKamu tidak memiliki cukup uang untuk memperbaiki pancingan ini!");
                player.sendMessage("§cBiaya perbaikan: §f" + economy.formatMoney(repairCost) + 
                        " §7(Tier " + rod.getTier().getDisplayName() + ")");
                return false;
            }
            
            // Charge the player
            economy.withdrawMoney(player, repairCost);
            player.sendMessage("§aKamu membayar §f" + economy.formatMoney(repairCost) + 
                    " §auntuk memperbaiki pancingan §f" + rod.getTier().getColor() + rod.getName());
        }
        
        // Try to repair the rod
        if (FishingListener.repairRod(player, heldItem)) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
            player.sendMessage("§a§lBerhasil! §fPancingan telah diperbaiki.");
            return true;
        } else {
            // Refund money if repair failed
            if (economy.isEconomyEnabled()) {
                economy.depositMoney(player, repairCost);
            }
            player.sendMessage("§cTidak dapat memperbaiki pancingan ini.");
            return false;
        }
    }
    
    /**
     * Sell fish from player's inventory
     * @param player The player selling fish
     * @param sellAll Whether to sell all fish or only the one in hand
     * @return true if successful
     */
    private boolean sellFish(Player player, boolean sellAll) {
        EconomyManager economy = plugin.getEconomyManager();
        
        if (!economy.isEconomyEnabled()) {
            player.sendMessage("§cFitur ekonomi tidak aktif!");
            return false;
        }
        
        double totalEarnings = 0;
        int totalFishSold = 0;
        
        if (sellAll) {
            // Sell all fish in storage inventory
            ItemStack[] contents = player.getInventory().getStorageContents();
            Map<String, Integer> fishSold = new HashMap<>();
            
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null) continue;
                
                CustomFish fish = getFishFromItem(item);
                if (fish == null) continue;
                
                double price = economy.calculateFishPrice(fish);
                double earnings = price * item.getAmount();
                totalEarnings += earnings;
                totalFishSold += item.getAmount();
                
                // Keep track of fish types sold for price dynamics
                fishSold.put(fish.getId(), fishSold.getOrDefault(fish.getId(), 0) + item.getAmount());
                
                // Remove the fish from inventory
                player.getInventory().setItem(i, null);
            }
            
            // Update dynamic prices for all fish sold
            for (Map.Entry<String, Integer> entry : fishSold.entrySet()) {
                economy.updateDynamicPrice(entry.getKey(), entry.getValue());
            }
        } else {
            // Sell only the fish in hand
            ItemStack handItem = player.getInventory().getItemInMainHand();
            
            if (handItem.getType() == Material.AIR) {
                player.sendMessage("§cKamu tidak memegang ikan!");
                return false;
            }
            
            CustomFish fish = getFishFromItem(handItem);
            if (fish == null) {
                player.sendMessage("§cItem yang kamu pegang bukan ikan hasil pancingan!");
                return false;
            }
            
            double price = economy.calculateFishPrice(fish);
            totalEarnings = price * handItem.getAmount();
            totalFishSold = handItem.getAmount();
            
            // Update dynamic price for this fish
            economy.updateDynamicPrice(fish.getId(), handItem.getAmount());
            
            // Remove the fish from hand
            player.getInventory().setItemInMainHand(null);
        }
        
        if (totalFishSold == 0) {
            player.sendMessage("§cTidak ada ikan yang dapat dijual!");
            return false;
        }
        
        // Add money to player
        economy.depositMoney(player, totalEarnings);
        
        // Notify player
        player.sendMessage("§a§lBerhasil! §f" + 
                "Kamu menjual §b" + totalFishSold + " ikan§f dengan total " + 
                "§a" + economy.formatMoney(totalEarnings) + "§f.");
        
        // Play sound
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        return true;
    }
    
    /**
     * Get CustomFish information from an ItemStack
     * @param item The item to check
     * @return The CustomFish data, or null if not a fish
     */
    private CustomFish getFishFromItem(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        NamespacedKey cleanFishIdKey = NamespacedKey.fromString("nusafishing:fish_id");
        NamespacedKey legacyFishIdKey = new NamespacedKey(plugin, "fish_id");

        String fishId = null;
        if (cleanFishIdKey != null && container.has(cleanFishIdKey, PersistentDataType.STRING)) {
            fishId = container.get(cleanFishIdKey, PersistentDataType.STRING);
        } else if (container.has(legacyFishIdKey, PersistentDataType.STRING)) {
            fishId = container.get(legacyFishIdKey, PersistentDataType.STRING);
        }

        if (fishId == null) return null;

        NamespacedKey cleanWeightKey = NamespacedKey.fromString("nusafishing:fish_weight");
        NamespacedKey legacyWeightKey = new NamespacedKey(plugin, "fish_weight");
        NamespacedKey cleanQualityKey = NamespacedKey.fromString("nusafishing:fish_quality");
        NamespacedKey legacyQualityKey = new NamespacedKey(plugin, "fish_quality");
        NamespacedKey cleanTierKey = NamespacedKey.fromString("nusafishing:fish_tier");
        NamespacedKey legacyTierKey = new NamespacedKey(plugin, "fish_tier");

        CustomFish templateFish = plugin.getConfigManager().getCustomFishes().get(fishId);
        if (templateFish == null) return null;

        CustomFish fish = new CustomFish(
            templateFish.getId(),
            templateFish.getName(),
            templateFish.getDescription(),
            templateFish.getMaterial(),
            templateFish.getCustomModelData(),
            templateFish.getTextureProvider(),
            templateFish.getTextureId(),
            templateFish.getMinWeight(),
            templateFish.getMaxWeight(),
            templateFish.getMinQuality(),
            templateFish.getMaxQuality(),
            templateFish.getBasePrice(),
            templateFish.getBaseTier() // Pass the base tier from template fish
        );

        double weight = templateFish.getMinWeight();
        if (cleanWeightKey != null && container.has(cleanWeightKey, PersistentDataType.DOUBLE)) {
            weight = container.get(cleanWeightKey, PersistentDataType.DOUBLE);
        } else if (container.has(legacyWeightKey, PersistentDataType.DOUBLE)) {
            weight = container.get(legacyWeightKey, PersistentDataType.DOUBLE);
        }

        int quality = templateFish.getMinQuality();
        if (cleanQualityKey != null && container.has(cleanQualityKey, PersistentDataType.INTEGER)) {
            quality = container.get(cleanQualityKey, PersistentDataType.INTEGER);
        } else if (container.has(legacyQualityKey, PersistentDataType.INTEGER)) {
            quality = container.get(legacyQualityKey, PersistentDataType.INTEGER);
        }
        
        // Baca data tier yang tersimpan (atau gunakan base tier jika tidak ada)
        int tierOrdinal = templateFish.getBaseTier().ordinal();
        if (cleanTierKey != null && container.has(cleanTierKey, PersistentDataType.INTEGER)) {
            tierOrdinal = container.get(cleanTierKey, PersistentDataType.INTEGER);
        } else if (container.has(legacyTierKey, PersistentDataType.INTEGER)) {
            tierOrdinal = container.get(legacyTierKey, PersistentDataType.INTEGER);
        }
        if (tierOrdinal < 0 || tierOrdinal >= CustomFish.FishTier.values().length) {
            tierOrdinal = templateFish.getBaseTier().ordinal();
        }
        CustomFish.FishTier actualTier = CustomFish.FishTier.values()[tierOrdinal];

        fish.applyCaughtData(weight, quality, actualTier);
        return fish;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Main subcommands
            List<String> subCommands = new ArrayList<>(Arrays.asList("help", "rod", "bait", "repair",
                    "location", "event", "collector", "sell", "shop", "tournament", "afk"));
            if (sender.hasPermission("nusafishing.collector")) {
                subCommands.add("collector");
            }
            if (sender.hasPermission("nusafishing.sell")) {
                subCommands.add("sell");
            }
            
            if (sender.hasPermission("nusafishing.admin")) {
                subCommands.add("reload");
                subCommands.add("location");
            }
            
            if (sender.hasPermission("nusafishing.shop")) {
                subCommands.add("shop");
            }
            
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("sell") && sender.hasPermission("nusafishing.sell")) {
                completions.add("all");
            } else if (args[0].equalsIgnoreCase("rod") && sender.hasPermission("nusafishing.rod")) {
                // Rod tab completion logic (existing code)
            } else if (args[0].equalsIgnoreCase("event")) {
                return Arrays.asList("start", "stop", "info").stream()
                       .filter(s -> s.startsWith(args[1].toLowerCase()))
                       .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("tournament")) {
                List<String> options = new ArrayList<>();
                options.add("join");
                options.add("info");
                options.add("leaderboard");
                options.add("schedule");
                
                if (sender.hasPermission("nusafishing.admin.tournament")) {
                    options.add("start");
                    options.add("end");
                }
                
                return filterCompletions(options, args[1]);
            }
            // Rest of the tab completion logic
        } else if (args.length == 3 && args[0].equalsIgnoreCase("event") && args[1].equalsIgnoreCase("start")) {
            return Arrays.stream(EventType.values())
                   .map(Enum::name)
                   .filter(s -> s.startsWith(args[2].toUpperCase()))
                   .collect(Collectors.toList());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("tournament") && 
                args[1].equalsIgnoreCase("start") && 
                sender.hasPermission("nusafishing.admin.tournament")) {
            return filterCompletions(
                    new ArrayList<>(plugin.getTournamentManager().getScheduledTournaments().keySet()),
                    args[2]);
        }
        
        return completions;
    }
    
    /**
     * Filter tab completion options based on the current input
     * @param options List of all possible options
     * @param input Current user input to filter by
     * @return Filtered list of options that match the input
     */
    private List<String> filterCompletions(List<String> options, String input) {
        if (input.isEmpty()) {
            return options;
        }
        
        String lowercaseInput = input.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lowercaseInput))
                .collect(Collectors.toList());
    }
    
    /**
     * Helper method to format time in seconds to mm:ss
     * @param seconds The time in seconds
     * @return Formatted time string
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
    
    /**
     * Kirim bantuan perintah turnamen ke pengirim
     * @param sender Pengirim perintah
     */
    private void sendTournamentHelp(CommandSender sender) {
        sender.sendMessage("§e§l=== Perintah Turnamen NusaFishing ===");
        sender.sendMessage("§f/nf tournament join §7- Bergabung dengan turnamen aktif");
        sender.sendMessage("§f/nf tournament info §7- Lihat info turnamen aktif");
        sender.sendMessage("§f/nf tournament leaderboard §7- Lihat leaderboard turnamen");
        sender.sendMessage("§f/nf tournament schedule §7- Lihat jadwal turnamen");
        
        if (sender.hasPermission("nusafishing.admin.tournament")) {
            sender.sendMessage("§6§l=== Perintah Admin ===");
            sender.sendMessage("§f/nf tournament start [id] §7- Mulai turnamen");
            sender.sendMessage("§f/nf tournament end §7- Akhiri turnamen");
        }
    }
    
    /**
     * Mulai turnamen
     * @param sender Pengirim perintah
     * @param tournamentId ID turnamen yang akan dimulai (opsional)
     * @return true jika perintah berhasil
     */
    private boolean startTournament(CommandSender sender, String tournamentId) {
        if (plugin.getTournamentManager().isTournamentActive()) {
            sender.sendMessage("§c§l[TURNAMEN] §fAda turnamen yang sedang berlangsung. Akhiri dulu dengan /nf tournament end");
            return true;
        }
        
        // Jika ID tidak diberikan, gunakan turnamen default
        if (tournamentId == null) {
            // Buat turnamen instan dengan durasi 30 menit
            Tournament tournament = new Tournament(
                    "instant_tournament", 
                    "Turnamen Instan", 
                    "Turnamen cepat yang dibuat oleh admin", 
                    TournamentType.HEAVIEST_FISH, 
                    30);
            
            // Set hadiah default
            tournament.addReward("first_place", 10000, 500, null);
            tournament.addReward("second_place", 5000, 300, null);
            tournament.addReward("third_place", 2500, 150, null);
            
            boolean started = plugin.getTournamentManager().startTournament(tournament);
            if (started) {
                sender.sendMessage("§a§l[TURNAMEN] §fTurnamen instan berhasil dimulai!");
            } else {
                sender.sendMessage("§c§l[TURNAMEN] §fGagal memulai turnamen.");
            }
            return true;
        } else {
            // Coba cari turnamen dengan ID yang diberikan
            Tournament tournament = plugin.getTournamentManager().getScheduledTournaments().get(tournamentId);
            if (tournament == null) {
                sender.sendMessage("§c§l[TURNAMEN] §fTurnamen dengan ID '" + tournamentId + "' tidak ditemukan.");
                sender.sendMessage("§c§l[TURNAMEN] §fID turnamen yang tersedia: " + 
                        String.join(", ", plugin.getTournamentManager().getScheduledTournaments().keySet()));
                return true;
            }
            
            boolean started = plugin.getTournamentManager().startTournament(tournament);
            if (started) {
                sender.sendMessage("§a§l[TURNAMEN] §fTurnamen '" + tournament.getName() + "' berhasil dimulai!");
            } else {
                sender.sendMessage("§c§l[TURNAMEN] §fGagal memulai turnamen.");
            }
            return true;
        }
    }
    
    /**
     * Akhiri turnamen yang sedang berlangsung
     * @param sender Pengirim perintah
     * @return true jika perintah berhasil
     */
    private boolean endTournament(CommandSender sender) {
        if (!plugin.getTournamentManager().isTournamentActive()) {
            sender.sendMessage("§c§l[TURNAMEN] §fTidak ada turnamen yang sedang berlangsung.");
            return true;
        }
        
        plugin.getTournamentManager().endTournament();
        sender.sendMessage("§a§l[TURNAMEN] §fTurnamen telah diakhiri.");
        return true;
    }
    
    /**
     * Bergabung dengan turnamen aktif
     * @param player Pemain yang ingin bergabung
     * @return true jika perintah berhasil
     */
    private boolean joinTournament(Player player) {
        return plugin.getTournamentManager().joinTournament(player);
    }
    
    /**
     * Tampilkan leaderboard turnamen
     * @param sender Pengirim perintah
     * @return true jika perintah berhasil
     */
    private boolean showTournamentLeaderboard(CommandSender sender) {
        if (!plugin.getTournamentManager().isTournamentActive()) {
            sender.sendMessage("§c§l[TURNAMEN] §fTidak ada turnamen yang sedang berlangsung.");
            return true;
        }
        
        // Tampilkan leaderboard sementara
        plugin.getTournamentManager().broadcastLeaderboard(false);
        return true;
    }
    
    /**
     * Tampilkan info turnamen aktif
     * @param sender Pengirim perintah
     * @return true jika perintah berhasil
     */
    private boolean showTournamentInfo(CommandSender sender) {
        if (!plugin.getTournamentManager().isTournamentActive()) {
            sender.sendMessage("§c§l[TURNAMEN] §fTidak ada turnamen yang sedang berlangsung.");
            return true;
        }
        
        Tournament tournament = plugin.getTournamentManager().getActiveTournament();
        if (tournament == null) {
            sender.sendMessage("§c§l[TURNAMEN] §fTerjadi kesalahan saat mendapatkan informasi turnamen.");
            return true;
        }
        
        // Hitung waktu tersisa
        long timeRemaining = (tournament.getEndTime() - System.currentTimeMillis()) / 1000;
        
        // Format tipe turnamen
        String type;
        switch (tournament.getType()) {
            case MOST_FISH: type = "Ikan Terbanyak"; break;
            case HEAVIEST_FISH: type = "Ikan Terberat"; break;
            case HIGHEST_QUALITY: type = "Kualitas Tertinggi"; break;
            case TOTAL_WEIGHT: type = "Berat Total"; break;
            case SPECIFIC_SPECIES: type = "Spesies Tertentu"; break;
            default: type = tournament.getType().toString(); break;
        }
        
        // Kirim info
        sender.sendMessage("§e§l=== Informasi Turnamen ===");
        sender.sendMessage("§fNama: §b" + tournament.getName());
        sender.sendMessage("§fDeskripsi: §7" + tournament.getDescription());
        sender.sendMessage("§fTipe: §b" + type);
        sender.sendMessage("§fDurasi: §b" + tournament.getDurationMinutes() + " menit");
        sender.sendMessage("§fWaktu tersisa: §b" + TimeUtils.formatTime((int) timeRemaining));
        sender.sendMessage("§7Gunakan /nf tournament join untuk berpartisipasi!");
        
        return true;
    }
    
    /**
     * Tampilkan jadwal turnamen
     * @param sender Pengirim perintah
     * @return true jika perintah berhasil
     */
    private boolean showTournamentSchedule(CommandSender sender) {
        Map<String, Tournament> scheduledTournaments = plugin.getTournamentManager().getScheduledTournaments();
        
        if (scheduledTournaments.isEmpty()) {
            sender.sendMessage("§c§l[TURNAMEN] §fTidak ada turnamen terjadwal.");
            return true;
        }
        
        sender.sendMessage("§e§l=== Jadwal Turnamen ===");
        for (Map.Entry<String, Tournament> entry : scheduledTournaments.entrySet()) {
            Tournament tournament = entry.getValue();
            
            // Format tipe turnamen
            String type;
            switch (tournament.getType()) {
                case MOST_FISH: type = "Ikan Terbanyak"; break;
                case HEAVIEST_FISH: type = "Ikan Terberat"; break;
                case HIGHEST_QUALITY: type = "Kualitas Tertinggi"; break;
                case TOTAL_WEIGHT: type = "Berat Total"; break;
                case SPECIFIC_SPECIES: type = "Spesies Tertentu"; break;
                default: type = tournament.getType().toString(); break;
            }
            
            // Format jadwal
            String schedule = tournament.getHourOfDay() + ":" + 
                    String.format("%02d", tournament.getMinuteOfHour());
            String days = String.join(", ", tournament.getDaysOfWeek());
            
            sender.sendMessage("§b" + tournament.getName() + " §7(" + type + ")");
            sender.sendMessage("  §7Jadwal: §f" + schedule + " pada " + days);
            sender.sendMessage("  §7Durasi: §f" + tournament.getDurationMinutes() + " menit");
            sender.sendMessage("");
        }
        
        return true;
    }
}