# NusaFishing

<div align="center">

![Java](https://img.shields.io/badge/Java-21%20%7C%2025%20(MR--JAR)-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Paper](https://img.shields.io/badge/Platform-Paper%20%2F%20Purpur%201.21.x+-1E88E5?style=for-the-badge&logo=minecraft&logoColor=white)
![Release](https://img.shields.io/badge/Release-v1.5%2BAlpha-00C853?style=for-the-badge)
![License](https://img.shields.io/badge/License-GNU%20GPL%20v2-blue?style=for-the-badge)

**An enterprise-grade, RPG-styled fishing system engineered for modern Spigot, Paper, and Purpur Minecraft servers.**

[Features](#-features) • [Installation](#-installation) • [Texture Providers](#-custom-texture-providers) • [Mechanics](#-gameplay-mechanics) • [Commands](#-commands--permissions) • [Placeholders](#-placeholderapi) • [Developer Guide](#-developer--build-guide)

</div>

---

## 📖 Overview

**NusaFishing** transforms vanilla Minecraft fishing into an immersive, competitive RPG experience. Featuring procedural fish weights and qualities, left-click strike minigames, custom fishing rods and specialty baits, dynamic market saturation, server-wide mystic events, automated tournaments, and seamless multi-engine custom textures (**Nexo**, **Oraxen**, and **ItemsAdder**), NusaFishing is built from the ground up for high-performance servers.

---

## ✨ Features

- 🎨 **Pluggable Custom Texture Providers**: Native integration with **Nexo**, **Oraxen**, and **ItemsAdder** with automatic, zero-crash fallback to vanilla materials and custom model data.
- ⚡ **AuraSkills Integration**: Seamless connection to [AuraSkills](https://wiki.aurelium.dev/auraskills/api) awarding fishing skill experience on custom and vanilla catches, togglable and balanced per tier in `config.yml`.
- 🤖 **Balanced AFK Auto-Fishing (`/nf afk`)**: Built-in automated fishing with configurable `luck-nerf: <angka>%` and `speed-nerf: <angka>%`, complete with anti-exploit distance and stamina checks.
- 🔄 **Self-Updating Configuration Engine**: Automatically detects plugin updates, synchronizes config versions, and provisions missing configuration sections with defaults without wiping user settings.
- ☕ **Java Multi-Release Architecture (MR-JAR)**: Single universal binary (`nusafishing-<version>.jar`) compiling to Java 21 baseline for 1.21.x and Java 25 bytecode for Paper 26.2+.
- 🐟 **Procedural Catches**: Caught fish feature procedural weights (from grams to tons), quality ratings (1–100), and dynamic tier elevations.
- 🎣 **Strike Minigame**: Interactive left-click mini-game upon fish bites with real-time title progress bars and stamina consumption.
- 📈 **Dynamic Supply & Demand Economy**: Market saturation system penalizes automated fish farming with dynamic price drops, while automated cycles restore prices over time (Vault economy supported).
- 🏆 **Automated Tournaments & Events**: Timed community fishing tournaments (heaviest catch, most fish, specific species) with configurable commands, exp, and cash rewards.
- 🔮 **Mystic Fish Collector**: Special collector NPC GUI offering premium pricing, combo streaks, and randomized bonus loot for rare specimens.
- 📊 **PlaceholderAPI Expansion**: Full placeholder support for leaderboards, player stats, levels, and tournament standings.

---

## ⚙️ Platform & Compatibility

| Component | Minimum Requirement | Recommended |
|---|---|---|
| **Minecraft Server** | Paper / Purpur 1.21.0 | Paper / Purpur 1.21.4+ or 26.2+ |
| **Java Runtime** | Java 21 | Java 21 or Java 25 |
| **Economy** | Optional | [Vault](https://www.spigotmc.org/resources/vault.34315/) + Economy plugin |
| **Textures** | Vanilla Resource Pack | [Nexo](https://nexomc.com) / [Oraxen](https://oraxen.com) / [ItemsAdder](https://itemsadder.dev) |
| **Placeholders** | Optional | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) |

### Java Multi-Release JAR (MR-JAR)
NusaFishing distributes a single JAR leveraging JEP 238:
- **Java 21 Baseline**: Standard classes targeted for Java 21 bytecode (version 65.0) ensuring compatibility with standard 1.21.x environments.
- **Java 25 Optimizations**: Optimized implementations located under `META-INF/versions/25/` (bytecode version 69.0) loaded automatically on Java 25 runtimes.

---

## 🚀 Installation

1. **Download**: Grab the latest release `nusafishing-<version>.jar` from the [GitHub Releases](https://github.com/kaito65452/nusafishing/releases).
2. **Deploy**: Place the JAR file into your server's `plugins/` directory.
3. **Optional Dependencies**: Install Vault and your custom item plugin (Nexo, Oraxen, or ItemsAdder) if desired.
4. **Restart**: Start or restart your server to generate configuration files:
   - `plugins/nusafishing/config.yml` (Main settings, events, rewards)
   - `plugins/nusafishing/fishing.yml` (Rods, baits, fish species, special locations)
   - `plugins/nusafishing/rewards.yml` (Tournament and event rewards)

---

## 🎨 Custom Texture Providers

NusaFishing abstracts custom item generation through a clean provider pipeline. Set `texture_provider` and `texture_id` on any fish or fishing rod in `fishing.yml`.

### Supported Engines

| Provider | Configuration Key | Identifier Format | Readiness Event |
|---|---|---|---|
| **Vanilla** | `vanilla` | *(None - uses `custom_model_data`)* | Instant |
| **Nexo** | `nexo` | `item_id` (e.g. `carbon_rod`) | `NexoItemsLoadedEvent` |
| **Oraxen** | `oraxen` | `item_id` (e.g. `mythic_rod`) | `OraxenItemsLoadedEvent` |
| **ItemsAdder** | `itemsadder` | `namespace:item_id` (e.g. `nusafishing:poseidon_rod`) | `ItemsAdderLoadDataEvent` |

### Configuration Examples (`fishing.yml`)

#### Custom Fishing Rods

```yaml
fishing_rods:
  # Nexo Fishing Rod
  nexo_carbon_rod:
    name: "§b§lPancingan Carbon Nexo"
    description: "Pancingan serat karbon berbobot ringan dari Nexo."
    texture_provider: nexo
    texture_id: "carbon_rod"
    material: FISHING_ROD       # Fallback jika Nexo tidak aktif
    custom_model_data: 1010     # Fallback CMD
    durability: 400
    stamina_modifier: 0.45
    tier: ADVANCED
    special_ability: true
    ability_description: "15% kesempatan mendapat ikan tier lebih tinggi"
    price: 35000
    lure_level: 3
    max_weight_capacity: 750.0

  # Oraxen Fishing Rod
  oraxen_mythic_rod:
    name: "§5§lPancingan Mistik Oraxen"
    description: "Pancingan bertuah dari kedalaman Oraxen."
    texture_provider: oraxen
    texture_id: "mythic_rod"
    material: FISHING_ROD       # Fallback jika Oraxen tidak aktif
    custom_model_data: 1020     # Fallback CMD
    durability: 600
    stamina_modifier: 0.35
    tier: EXPERT
    price: 90000
    lure_level: 4
    max_weight_capacity: 3500.0

  # ItemsAdder Fishing Rod
  ia_poseidon_rod:
    name: "§6§lPancingan Poseidon ItemsAdder"
    description: "Pancingan dewa laut legendaris dari ItemsAdder."
    texture_provider: itemsadder
    texture_id: "nusafishing:poseidon_rod"
    material: FISHING_ROD       # Fallback jika ItemsAdder tidak aktif
    custom_model_data: 1030     # Fallback CMD
    durability: 1200
    stamina_modifier: 0.25
    tier: MASTER
    price: 300000
    lure_level: 5
    max_weight_capacity: 15000.0
```

#### Custom Fish Species

```yaml
fishes:
  # Nexo Custom Item
  nexo_koi:
    name: "Koi Emas Nexo"
    description: "Ikan hias keberuntungan dengan corak emas kemilau."
    texture_provider: nexo
    texture_id: "koi_fish"
    material: COD
    custom_model_data: 3010
    min_weight: 1.0
    max_weight: 15.0
    min_quality: 30
    max_quality: 95
    base_price: 35.0
    tier: UNCOMMON

  # Oraxen Custom Item
  oraxen_bass:
    name: "Bass Mistis Oraxen"
    description: "Ikan predator air tawar dengan sisik bercahaya."
    texture_provider: oraxen
    texture_id: "mystic_bass"
    material: SALMON
    custom_model_data: 3020
    min_weight: 5.0
    max_weight: 40.0
    min_quality: 40
    max_quality: 100
    base_price: 75.0
    tier: RARE

  # ItemsAdder Custom Item
  ia_abyssal_trout:
    name: "Trout Palung ItemsAdder"
    description: "Ikan bercahaya dari palung laut terdalam."
    texture_provider: itemsadder
    texture_id: "nusafishing:abyssal_trout"
    material: PUFFERFISH
    custom_model_data: 3030
    min_weight: 15.0
    max_weight: 120.0
    min_quality: 60
    max_quality: 100
    base_price: 150.0
    tier: EPIC

  # Vanilla Custom Model Data
  vanilla_salmon:
    name: "Salmon Sungai"
    description: "Salmon liar segar dengan daging lembut berkualitas."
    texture_provider: vanilla
    material: SALMON
    custom_model_data: 3001
    min_weight: 2.0
    max_weight: 10.0
    min_quality: 20
    max_quality: 90
    base_price: 25.0
    tier: COMMON
```

> [!TIP]
> If a texture plugin is uninstalled, reloaded, or missing, NusaFishing automatically falls back to `material` and `custom_model_data` without losing item data or crashing.

---

## 🎮 Gameplay Mechanics

### 1. Fish Tiers & Value Multipliers

| Tier | Default Color | Price Multiplier | Description |
|---|---|---|---|
| **COMMON** | Gray (`§7`) | `1.0x` | Standard fish found commonly in all bodies of water. |
| **UNCOMMON** | Green (`§a`) | `1.5x` | Healthier catches with moderate market value. |
| **RARE** | Blue (`§9`) | `2.0x` | Elusive species found with good bait or advanced rods. |
| **EPIC** | Purple (`§5`) | `3.0x` | Heavy, prize-worthy specimens. |
| **LEGENDARY** | Gold (`§6`) | `5.0x` | Legendary catches capable of triggering server notifications. |
| **MYTHIC** | Red (`§c`) | `8.0x` | Ancient entities and sea deities with guaranteed 3x minimum base value. |

> [!NOTE]
> All tier colors for both fish and rods are fully customizable in `config.yml` under the `tier_colors:` section (supporting standard Minecraft color codes like `&7`, `&a`, `&c`, etc.).

### 2. Procedural Weight & Diminishing Scaling
Fish weights scale dynamically:
- **< 10 kg**: Linear scaling based on size.
- **10 kg – 100 kg**: Logarithmic scaling with diminishing returns.
- **> 100 kg**: Square-root formula preventing hyper-inflated economy values while rewarding exceptional catches.

### 3. Left-Click Strike Minigame
- When a fish bites, an interactive minigame initiates.
- Players left-click to fill the progress bar before the 10-second timer expires.
- If the player opens their inventory or line snaps, the fish escapes.
- Full inventories automatically drop caught fish naturally on the ground.

### 4. Dynamic Economy & Market Saturation
- **Flooding the Market**: Selling large quantities of the same species lowers its purchase price (down to 30% of base price).
- **Price Recovery**: An automatic 15-minute background task slowly restores prices as market saturation clears.
- **Rod Repair**: Players can repair damaged custom rods using `/nf repair`, with costs calculated dynamically based on lost durability and rod tier.

### 5. AuraSkills Integration
NusaFishing connects natively with [AuraSkills](https://wiki.aurelium.dev/auraskills/api) to reward players with fishing skill experience:
```yaml
auraskills:
  enabled: true
  base_xp: 15.0
  tier_xp_multipliers:
    COMMON: 1.0
    UNCOMMON: 1.5
    RARE: 2.5
    EPIC: 4.0
    LEGENDARY: 7.0
    MYTHIC: 12.0
```

### 6. AFK Auto-Fishing System (`/nf afk`)
Players can toggle automated fishing with `/nf afk`. To preserve economic equilibrium and avoid unfair advantages:
- **`luck-nerf: <angka>%`**: Reduces higher-tier fish roll weights and diminishes quality/weight boosts.
- **`speed-nerf: <angka>%`**: Extends both the bite wait time and the automated reel-in duration.
- **Safety Measures**: Auto-fishing immediately halts if the player runs out of stamina, breaks their rod, or moves more than 5 blocks from their initial cast location.

```yaml
afk_fishing:
  enabled: true
  luck-nerf: 20%
  speed-nerf: 30%
```

### 7. Interactive Give, Catalog, & Fishbook System
- **Interactive Give GUI (`/nf give <fish|bait|rod>`)**: Omitting the ID opens an interactive 54-slot paginated GUI for admins and staff. Left-click gives 1 item, Shift-click gives 1 stack (capped by `give.max-amount`). Dynamic permission re-validation on every click prevents authorization leaks.
- **Admin Fish Protection**: Fish generated by `/nf give` are stamped with a persistent `admin_given` PDC tag. When `give.admin-fish-sellable` is set to `false`, these fish cannot be sold via `/nf sell`, `ShopGUI`, or `EventFishCollectorGUI`, completely eliminating dupe or economy exploits.
- **Read-Only View Catalog (`/nf view <fish|bait|rod> [id]`)**: Full technical specifications inspection (tiers, weight bounds, quality ranges, stat multipliers, ability descriptions). All interactions, dragging, dropping, and hopper extraction are cancelled.
- **Personal Fishbook (`/nf fishbook`)**: Dynamic collection codex recording caught fish per player. Caught specimens display original icons and records (catch count, max weight, max quality), while undiscovered species remain hidden as `BLACK_CONCRETE` (`???`).

```yaml
# config.yml additions
give:
  max-amount: 64
  admin-fish-sellable: false
  drop-if-full: false

fishbook:
  include-event-fish: true
```

---

## 📋 Commands & Permissions

**Base Command Aliases**: `/nusafishing`, `/nf`, `/fishing`

| Command | Description | Permission | Default |
|---|---|---|---|
| `/nf help` | Shows the command guide | None | Everyone |
| `/nf fishbook` | Opens the personal caught fish collection book | `nusafishing.fishbook` | `true` |
| `/nf view <fish\|bait\|rod> [id]` | Opens read-only technical item catalog | `nusafishing.view` | `true` |
| `/nf give <fish\|bait\|rod> [id] [amount] [player]` | Gives items or opens interactive Give GUI | `nusafishing.give` (`.fish`/`.bait`/`.rod`) | `op` |
| `/nf rod [id] [amount] [player]` | Gives custom fishing rod or opens Rod Give GUI | `nusafishing.give.rod`, `nusafishing.rod` | `op` |
| `/nf bait [id] [amount] [player]` | Gives custom bait or opens Bait Give GUI | `nusafishing.give.bait`, `nusafishing.bait` | `op` |
| `/nf afk` | Toggles AFK auto-fishing mode with configured nerfs | `nusafishing.afk` | `true` |
| `/nf shop` | Opens the graphical Rod, Bait, & Fish Market | `nusafishing.shop` | `true` |
| `/nf sell` | Sells the fish held in main hand | `nusafishing.sell` | `true` |
| `/nf sell all` | Sells all fish in inventory storage | `nusafishing.sell` | `true` |
| `/nf repair` | Repairs the held custom fishing rod | `nusafishing.repair` | `true` |
| `/nf stats [player]` | Inspects personal or other player's fishing stats | `nusafishing.stats` (`.others`) | `true` / `op` |
| `/nf collector` | Opens the Mystic Fish Collector GUI | `nusafishing.collector` | `true` |
| `/nf tournament join` | Joins the active community fishing tournament | None | Everyone |
| `/nf reload` | Reloads all configuration files and closes open GUIs | `nusafishing.admin.reload` | `op` |
| `/nf location <list\|tp\|create\|delete>` | Manages special fishing spots | `nusafishing.admin` | `op` |
| `/nf event <start\|stop\|info>` | Manages server-wide fishing events | `nusafishing.admin.event` | `op` |
| `/nf tournament <start\|stop\|info>` | Manages fishing tournaments | `nusafishing.admin.tournament` | `op` |

---

## 📊 PlaceholderAPI

NusaFishing registers the `%nusafishing_*%` expansion automatically when PlaceholderAPI is present:

| Placeholder | Description |
|---|---|
| `%nusafishing_fish_caught%` | Total number of fish caught by the player |
| `%nusafishing_legendary_caught%` | Total legendary fish caught by the player |
| `%nusafishing_mythic_caught%` | Total mythic fish caught by the player |
| `%nusafishing_event_fish_caught%` | Total event fish caught by the player |
| `%nusafishing_fishing_level%` | Player's fishing skill level |
| `%nusafishing_fishing_exp%` | Player's current fishing experience |
| `%nusafishing_largest_fish_weight%` | Weight of the player's heaviest fish (kg) |
| `%nusafishing_largest_fish_name%` | Name of the player's heaviest fish |
| `%nusafishing_top_fisher_<rank>%` | Name of the leaderboard fisher at rank `X` |
| `%nusafishing_top_fisher_count_<rank>%` | Catch count of the leaderboard fisher at rank `X` |
| `%nusafishing_top_largest_fish_<rank>%` | Name of the player with heaviest fish at rank `X` |
| `%nusafishing_top_largest_fish_weight_<rank>%` | Weight of the heaviest fish at rank `X` |

---

## 🛠 Developer & Build Guide

### Prerequisites
- **JDK 25** (or JDK 21+ with Java 25 installed for MR-JAR compilation).
- Gradle wrapper is included (`./gradlew`).

### Building from Source

```bash
# Clone the repository
git clone https://github.com/kaito65452/nusafishing.git
cd nusafishing

# Run clean build and unit test suite
./gradlew clean test build
```

The compiled multi-release artifact will be generated in:
```
build/libs/nusafishing-1.5+Alpha+<commit>.jar
```

### Verifying Multi-Release Bytecode

```bash
# Verify base bytecode class version (major 65 = Java 21)
javap -verbose -cp build/libs/nusafishing-*.jar id.nusafishing.NusaFishing | grep "major version"

# Verify version 25 bytecode (major 69 = Java 25)
unzip -p build/libs/nusafishing-*.jar META-INF/versions/25/id/nusafishing/compat/PlatformHelper.class > /tmp/PlatformHelper25.class
javap -verbose /tmp/PlatformHelper25.class | grep "major version"
```

---

## 📄 License

This project is licensed under the [GNU General Public License v2.0 (GPL-2.0)](LICENSE).
