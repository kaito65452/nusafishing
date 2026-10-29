# Laporan Audit Kode: NusaFishing

- Commit/Versi: `a81f593` (v1.4+hotfix)
- Tanggal Audit: 2026-10-29
- Peran Auditor: Senior Code Auditor (Logic Reasoning Pass)

## Ringkasan Audit
- **CRITICAL**: 2 temuan (Kehilangan item permanen saat transaksi ekonomi gagal di GUI Event Collector; Eksploitasi duplikasi durabilitas dan bypass stamina via fishing rod di offhand)
- **HIGH**: 3 temuan (Hadiah turnamen lenyap total untuk pemenang offline; Penjadwalan turnamen gagal trigger akibat drift TPS; Command repair rod menolak custom rod non-CustomModelData)
- **MEDIUM**: 2 temuan (Bait dengan material lowercase di config gagal dideteksi; Auto-recast AFK fishing memeriksa durabilitas rod lama setelah swap)
- **LOW**: 1 temuan (Pemain level-up berulang memicu spam sound dan log tanpa rate limit)

**File yang Diperiksa:**
- `src/main/java/id/nusafishing/gui/EventFishCollectorGUI.java`
- `src/main/java/id/nusafishing/fishing/FishingListener.java`
- `src/main/java/id/nusafishing/tournament/TournamentManager.java`
- `src/main/java/id/nusafishing/commands/NusaFishingCommand.java`
- `src/main/java/id/nusafishing/config/ConfigManager.java`
- `src/main/java/id/nusafishing/fishing/afk/AfkFishingManager.java`
- `src/main/java/id/nusafishing/fishing/model/FishingBait.java`
- `src/main/java/id/nusafishing/fishing/data/FishingStatisticsManager.java`
- `src/main/java/id/nusafishing/skills/PlayerSkillManager.java`

**Area yang TIDAK Sempat Diperiksa:**
- Webhook Discord integrasi eksternal
- Sistem komparasi NBT internal Oraxen/ItemsAdder adapter
- Render teks GUI dynamic pagination custom lore builder

---

## Temuan Audit

### [CRITICAL] ID-001: Item Ikan Event Dihapus Permanen Saat Transaksi Vault Gagal
- **Lokasi**: `src/main/java/id/nusafishing/gui/EventFishCollectorGUI.java:395-408` dan `483-495`
- **Keyakinan**: TINGGI
- **Deskripsi**: Pada metode `sellEventFish` dan `sellAllEventFish`, item ikan dihapus dari inventaris pemain (`player.getInventory().setItem(slot, null)`) sebelum pemanggilan `economy.depositMoney(player, totalEarnings)`. Nilai balik boolean dari `depositMoney` diabaikan dan tidak ada mekanisme rollback ketika Vault gagal memproses deposit.
- **Skenario**:
  1. Pemain membuka GUI Event Collector membawa ikan event langka bernilai tinggi.
  2. Provider ekonomi Vault mengalami kegagalan internal (misal DB crash, lock akun, atau batas limit saldo).
  3. Pemain menekan tombol "Jual Ikan" atau "Jual Semua".
  4. Kode menghapus item ikan dari inventory pemain.
  5. `depositMoney` dipanggil, mengembalikan `false` atau gagal.
  6. Uang tidak masuk ke saldo, ikan hilang permanen dari inventaris.
- **Dampak**: Kehilangan item permanen pemain tanpa kompensasi uang.
- **Saran perbaikan**: Simpan referensi `ItemStack` dan slot asal, lakukan `depositMoney` terlebih dahulu atau verifikasi status transaksi. Jika deposit gagal, jangan hapus item atau kembalikan item ke slot semula serta kirim pesan error.
- **Cara uji manual**:
  1. Pasang provider Vault mock yang selalu mengembalikan respon gagal pada deposit.
  2. Masukkan ikan event ke inventory pemain.
  3. Klik tombol jual pada Event Fish Collector GUI.
  4. Periksa apakah ikan hilang dan saldo tetap nol.
- **Status**: BELUM DIUJI

---

