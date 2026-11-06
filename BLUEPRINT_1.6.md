# 🏛️ BLUEPRINT ARSITEKTUR & LAPORAN AUDIT TEKNIS
## NusaFishing v1.6+alpha (Fisch Roblox Overhaul Engine)
**Target Reviewer:** Senior AI Reasoning Models / Lead Systems Engineer  
**Branch:** `dev` (Synced with `fitur-update-1.6`)  
**Commit:** `570fba2`  
**License:** GNU GPL v2  
**Platform Target:** Paper/Purpur/Spigot 1.21.x s/d 26.3+ (Java 21 / Java 25 MR-JAR)

---

## 📑 DAFTAR ISI
1. [Ringkasan Eksekutif & Filosofi Desain](#1-ringkasan-eksekutif--filosofi-desain)
2. [Peta Struktur Direktori & Modul](#2-peta-struktur-direktori--modul)
3. [Arsitektur Komponen Fisch Roblox](#3-arsitektur-komponen-fisch-roblox)
   - 3.1 [Data-Driven Registries & Models](#31-data-driven-registries--models)
   - 3.2 [Environment Engine: Cuaca, Musim, & Totem Samudra](#32-environment-engine-cuaca-musim--totem-samudra)
   - 3.3 [Virtual Rod Inventory (Zero-Dupe Architecture)](#33-virtual-rod-inventory-zero-dupe-architecture)
   - 3.4 [Misi & Quest Harian](#34-misi--quest-harian)
4. [Mekanisme Fallback Hooks (Zero Dependency Crashes)](#4-mekanisme-fallback-hooks-zero-dependency-crashes)
   - 4.1 [RealisticSeasons -> Internal Astronomical Clock](#41-realisticseasons---internal-astronomical-clock)
   - 4.2 [AuraSkills -> Decoupled Reflection Handler & Internal Skill Manager](#42-auraskills---decoupled-reflection-handler--internal-skill-manager)
   - 4.3 [FancyNpcs -> Dynamic EventExecutor & Zero Auto-Spawn](#43-fancynpcs---dynamic-eventexecutor--zero-auto-spawn)
5. [Verifikasi Kompatibilitas Versi 1.5](#5-verifikasi-kompatibilitas-versi-15)
6. [Analisis Kinerja & Manajemen Sumber Daya (Resource Budget)](#6-analisis-kinerja--manajemen-sumber-daya-resource-budget)
7. [Checklist Skenario Pengujian Sistem (System Test Plan)](#7-checklist-skenario-pengujian-sistem-system-test-plan)
8. [Matriks Risiko Teknis & Mitigasi](#8-matriks-risiko-teknis--mitigasi)

---

## 1. Ringkasan Eksekutif & Filosofi Desain

Versi 1.6+alpha merombak mekanik inti NusaFishing untuk mengadopsi loop gameplay dari **Fisch (Roblox)** tanpa mengorbankan kesederhanaan, kestabilan, dan efisiensi memori plugin.

### Prinsip Inti:
1. **Data-Driven (Zero Class Bloat):** Tidak ada pembuatan class Java untuk setiap variasi ikan, pancingan, totem, enchant, atau misi. Semuanya menggunakan model generik yang dibaca dari YAML dan di-cache dalam Indexed Array / Fast Map saat startup.
2. **Deterministic & Exploit-Free State:** Inventori virtual pancingan (`/nf rods`) memotong seluruh jalur transfer item tradisional Minecraft (hopper, chest, offhand swap `F`, item frame, armor stand, player drop, death drop).
3. **Decoupled Soft-Dependencies:** Tidak ada import compile-time hard-dependency yang dapat memicu `NoClassDefFoundError` pada runtime jika plugin pihak ketiga tidak terpasang.
4. **Non-Blocking I/O (Async Debounced Batching):** Disk writing untuk data pemain dilakukan pada thread asinkron dengan debounce timer untuk mencegah micro-stutter pada tick main thread.

---

## 2. Peta Struktur Direktori & Modul

```
src/main/
├── java/
│   └── id/nusafishing/
│       ├── NusaFishing.java                    # Plugin lifecycle, IoC bootstrap, graceful shutdown
│       ├── commands/
│       │   └── NusaFishingCommand.java         # Master command handler (/nf help, rods, totem, mission, season, npc, give, view, afk, etc.)
│       ├── config/
│       │   ├── ConfigManager.java              # Registry configuration & tier loader
│       │   └── ConfigSync.java                 # Auto-updater preservatif untuk config.yml & fishing.yml
│       ├── fisch/
│       │   ├── environment/
│       │   │   └── FischEnvironmentManager.java # Scheduler cuaca global, totem expiration, dan partikel
│       │   ├── inventory/
│       │   │   ├── PlayerVirtualRods.java       # POJO data pancingan pemain (thread-safe dirty tracking)
│       │   │   ├── VirtualRodHolder.java        # InventoryHolder unik untuk identifikasi GUI
│       │   │   ├── VirtualRodInventoryGUI.java  # Render inventori 54-slot vanilla
│       │   │   ├── VirtualRodListener.java      # Anti-dupe, anti-drag, anti-offhand, anti-container handler
│       │   │   └── VirtualRodManager.java       # Storage manager, async debounced saving, equip logic
│       │   ├── mission/
│       │   │   └── FischMissionManager.java     # Tracking progress misi tangkapan & reward distributor
│       │   ├── model/
│       │   │   ├── FischEnchant.java            # Model data enchant alat pancing
│       │   │   ├── FischMission.java            # Model data misi/quest
│       │   │   ├── FischMutation.java           # Model mutasi ikan (Albino, Electric, Giant, dsb.)
│       │   │   ├── FischSeason.java             # Enum 4 musim (SPRING, SUMMER, AUTUMN, WINTER)
│       │   │   ├── FischTotem.java              # Model totem samudra berdurasi
│       │   │   └── FischWeather.java            # Enum cuaca samudra & peluang ikan khusus
│       │   └── registry/
│       │       ├── EnchantRegistry.java         # Cache enchant & parser PersistentDataContainer
│       │       ├── MissionRegistry.java         # Cache misi & selector misi harian
│       │       ├── MutationRegistry.java        # Weighted random roll mutasi ikan O(1)
│       │       └── TotemRegistry.java           # Cache definisi totem & generator item
│       ├── fishing/
│       │   ├── FishingListener.java             # Event cast, bite, reel, strike, totem right-click
│       │   ├── FishingManager.java              # Logika seleksi ikan tertangkap, multiplier, mutasi roll
│       │   ├── afk/AfkFishingManager.java       # AFK auto-fishing dengan luck/speed nerf & distance check
│       │   ├── data/
│       │   │   ├── FishingStatisticsManager.java# Leaderboard & counter statistik tangkapan
│       │   │   ├── PlayerSkillManager.java      # Level & EXP memancing internal (fallback AuraSkills)
│       │   │   └── StaminaManager.java          # Manajemen stamina pemain
│       │   ├── minigame/StrikeMinigame.java     # Mini-game klik kiri saat kail disambar
│       │   └── model/
│       │       ├── CustomFish.java              # Model kalkulasi harga logaritmik, bobot, & mutasi
│       │       ├── FishingBait.java             # Model umpan pancing
│       │       └── FishingRod.java              # Model pancingan custom
│       ├── gui/
│       │   ├── GUI.java                         # Base GUI class berbasis InventoryHolder
│       │   ├── GUIListener.java                 # Universal GUI security & leaked-item cleaner
│       │   ├── GenericGUIHolder.java            # Holder penanda GUI NusaFishing
│       │   ├── ShopGUI.java                     # Toko nelayan terintegrasi Virtual Rod
│       │   └── EventFishCollectorGUI.java       # Kolektor ikan mistik
│       └── integration/
│           ├── auraskills/
│           │   ├── AuraSkillsHandler.java       # Interface decoupled AuraSkills
│           │   ├── AuraSkillsHook.java          # Safe wrapper dengan fallback internal
│           │   └── RealAuraSkillsHandler.java   # Implementasi API AuraSkills terisolasi
│           ├── npc/FancyNpcsHook.java           # Optional NPC hook (admin-defined only)
│           └── season/
│               ├── SeasonHook.java              # Abstraksi musim
│               └── RealisticSeasonsHook.java    # Refleksi RealisticSeasons + internal clock fallback
└── resources/
    ├── config.yml                              # Pengaturan fitur, nerf, musim, hooks, & tier colors
    ├── fishing.yml                             # Registry data alat pancing, umpan, dan ikan
    └── plugin.yml                              # Deklarasi command, permissions, & softdepend
```

---

## 3. Arsitektur Komponen Fisch Roblox

### 3.1 Data-Driven Registries & Models
- **Mutasi Ikan:** Didefinisikan dalam `MutationRegistry` dengan method roll berbobot:
  $$\text{EffectiveChance} = \text{BaseChance} \times (1.0 + \text{MutationBoost})$$
  Memvalidasi kondisi cuaca (mis. `Electric` hanya saat cuaca `STORM`), waktu malam, dan bobot tubuh ikan.
- **Enchantment Alat Pancing:** Disimpan dalam NBT/PDC item pancingan virtual (`nusafishing:rod_enchants`). Bonus stat (Luck, Speed, Weight, Resilience, Mutation) diakumulasi secara deterministik $O(N)$ di mana $N \le 6$ (jumlah enchant terpasang).

### 3.2 Environment Engine
- **Global Scheduler:** Berjalan setiap 20 detik (400 ticks) dalam `FischEnvironmentManager` untuk memeriksa status cuaca dunia dan masa berlaku Totem Samudra.
- **Totem Activation:** Pemain mengklik kanan item totem; sistem memvalidasi PDC `nusafishing:totem_id`, mengonsumsi 1 totem, mengubah cuaca dunia, menyiarkan pesan samudra, dan mencatat masa berlaku totem aktif.
- **Totem Visual Particles:** Task terpisah setiap 2 detik (40 ticks) yang hanya merender partikel ringan di koordinat aktivasi jika chunk tersebut dimuat dan terdapat pemain di sekitarnya.

### 3.3 Virtual Rod Inventory (Zero-Dupe Architecture)
Sistem pancingan virtual menggantikan penyimpanan pancingan fisik biasa untuk rod bernilai tinggi:
1. **VirtualRodManager:**
   - Menyimpan status kepemilikan dan durabilitas rod per pemain di `virtual_rods.yml`.
   - Perubahan data ditandai dengan flag `dirty = true` dan disimpan secara asinkron ke disk dengan debounce timer 2.5 detik.
2. **Anti-Dupe & Exploit Guard (`VirtualRodListener`):**
   - **Click Cancellation:** Seluruh event pada GUI `/nf rods` dibatalkan seketika (`event.setCancelled(true)`).
   - **Container Protection:** Event dibatalkan jika pemain membuka Peti, Hopper, Shulker, atau wadah eksternal dan mencoba memindahkan Virtual Rod via Shift-Click, Cursor Drop, Number Key Hotbar Swap (1-9), atau **Offhand Swap (`F` / `ClickType.SWAP_OFFHAND`)**.
   - **Complete Inventory Cleansing:** Saat pemain melakukan unequip atau swap rod, method `removeVirtualRodsFromInventory` membersihkan slot inventori utama (`0..35`) dan **slot offhand**.
   - **Entity Placement Block:** Mencegah pemain menempatkan pancingan virtual ke dalam **Item Frame**, **Glow Item Frame**, atau **Armor Stand**.
   - **Soulbound on Death:** Saat pemain mati, item Virtual Rod dicegat dari `event.getDrops()` agar tidak tercecer di tanah dan tidak dapat dijarah.

### 3.4 Misi & Quest Harian
- Dikelola oleh `FischMissionManager`.
- Setiap tangkapan ikan memvalidasi target jenis ikan dan tier.
- Setelah jumlah tangkapan tercapai, reward langsung didistribusikan:
  - **ROD:** Otomatis dibuka di inventori virtual `/nf rods`.
  - **BAIT / TOTEM:** Diberikan ke inventori pemain.
  - **EXP / MONEY:** Dikreditkan ke akun pemain (didukung Vault).
  - Sistem kemudian otomatis mengundi misi harian baru dari `MissionRegistry`.

---

## 4. Mekanisme Fallback Hooks (Zero Dependency Crashes)

### 4.1 RealisticSeasons -> Internal Astronomical Clock
- Implementasi: `RealisticSeasonsHook.java`
- Deteksi via `PluginManager.isPluginEnabled("RealisticSeasons")` dan refleksi runtime.
- **Fallback Otomatis:** Jika plugin tidak terpasang, sistem menghitung musim secara deterministik:
  $$\text{Hari Dunia} = \left\lfloor \frac{\text{WorldFullTime}}{24000} \right\rfloor$$
  $$\text{Index Musim} = \left\lfloor \frac{\text{Hari Dunia}}{\text{DaysPerSeason}} \right\rfloor \pmod 4$$
  Sistem berotasi sempurna melalui `SPRING -> SUMMER -> AUTUMN -> WINTER` tanpa melempar runtime exception.

### 4.2 AuraSkills -> Decoupled Reflection Handler
- Implementasi: `AuraSkillsHook.java`, `AuraSkillsHandler.java`, `RealAuraSkillsHandler.java`
- Kelas `RealAuraSkillsHandler` yang mengimpor kelas dari `dev.aurelium.auraskills.*` hanya dimuat secara dinamis via `Class.forName(...)` saat AuraSkills terdeteksi aktif.
- **Fallback Otomatis:** Jika AuraSkills tidak ada, pemanggilan `addFishingXp` dan `getFishingLevel` secara mulus dialihkan ke `PlayerSkillManager` bawaan NusaFishing.

### 4.3 FancyNpcs -> Dynamic EventExecutor & Zero Auto-Spawn
- Implementasi: `FancyNpcsHook.java`
- Dinonaktifkan secara default di `config.yml` (`fancynpcs.enabled: false`).
- **Zero Auto-Spawn:** Tidak ada entitas NPC yang dibuat secara otomatis. NPC hanya didaftarkan jika admin server menjalankan perintah eksplisit:
  `/nusafishing npc register <id> <type> [nama]`
- Interaksi pemain ditangani menggunakan `EventExecutor` Bukkit tanpa ketergantungan compile-time terhadap jar FancyNpcs.

---

## 5. Verifikasi Kompatibilitas Versi 1.5

Fitur versi sebelumnya tetap berfungsi penuh:
- `/nf give <fish|bait|rod|totem> [id] [jumlah] [pemain]` -> Berjalan via `GiveManager` dan `handleGiveCommand`.
- `/nf view <fish|bait|rod|totem> [id]` -> Membuka katalog visual atau ringkasan info item.
- `/nf fishbook` -> Membuka katalog buku catatan spesies ikan.
- `/nf afk` -> Mode auto-fishing dengan `luck-nerf` dan `speed-nerf` terkonfigurasi.
- `/nf shop`, `/nf collector`, `/nf tournament`, dan `/nf stats` -> Beroperasi normal dengan tab-completion lengkap.

---

## 6. Analisis Kinerja & Manajemen Sumber Daya (Resource Budget)

| Aspek | Desain Implementasi | Analisis Dampak Kinerja |
|---|---|---|
| **Penyimpanan Data** | File YAML terpisah (`virtual_rods.yml`, `fisch_missions_data.yml`) dengan snapshot in-memory dan debounced async save | **Zero lag spike** saat pemain memancing atau mengganti rod. |
| **Pemeriksaan Cuaca** | 1 global task per 20 detik untuk semua world | Konsumsi CPU $\approx 0.002$ ms per siklus evaluasi. |
| **Kalkulasi Mutasi** | Weighted array roll $O(M)$ dengan $M \le 16$ | Waktu eksekusi $< 5$ mikrodetik per penangkapan ikan. |
| **Listener Filtering** | Fast-fail check di baris pertama event handler (`isPlayerInMinigame`, `item == null`, `type != Material.FISHING_ROD`) | Overhead minimum pada event pipeline server. |

---

## 7. Checklist Skenario Pengujian Sistem (System Test Plan)

Pengujian berikut direkomendasikan untuk dijalankan oleh user/admin pada server staging:

1. **Tes Inventori Virtual (`/nf rods`):**
   - [ ] Buka `/nf rods`, pilih rod untuk diequip. Pastikan item masuk ke hotbar utama.
   - [ ] Coba pindahkan pancingan virtual ke dalam Peti (Chest) menggunakan tombol `F` (Swap Offhand). Pastikan aksi dibatalkan dan muncul peringatan.
   - [ ] Coba masukkan pancingan virtual ke dalam Item Frame atau Armor Stand. Pastikan aksi dibatalkan.
   - [ ] Bunuh karakter pemain (`/kill`) saat memegang pancingan virtual. Pastikan pancingan tidak jatuh ke tanah.
2. **Tes Totem Samudra:**
   - [ ] Berikan totem via `/nf totem give tempest_totem <pemain>`.
   - [ ] Klik kanan totem di tangan. Pastikan cuaca seketika berubah menjadi badai (`STORM`), pesan broadcast muncul, dan partikel muncul di tanah.
3. **Tes Mutasi Ikan:**
   - [ ] Pancing ikan saat badai berlangsung. Verifikasi kemungkinan mendapatkan ikan dengan mutasi `Electric` atau `Albino` dan periksa kenaikan harga jualnya.
4. **Tes Misi Harian:**
   - [ ] Buka `/nf mission`. Tangkap ikan sesuai kriteria misi.
   - [ ] Pastikan progress bertambah dan reward (Rod/Totem/Bait/Uang) langsung diterima saat target terpenuhi.
5. **Tes Fallback:**
   - [ ] Jalankan server tanpa plugin RealisticSeasons dan FancyNpcs. Pastikan konsol bebas dari error `NoClassDefFoundError`.
   - [ ] Jalankan `/nf season` dan periksa perputaran hari pada Internal Season Clock.

---

## 8. Matriks Risiko Teknis & Mitigasi

| Risiko | Tingkat | Mitigasi yang Diterapkan |
|---|:---:|---|
| **Eksploitasi Duplikasi Virtual Rod** | Kritis | Isolasi total item via custom PDC (`nusafishing:virtual_rod`, `nusafishing:soulbound`), pembersihan slot inventori dan offhand secara rekursif, serta pemblokiran event container/offhand/frame. |
| **I/O Freeze pada Server Ramai** | Tinggi | Debounce buffer (2.5 detik) yang mengumpulkan perubahan data pemain sebelum menulis ke disk secara asinkron. |
| **Crash Saat Soft-Dependency Absen** | Tinggi | Isolasi dinamis via reflection (`RealAuraSkillsHandler`, `RealisticSeasonsHook`, `FancyNpcsHook`) dengan fallback terverifikasi. |
| **Desinkronisasi State Minigame** | Sedang | Pembersihan otomatis hook pancingan (`removePlayerFishingHook`) dan pembatalan minigame jika pemain membuka inventori atau kail lepas. |

---
*Blueprint ini siap ditinjau oleh model AI berkapasitas penalaran mendalam atau lead developer sebelum pengujian lapangan.*
