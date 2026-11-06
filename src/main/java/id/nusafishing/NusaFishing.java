package id.nusafishing;

import java.util.logging.Logger;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import id.nusafishing.commands.NusaFishingCommand;
import id.nusafishing.config.ConfigManager;
import id.nusafishing.economy.EconomyManager;
import id.nusafishing.fishing.FishingListener;
import id.nusafishing.fishing.FishingManager;
import id.nusafishing.fishing.data.PlayerSkillManager;
import id.nusafishing.fishing.data.StaminaManager;
import id.nusafishing.util.ItemBuilder;
import id.nusafishing.gui.GUIListener;
import id.nusafishing.event.FishingEventManager;
import id.nusafishing.event.ConfigEventFish;
import id.nusafishing.placeholder.NusaFishingPlaceholders;
import id.nusafishing.fishing.data.FishingStatisticsManager;
import id.nusafishing.tournament.TournamentManager;
import id.nusafishing.managers.RewardsManager;
import id.nusafishing.managers.GiveManager;
import id.nusafishing.integration.item.ItemProviderManager;
import id.nusafishing.integration.auraskills.AuraSkillsHook;
import id.nusafishing.integration.season.SeasonHook;
import id.nusafishing.integration.season.RealisticSeasonsHook;
import id.nusafishing.integration.npc.FancyNpcsHook;
import id.nusafishing.fisch.inventory.VirtualRodManager;
import id.nusafishing.fisch.inventory.VirtualRodListener;
import id.nusafishing.fisch.environment.FischEnvironmentManager;
import id.nusafishing.fisch.registry.MutationRegistry;
import id.nusafishing.fisch.registry.TotemRegistry;
import id.nusafishing.fisch.registry.EnchantRegistry;
import id.nusafishing.fisch.registry.MissionRegistry;
import id.nusafishing.fisch.mission.FischMissionManager;
import id.nusafishing.fishing.afk.AfkFishingManager;

public class NusaFishing extends JavaPlugin {
    private static final Logger LOGGER = Logger.getLogger("nusafishing");
    
    private static NusaFishing instance;
    
    private ItemProviderManager itemProviderManager;
    private AuraSkillsHook auraSkillsHook;
    private SeasonHook seasonHook;
    private FancyNpcsHook fancyNpcsHook;
    private VirtualRodManager virtualRodManager;
    private FischEnvironmentManager fischEnvironmentManager;
    private MutationRegistry mutationRegistry;
    private TotemRegistry totemRegistry;
    private EnchantRegistry enchantRegistry;
    private MissionRegistry missionRegistry;
    private FischMissionManager fischMissionManager;
    private AfkFishingManager afkFishingManager;
    private ConfigManager configManager;
    private StaminaManager staminaManager;
    private PlayerSkillManager skillManager;
    private FishingManager fishingManager;
    private EconomyManager economyManager;
    private FishingEventManager eventManager;
    private FishingStatisticsManager statisticsManager;
    private TournamentManager tournamentManager;
    private RewardsManager rewardsManager;
    private GiveManager giveManager;
    private FishingListener fishingListener;
    
    private BukkitTask priceUpdateTask;
    