### [CRITICAL] ID-002: Pemancingan Menggunakan Tangan Kiri (Offhand) Mem-Bypass Konsumsi Durabilitas dan Stamina
- **Lokasi**: `src/main/java/id/nusafishing/fishing/FishingListener.java:124-137`
- **Keyakinan**: TINGGI
- **Deskripsi**: Event handler `onPlayerFish` secara eksplisit memeriksa `player.getInventory().getItemInMainHand()`. Jika pemain memegang pancingan custom di offhand dan item non-rod di mainhand (atau mainhand kosong tapi event di-cast dari offhand), pengecekan `fishingRod.getType() != Material.FISHING_ROD` pada mainhand menghentikan eksekusi logika listener. Logika konsumsi durabilitas custom, konsumsi stamina, dan verifikasi minigame tidak dijalankan, namun vanilla Minecraft tetap memproses pelemparan pancing dari offhand.
- **Skenario**:
  1. Pemain memegang pancingan custom bernilai tinggi di slot offhand (tangan kiri).
  2. Pemain memegang item biasa (misal obor/pedang) atau tangan kosong di mainhand.
  3. Pemain melempar dan menarik pancing.
  4. Listener mendeteksi mainhand bukan pancingan, lalu langsung `return`.
  5. Pancingan custom offhand tidak berkurang durabilitas customnya dan pemain tidak kehilangan stamina.
- **Dampak**: Eksploitasi tak terbatas pada pancingan tier tinggi tanpa pengurangan durabilitas custom dan stamina.
- **Saran perbaikan**: Periksa kedua tangan (`getItemInMainHand()` dan `getItemInOffHand()`), atau identifikasi tangan aktif yang memicu event melalui event pancingan jika tersedia.
- **Cara uji manual**:
  1. Taruh pancingan custom NusaFishing di slot offhand.
  2. Kosongkan mainhand.
  3. Lakukan aktivitas memancing sampai menangkap ikan.
  4. Amati apakah NBT durabilitas custom pada pancingan offhand berkurang atau tetap utuh.
- **Status**: BELUM DIUJI

---

### [HIGH] ID-003: Hadiah Turnamen Menghilang untuk Pemenang yang Sedang Offline
- **Lokasi**: `src/main/java/id/nusafishing/tournament/TournamentManager.java:468-478`
- **Keyakinan**: TINGGI
- **Deskripsi**: Saat turnamen memancing selesai, `distributeRewards` menjalankan command hadiah (seperti `/give <player> ...`) melalui console menggunakan `player.getName()`. Jika pemain yang memenangkan turnamen offline pada saat distribusi, vanilla command `/give` gagal menemukan target pemain online dan reward command gagal dieksekusi tanpa antrian klaim offline.
- **Skenario**:
  1. Pemain memimpin klasemen turnamen memancing.
  2. 5 menit sebelum turnamen selesai, koneksi pemain terputus (offline).
  3. Turnamen berakhir.
  4. Server menjalankan `dispatchCommand(console, "give " + player.getName() + " ...")`.
  5. Console mencatat error "Player not found" dan hadiah item hilang selamanya.
- **Dampak**: Pemenang turnamen kehilangan reward item/voucher akibat diskoneksi.
- **Saran perbaikan**: Tambahkan sistem antrian reward offline berbasis UUID/file data agar hadiah dapat diklaim saat pemain login kembali.
- **Cara uji manual**:
  1. Mulai turnamen berdurasi 1 menit di server uji coba.
  2. Tangkap ikan dengan skor tertinggi, lalu disconnect dari server.
  3. Tunggu hingga turnamen berakhir.
  4. Login kembali dan periksa apakah hadiah masuk atau gagal di konsol.
- **Status**: BELUM DIUJI

---

### [HIGH] ID-004: Penjadwalan Turnamen Melewatkan Eksekusi Akibat TPS Drift
- **Lokasi**: `src/main/java/id/nusafishing/tournament/TournamentManager.java:200-218`
- **Keyakinan**: TINGGI
- **Deskripsi**: Pengecekan waktu turnamen dijalankan menggunakan Bukkit scheduler berulang tiap 1200 tick (asumsi tepat 60 detik). Pada kondisi server mengalami beban atau lag (TPS < 20.0), 1200 tick membutuhkan waktu nyata lebih dari 60 detik. Karena perbandingannya menggunakan kesetaraan presisi menit (`tournament.getMinuteOfHour() == currentMinute`), lompatan waktu nyata dapat membuat pengecekan menit target terlewati seutuhnya.
- **Skenario**:
  1. Turnamen dijadwalkan mulai pukul 15:30.
  2. Server mengalami lag moderat (TPS ~18).
  3. Scheduler tick terakhir berjalan pada pukul 15:29:55.
  4. Eksekusi 1200 tick berikutnya jatuh pada pukul 15:31:02.
  5. Nilai `currentMinute` adalah 31, bukan 30.
  6. Kondisi `30 == 31` bernilai false; turnamen batal dimulai secara otomatis sepanjang hari tersebut.
- **Dampak**: Turnamen otomatis sering tidak aktif sesuai jadwal yang dikonfigurasi admin.
- **Saran perbaikan**: Catat timestamp eksekusi terakhir dan gunakan perbandingan rentang waktu interval (`now >= scheduledTime && lastRun < scheduledTime`).
- **Cara uji manual**:
  1. Setel jadwal turnamen di config 2 menit ke depan.
  2. Simulasi lag server atau atur tick delay lebih dari interval menit.
  3. Amati apakah turnamen otomatis aktif tepat waktu saat menit terlewati.
