# Arsitektur — Apotek Lestari

Dokumen singkat peta proyek: struktur kode, database, dan aliran data utama.
Ditulis berdasarkan kode di head saat ini (versi aplikasi 1.1.0).

## Gambaran umum

Apotek Lestari adalah aplikasi kasir + inventaris apotek **sepenuhnya offline**:
semua data tersimpan lokal di HP (Room/SQLite), tanpa server dan tanpa akun.
Satu modul aplikasi (`kotlin-app/app`), satu Activity, navigasi antar-layar
diatur di `MainActivity.kt` dengan bottom bar Material 3 Expressive
(ShortNavigationBar): **Beranda, Kasir, Stok, Keuangan**, dan sheet **Lainnya**
(Laba, Pengingat, Riwayat, Faktur PBF, Obat Terlaris, Backup & Restore,
Tentang). Aplikasi dwibahasa ID/EN (fungsi `tr(id, en)` di tema) dan punya
mode gelap/terang.

## Teknologi

- Kotlin 2.2.20, Jetpack Compose, Material 3 Expressive (material3 1.5.0-alpha08)
- Skema warna Fidelity dari seed `#0061A4`
- Room (database lokal, versi 12) — tanpa jaringan sama sekali
- Apache POI 5.2.5 untuk impor/ekspor Excel (.xlsx)
- Android Gradle Plugin 8.11.1, Gradle 8.13, JDK 17
- minSdk 26, targetSdk 34, compileSdk 36

## Struktur proyek

```
kotlin-app/
  build.gradle.kts            konfigurasi plugin tingkat proyek (versi saja)
  app/
    build.gradle.kts          konfigurasi aplikasi + signing release + penjaga signing
    src/main/java/id/apotek/lestari/
      MainActivity.kt         satu-satunya Activity: tema, navigasi, bottom bar
      data/
        Models.kt             model data (Medicine, Sale, CashEntry, Purchase, ...)
        Repository.kt         satu-satunya pintu logika bisnis & query
        ExcelHelper.kt        template/impor/ekspor Excel stok
        BackupHelper.kt       backup/restore JSON
        local/AppDatabase.kt  entity Room, DAO, migrasi, DatabaseProvider
      ui/
        theme/                Theme.kt — skema warna, tr() ID/EN, stockLabel()
        screens/              satu berkas per layar (BerandaScreen, KasirScreen,
                              StokScreen, KeuanganScreen, LabaScreen, PbfScreen,
                              PengingatScreen, RiwayatScreen, BackupScreen)
                              + Screens.kt (komponen bersama & layar kecil)
        BluetoothPrinter.kt   cetak struk ESC/POS ke printer thermal terpasang
        ReceiptShare.kt       struk sebagai gambar untuk dibagikan
        LogoData.kt           logo apotek (Base64) untuk header & struk
```

Layar tidak menyentuh DAO langsung — semua lewat `Repository`, jadi aturan
bisnis (batas stok, snapshot harga, sumber kas) konsisten di semua layar.

## Database (Room, versi 12)

| Tabel | Isi |
|---|---|
| `medicines` | Obat: nama, kategori, satuan, harga beli/jual/grosir, stok, kedaluwarsa, barcode (indeks unik) |
| `sales` | Penjualan: nomor invoice `INV - dd/MM/yy` (indeks TIDAK unik — penjualan seharian berbagi nomor tampil), total, metode bayar, bayar/kembalian |
| `sale_items` | Baris penjualan: nama, qty, harga snapshot, subtotal, `priceType` ECER/GROSIR |
| `cash_entries` | Kas masuk/keluar: `type` in/out, `source` (`sale` = dari penjualan, `manual` = catat kas) |
| `purchases` | Faktur PBF: nama PBF, nomor faktur, total termasuk PPN, CASH/KREDIT, jatuh tempo |
| `purchase_items` | Baris item faktur PBF |

- Migrasi berurutan 1 → 12 terdaftar di `AppDatabase.kt`; jangan pernah
  mengubah skema tanpa migrasi baru.
- Jaring pengaman update: `DatabaseProvider` menyalin berkas database ke
  `filesDir/db-snapshots/` (maks. 3 terbaru) sebelum Room dibuka, bila waktu
  update paket berubah — salinan hilang bila aplikasi di-uninstall, jadi
  Backup JSON tetap pelindung utama.
- DAO: `MedicineDao`, `SaleDao`, `CashDao`, `PurchaseDao`.

## Aliran data utama

**Penjualan (Kasir).** Keranjang menahan qty per obat (tidak bisa melewati
stok; `Repository.createSale` juga menolak penjualan melebihi stok, sehingga
stok tidak pernah negatif) → harga yang dipakai di-snapshot ke
`sale_items.price` (ecer atau grosir per baris) → stok obat berkurang → satu
`cash_entries` bertipe `in` dengan `source = "sale"` dibuat → invoice &
struk (gambar, bagikan, atau cetak thermal Bluetooth).

**Stok.** Tambah/edit lewat formulir, atau impor Excel (dicocokkan per
Nama+Kategori+Satuan; templat & ekspor 8 kolom termasuk Harga Grosir).
Status stok murni dari jumlah: 0 = Habis, 1–9 = Rendah, 10–15 = Sedang,
16+ = Aman (`stockLabel()` di Theme.kt juga kunci logika internal).

**Keuangan & Laba.** Saldo Kas = semua kas masuk − keluar. Khusus
"Pemasukan Hari Ini" di Beranda hanya menghitung penjualan (`source = "sale"`);
kas manual hanya memengaruhi Saldo Kas. Laba dihitung berbasis penjualan:
omzet − HPP dari harga beli saat ini per obat terjual.

**Faktur PBF.** Murni catatan pembelian: **tidak** mengubah stok, kas, harga
beli, laba, maupun Saldo Kas. Pembelian Kredit punya tanggal jatuh tempo,
bisa ditandai lunas (berubah jadi CASH), dan tagihan yang jatuh tempo ≤ 7
hari muncul di Pengingat serta kartu Pengingat Beranda. Ringkasan belanja
hanya menghitung CASH.

**Backup & Restore.** Satu berkas JSON `{version, app, medicines, sales,
saleItems, cashEntries, purchases, purchaseItems}` lewat Storage Access
Framework (bisa disimpan ke Drive). Restore menimpa seluruh data dalam satu
transaksi Room, setelah konfirmasi.

## Build & rilis

CI (`.github/workflows/main.yml`, dijalankan manual) membangun APK debug,
lalu mengambil keystore dari repo privat Sigkey, menulis `signing.properties`
dari GitHub Secrets, dan membangun APK release (`app-release-apk`).
`app/build.gradle.kts` membaca `signing.properties` itu; penjaga
`verifyReleaseSigning` menggagalkan build release bila signing tidak
lengkap — APK release tidak akan pernah terbit bertanda tangan kunci debug
secara diam-diam.
