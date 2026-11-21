# JalokDuitNow

JalokDuitNow adalah aplikasi desktop pencatat keuangan pribadi yang dibuat dengan gamifikasi: mencatat pemasukan dan
pengeluaran memberi XP, level, streak, badge, dan quest mingguan. Aplikasi ini ditulis dengan Java 21 dan JavaFX, dan
berjalan di Windows dan macOS.

Semua data tersimpan di komputermu sendiri. Tidak ada akun, tidak ada server, dan tidak ada data yang dikirim ke mana pun
kecuali kamu sendiri menekan tombol berbagi.

<!-- TODO: tambahkan tangkapan layar di docs/screenshots/ (misalnya beranda.png, laporan.png, tema-gelap.png) dan tampilkan di sini. -->

## Tangkapan layar

Belum ada. Simpan gambar di `docs/screenshots/`.

## Fitur

- Transaksi pemasukan dan pengeluaran: tambah, ubah, hapus, cari, dan filter per periode.
- Kolom kustom (teks, angka, tanggal, ya/tidak, pilihan; bisa diwajibkan) lewat halaman **Kolom**.
- Kategori kustom dengan ikon vektor, budget bulanan, arsip, dan gabung kategori (halaman **Kategori**).
- Beranda berisi ringkasan, grafik lingkaran dan batang, dan rentang tahun yang menyesuaikan data.
- Gamifikasi: XP, level, streak dengan freeze token, badge, quest mingguan, dan target tabungan (halaman **Quest & Badge**).
- Laporan bulanan (halaman **Laporan**): laporan pribadi dan laporan donor dalam teks polos.
- Berbagi laporan ke donor lewat WhatsApp atau clipboard.
- Pengingat: Pengingat mencatat, Penjaga streak, Laporan donor bulanan, dan Batas budget, dengan jam tenang,
  notifikasi di dalam aplikasi (lonceng), dan notifikasi sistem bila tray didukung.
- Ekspor transaksi ke CSV dan cadangan lengkap ke JSON.
- Cadangan otomatis harian, pulihkan dari cadangan dengan pratinjau dan cadangan pengaman.
- Zona Berbahaya: hapus semua transaksi, reset progres game, dan reset pabrik, masing-masing dengan konfirmasi dan cadangan pengaman.
- Tema terang dan gelap.

## Unduh dan pasang

Unduh installer dari halaman rilis: `https://github.com/oktoramadhan/jalokduitnow/releases`

| Sistem | File | Dibuat oleh |
|--------|------|-------------|
| Windows | `JalokDuitNow-1.0.0.msi` | `mvn -Pinstaller-windows package` |
| macOS | `JalokDuitNow-1.0.0.dmg` | `mvn -Pinstaller-mac package` |

Installer belum ditandatangani, jadi sistem operasi akan memperingatkan saat pertama kali dibuka. Ini normal untuk
aplikasi tanpa sertifikat penerbit.

**Windows**

1. Jalankan file `.msi`. Jika muncul layar biru "Windows protected your PC" (SmartScreen), klik **More info**, lalu **Run anyway**.
2. Ikuti pemasangan. Pintasan dibuat di menu Start.

**macOS**

1. Buka file `.dmg`, lalu seret **JalokDuitNow** ke folder **Applications**.
2. Saat pertama kali membuka, klik kanan aplikasi lalu pilih **Open**, dan konfirmasi dengan **Open**.
3. Jika tetap diblokir: buka **System Settings > Privacy & Security**, gulir ke bawah, lalu klik **Open Anyway** pada JalokDuitNow.

## Jalankan dari kode sumber

Kebutuhan:

- JDK 21 atau lebih baru (proyek dikompilasi dengan `release 21`).
- Maven 3.9 atau lebih baru (pom.xml tidak mengunci versi Maven).
- Git.

```bash
git clone https://github.com/oktoramadhan/jalokduitnow.git
cd jalokduitnow
mvn javafx:run
```

Jalankan tes:

```bash
mvn test
```

Build lengkap (kompilasi dan tes, sama seperti yang dijalankan CI):

```bash
mvn verify
```

## Membuat installer sendiri

Installer dibuat dengan `jpackage` (bagian dari JDK) lewat profil Maven di `pom.xml`. **Setiap installer harus dibuat
di sistem operasinya sendiri**: `.msi` hanya di Windows, `.dmg` hanya di macOS. Gunakan JDK 21 atau lebih baru.

| Sistem | Prasyarat | Perintah | Hasil |
|--------|-----------|----------|-------|
| Windows | WiX Toolset 3.x di `PATH` | `mvn -DskipTests -Pinstaller-windows package` | `target/installer/output/JalokDuitNow-1.0.0.msi` |
| macOS | Xcode Command Line Tools (`xcode-select --install`) | `mvn -DskipTests -Pinstaller-mac package` | `target/installer/output/JalokDuitNow-1.0.0.dmg` |

Profil yang sesuai juga aktif otomatis jika kamu menambahkan `-Dinstaller`:

```bash
mvn -DskipTests -Dinstaller package
```

Catatan:

- Nama aplikasi, versi, dan ikon diatur di `pom.xml` (`installer.name`, `installer.version`) dan `packaging/icons/`
  (`app.ico`, `app.icns`, `app.png`). Versi installer mengikuti versi proyek; macOS mewajibkan angka pertama minimal 1.
- Ikon dibuat ulang dengan `java packaging/tools/IconGenerator.java packaging/icons`. File `.icns` dibuat dari iconset
  dengan `iconutil` di macOS.
- Program dijalankan lewat kelas `com.okto.jalokduitnow.Launcher`, yang memanggil `MainApp`.

**GitHub Actions.** Workflow `.github/workflows/build.yml` berjalan di `ubuntu-latest`, `windows-latest`, dan `macos-latest`
dengan JDK 21:

1. Job `test` menjalankan `mvn verify` di ketiga sistem.
2. Job `installer` membuat `.msi` (Windows) dan `.dmg` (macOS) dan mengunggahnya sebagai artifact `installer-windows-latest`
   dan `installer-macos-latest` di halaman run.
3. Saat kamu mendorong tag berawalan `v` (misalnya `v1.0.0`), job `release` melampirkan installer ke GitHub Release.

## Lokasi data

Data disimpan di folder `.jalokduitnow` di dalam folder home pengguna:

| Sistem | Lokasi |
|--------|--------|
| Windows | `C:\Users\<nama>\.jalokduitnow` |
| macOS | `/Users/<nama>/.jalokduitnow` |

Isi folder:

| File | Isi |
|------|-----|
| `transactions.json` | transaksi |
| `schema.json` | kolom kustom |
| `categories.json` | kategori |
| `game.json` | XP, level, streak, badge, quest, target tabungan |
| `recipients.json`, `donor-settings.json` | daftar donor dan pengaturan donor |
| `reminders.json`, `reminder-log.json`, `notifications.json` | pengaturan pengingat, catatan pengingat yang sudah muncul, dan notifikasi |
| `settings.properties` | tema terang atau gelap |
| `*.bak` | salinan versi sebelumnya dari tiap file data |
| `backups/` | cadangan otomatis dan cadangan pengaman |

Cadangan di folder `backups/`:

- `otomatis-<tanggal>.json`: satu per hari saat aplikasi dibuka; 5 yang terbaru disimpan.
- `sebelum-pemulihan-<waktu>.json` dan `sebelum-hapus-<waktu>.json`: cadangan pengaman sebelum pemulihan atau penghapusan
  data. Ini tidak pernah dihapus otomatis.

## Cadangan dan pemulihan

**Membuat cadangan:** buka **Pengaturan**, kartu **Ekspor dan Cadangan**, klik **Buat Cadangan...**. File JSON berisi semua
data (transaksi, kolom, kategori, progres game, donor, pengaturan pengingat). Kotak **Sertakan daftar donor dan nomor
telepon** menentukan apakah data donor ikut; kotak ini aktif secara bawaan.

**Memulihkan:** buka **Pengaturan**, kartu **Pulihkan dari Cadangan**, klik **Pulihkan dari cadangan...**.

1. Pilih file cadangan, atau pilih salah satu dari daftar cadangan otomatis lalu klik **Periksa cadangan terpilih**.
2. Periksa pratinjau yang membandingkan data saat ini dengan isi cadangan. Tidak ada yang berubah pada tahap ini.
3. Ketik `RESTORE`, lalu klik **Ganti data saat ini**.
4. Data saat ini disimpan dulu sebagai cadangan pengaman; jika sesuatu gagal, data lama dikembalikan.

Pemulihan tidak memberi XP atau badge baru.

## Privasi

- Data tidak meninggalkan komputer kecuali kamu menekan tombol berbagi. Satu-satunya akses ke luar adalah membuka tautan
  WhatsApp di browser.
- WhatsApp hanya terbuka dengan pesan yang sudah terisi. Kamu tetap harus menekan Kirim di WhatsApp.
- Laporan donor tidak menyertakan detail transaksi secara bawaan. Kotak **Sertakan detail transaksi** mati, dan jika dinyalakan
  aplikasi meminta konfirmasi sebelum mengirim.
- Cadangan JSON dapat berisi nama dan nomor telepon donor (kotak **Sertakan daftar donor dan nomor telepon** aktif secara
  bawaan). Simpan file cadangan di tempat yang aman.
- Jangan commit folder data atau file cadangan ke Git. `.gitignore` sudah mengecualikan `.jalokduitnow/` dan
  nama file cadangan dan ekspor bawaan aplikasi, tetapi periksa sebelum mendorong.

## Menghapus aplikasi

| Sistem | Cara |
|--------|------|
| Windows | **Settings > Apps > Installed apps > JalokDuitNow > Uninstall** |
| macOS | Seret **JalokDuitNow** dari folder **Applications** ke Trash |

Menghapus aplikasi tidak menghapus datamu. Untuk menghapus data:

- Sebelum uninstall, buka **Pengaturan**, kartu **Zona Berbahaya**, pilih **Reset pabrik** (ketik `DELETE`; centang
  **Hapus juga cadangan yang ada** jika cadangan juga ingin dihapus), atau