- **Status**: BELUM DIUJI

---

### [HIGH] ID-005: Command Repair Rod Menolak Pancingan Custom Tanpa CustomModelData
- **Lokasi**: `src/main/java/id/nusafishing/commands/NusaFishingCommand.java:775-785`
- **Keyakinan**: TINGGI
- **Deskripsi**: Metode perbaikan pancingan `repairRod` memeriksa validitas pancingan dengan guard `if (meta == null || !meta.hasCustomModelData())`. Pancingan custom yang menggunakan NBT PDC id (`nusafishing:rod_id`) tetapi tidak memakai CustomModelData vanilla (misalnya rod default, custom model via shader, model 0, atau hook plugin eksternal) akan ditolak dan dianggap "Bukan pancingan custom".
- **Skenario**:
  1. Admin membuat pancingan custom tingkat pemula dengan `custom-model-data: 0` atau tanpa custom model.
  2. Pemain menggunakan pancingan hingga durabilitas berkurang.
  3. Pemain menjalankan perintah `/nf repair`.
  4. Guard mendeteksi `!meta.hasCustomModelData()`.
  5. Pesan kesalahan "§cIni bukan pancingan custom!" muncul dan perbaikan ditolak.
- **Dampak**: Pancingan custom yang valid tidak dapat diperbaiki melalui fitur plugin.
- **Saran perbaikan**: Validasi keberadaan pancingan custom menggunakan pengecekan NBT PDC key `nusafishing:rod_id`, bukan semata-mata `hasCustomModelData()`.
- **Cara uji manual**:
  1. Berikan pancingan custom yang dikonfigurasi tanpa CustomModelData ke pemain.
  2. Kurangi durabilitas pancingan tersebut.
  3. Jalankan `/nf repair` saat memegang pancingan.
  4. Periksa apakah sistem menolak perbaikan.
- **Status**: BELUM DIUJI

---

### [MEDIUM] ID-006: Material Umpan Berhuruf Kecil pada Config Gagal Dimuat
- **Lokasi**: `src/main/java/id/nusafishing/config/ConfigManager.java:205`
- **Keyakinan**: TINGGI
- **Deskripsi**: Pembacaan material umpan memanggil `Material.getMaterial(baitsSection.getString(key + ".material", "COD"))` tanpa `.toUpperCase(Locale.ROOT)`. `Material.getMaterial` pada Bukkit API peka terhadap huruf kapital (case-sensitive) untuk string enumerasi. Jika config diisi `wheat_seeds` atau `cod`, metode mengembalikan `null`. Pada `FishingBait.isThisBait`, pemanggilan `item.getType() != material` akan selalu mengembalikan false jika `material == null`, menyebabkan umpan tidak dapat digunakan.
- **Skenario**:
  1. Pengelola server mengedit file `baits.yml` dan menulis `material: "wheat_seeds"`.
  2. Plugin memuat konfigurasi saat startup.
  3. `Material.getMaterial("wheat_seeds")` menghasilkan `null`.
  4. Pemain memegang bibit gandum dengan metadata umpan terkait.
  5. Sistem tidak mendeteksi umpan saat melempar pancingan.
- **Dampak**: Umpan menjadi tidak berfungsi saat pemain memancing.
- **Saran perbaikan**: Konversi string input konfigurasi menjadi huruf kapital sebelum mencari enum material: `Material.getMaterial(rawMaterial.toUpperCase(Locale.ROOT))`.
- **Cara uji manual**:
  1. Ubah material salah satu bait di konfigurasi menjadi huruf kecil.
  2. Reload konfigurasi `/nf reload`.
  3. Coba pancing dengan umpan tersebut di inventory.
  4. Periksa apakah efek umpan bekerja.
- **Status**: BELUM DIUJI

---