    @Override
    public void onEnable() {
        // Set instance for static access
        instance = this;
        
        // Save default config
        saveDefaultConfig();
        
        // Initialize item provider manager for texture support (Nexo, Oraxen, ItemsAdder, Vanilla)
        itemProviderManager = new ItemProviderManager(this);

        // Initialize Fisch Registries (Data-driven: Mutations, Totems, Enchants, Missions)
        this.mutationRegistry = new MutationRegistry();
        this.totemRegistry = new TotemRegistry();
        this.enchantRegistry = new EnchantRegistry();
        this.missionRegistry = new MissionRegistry();
        this.fischMissionManager = new FischMissionManager(this);
        
        // Initialize managers
        configManager = new ConfigManager(this);
        
        // Load event fish from config
        ConfigEventFish.loadFromConfig(this);
        
        // Initialize statistics manager
        statisticsManager = new FishingStatisticsManager(this);
        
        staminaManager = new StaminaManager(this);
        skillManager = new PlayerSkillManager(this);
        
        // Initialize fishing manager after config is loaded
        fishingManager = new FishingManager(this, configManager);
        
        // Initialize economy manager after config is loaded
        economyManager = new EconomyManager(this);
        
        // Initialize event manager after fishing manager
        eventManager = new FishingEventManager(this);
        
        // Initialize tournament manager
        tournamentManager = new TournamentManager(this);
        getServer().getPluginManager().registerEvents(tournamentManager, this);
        
        // Initialize rewards manager
        this.rewardsManager = new RewardsManager(this);

        // Initialize give manager
        this.giveManager = new GiveManager(this);

        // Initialize AuraSkills integration
        this.auraSkillsHook = new AuraSkillsHook(this);

        // Initialize Season integration (RealisticSeasons with internal fallback)
        this.seasonHook = new RealisticSeasonsHook(this);

        // Initialize Fisch Global Environment Manager (Weather, Seasons, Active Totems, lightweight scheduler)
        this.fischEnvironmentManager = new FischEnvironmentManager(this);

        // Initialize Virtual Rod Manager & register its high-security listener
        this.virtualRodManager = new VirtualRodManager(this);
        getServer().getPluginManager().registerEvents(new VirtualRodListener(this, virtualRodManager), this);

        // Initialize FancyNpcs integration (Optional, disabled by default)
        this.fancyNpcsHook = new FancyNpcsHook(this);

        // Initialize AFK fishing manager and register its listener
        this.afkFishingManager = new AfkFishingManager(this);
        getServer().getPluginManager().registerEvents(afkFishingManager, this);

        // Register listeners
        this.fishingListener = new FishingListener(this, fishingManager, staminaManager, skillManager);
        getServer().getPluginManager().registerEvents(fishingListener, this);
        
        // Register GUI listener
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        
        // Register commands
        NusaFishingCommand commandExecutor = new NusaFishingCommand(this, fishingManager);
        getCommand("nusafishing").setExecutor(commandExecutor);
        getCommand("nusafishing").setTabCompleter(commandExecutor);
        
        // Register PlaceholderAPI expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new NusaFishingPlaceholders(this).register();
            getLogger().info("PlaceholderAPI ditemukan - placeholder NusaFishing terdaftar!");
        }
        
        // Schedule price regeneration task if economy is enabled (runs every 15 minutes)
        if (economyManager.isEconomyEnabled()) {
            priceUpdateTask = getServer().getScheduler().runTaskTimer(this, 
                () -> economyManager.regeneratePrices(), 
                20 * 60 * 15, // Initial delay: 15 minutes
                20 * 60 * 15  // Run every 15 minutes
            );
        }
        