- hapus folder `.jalokduitnow` secara manual (lokasinya ada di bagian Lokasi data).

## Teknologi dan konsep OOP

Java 21, JavaFX 21.0.5, Maven, Jackson (JSON), Ikonli (ikon Material Design), dan JUnit 5.

| Topik | Kelas di kode |
|-------|---------------|
| Pewarisan dan abstraksi | `Transaction` (abstrak) dengan `Income` dan `Expense`; `Quest` dengan `LogDaysQuest`; `Achievement` dengan `StreakAchievement`; `Reminder` dengan `DailyLogReminder`; `AbstractReportGenerator` dengan `PersonalReportGenerator` dan `DonorReportGenerator`; `FieldType<T>` dengan `TextType` |
| Polimorfisme | Antarmuka `Sharer` (`WhatsAppSharer`, `ClipboardSharer`), `ReportGenerator`, `Repository<T>`, `UriOpener`, `TrayBackend`, `NotificationSink` |
| Enkapsulasi | `GameProfile`, `Category`, `Recipient`, `SavingsGoal`: field privat dan validasi di dalam kelas |
| Generik | `Repository<T>`, `JsonRepository<T>`, `AutoSaveService<T>`, `FieldType<T>` |
| Koleksi | `EnumMap` (halaman di `MainApp`), `LinkedHashMap` (`ReminderLog`, `TransactionDialog`), `List` dan `Map` di seluruh service |
| Exception dan assertion | `StorageException`, `InvalidTransactionException`, `InvalidCategoryException`, `ShareException`; `assert` di `Reminder` dan `StreakAchievement`; assertion diaktifkan di surefire dan `javafx:run` (`-ea`) |
| Java IO | `JsonRepository`, `ExportFile`, `FileSwapper`, `SafeDataFolder`, `CsvExporter` (paket `java.nio.file`) |
| Multithreading | `AutoSaveService`, `DailyScheduler`, `ReminderService`, `ExportTask`, serta `Task` JavaFX di `RestoreDialog` dan `DeleteDataDialog` dengan pembaruan UI lewat `Platform.runLater` |
| JavaFX | `MainApp`, `DashboardView`, `TransactionView`, `ReportView`, `QuestsView`, `SettingsView`, tema lewat CSS (`style.css`, `theme-light.css`, `theme-dark.css`) |
| Event | `GameEvent` (sealed interface) dan `GameEventBus` |

## Struktur proyek

```
src/main/java/com/okto/jalokduitnow/
  Launcher.java, AppInfo.java   titik masuk installer dan versi aplikasi
  model/        Transaction, Income, Expense, Category, GameProfile, SavingsGoal, Recipient
  schema/       kolom kustom dan tipe field
  service/      logika aplikasi: FinanceService, GameService, CategoryService, backup, restore, hapus data
  storage/      penyimpanan JSON, ekspor CSV, FileSwapper, SafeDataFolder, validasi cadangan
  thread/       penyimpanan otomatis, penjadwal harian, pengingat, ekspor di background
  achievement/  badge dan aturannya
  quest/        quest mingguan
  reminder/     jenis pengingat dan notifikasi
  report/       pembuat laporan pribadi dan donor
  share/        berbagi lewat WhatsApp dan clipboard
  event/        event game dan bus event
  exception/    exception khusus aplikasi
  util/         format Rupiah, nomor telepon, jam sistem
  ui/           JavaFX: jendela utama, halaman, dialog, tema
src/main/resources/   stylesheet tema
src/test/java/        tes JUnit
packaging/            ikon dan pembuat ikon untuk installer
docs/                 daftar uji manual dan tangkapan layar
.github/workflows/    CI dan pembuatan installer
```

## Pemecahan masalah

| Masalah | Solusi |
|---------|--------|
| Aplikasi diblokir saat pertama kali dibuka | Aplikasi belum ditandatangani. Ikuti langkah SmartScreen (Windows) atau **Open Anyway** (macOS) di bagian Unduh dan pasang. |
| WhatsApp tidak terbuka | Pastikan ada browser bawaan dan WhatsApp (aplikasi atau WhatsApp Web) terpasang. Gunakan tombol **Salin** di halaman **Laporan** lalu tempel sendiri. |
| Notifikasi sistem tidak muncul | Buka **Pengaturan > Pengingat** dan nyalakan **Tampilkan juga sebagai notifikasi sistem**. Jika kotaknya nonaktif, tray tidak didukung di sistemmu; pengingat tetap muncul di lonceng dan sebagai toast di dalam aplikasi. |
| Data terasa hilang | Buka **Pengaturan > Pulihkan dari Cadangan** dan pulihkan cadangan otomatis atau cadangan pengaman (`sebelum-hapus-...`) terbaru. File `*.bak` di folder data juga menyimpan versi sebelumnya. |
| Pesan bahwa file terkunci (Windows) | Tutup program lain yang membuka folder data (editor, antivirus, sinkronisasi) lalu ulangi. Tidak ada data yang berubah saat pesan ini muncul. |

## Lisensi

MIT. Lihat file [LICENSE](LICENSE).
