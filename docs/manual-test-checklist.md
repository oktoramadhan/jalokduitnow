# Daftar uji manual JalokDuitNow

Jalankan sebelum setiap rilis, di Windows dan macOS. Beri tanda pada setiap butir. Gunakan folder data yang bersih
(lihat bagian "Lokasi data" di README.md) agar data pribadi tidak ikut terpakai.

Catatan: uji dengan data contoh buatan sendiri, bukan data keuangan sungguhan.

## 1. Pertama kali dijalankan

- [ ] Instal dari installer (.msi di Windows, .dmg di macOS) dan buka aplikasi dari menu Start atau Applications.
- [ ] Peringatan SmartScreen (Windows) atau Gatekeeper (macOS) muncul karena aplikasi belum ditandatangani; jalur "More info > Run anyway" atau "Open Anyway" berhasil.
- [ ] Jendela terbuka dengan halaman Beranda, kategori bawaan, dan satu kolom bawaan (Keterangan).
- [ ] Tidak ada transaksi, XP 0, level 1.
- [ ] Ikon aplikasi tampil di Dock atau taskbar.

## 2. Lokasi data

- [ ] Folder `.jalokduitnow` dibuat di folder home pengguna (Windows: `C:\Users\<nama>\.jalokduitnow`, macOS: `/Users/<nama>/.jalokduitnow`).
- [ ] Setelah menambah satu transaksi dan menunggu sebentar, `transactions.json` terisi.
- [ ] Folder `backups` berisi satu `otomatis-<tanggal>.json` setelah aplikasi dibuka.
- [ ] Menutup aplikasi lalu membukanya lagi memuat data yang sama.

## 3. Transaksi: tambah, ubah, hapus

- [ ] Tambah pemasukan dan pengeluaran lewat "Tambah Transaksi"; tanggal, kategori, jumlah, dan catatan tersimpan.
- [ ] Validasi: jumlah kosong, nol, atau negatif ditolak dengan pesan jelas.
- [ ] Ubah transaksi; tabel dan Beranda ikut berubah.
- [ ] Hapus transaksi; ada konfirmasi dan total berubah.
- [ ] Pencarian dan filter periode di halaman Transaksi bekerja.

## 4. Kolom kustom

- [ ] Halaman Kolom: "Tambah Kolom" untuk tiap tipe (teks, angka, tanggal, boolean, pilihan).
- [ ] Kolom wajib: transaksi tanpa nilai ditolak.
- [ ] Ubah urutan, ubah nama, dan hapus kolom; data lama tetap aman.
- [ ] Kolom baru muncul di tabel, form transaksi, dan ekspor CSV.

## 5. Kategori

- [ ] Tambah, ubah nama, ubah ikon dan warna kategori; tidak ada emoji, hanya ikon vektor.
- [ ] Atur budget bulanan kategori.
- [ ] "Arsipkan", "Gabung ke...", dan "Hapus..." bekerja dan transaksi lama tetap terhubung ke kategori yang benar.

## 6. Beranda lintas pergantian tahun

- [ ] Tambah transaksi tanggal 31 Desember dan 1 Januari; ringkasan per bulan dan grafik memisahkan tahun dengan benar.
- [ ] Pindah bulan dengan "Bulan sebelumnya" dan "Bulan berikutnya" melewati Desember ke Januari.
- [ ] Rentang tahun pada pilihan periode ikut bertambah sesuai data.
- [ ] Ubah jam sistem ke tanggal 1 Januari dan buka ulang: periode bawaan berpindah ke bulan baru.

## 7. Quest, badge, dan streak

- [ ] Halaman "Quest & Badge" menampilkan quest minggu ini.
- [ ] Mencatat transaksi menambah XP, memunculkan toast, dan menaikkan streak sekali per hari.
- [ ] Level naik saat XP cukup; ada toast level.
- [ ] Quest selesai membayar hadiah sekali saja; mengubah atau menghapus transaksi tidak menarik kembali hadiah.
- [ ] "Tambah Target" membuat target tabungan; "Ubah" dan "Hapus" bekerja.
- [ ] Streak terputus setelah melewatkan hari (uji dengan mengubah jam sistem) dan freeze token dipakai bila ada.

## 8. Pengingat, tray, dan toast