### [MEDIUM] ID-007: Stale Reference Pancingan pada Penjadwalan Ulang AFK Fishing
- **Lokasi**: `src/main/java/id/nusafishing/fishing/afk/AfkFishingManager.java:255-275`
- **Keyakinan**: TINGGI
- **Deskripsi**: Dalam task penjadwalan `scheduleAutoRecast`, callback menerima parameter `customRod` dari awal cast sebelumnya. Ketika scheduler berjalan beberapa detik kemudian, kode mengecek apakah rod masih ada di tangan pemain. Namun untuk pengecekan durabilitas, kode mengevaluasi `FishingListener.getRodDurability(currentRod)` hanya jika referensi lama `customRod != null`. Jika pemain menukar pancingan custom ke pancingan custom lain dengan ID berbeda atau pancingan biasa di tangan, referensi pengecekan dan validasi logika menjadi tidak konsisten.
- **Skenario**:
  1. Pemain mengaktifkan AFK fishing dengan pancingan custom A (durabilitas tersisa 1).
  2. Saat pancingan sedang di air menunggu recast, pemain menukar slot ke pancingan custom B (durabilitas penuh).
  3. Task recast terpicu dan mengevaluasi status item tangan saat ini dengan konfigurasi referensi item lama.
  4. Status durabilitas item tertukar dan auto-fishing berpotensi berhenti salah sasaran atau melanjutkan cast dengan konfigurasi yang salah.
- **Dampak**: Disorientasi status durabilitas dan ketidaksesuaian bait/rod pada sistem AFK.
- **Saran perbaikan**: Evaluasi ulang instance pancingan secara penuh dari inventory tangan saat scheduler dieksekusi tanpa mengandalkan referensi closure masa lalu.
- **Cara uji manual**:
  1. Aktifkan mode AFK fishing.
  2. Segera ganti item pancingan di hotbar sebelum jeda recast selesai.
  3. Periksa perilaku pesan pembatalan dan konsumsi durabilitas pada item yang baru.
- **Status**: BELUM DIUJI

---

### [LOW] ID-008: Loop Level-Up Skill Beruntun Memicu Spam Efek Suara dan Pesan
- **Lokasi**: `src/main/java/id/nusafishing/skills/PlayerSkillManager.java:120-128`
- **Keyakinan**: SEDANG
- **Deskripsi**: Pada `addFishingExp`, jika penambahan exp dalam jumlah masif diterima (misal dari hadiah event/turnamen besar bernilai ribuan exp saat level rendah), loop `while (newExp >= expForNextLevel)` mengeksekusi pesan chat dan efek suara `playSound` pada setiap iterasi kenaikan 1 level.
- **Skenario**:
  1. Pemain baru level 1 memenangkan turnamen dan menerima 5000 exp sekaligus.
  2. Loop menaikkan level berturut-turut dari level 1 ke level 15 dalam 1 tick.
  3. Sistem memicu 14 pesan chat terpisah dan 14 paket suara level-up simultan ke client pemain.
- **Dampak**: Spamming chatbox dan penumpukan audio sound packet pada koneksi client.
- **Saran perbaikan**: Hitung kenaikan level total terlebih dahulu, kemudian kirimkan 1 pesan rangkuman dan 1 efek suara setelah loop selesai.
- **Cara uji manual**:
  1. Berikan EXP dalam jumlah sangat besar (misal 100.000) ke akun level 1 melalui API/event.
  2. Amati aliran chat dan volume suara yang diterima client.
- **Status**: BELUM DIUJI

---

## Tabel Ringkasan Temuan

| ID | Severity | Lokasi | Status |
|---|---|---|---|
| ID-001 | CRITICAL | `src/main/java/id/nusafishing/gui/EventFishCollectorGUI.java:395` | BELUM DIUJI |
| ID-002 | CRITICAL | `src/main/java/id/nusafishing/fishing/FishingListener.java:124` | BELUM DIUJI |
| ID-003 | HIGH | `src/main/java/id/nusafishing/tournament/TournamentManager.java:468` | BELUM DIUJI |
| ID-004 | HIGH | `src/main/java/id/nusafishing/tournament/TournamentManager.java:200` | BELUM DIUJI |
| ID-005 | HIGH | `src/main/java/id/nusafishing/commands/NusaFishingCommand.java:775` | BELUM DIUJI |
| ID-006 | MEDIUM | `src/main/java/id/nusafishing/config/ConfigManager.java:205` | BELUM DIUJI |
| ID-007 | MEDIUM | `src/main/java/id/nusafishing/fishing/afk/AfkFishingManager.java:255` | BELUM DIUJI |
| ID-008 | LOW | `src/main/java/id/nusafishing/skills/PlayerSkillManager.java:120` | BELUM DIUJI |

---

## Asumsi yang Perlu Dikonfirmasi Pemilik Proyek
1. Apakah pancingan di tangan kiri (offhand) dimaksudkan untuk diizinkan memancing, atau seharusnya diblokir seutuhnya oleh plugin?
2. Apakah hadiah turnamen via command console `/give` diharapkan tetap berjalan jika pemain memenangkan turnamen dalam keadaan offline?
3. Apakah kustomisasi item pada plugin selalu mewajibkan field `CustomModelData`, atau apakah model berbasis NBT PDC/Pack modern harus didukung penuh?
