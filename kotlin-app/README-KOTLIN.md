# Apotik Lestari — Versi Kotlin Native (Android)

Proyek Android Studio murni Kotlin + Jetpack Compose. BUKAN WebView dan BUKAN Capacitor: tidak ada folder www/, tidak ada HTML di dalam APK. Semua layar ditulis ulang sebagai composable asli mengikuti versi web (Beranda, Kasir, Stok, Keuangan, Laba, Pengingat, Riwayat), dengan tema Material 3 teal apotek #00696D dan aturan stok yang sama (>20 Aman, 20 Sedang, <20 Rendah, 0 Habis).

## Isi source.zip

1. `app-source/` — source code lengkap proyek web asli (client, server, database/migrasi, PWA). Ini kode yang menjalankan aplikasi web yang sekarang kamu pakai, dipertahankan apa adanya sebagai referensi.
2. `kotlin-app/` — proyek Kotlin native ini, siap dibuka di Android Studio.

## Syarat di komputer

- Android Studio terbaru (di dalamnya sudah ada Android SDK dan JDK 17)
- Internet saat Gradle Sync pertama kali (mengunduh Gradle 8.7 dan dependency)

## Langkah compile jadi APK

1. Extract `source.zip`, buka folder `kotlin-app/`.
2. Di Android Studio pilih **Open** lalu arahkan ke folder `kotlin-app` itu.
3. Tunggu **Gradle Sync** selesai. Proyek ini memakai Kotlin 2.0.20, AGP 8.5.2, compileSdk 34, minSdk 24, dan Gradle 8.7 (di `gradle/wrapper/gradle-wrapper.properties`).
   - Baris perintah alternatif: `gradle wrapper --gradle-version 8.7` bila file `gradlew` belum ada, atau buka saja di Android Studio dan biarkan ia membuatnya.
4. Compile dari HP memakai GitHub Actions: upload ISI folder `kotlin-app/` ke root repo GitHub (jadi `settings.gradle.kts` ada di root repo), buat file `.github/workflows/build-apk.yml` berisi job `assembleDebug` (setup JDK 17 + setup Android SDK, lalu `./gradlew assembleDebug`), jalankan workflow dari tab Actions, lalu unduh artifact APK-nya. Langkah ini juga cara Termux: di Termux, compile-nya tidak dilakukan di HP — APK di-build di server GitHub.
5. Di Android Studio: **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
   Hasil debug: `kotlin-app/app/build/outputs/apk/debug/app-debug.apk` — copy ke HP/tablet lalu install (izinkan "Instal dari sumber tidak dikenal" bila diminta).
6. Rilis: **Build > Generate Signed App Bundle / APK**, buat keystore baru dan SIMPAN baik-baik.

## Izin kamera & scan barcode

Sudah termasuk: `AndroidManifest.xml` berisi `INTERNET`, `CAMERA`, dan `uses-feature camera required=false`. Scanner asli ada di `ui/components/BarcodeScanner.kt` (CameraX + ML Kit Barcode Scanning), dipakai di Kasir dan Stok, lengkap dengan permintaan izin runtime dan kolom ketik manual sebagai cadangan (juga cocok untuk scanner USB).

## Catatan jujur soal data (penting)

Data asli aplikasi ini — stok, penjualan, keuangan — tersimpan di SERVER web (server actions + database di server), bukan di HP. Proyek Kotlin ini sudah disiapkan untuk tersambung ke server yang sama:

- Di `app/build.gradle.kts` ada `BASE_URL` (BuildConfig) yang secara bawaan berisi `https://example.invalid/`.
- Ganti dengan alamat asli server aplikasi web kamu, contoh `"https://alamat-aplikasi-kamu/"`, lalu Sync lagi. Setelah itu semua layar memakai data asli lewat `data/ApiService.kt` (Retrofit, POST `actions` dengan envelope `{action, args}` yang sama seperti versi web) dan `data/Repository.kt`.
- Selama BASE_URL masih `example.invalid`, aplikasi berjalan dalam **MODE DEMO LOKAL**: tampilannya lengkap tetapi datanya hanya contoh lokal di HP (lihat `DemoData` di Repository.kt) dan ada banner peringatan demo di setiap layar. Mode demo BUKAN data aslimu.
- Karena itu APK tetap butuh koneksi internet untuk data asli, sama seperti versi web dan PWA.

## Struktur kode

- `MainActivity.kt` — ComponentActivity, `setContent` Material 3, NavHost dengan bottom navigation 7 menu.
- `data/Models.kt` — model obat, penjualan, kas, dashboard, laporan laba (HPP, margin, per obat/kategori) + request/response tiap action.
- `data/ApiService.kt` — 17 server actions versi web sebagai fungsi Retrofit bertipe.
- `data/ApiClient.kt` — Retrofit + OkHttp + logging, membaca `BuildConfig.BASE_URL`.
- `data/Repository.kt` — pintu data UI, sealed class `ApiResult`, dan fallback demo lokal yang diberi komentar jelas.
- `ui/screens/Screens.kt` — tujuh layar: Beranda (kartu ringkasan, peringatan stok/kadaluarsa, obat terjual), Kasir (keranjang, tunai/kembalian, scan), Stok (cari, chip status stok, form tambah/edit, scan), Keuangan (kas masuk/keluar), Laba (HPP/margin/periode), Pengingat (stok menipis + cari nama/kategori), Riwayat (invoice "Apotek Lestari").
- `ui/theme/Theme.kt` — Material 3 light/dark dari seed #00696D, format Rupiah, label stok.
- Logo & ikon: file logo asli ada di `app-source/` dan di versi web; untuk ikon launcher, buat lewat Android Studio **Image Asset Studio** dari logo tersebut bila ingin mengganti ikon bawaan.

File ZIP ini dibuat otomatis dari proyek setiap aplikasi diperbarui, jadi isinya selalu versi terbaru.
