# Apotek Lestari — Pharmacy Cashier & Inventory Management App

An Android app for Apotek Lestari: sales cashier, medicine stock, cash book, profit reports, expiry/restock reminders, and purchase records from distributors (PBF) — **fully offline**, with all data stored on the device.

[![Build APK](https://github.com/Achmadh7/Apk-Apotik-Lestari/actions/workflows/main.yml/badge.svg)](https://github.com/Achmadh7/Apk-Apotik-Lestari/actions)

- **Technology:** Kotlin + Jetpack Compose (Material 3 Expressive), local Room/SQLite database
- **App version:** 1.0.0 — minimum Android 8.0 (API 26)
- **Time zone:** all "today / this month" boundaries use Asia/Jakarta (WIB)

## Screenshots

| Home | Medicine Stock |
|:---:|:---:|
| <img src="docs/screenshots/beranda.jpg" width="280" alt="Home"> | <img src="docs/screenshots/stok-obat.jpg" width="280" alt="Medicine Stock"> |

| Cashier | Finance |
|:---:|:---:|
| <img src="docs/screenshots/kasir.jpg" width="280" alt="Cashier"> | <img src="docs/screenshots/keuangan.jpg" width="280" alt="Finance"> |

| More Menu | Profit Report |
|:---:|:---:|
| <img src="docs/screenshots/menu-lainnya.jpg" width="280" alt="More Menu"> | <img src="docs/screenshots/laporan-laba.jpg" width="280" alt="Profit Report"> |

| Best-Selling Medicines | Reminders |
|:---:|:---:|
| <img src="docs/screenshots/obat-terlaris.jpg" width="280" alt="Best-Selling Medicines"> | <img src="docs/screenshots/pengingat.jpg" width="280" alt="Reminders"> |

| Sales History | Supplier Invoices (Faktur PBF) |
|:---:|:---:|
| <img src="docs/screenshots/riwayat-penjualan.jpg" width="280" alt="Sales History"> | <img src="docs/screenshots/faktur-pbf.jpg" width="280" alt="Supplier Invoices"> |

*Note: the screenshots show an earlier interface. The app has since moved to the official Material 3 Expressive design and components.*

## Menus & main features

- **Beranda (Home)** — today's sales summary with a "Pemasukan Hari Ini" (today's revenue) hero card, a money card (cash in / cash out / profit-loss), and combined reminders.
- **Kasir (Cashier)** — shopping cart, Cash/QRIS/Transfer payments, per-medicine Retail/Wholesale (Ecer/Grosir) pricing, and receipts carrying the "Semoga Cepat Sembuh" (get well soon) message.
- **Stok Obat (Medicine Stock)** — stock status Safe (16+) / Medium (10–15) / Low (1–9) / Out of stock (0), search & status filters, Excel import/export/template.
- **Keuangan (Finance)** — cash balance, cash in/out entries (Catat Kas), and a 7-day cash-flow chart.
- **Laporan Laba (Profit Report)** — profit per period with charts.
- **Obat Terlaris (Best-Selling Medicines)** — medicine ranking by units sold.
- **Pengingat (Reminders)** — low/out-of-stock medicines, medicines nearing expiry, and distributor bills nearing their due date.
- **Riwayat Penjualan (Sales History)** — daily history, invoice details, receipt sharing/printing.
- **Faktur PBF (Supplier Invoices)** — purchase records from distributors (Cash/Credit, VAT/PPN, due dates, mark-as-paid). Records only: they never change stock or cash.
- **Backup & Restore** — a single JSON file through the system file picker (can be saved to Google Drive).
- **Tentang Aplikasi (About)** — app version info.

Supporting features: receipt printing to Bluetooth thermal printers (ESC/POS, 58 mm paper), system printing/PDF via the Android print framework, dark/light themes, and invoice numbers in the `INV - dd/MM/yy` format (e.g. `INV - 01/11/26`).

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.2.20 |
| UI | Jetpack Compose — Material 3 Expressive (`material3` 1.5.0-alpha08, Compose BOM 2025.10.00) |
| Theme | `MaterialExpressiveTheme` + `MotionScheme.expressive()`; official components (`ShortNavigationBar`, `SegmentedButton`, chips, `ListItem`); color scheme generated with Google's Material Color Utilities algorithm (Expressive style, seed `#0061A4`, 2025 color spec); the semantic profit/warning colors are harmonized to the scheme with the same library (`Blend.harmonize`), and status badges use a neutral surface with a small colored dot so colors never fight each other |
| Database | Room 2.7.2 (SQLite), fully offline |
| Excel | Apache POI 5.2.5 (`.xlsx` import/export/template) |
| Build | AGP 8.11.1, Gradle 8.13, compileSdk 36, targetSdk 34, minSdk 26 |
| Printing | Android PrintManager + ESC/POS over Bluetooth |

## How to build the APK

**Without a computer (the everyday flow):**

1. Open the **Actions** tab in this repo → the **Build APK** workflow → **Run workflow** on the `main` branch.
2. Download the **`app-release-apk`** artifact (signed release build, R8 — the one used day to day) or **`app-debug-apk`** (debug build) from the finished run page.
3. Install the APK on the phone/tablet.

**With Android Studio:** open the `kotlin-app/` folder, wait for Gradle Sync, then Build APK.

Build notes:

- Builds require internet access (Gradle dependency resolution).
- Release signing uses a keystore from a separate private repository (`Sigkey-Apotek-Lestari`), supplied through GitHub Actions secrets.

## Project structure

- `.github/workflows/main.yml` — the GitHub Actions workflow that builds the debug + release APKs.
- `kotlin-app/` — the Android project (Kotlin + Jetpack Compose):
  - `app/src/main/java/id/apotek/lestari/MainActivity.kt` — the app shell: tab navigation (official `ShortNavigationBar`), the Lainnya (More) sheet, screen routing.
  - `.../ui/screens/Screens.kt` — every screen (Home, Cashier, Stock, Finance, Profit, Reminders, History, Supplier Invoices, Backup).
  - `.../ui/theme/Theme.kt` — the Material Expressive theme, the generated color scheme, IDR/date formatting, and the stock-status scale.
  - `.../ui/` — also `BluetoothPrinter.kt` (ESC/POS printing), `ReceiptShare.kt` (receipt image sharing), `LogoData.kt` (the pharmacy logo shown on receipts).
  - `.../data/` — `Repository.kt` (business logic), `Models.kt`, `ExcelHelper.kt` (.xlsx import/export), `BackupHelper.kt` (JSON backup/restore), and `local/` (Room database, entities, DAOs, migrations).

## FAQ

- **Is any data uploaded to a server?** No. The app is fully offline; everything lives in the on-device database (see the notes below).
- **How do I move to a new phone?** On the old phone: **Lainnya → Backup & Restore → Backup** and save the JSON file (for example to Google Drive). On the new phone, install the app and choose **Restore** with that file — all data is replaced in one transaction.
- **The Bluetooth printer does not appear — what do I check?** Pair the printer in Android's Bluetooth settings first; the app prints only to already-paired printers and remembers the one you pick.
- **Which Excel format does stock import use?** Use the template the app provides in the Stock menu — imports match medicines by name + category + unit, so existing rows update instead of duplicating.

## Data & update safety notes

- All real data (stock, sales, cash, purchases) lives **locally on the device** in the `apotek-lestari.db` database — not in this repo and not on any server. This repo contains program code only: no database, sales data, or credentials.
- As a safety net, whenever the app detects it has just been updated, the database file is **automatically copied before migrations run** (up to the 3 most recent copies in the app's internal storage), so pre-update data stays intact if a migration ever goes wrong.
- A manual backup is still recommended before major updates: **Lainnya (More) → Backup & Restore → Backup**.