        LOGGER.info("NusaFishing v" + getDescription().getVersion() + " enabled!");
    }
    
    @Override
    public void onDisable() {
        // Save player data
        if (skillManager != null) {
            skillManager.saveAllData();
        }
        
        // Save statistics and cancel tasks before shutdown
        if (statisticsManager != null) {
            statisticsManager.shutdown();
        }
        
        // Clean up resources
        if (staminaManager != null) {
            staminaManager.shutdown();
        }
        
        // Cancel scheduled tasks
        if (priceUpdateTask != null) {
            priceUpdateTask.cancel();
        }
        
        // Shutdown event manager
        if (eventManager != null) {
            eventManager.shutdown();
        }
        
        // Shutdown tournament manager
        if (tournamentManager != null) {
            tournamentManager.shutdown();
        }

        // Cleanup AFK fishing manager
        if (afkFishingManager != null) {
            afkFishingManager.cleanup();
        }

        // Close all open GUIs and clean leaked GUI items
        id.nusafishing.gui.GUI.closeAll();

        // Shutdown Fisch environment manager
        if (fischEnvironmentManager != null) {
            fischEnvironmentManager.shutdown();
        }

        // Save virtual rods data synchronously to disk
        if (virtualRodManager != null) {
            virtualRodManager.saveAll(false);
        }

        // Save mission data synchronously to disk
        if (fischMissionManager != null) {
            fischMissionManager.saveNow();
        }

        LOGGER.info("NusaFishing disabled");
    }
    
    // Static getter for plugin instance
    public static NusaFishing getInstance() {
        return instance;
    }
    
    public ItemProviderManager getItemProviderManager() {
        return itemProviderManager;
    }
    
    // Getter methods for managers
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public StaminaManager getStaminaManager() {
        return staminaManager;
    }
    
    public PlayerSkillManager getSkillManager() {
        return skillManager;
    }
    
    public FishingManager getFishingManager() {
        return fishingManager;
    }
    
    public EconomyManager getEconomyManager() {
        return economyManager;
    }
    
    public FishingEventManager getEventManager() {
        return eventManager;
    }
    
    public FishingStatisticsManager getStatisticsManager() {
        return statisticsManager;
    }
    
    public TournamentManager getTournamentManager() {
        return tournamentManager;
    }
    
    public RewardsManager getRewardsManager() {
        return rewardsManager;
    }

    public GiveManager getGiveManager() {
        return giveManager;
    }

    public AuraSkillsHook getAuraSkillsHook() {
        return auraSkillsHook;
    }

    public SeasonHook getSeasonHook() {
        return seasonHook;
    }

    public FancyNpcsHook getFancyNpcsHook() {
        return fancyNpcsHook;
    }

    public VirtualRodManager getVirtualRodManager() {
        return virtualRodManager;
    }

    public FischEnvironmentManager getFischEnvironmentManager() {
        return fischEnvironmentManager;
    }

    public MutationRegistry getMutationRegistry() {
        return mutationRegistry;
    }

    public TotemRegistry getTotemRegistry() {
        return totemRegistry;
    }

    public EnchantRegistry getEnchantRegistry() {
        return enchantRegistry;
    }

    public MissionRegistry getMissionRegistry() {
        return missionRegistry;
    }

    public FischMissionManager getFischMissionManager() {
        return fischMissionManager;
    }

    public AfkFishingManager getAfkFishingManager() {
        return afkFishingManager;
    }

    public FishingListener getFishingListener() {
        return fishingListener;
    }
    
    public void reloadAllConfigurations() {
        // Reload main config
        reloadConfig();
        
        // Reload fishing config
        configManager.reloadConfigs();
        
        // Reload event fish from config
        ConfigEventFish.loadFromConfig(this);

        // Reload stamina manager config
        if (staminaManager != null) {
            staminaManager.loadConfig();
        }

        // Recheck AuraSkills availability
        if (auraSkillsHook != null) {
            auraSkillsHook.checkAvailability();
        }

        // Recheck Season hook availability
        if (seasonHook instanceof RealisticSeasonsHook) {
            ((RealisticSeasonsHook) seasonHook).checkAvailability();
        }

        // Recheck FancyNpcs availability & sync NPCs
        if (fancyNpcsHook != null) {
            fancyNpcsHook.checkAvailability();
        }
        
        // Reload dependent systems
        fishingManager.reloadConfig();
        
        // Reschedule price update task if needed
        if (priceUpdateTask != null) {
            priceUpdateTask.cancel();
            priceUpdateTask = null;
        }
        if (economyManager != null && economyManager.isEconomyEnabled()) {
            priceUpdateTask = getServer().getScheduler().runTaskTimer(this, 
                () -> economyManager.regeneratePrices(), 
                20 * 60 * 15,
                20 * 60 * 15
            );
        }

        getLogger().info("All configurations reloaded successfully!");
    }
}
