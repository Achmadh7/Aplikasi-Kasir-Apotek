# Apotek Lestari — Mode Online (Firebase)

Mode Online adalah lapisan cadangan awan **opsional** di atas aplikasi
yang offline-first. Tanpa konfigurasi Firebase, aplikasi berjalan persis
seperti sebelumnya: semua data di HP, tanpa akun, tanpa server.

Pola yang dipakai sama dengan KasBulanan: Firebase Authentication untuk
masuk, Cloud Firestore sebagai cermin data per pengguna, dan aturan
konflik **last-write-wins** (baris dengan cap waktu ubah terbaru yang
menang; penghapusan menyebar sebagai tombstone).

## Cara kerja

- Saat build, plugin `google-services` hanya diterapkan bila file
  `kotlin-app/app/google-services.json` ada. File itu yang menyalakan
  Mode Online.
- Bila Mode Online terkonfigurasi, aplikasi membuka **gerbang login** di
  awal (Google, atau Lewati sebagai tamu per sesi). Sejak 2026-11-10
  login HANYA Google — formulir email/kata sandi dihapus atas keputusan
  pemilik: akunnya akun Google dan jalur email hanya berisiko membelah
  akun. Tamu memakai aplikasi sepenuhnya secara lokal; data tamu tidak
  tersinkron.
- Yang tersinkron per akun (`users/{uid}/...`): `obat`, `penjualan`,
  `penjualan_item`, `kas`, `pbf`, `pbf_item`, `pemasok` — satu dokumen per baris,
  dikunci `remoteId`-nya.
- Sinkronisasi berjalan: otomatis beberapa detik setelah data berubah
  (saat masuk; senyap — tanpa pemberitahuan, statusnya terlihat di ikon
  awan Beranda), berkala tiap 6 jam (WorkManager, hanya saat ada
  jaringan), dan manual dari menu Lainnya / ikon awan di Beranda.
- Mengembalikan backup JSON saat sedang masuk menandai semua baris
  kotor, jadi isi restore tersinkron naik menggantikan isi awan.
- **Reset Semua Data** (paling bawah menu Lainnya) menghapus data lokal
  DAN salinan awan akun yang sedang masuk — awan dihapus lebih dulu,
  dan bila penghapusan awan gagal, data lokal tidak jadi dihapus.
  Dilindungi dialog konfirmasi Ya/Tidak; akun tetap dalam keadaan masuk.
- Masuk hanya lewat Google (lihat di atas), jadi risiko akun terbelah
  antar-metode tidak ada di aplikasi ini. Tetap konsisten memakai SATU
  akun Google utama — akun Google yang berbeda berarti pohon data yang
  berbeda.

## Menyiapkan Firebase (satu kali, ±10 menit)

1. Buka [Firebase Console](https://console.firebase.google.com). Buat
   proyek baru, ATAU pakai proyek yang sudah ada (misalnya proyek
   KasBulanan — Apotek Lestari produksi memang berbagi proyek itu):
   data tetap terpisah karena nama koleksi Firestore-nya berbeda.
2. Tambahkan aplikasi Android dengan package persis `id.apotek.lestari`.
3. Daftarkan sidik jari sertifikat rilis (SHA-1) dari keystore aplikasi
   di Project settings — diperlukan untuk Masuk dengan Google.
4. Unduh `google-services.json`, letakkan di `kotlin-app/app/`, lalu
   kirim/commit file itu ke repo (file ini aman berada di repo; yang
   rahasia adalah aturan server di bawah).
5. Di menu Authentication: aktifkan provider **Google** (isi support
   email proyek). Provider Email/Password tidak dipakai aplikasi ini.
6. Buat **Cloud Firestore** (mode produksi), buka tab Rules, tempel isi
   `firestore.rules` dari root repo ini, lalu Publish.
7. Jalankan workflow build — APK berikutnya membawa Mode Online aktif
   dan gerbang login akan muncul saat aplikasi dibuka.

## Verifikasi sinkronisasi pertama

1. Buka aplikasi, masuk dengan Google. (Catatan:
   proyek produksi berbagi proyek Firebase dengan KasBulanan,
   `kasbulanan-6171e` — pohon data Apotek berada di koleksi
   `obat`/`penjualan`/dll. di bawah `users/{uid}` yang sama.)
2. Catat satu obat / satu penjualan uji.
3. Di Firebase Console: Authentication > Users harus memuat akunmu;
   Firestore Database > Data > `users/{uid}` harus memuat koleksi
   `obat`/`penjualan`/dll. berisi dokumen uji tadi.

## Yang BUKAN cadangan ini

- File Excel adalah laporan, bukan cadangan.
- Backup JSON manual (menu Backup) tetap jalur cadangan terpisah yang
  disarankan dilakukan setelah momen penting; simpan file-nya di luar
  HP (mis. Google Drive) secara manual.
