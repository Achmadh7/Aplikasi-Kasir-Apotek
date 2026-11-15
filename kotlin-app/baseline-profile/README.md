# Baseline Profile — cara generate (Apotek Lestari)

Modul ini membuat profil kompilasi awal (Baseline Profile) supaya aplikasi
lebih cepat dibuka dari keadaan dingin. Profil HANYA bisa dihasilkan di HP
fisik / emulator — tidak bisa dari CI, karena butuh perangkat tersambung.

## Prasyarat

- HP POCO F5 tersambung ke laptop (USB debugging aktif), API 33+ (Android 16/17) — tanpa root.
- Android Studio terbuka di folder `kotlin-app/`.
- Bahasa HP Indonesia (generator mencari tombol "Lewati" dan tab "Kasir"/"Stok"/"Beranda").

## Generate profil

Dari terminal, di folder `kotlin-app/`:

```
./gradlew :app:generateBaselineProfile
```

atau di Android Studio: jalankan Run Configuration **"Generate Baseline Profile"**.

Tugas ini membangun aplikasi (varian non-minified release), menjalankan
generator di HP, lalu menyalin hasilnya ke:

```
app/src/main/generated/baselineProfiles/baseline-prof.txt
app/src/main/generated/baselineProfiles/startup-prof.txt
```

Commit kedua berkas itu. APK release berikutnya otomatis memakai profilnya
(ProfileInstaller memasangnya saat aplikasi pertama dibuka — penting karena
APK dipasang sideload, bukan dari Play).

## Kalau gagal karena signing

Tugas generate membangun varian release. Kalau muncul error
`signing.properties tidak ada/kurang lengkap`, buat file
`kotlin-app/signing.properties` dari keystore Apotek (repo
Sigkey-Apotek-Lestari), lalu ulangi perintahnya.

## Ukur hasilnya (sebelum vs sesudah)

Jalankan `StartupBenchmarks.startup` di modul ini (klik gutter di samping
fungsi `startup`, atau `./gradlew :baseline-profile:connectedBenchmarkReleaseAndroidTest`):

- Sebelum profil ada: catat median `timeToInitialDisplayMs`.
- Sesudah profil di-commit + APK release dipasang: jalankan lagi,
  bandingkan mediannya.

## Kapan regenerate

- Setelah redesain layar / tambah alur utama baru.
- Setelah naik versi Compose / AGP.
- Sebelum rilis.

Profil hanya mempercepat jalur yang dijalani generator: cold start,
gerbang login, Beranda, Kasir, Stok. Jalur lain (Lainnya, Laba, PBF, ...)
tidak ikut terkompilasi lebih awal.
