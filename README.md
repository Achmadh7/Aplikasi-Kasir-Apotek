# Apotek Lestari — Aplikasi Kasir & Manajemen Stok Apotek

Aplikasi Android untuk Apotek Lestari: kasir penjualan, stok obat, keuangan, laporan laba, pengingat kedaluwarsa/restock, dan catatan pembelian dari PBF — **sepenuhnya offline**, semua data tersimpan di HP.

- **Teknologi:** Kotlin + Jetpack Compose (Material 3 Expressive), database lokal Room/SQLite
- **Versi aplikasi:** 1.0.0 — minimum Android 8.0 (API 26)
- **Zona waktu:** semua batas "hari ini / bulan ini" memakai Asia/Jakarta (WIB)

## Menu & fitur utama

- **Beranda** — ringkasan penjualan hari ini, kartu uang (masuk/keluar/selisih), dan pengingat gabungan.
- **Kasir** — keranjang belanja, pembayaran Tunai/QRIS/Transfer, harga Ecer/Grosir per obat, struk dengan pesan "Semoga Cepat Sembuh".
- **Stok Obat** — status stok Aman (16+) / Sedang (10–15) / Rendah (1–9) / Habis (0), pencarian & filter status, impor/ekspor/template Excel.
- **Keuangan** — saldo kas, catat kas masuk/keluar, grafik arus kas 7 hari.
- **Laporan Laba** — laba per periode dengan grafik.
- **Obat Terlaris** — peringkat obat berdasarkan jumlah item terjual.
- **Pengingat** — stok rendah/habis, obat mendekati kedaluwarsa, dan tagihan PBF mendekati jatuh tempo.
- **Riwayat Penjualan** — riwayat per hari, detail invoice, bagikan/cetak struk.
- **Faktur PBF** — catatan pembelian dari distributor (Cash/Kredit, PPN, tanggal jatuh tempo, tandai lunas). Murni catatan: tidak mengubah stok dan kas.
- **Backup & Restore** — satu berkas JSON lewat pemilih berkas sistem (bisa disimpan ke Google Drive).
- **Tentang Aplikasi** — info versi aplikasi.

Fitur pendukung: cetak struk ke printer thermal Bluetooth (ESC/POS, kertas 58mm), tema gelap/terang, dan nomor invoice format `INV - Hari / Tanggal / Bulan` (mis. `INV - Minggu / 01 / November`).

## Cara build APK

**Tanpa laptop (dipakai sehari-hari):**

1. Buka tab **Actions** di repo ini → workflow **Build APK** → **Run workflow** di branch `main`.
2. Unduh artifact **`app-debug-apk`** (untuk pemakaian langsung) atau **`app-release-apk`** (rilis bertanda tangan, R8) dari halaman run yang selesai.
3. Pasang APK-nya di HP/tablet.

**Dengan Android Studio:** buka folder `kotlin-app/`, tunggu Gradle Sync, lalu Build APK.

Catatan build:

- Build membutuhkan internet — font Poppins diunduh saat build oleh task Gradle `downloadPoppinsFonts`.
- Penandatanganan rilis memakai keystore dari repo privat terpisah (`Sigkey-Apotek-Lestari`) yang diambil lewat secrets GitHub Actions.

## Isi repo

- `kotlin-app/` — proyek Android (Kotlin + Jetpack Compose) yang menjadi aplikasi saat ini.
- `.github/workflows/build-apk.yml` — workflow GitHub Actions untuk build APK debug + rilis.

## Catatan data & keamanan update

- Semua data asli (stok, penjualan, kas, pembelian) tersimpan **lokal di HP** dalam database `apotek-lestari.db` — bukan di repo ini dan bukan di server. Repo hanya berisi kode program: tidak ada database, data penjualan, maupun kredensial.
- Sebagai jaring pengaman, setiap kali aplikasi terdeteksi baru diperbarui, berkas database **disalin otomatis sebelum migrasi berjalan** (maksimal 3 salinan terakhir di penyimpanan internal aplikasi), jadi data sebelum update tetap utuh bila suatu migrasi bermasalah.
- Backup manual tetap disarankan sebelum update besar: menu **Lainnya → Backup & Restore → Backup**.