- [ ] Pengaturan > Pengingat: nyalakan dan matikan tiap pengingat (Pengingat mencatat, Penjaga streak, Laporan donor bulanan, Batas budget).
- [ ] "Kirim notifikasi uji" memunculkan entri di lonceng dan notifikasi sistem.
- [ ] Windows: notifikasi muncul dari tray. macOS: notifikasi muncul atau, jika tray tidak tersedia, ada toast di dalam aplikasi.
- [ ] Jika notifikasi sistem tidak didukung, kotak centang dinonaktifkan dan toast di dalam aplikasi tetap muncul.
- [ ] "Jam tenang" menahan pengingat di jam yang dipilih.
- [ ] Lonceng: tandai dibaca, hapus semua, dan tombol aksi di notifikasi bekerja.
- [ ] Menutup aplikasi menghentikan proses sepenuhnya (tidak ada proses tersisa di Task Manager atau Activity Monitor).

## 9. Link WhatsApp

- [ ] Halaman Laporan: pilih bulan dan "Atur Laporan"; pratinjau teks benar.
- [ ] Donor dengan nomor: "Kirim via WhatsApp" membuka chat ke nomor itu dengan pesan siap kirim; pesan tidak terkirim sampai Send ditekan di WhatsApp.
- [ ] Tanpa nomor donor: WhatsApp terbuka dan meminta memilih penerima.
- [ ] Nomor dengan awalan 0, +62, dan spasi atau tanda hubung dinormalkan dengan benar.
- [ ] "Sertakan detail transaksi" mati secara bawaan; menyalakannya meminta konfirmasi sebelum mengirim.
- [ ] Jika browser atau WhatsApp tidak bisa dibuka, ada pesan yang jelas dan teks tetap bisa disalin.

## 10. Clipboard

- [ ] Tombol "Salin" menyalin laporan; tempel di aplikasi lain menghasilkan teks yang sama dengan pratinjau.
- [ ] Teks berformat polos, tanpa emoji.

## 11. Ekspor dan pemulihan

- [ ] "Ekspor CSV..." (semua transaksi dan satu bulan) terbuka benar di Excel dengan pemisah yang dipilih; kolom kustom ikut.
- [ ] "Buat Cadangan..." menghasilkan file JSON; opsi "Sertakan daftar donor dan nomor telepon" dihormati.
- [ ] Pengaturan > Pulihkan dari Cadangan: pilih file, periksa pratinjau perbandingan, ketik `RESTORE`, lalu ganti data.
- [ ] Setelah pemulihan semua tampilan segar tanpa restart, dan file `sebelum-pemulihan-*.json` dibuat.
- [ ] File rusak atau bukan cadangan ditolak tanpa mengubah data.
- [ ] Pemulihan tidak memberi XP atau badge baru.

## 12. Zona Berbahaya

- [ ] "Hapus semua transaksi": dialog menyebut jumlah, tombol baru aktif setelah mengetik `DELETE` persis, cadangan `sebelum-hapus-*.json` dibuat dan namanya tampil.
- [ ] "Reset progres game": XP, level, streak, quest, badge, dan target tabungan kembali ke awal; transaksi tetap ada; tidak ada XP atau badge baru dari data lama.
- [ ] "Reset pabrik": semua kembali ke keadaan pertama dibuka, tema kembali terang.
- [ ] Kotak "Hapus juga cadangan yang ada" hanya ada di reset pabrik, mati secara bawaan, dan cadangan pengaman yang baru tetap ada.
- [ ] Tombol "Pulihkan cadangan ini" mengembalikan data; tombol Pulihkan di lonceng hilang setelah 15 menit.
- [ ] File lain yang disimpan sendiri di folder data tidak ikut terhapus.
- [ ] Windows: dengan file data terbuka di program lain, muncul pesan "terkunci" yang jelas dan data tidak berubah.

## 13. Tema gelap

- [ ] Sakelar tema di header dan di Pengaturan sinkron; pilihan diingat setelah dibuka ulang.
- [ ] Semua halaman dan dialog terbaca di tema gelap (kontras, ikon, kartu).

## 14. Ukuran jendela

- [ ] Perkecil jendela ke ukuran minimum: tidak ada teks terpotong atau tombol hilang.
- [ ] Perbesar dan maksimalkan: tata letak tetap rapi.
- [ ] Tampilan di layar dengan skala 125 persen dan 150 persen (Windows) serta layar Retina (macOS).
- [ ] Dialog yang berubah isi (pemulihan, hapus data) menyesuaikan ukuran jendelanya.

## 15. Menghapus aplikasi

- [ ] Windows: Settings > Apps > JalokDuitNow > Uninstall menghapus program dan pintasan.
- [ ] macOS: seret JalokDuitNow dari Applications ke Trash.
- [ ] Folder data `.jalokduitnow` tetap ada setelah uninstall (dengan sengaja) dan bisa dihapus manual atau lewat Zona Berbahaya > Reset pabrik sebelum uninstall.
- [ ] Instal ulang memuat data lama bila folder data masih ada.
