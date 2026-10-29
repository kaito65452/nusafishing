package id.nusafishing.tournament;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import id.nusafishing.NusaFishing;
import id.nusafishing.fishing.model.CustomFish;
import id.nusafishing.tournament.model.Tournament;
import id.nusafishing.tournament.model.TournamentEntry;
import id.nusafishing.tournament.model.TournamentType;
import id.nusafishing.util.TimeUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class TournamentManager implements Listener {
    private final NusaFishing plugin;
    private Tournament activeTournament;
    private final Map<UUID, TournamentEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, Tournament> scheduledTournaments = new HashMap<>();
    private final Map<String, java.time.LocalDate> lastTournamentRunDates = new HashMap<>();
    private final File pendingRewardsFile;
    private final FileConfiguration pendingRewardsConfig;
    private BukkitTask schedulerTask;
    private BukkitTask updateTask;
    private BukkitTask endTask;

    // Waktu minimum antara turnamen (dalam menit)
    private int minTimeBetweenTournaments = 60;

    public TournamentManager(NusaFishing plugin) {
        this.plugin = plugin;
        this.pendingRewardsFile = new File(plugin.getDataFolder(), "pending_tournament_rewards.yml");
        if (!pendingRewardsFile.exists()) {
            pendingRewardsFile.getParentFile().mkdirs();
            try {
                pendingRewardsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create pending_tournament_rewards.yml: " + e.getMessage());
            }
        }
        this.pendingRewardsConfig = YamlConfiguration.loadConfiguration(pendingRewardsFile);
        loadConfiguration();
        startScheduler();
    }
    
    /**
     * Muat konfigurasi turnamen dari config.yml
     */
    private void loadConfiguration() {
        FileConfiguration config = plugin.getConfig();
        
        // Buat bagian tournament di config jika belum ada
        if (!config.contains("tournaments")) {
            config.createSection("tournaments");
            config.set("tournaments.min_time_between_tournaments", minTimeBetweenTournaments);
            config.set("tournaments.auto_schedule", true);
            
            // Contoh turnamen terjadwal
            ConfigurationSection scheduledSection = config.createSection("tournaments.scheduled");
            
            // Contoh turnamen harian
            ConfigurationSection dailyTournament = scheduledSection.createSection("daily_heaviest");
            dailyTournament.set("name", "Turnamen Ikan Terberat Harian");
            dailyTournament.set("description", "Tangkap ikan terberat untuk memenangkan hadiah!");
            dailyTournament.set("type", "HEAVIEST_FISH");
            dailyTournament.set("duration_minutes", 30);
            dailyTournament.set("hour_of_day", 18); // Jam 6 sore
            dailyTournament.set("minute_of_hour", 0);
            dailyTournament.set("days_of_week", List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"));
            dailyTournament.set("auto_start", true);
            
            // Tambahkan hadiah
            ConfigurationSection rewards = dailyTournament.createSection("rewards");
            rewards.set("first_place.money", 10000);
            rewards.set("first_place.exp", 500);
            rewards.set("first_place.commands", List.of("give %player% diamond 5"));
            rewards.set("second_place.money", 5000);
            rewards.set("second_place.exp", 300);
            rewards.set("third_place.money", 2500);
            rewards.set("third_place.exp", 150);
            
            // Turnamen mingguan
            ConfigurationSection weeklyTournament = scheduledSection.createSection("weekly_most_fish");
            weeklyTournament.set("name", "Turnamen Jumlah Ikan Mingguan");
            weeklyTournament.set("description", "Tangkap sebanyak mungkin ikan untuk memenangkan hadiah besar!");
            weeklyTournament.set("type", "MOST_FISH");
            weeklyTournament.set("duration_minutes", 60);
            weeklyTournament.set("hour_of_day", 20); // Jam 8 malam
            weeklyTournament.set("minute_of_hour", 0);
            weeklyTournament.set("days_of_week", List.of("SATURDAY"));
            weeklyTournament.set("auto_start", true);
            
            // Tambahkan hadiah
            ConfigurationSection weeklyRewards = weeklyTournament.createSection("rewards");
            weeklyRewards.set("first_place.money", 50000);
            weeklyRewards.set("first_place.exp", 2000);
            weeklyRewards.set("first_place.commands", List.of(
                "give %player% diamond 20",
                "give %player% netherite_ingot 2"
            ));
            weeklyRewards.set("second_place.money", 25000);
            weeklyRewards.set("second_place.exp", 1000);
            weeklyRewards.set("third_place.money", 10000);
            weeklyRewards.set("third_place.exp", 500);
            
            plugin.saveConfig();
        }
        
        // Baca konfigurasi
        minTimeBetweenTournaments = config.getInt("tournaments.min_time_between_tournaments", 60);
        
        // Muat turnamen terjadwal
        loadScheduledTournaments();
    }
    
    /**
     * Muat turnamen terjadwal dari konfigurasi
     */
    private void loadScheduledTournaments() {
        ConfigurationSection scheduledSection = plugin.getConfig().getConfigurationSection("tournaments.scheduled");
        if (scheduledSection == null) return;
        
        for (String tournamentId : scheduledSection.getKeys(false)) {
            ConfigurationSection tournamentSection = scheduledSection.getConfigurationSection(tournamentId);
            if (tournamentSection == null) continue;
            
            try {
                String name = tournamentSection.getString("name", "Turnamen Memancing");
                String description = tournamentSection.getString("description", "");
                TournamentType type = TournamentType.valueOf(tournamentSection.getString("type", "MOST_FISH"));
                int durationMinutes = tournamentSection.getInt("duration_minutes", 30);
                int hourOfDay = tournamentSection.getInt("hour_of_day", 12);
                int minuteOfHour = tournamentSection.getInt("minute_of_hour", 0);
                List<String> daysOfWeek = tournamentSection.getStringList("days_of_week");
                boolean autoStart = tournamentSection.getBoolean("auto_start", true);
                
                // Buat objek turnamen
                Tournament tournament = new Tournament(tournamentId, name, description, type, durationMinutes);
                tournament.setScheduleTime(hourOfDay, minuteOfHour);
                tournament.setDaysOfWeek(daysOfWeek);
                tournament.setAutoStart(autoStart);
                
                // Muat konfigurasi hadiah
                ConfigurationSection rewardsSection = tournamentSection.getConfigurationSection("rewards");
                if (rewardsSection != null) {
                    for (String place : rewardsSection.getKeys(false)) {
                        ConfigurationSection placeSection = rewardsSection.getConfigurationSection(place);
                        if (placeSection != null) {
                            double money = placeSection.getDouble("money", 0);
                            int exp = placeSection.getInt("exp", 0);
                            List<String> commands = placeSection.getStringList("commands");
                            
                            tournament.addReward(place, money, exp, commands);
                        }
                    }
                }
                
                // Tambahkan ke daftar turnamen terjadwal
                scheduledTournaments.put(tournamentId, tournament);
                plugin.getLogger().info("Loaded scheduled tournament: " + name + " (Type: " + type + ")");
                
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load tournament: " + tournamentId + " - " + e.getMessage());
            }
        }
    }
    
    private synchronized void queueOfflineReward(UUID playerId, String playerName, int exp, List<String> commands, double money) {
        String key = playerId.toString();
        int existingExp = pendingRewardsConfig.getInt(key + ".exp", 0);
        pendingRewardsConfig.set(key + ".exp", existingExp + exp);
        pendingRewardsConfig.set(key + ".player_name", playerName);

        double existingMoney = pendingRewardsConfig.getDouble(key + ".money", 0.0);
        if (money > 0) {
            pendingRewardsConfig.set(key + ".money", existingMoney + money);
        }

        List<String> existingCommands = pendingRewardsConfig.getStringList(key + ".commands");
        if (commands != null && !commands.isEmpty()) {
            existingCommands.addAll(commands);
        }
        pendingRewardsConfig.set(key + ".commands", existingCommands);

        try {
            pendingRewardsConfig.save(pendingRewardsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save pending tournament reward: " + e.getMessage());
        }
    }

    @EventHandler
    public synchronized void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            String key = player.getUniqueId().toString();
            synchronized (this) {
                if (!pendingRewardsConfig.contains(key)) {
                    return;
                }

                int exp = pendingRewardsConfig.getInt(key + ".exp", 0);
                double money = pendingRewardsConfig.getDouble(key + ".money", 0.0);
                List<String> commands = pendingRewardsConfig.getStringList(key + ".commands");

                if (money > 0 && plugin.getEconomyManager().isEconomyEnabled()) {
                    plugin.getEconomyManager().depositMoney(player, money);
                }

                if (exp > 0) {
                    plugin.getSkillManager().addFishingExp(player, exp);
                    if (plugin.getAuraSkillsHook() != null) {
                        plugin.getAuraSkillsHook().addXp(player, exp);
                    }
                }

                if (commands != null && !commands.isEmpty()) {
                    for (String cmd : commands) {
                        String finalCmd = cmd.replace("%player%", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                    }
                }

                player.sendMessage("§a§l[TURNAMEN] §fKamu menerima hadiah turnamen yang tertunda!");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

                pendingRewardsConfig.set(key, null);
                try {
                    pendingRewardsConfig.save(pendingRewardsFile);
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not save pending tournament rewards after claim: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Mulai scheduler untuk mengecek turnamen terjadwal
     */
    private void startScheduler() {
        // Batalkan task yang berjalan jika ada
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }

        // Jalankan scheduler setiap 10 detik (200 ticks) untuk toleransi TPS drift
        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkScheduledTournaments();
            }
        }.runTaskTimer(plugin, 200L, 200L);
    }

    /**
     * Cek apakah ada turnamen terjadwal yang harus dimulai
     */
    private void checkScheduledTournaments() {
        if (activeTournament != null) return; // Jangan mulai turnamen baru jika ada yang sedang berjalan

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDate today = now.toLocalDate();
        String currentDayOfWeek = now.getDayOfWeek().toString();

        for (Tournament tournament : scheduledTournaments.values()) {
            if (!tournament.isAutoStart()) continue;

            boolean dayMatches = tournament.getDaysOfWeek() != null &&
                    tournament.getDaysOfWeek().stream().anyMatch(d -> d.equalsIgnoreCase(currentDayOfWeek));
            if (!dayMatches) continue;

            // Jangan jalankan turnamen yang sama lebih dari sekali di hari yang sama
            if (today.equals(lastTournamentRunDates.get(tournament.getId()))) {
                continue;
            }

            java.time.LocalDateTime scheduledDateTime = today.atTime(tournament.getHourOfDay(), tournament.getMinuteOfHour());
            // Cek rentang waktu 5 menit toleransi lag TPS
            if (!now.isBefore(scheduledDateTime) && now.isBefore(scheduledDateTime.plusMinutes(5))) {
                lastTournamentRunDates.put(tournament.getId(), today);
                startTournament(tournament);
                return;
            }
        }
    }
    
    /**
     * Mulai turnamen baru
     * @param tournament Turnamen yang akan dimulai
     * @return true jika berhasil dimulai
     */
    public boolean startTournament(Tournament tournament) {
        if (activeTournament != null) {
            return false; // Ada turnamen yang sedang berlangsung
        }
        
        // Set turnamen aktif
        activeTournament = tournament;
        entries.clear();
        
        // Set waktu mulai dan selesai
        activeTournament.setStartTime(System.currentTimeMillis());
        long endTime = activeTournament.getStartTime() + ((long) activeTournament.getDurationMinutes() * 60L * 1000L);
        activeTournament.setEndTime(endTime);
        
        // Umumkan turnamen dimulai
        broadcastTournamentStart();
        
        // Jadwalkan task untuk update leaderboard secara berkala (setiap 2 menit)
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                broadcastLeaderboard(false);
            }
        }.runTaskTimer(plugin, 20L * 60L * 2L, 20L * 60L * 2L);
        
        // Jadwalkan task untuk mengakhiri turnamen
        endTask = new BukkitRunnable() {
            @Override
            public void run() {
                endTournament();
            }
        }.runTaskLater(plugin, 20L * 60L * (long) activeTournament.getDurationMinutes());
        
        return true;
    }
    
    /**
     * Akhiri turnamen yang sedang berjalan
     */
    public void endTournament() {
        if (activeTournament == null) return;
        
        // Batalkan task update leaderboard
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        
        // Batalkan task penghentian turnamen
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        
        // Umumkan hasil turnamen
        broadcastLeaderboard(true);
        
        // Berikan hadiah kepada pemenang
        distributeRewards();
        
        // Reset turnamen aktif
        activeTournament = null;
        entries.clear();
    }
    
    /**
     * Batalkan turnamen yang sedang berjalan
     */
    public void cancelTournament() {
        if (activeTournament == null) return;
        
        // Batalkan task update leaderboard
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        
        // Batalkan task penghentian turnamen
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        
        // Umumkan pembatalan turnamen
        Bukkit.broadcastMessage("§c§l[TURNAMEN] §fTurnamen §b" + activeTournament.getName() + " §fdibatalkan!");
        
        // Reset turnamen aktif
        activeTournament = null;
        entries.clear();
    }
    
    /**
     * Cek dan catat ikan yang ditangkap untuk turnamen
     * @param player Pemain yang menangkap ikan
     * @param fish Ikan yang ditangkap
     */
    public void recordCatch(Player player, CustomFish fish) {
        if (activeTournament == null) return;
        
        UUID playerId = player.getUniqueId();
        TournamentEntry entry = entries.computeIfAbsent(playerId, id -> new TournamentEntry(player.getName(), id));
        
        // Update entry berdasarkan jenis turnamen
        switch (activeTournament.getType()) {
            case MOST_FISH:
                entry.incrementFishCount();
                break;
                
            case HEAVIEST_FISH:
                if (fish.getWeight() > entry.getHeaviestFishWeight()) {
                    entry.setHeaviestFish(fish);
                }
                break;
                
            case HIGHEST_QUALITY:
                if (fish.getQuality() > entry.getHighestQualityFishQuality()) {
                    entry.setHighestQualityFish(fish);
                }
                break;
                
            case TOTAL_WEIGHT:
                entry.addToTotalWeight(fish.getWeight());
                entry.incrementFishCount();
                break;
                
            case SPECIFIC_SPECIES:
                if (activeTournament.getTargetSpecies().contains(fish.getId())) {
                    entry.incrementFishCount();
                }
                break;
        }
        
        // Pesan konfirmasi ke pemain
        sendTournamentEntryMessage(player, fish);
    }
    
    /**
     * Kirim pesan ke pemain tentang entri turnamen mereka
     * @param player Pemain
     * @param fish Ikan yang ditangkap
     */
    private void sendTournamentEntryMessage(Player player, CustomFish fish) {
        if (activeTournament == null) return;
        
        UUID playerId = player.getUniqueId();
        TournamentEntry entry = entries.get(playerId);
        if (entry == null) return;
        
        String message = "";
        switch (activeTournament.getType()) {
            case MOST_FISH:
                message = "§e§l[TURNAMEN] §fTotal ikan tertangkap: §b" + entry.getFishCount();
                break;
                
            case HEAVIEST_FISH:
                message = "§e§l[TURNAMEN] §fIkan terberat: §b" + 
                        entry.getHeaviestFishName() + " §f(" + 
                        String.format("%.2f", entry.getHeaviestFishWeight()) + " kg)";
                break;
                
            case HIGHEST_QUALITY:
                message = "§e§l[TURNAMEN] §fIkan kualitas tertinggi: §b" + 
                        entry.getHighestQualityFishName() + " §f(Kualitas: §b" + 
                        entry.getHighestQualityFishQuality() + "§f)";
                break;
                
            case TOTAL_WEIGHT:
                message = "§e§l[TURNAMEN] §fTotal berat ikan: §b" + 
                        String.format("%.2f", entry.getTotalWeight()) + " kg §f(Jumlah: §b" + 
                        entry.getFishCount() + "§f)";
                break;
                
            case SPECIFIC_SPECIES:
                if (activeTournament.getTargetSpecies().contains(fish.getId())) {
                    message = "§e§l[TURNAMEN] §fIkan target tertangkap: §b" + 
                            fish.getName() + " §f(Total: §b" + entry.getFishCount() + "§f)";
                } else {
                    // Bukan ikan target, jangan kirim pesan
                    return;
                }
                break;
        }
        
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
    }
    
    /**
     * Distribusikan hadiah ke pemenang turnamen
     */
    private void distributeRewards() {
        if (activeTournament == null || entries.isEmpty()) return;
        
        // Dapatkan leaderboard
        List<TournamentEntry> leaderboard = getLeaderboard();
        
        // Daftar hadiah dan penerimanya
        StringBuilder rewardSummary = new StringBuilder();
        rewardSummary.append("\n§8§l§m------------------------------§r\n");
        rewardSummary.append("§e§l✦ HADIAH TURNAMEN ✦\n");
        
        // Berikan hadiah berdasarkan posisi
        int position = 1;
        for (TournamentEntry entry : leaderboard) {
            if (position > 3) break; // Hanya berikan hadiah untuk 3 teratas
            
            String placeName;
            switch (position) {
                case 1: placeName = "first_place"; break;
                case 2: placeName = "second_place"; break;
                case 3: placeName = "third_place"; break;
                default: placeName = "other"; break;
            }
            
            // Dapatkan hadiah untuk posisi ini
            double money = activeTournament.getRewardMoney(placeName);
            int exp = activeTournament.getRewardExp(placeName);
            List<String> commands = activeTournament.getRewardCommands(placeName);
            
            // Pemain tersebut mungkin offline, jadi gunakan Bukkit.getOfflinePlayer
            Player player = Bukkit.getPlayer(entry.getPlayerId());

            // Tambahkan uang
            double pendingMoney = 0.0;
            if (money > 0 && plugin.getEconomyManager().isEconomyEnabled()) {
                if (player != null) {
                    plugin.getEconomyManager().depositMoney(player, money);
                } else if (!plugin.getEconomyManager().depositMoney(Bukkit.getOfflinePlayer(entry.getPlayerId()), money)) {
                    pendingMoney = money;
                }
            }

            // Tambahkan ke ringkasan hadiah
            rewardSummary.append("§6" + position + ". §f" + entry.getPlayerName() +
                    (money > 0 ? " §7- §a" + plugin.getEconomyManager().formatMoney(money) : "") + "\n");

            // Tambahkan exp
            if (exp > 0 && player != null) {
                plugin.getSkillManager().addFishingExp(player, exp);
                if (plugin.getAuraSkillsHook() != null) {
                    plugin.getAuraSkillsHook().addXp(player, exp);
                }
            }

            // Jalankan perintah khusus untuk pemain online
            if (commands != null && !commands.isEmpty() && player != null) {
                for (String cmd : commands) {
                    // Ganti placeholder dengan nama pemain
                    String finalCmd = cmd.replace("%player%", entry.getPlayerName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                }
            }

            // Jika pemain offline, simpan ke antrian hadiah tertunda
            if (player == null && (exp > 0 || pendingMoney > 0 || (commands != null && !commands.isEmpty()))) {
                queueOfflineReward(entry.getPlayerId(), entry.getPlayerName(), exp, commands, pendingMoney);
            }
            
            position++;
        }
        
        rewardSummary.append("§8§l§m------------------------------§r");
        
        // Broadcast reward summary
        Bukkit.broadcastMessage(rewardSummary.toString());
    }
    
    /**
     * Dapatkan leaderboard untuk turnamen yang sedang berjalan
     * @return List peserta yang diurutkan berdasarkan peringkat
     */
    public List<TournamentEntry> getLeaderboard() {
        if (activeTournament == null) return new ArrayList<>();
        
        // Sort entries berdasarkan jenis turnamen
        return entries.values().stream()
                .sorted((e1, e2) -> {
                    switch (activeTournament.getType()) {
                        case MOST_FISH:
                        case SPECIFIC_SPECIES:
                            return Integer.compare(e2.getFishCount(), e1.getFishCount()); // Descending
                            
                        case HEAVIEST_FISH:
                            return Double.compare(e2.getHeaviestFishWeight(), e1.getHeaviestFishWeight()); // Descending
                            
                        case HIGHEST_QUALITY:
                            return Integer.compare(e2.getHighestQualityFishQuality(), e1.getHighestQualityFishQuality()); // Descending
                            
                        case TOTAL_WEIGHT:
                            return Double.compare(e2.getTotalWeight(), e1.getTotalWeight()); // Descending
                            
                        default:
                            return 0;
                    }
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Broadcast pesan tentang dimulainya turnamen
     */
    private void broadcastTournamentStart() {
        if (activeTournament == null) return;
        
        // Title untuk semua pemain
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    "§e§l✦ TURNAMEN MEMANCING ✦",
                    "§f" + activeTournament.getName(),
                    10, 70, 20);
            
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        
        // Pesan di chat dengan format fancy
        String message = "\n§8§l§m------------------------------§r\n" +
                "§e§l✦ TURNAMEN MEMANCING DIMULAI ✦\n" +
                "§f" + activeTournament.getName() + "\n" +
                "§7" + activeTournament.getDescription() + "\n" +
                "§fMode: §b" + formatTournamentType(activeTournament.getType()) + "\n" +
                "§fDurasi: §b" + activeTournament.getDurationMinutes() + " menit\n" +
                "§fBerakhir: §b" + TimeUtils.formatTime(activeTournament.getDurationMinutes() * 60) + "\n" +
                "§8§l§m------------------------------§r";
        
        Bukkit.broadcastMessage(message);
        
        // Tambahkan pesan klik untuk bergabung (tutorial)
        TextComponent joinMessage = new TextComponent("§a§l[KLIK DI SINI] §funtuk mengatur posisi peringkat Anda!");
        joinMessage.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nf tournament join"));
        joinMessage.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                new ComponentBuilder("§eBergabung dengan turnamen").create()));
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(joinMessage);
        }
    }
    
    /**
     * Broadcast leaderboard turnamen
     * @param isFinal Apakah ini adalah leaderboard akhir
     */
    public void broadcastLeaderboard(boolean isFinal) {
        if (activeTournament == null) return;
        
        List<TournamentEntry> leaderboard = getLeaderboard();
        if (leaderboard.isEmpty()) {
            if (isFinal) {
                Bukkit.broadcastMessage("§e§l[TURNAMEN SELESAI] §fTidak ada peserta yang berpartisipasi dalam turnamen.");
            }
            return;
        }
        
        // Headline untuk leaderboard
        String headerTitle = isFinal ? "§e§l✦ HASIL AKHIR TURNAMEN ✦" : "§e§l✦ PERINGKAT SEMENTARA ✦";
        String message = "\n§8§l§m------------------------------§r\n" +
                headerTitle + "\n" +
                "§f" + activeTournament.getName() + " - §b" + formatTournamentType(activeTournament.getType()) + "\n";
        
        // Tambahkan entries dari leaderboard
        int position = 1;
        for (TournamentEntry entry : leaderboard) {
            if (position > 10) break; // Hanya tampilkan 10 teratas
            
            String rankPrefix;
            if (position == 1) {
                rankPrefix = "§e§l1. ";
            } else if (position == 2) {
                rankPrefix = "§7§l2. ";
            } else if (position == 3) {
                rankPrefix = "§6§l3. ";
            } else {
                rankPrefix = "§f" + position + ". ";
            }
            
            // Format entry berdasarkan jenis turnamen
            String entryDetails;
            switch (activeTournament.getType()) {
                case MOST_FISH:
                case SPECIFIC_SPECIES:
                    entryDetails = "§b" + entry.getFishCount() + " ikan";
                    break;
                    
                case HEAVIEST_FISH:
                    entryDetails = "§b" + String.format("%.2f", entry.getHeaviestFishWeight()) + " kg §7(" + entry.getHeaviestFishName() + ")";
                    break;
                    
                case HIGHEST_QUALITY:
                    entryDetails = "§bKualitas " + entry.getHighestQualityFishQuality() + " §7(" + entry.getHighestQualityFishName() + ")";
                    break;
                    
                case TOTAL_WEIGHT:
                    entryDetails = "§b" + String.format("%.2f", entry.getTotalWeight()) + " kg §7(" + entry.getFishCount() + " ikan)";
                    break;
                    
                default:
                    entryDetails = "";
                    break;
            }
            
            message += rankPrefix + "§f" + entry.getPlayerName() + " §7- " + entryDetails + "\n";
            position++;
        }
        
        // Tampilkan waktu tersisa jika bukan leaderboard akhir
        if (!isFinal) {
            long timeRemaining = (activeTournament.getEndTime() - System.currentTimeMillis()) / 1000; // dalam detik
            message += "§fWaktu tersisa: §b" + TimeUtils.formatTime((int)timeRemaining) + "\n";
        }
        
        message += "§8§l§m------------------------------§r";
        
        // Broadcast leaderboard
        Bukkit.broadcastMessage(message);
        
        // Play sound untuk semua pemain
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), isFinal ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
        }
    }
    
    /**
     * Format jenis turnamen untuk ditampilkan
     * @param type Jenis turnamen
     * @return Nama tipe yang diformat
     */
    private String formatTournamentType(TournamentType type) {
        switch (type) {
            case MOST_FISH:
                return "Ikan Terbanyak";
            case HEAVIEST_FISH:
                return "Ikan Terberat";
            case HIGHEST_QUALITY:
                return "Kualitas Tertinggi";
            case TOTAL_WEIGHT:
                return "Berat Total Terbanyak";
            case SPECIFIC_SPECIES:
                return "Spesies Tertentu";
            default:
                return type.toString();
        }
    }
    
    /**
     * Metode untuk pemain bergabung ke turnamen (tidak wajib, hanya untuk tracking)
     * @param player Pemain yang bergabung
     * @return true jika berhasil bergabung
     */
    public boolean joinTournament(Player player) {
        if (activeTournament == null) {
            player.sendMessage("§c§l[TURNAMEN] §fTidak ada turnamen yang sedang berlangsung.");
            return false;
        }
        
        UUID playerId = player.getUniqueId();
        if (entries.containsKey(playerId)) {
            player.sendMessage("§c§l[TURNAMEN] §fAnda sudah bergabung dalam turnamen ini.");
            return false;
        }
        
        // Tambahkan pemain ke daftar peserta
        entries.put(playerId, new TournamentEntry(player.getName(), playerId));
        
        // Beri informasi ke pemain
        player.sendMessage("§e§l[TURNAMEN] §fAnda telah bergabung dalam turnamen §b" + 
                activeTournament.getName() + "§f! Mulailah memancing untuk berkompetisi.");
        
        switch (activeTournament.getType()) {
            case MOST_FISH:
                player.sendMessage("§e§l[TURNAMEN] §fMode: §bIkan Terbanyak §f- Tangkap sebanyak mungkin ikan!");
                break;
            case HEAVIEST_FISH:
                player.sendMessage("§e§l[TURNAMEN] §fMode: §bIkan Terberat §f- Tangkap ikan dengan berat terbesar!");
                break;
            case HIGHEST_QUALITY:
                player.sendMessage("§e§l[TURNAMEN] §fMode: §bKualitas Tertinggi §f- Tangkap ikan dengan kualitas terbaik!");
                break;
            case TOTAL_WEIGHT:
                player.sendMessage("§e§l[TURNAMEN] §fMode: §bBerat Total §f- Tangkap ikan dengan total berat terbanyak!");
                break;
            case SPECIFIC_SPECIES:
                player.sendMessage("§e§l[TURNAMEN] §fMode: §bSpesies Tertentu §f- Tangkap ikan dari jenis tertentu!");
                break;
        }
        
        return true;
    }
    
    /**
     * Cek apakah ada turnamen yang sedang berlangsung
     * @return true jika ada turnamen aktif
     */
    public boolean isTournamentActive() {
        return activeTournament != null;
    }
    
    /**
     * Dapatkan turnamen yang sedang berlangsung
     * @return Tournament yang aktif, atau null jika tidak ada
     */
    public Tournament getActiveTournament() {
        return activeTournament;
    }
    
    /**
     * Dapatkan daftar semua turnamen terjadwal
     * @return Map dari ID turnamen ke objek Tournament
     */
    public Map<String, Tournament> getScheduledTournaments() {
        return new HashMap<>(scheduledTournaments);
    }
    
    /**
     * Lakukan cleanup saat plugin dinonaktifkan
     */
    public void shutdown() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        
        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        
        // Simpan turnamen yang aktif ke file temporary jika perlu
        if (activeTournament != null) {
            endTournament();
        }
    }
}