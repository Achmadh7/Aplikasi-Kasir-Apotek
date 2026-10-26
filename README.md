# Aplikasi Kasir Apotek Lestari

Source code lengkap Aplikasi Apotik Lestari: kasir, stok obat, keuangan, laporan laba, dan pengingat kedaluwarsa/restock untuk apotek.

Versi ini adalah versi saat ini dari aplikasi web:
- Tampilan Material 3 + frosted glass (termasuk mode gelap)
- Semua batas "hari ini / bulan ini" memakai zona waktu Asia/Jakarta (WIB)
- 7 menu: Beranda, Kasir, Stok Obat, Keuangan, Laba, Pengingat, Riwayat Penjualan — tanpa menu Kode Sumber dan tanpa kartu Pasang Android di Beranda

## Isi repo

- `app-source/` — source lengkap aplikasi web (client React/TypeScript, server actions, migrasi database Drizzle, dan aset PWA). Ini kode yang menjalankan aplikasi web yang dipakai sehari-hari. Di dalamnya juga ada `source-code-apotik-lestari.txt`, gabungan seluruh kode web dalam satu file teks.
- `kotlin-app/` — proyek Android Studio murni Kotlin + Jetpack Compose (bukan WebView), siap di-compile menjadi APK. Panduan lengkap ada di `kotlin-app/README-KOTLIN.md` dan ringkasannya di `README-APK.md`.
- `.github/workflows/build-apk.yml` — workflow GitHub Actions untuk build APK debug tanpa laptop: buka tab **Actions** di repo ini, jalankan workflow **Build APK**, lalu unduh artifact `app-debug-apk`.

## Catatan data

Data asli (stok, penjualan, keuangan) tersimpan di server aplikasi web, bukan di repo ini dan bukan di HP. Repo ini hanya berisi kode program — tidak ada database, tidak ada data penjualan, dan tidak ada kredensial.

Untuk APK: ganti `BASE_URL` di `kotlin-app/app/build.gradle.kts` dari `https://example.invalid/` ke alamat server aplikasi web aslimu sebelum compile. Selama belum diganti, aplikasi Kotlin berjalan dalam mode demo lokal. APK tetap butuh internet untuk data asli, sama seperti versi web/PWA.

## Compile APK singkat

1. Dengan Android Studio: buka folder `kotlin-app/`, tunggu Gradle Sync, lalu **Build > Build APK(s)**. Hasil: `app/build/outputs/apk/debug/app-debug.apk`.
2. Tanpa laptop: pakai tab **Actions** di repo ini seperti di atas, lalu install APK hasil download ke HP/tablet.
