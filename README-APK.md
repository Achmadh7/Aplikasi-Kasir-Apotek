APLIKASI APOTIK LESTARI — CARA COMPILE JADI APK ANDROID
=====================================================================

Isi paket source ini:

1. app-source/ — source code lengkap proyek web (client, server,
   database/migrasi, PWA), versi saat ini: tampilan Material 3 +
   frosted glass, zona waktu Asia/Jakarta (WIB) untuk hitungan
   "hari ini", dan 7 menu tanpa menu Kode Sumber.

2. kotlin-app/ — proyek Android Studio MURNI KOTLIN + Jetpack Compose:
   murni native, tidak ada folder web di dalamnya, dengan 7 menu yang
   sama seperti versi web saat ini.
   Buka folder kotlin-app/ di Android Studio untuk compile APK.
   Panduan lengkap ada di kotlin-app/README-KOTLIN.md.

Ringkasan compile:
- Android Studio: Open folder kotlin-app, tunggu Gradle Sync,
  lalu Build > Build APK(s). Hasil: app/build/outputs/apk/debug/app-debug.apk
- Dari HP via GitHub Actions: repo ini sudah berisi workflow
  .github/workflows/build-apk.yml. Buka tab Actions di GitHub,
  jalankan workflow "Build APK", lalu unduh artifact app-debug-apk.
- Termux di HP: compile dilakukan lewat GitHub Actions (bukan di HP).

Catatan data: data asli ada di server web. Ganti BASE_URL di
kotlin-app/app/build.gradle.kts dari https://example.invalid/ ke
alamat server aslimu. Selama belum diganti, aplikasi Kotlin berjalan
dalam mode demo lokal (data contoh di HP saja). Lihat README-KOTLIN.md.
