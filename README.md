# Pharmacy Cashier App — Cashier & Inventory Management for Pharmacies

An Android app for pharmacies: sales cashier, medicine stock, cash book, profit reports, expiry/restock reminders, and purchase records from distributors (PBF) — **offline-first**, with all data stored on the device and an optional **Online Mode** that mirrors it to your own Firebase project when you sign in.

[![Build APK](https://github.com/Achmadh7/Aplikasi-Kasir-Apotek/actions/workflows/main.yml/badge.svg)](https://github.com/Achmadh7/Aplikasi-Kasir-Apotek/actions)

- **Technology:** Kotlin + Jetpack Compose (Material 3 Expressive), local Room/SQLite database
- **App version:** 1.2.0 — minimum Android 8.0 (API 26)
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

*Note: screenshots are from 5 November 2026 (version 1.2.0) and predate Online Mode and the current headers; they will be refreshed from a newer build.*

## Menus & main features

- **Beranda (Home)** — a merged hero panel with a Hari Ini / Bulan Ini (Today / This Month) period switch covering revenue, cash out, balance difference, and transaction count, plus combined reminders.
- **Kasir (Cashier)** — shopping cart with one combined total + payment-method panel, Cash/QRIS/Transfer payments, large quick-amount cards for cash received (exact amount or the next 50k round-ups, each showing its change), per-medicine Retail/Wholesale (Ecer/Grosir) pricing, and receipts carrying the "Semoga Cepat Sembuh" (get well soon) message.
- **Stok Obat (Medicine Stock)** — stock status Safe (16+) / Medium (10–15) / Low (1–9) / Out of stock (0), search & status filters, Excel import/export/template.
- **Keuangan (Finance)** — cash balance, cash in/out entries (Catat Kas), and a 7-day cash-flow chart.
- **Laporan Laba (Profit Report)** — side-by-side revenue and net-profit cards (net = revenue − operating costs, shown with its net margin), a revenue/expense breakdown, and a daily net-profit chart per period. The app deliberately does not track buy prices, so there is no COGS/HPP figure.
- **Obat Terlaris (Best-Selling Medicines)** — medicine ranking by units sold.
- **Pengingat (Reminders)** — low/out-of-stock medicines, medicines nearing expiry, and distributor bills nearing their due date.
- **Riwayat Penjualan (Sales History)** — daily history, invoice details, receipt sharing/printing.
- **Faktur PBF (Supplier Invoices)** — purchase records from distributors (Cash/Credit, VAT/PPN, due dates, mark-as-paid). Records only: they never change stock or cash.
- **Pemasok (Suppliers)** — a saved directory of distributor names and contacts, picked from a list in the PBF form instead of retyping; names can be imported once from past purchase records.
- **Backup & Restore** — a single JSON file through the system file picker (can be saved to Google Drive); restore shows a preview (file date and record counts) before anything is replaced. Optional **scheduled automatic backup** (daily or weekly) writes the same JSON file to a folder of your choice, silently in the background.
- **Online Mode (optional)** — a sign-in gate at launch (Google sign-in only, or continue as guest — email/password sign-in was removed on 2026-11-10 by the owner's decision: the account is a Google account, and the email path only risked split accounts), automatic sync to **your own Firebase project** (Cloud Firestore, one tree per account under `users/{uid}`) a few seconds after any change while signed in — silently, in the background — plus a periodic 6-hour sync, a sync status card and manual sync in Lainnya, and **Reset Semua Data** (bottom of Lainnya) that wipes this device *and* the signed-in account's cloud copy after a Yes/No confirmation. Sync feedback is quiet by design: opening the app syncs silently as well, a successful manual or just-signed-in sync shows a small floating “Synced” badge at the top centre, and failures are reported with a snackbar. The on-device database stays the source of truth; a build without a Firebase config file never shows the gate and runs fully local. Setup and details: `docs/ONLINE.md`.
- **Tentang Aplikasi (About)** — app version info.

Supporting features: receipt printing to Bluetooth thermal printers (ESC/POS, 58 mm paper), system printing/PDF via the Android print framework, dark/light themes (switched instantly), a full **Indonesian/English interface switch** (in the Lainnya menu — every screen, receipt, and message follows the chosen language; stored data values are never translated), and invoice numbers in the `INV - dd/MM/yy` format (e.g. `INV - 01/11/26`).

## Tech stack

| Area | Technology |
|---|---|
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose — Material 3 Expressive (`material3` 1.5.0-rc01, Compose BOM 2026.09.00), Navigation Compose 2.10.2 |
| Theme | `MaterialExpressiveTheme` + `MotionScheme.expressive()`; official components (`ShortNavigationBar`, `SegmentedButton`, chips, `ListItem`); color scheme generated with Google's Material Color Utilities algorithm (Fidelity style, seed `#0061A4`, 2025 color spec) |
| Database | Room 2.8.5 (SQLite), offline-first — database version 15 (sync columns + soft deletes for Online Mode; v14 retired the stored buy prices, v15 added the PBF supplier directory) |
| Online sync (optional) | Firebase Auth + Cloud Firestore (Firebase BOM 33.5.1), WorkManager 2.9.1 periodic sync, last-write-wins merging |
| Excel | Apache POI 5.5.1 (`.xlsx` import/export/template) |
| Build | AGP 9.5.1, Gradle 9.6.0, compileSdk 37, targetSdk 36, minSdk 26 |
| CI | GitHub Actions — 32 JVM unit tests, Android Lint (report-only), debug + signed release builds |
| Printing | Android PrintManager + ESC/POS over Bluetooth |

## How to build the APK

**Without a computer (the everyday flow):**

1. Open the **Actions** tab in this repo → the **Build APK** workflow → **Run workflow** on the `main` branch.
2. Download the **`Apotek Lestari-1.2.0-Release`** artifact (signed release build, R8 — the one used day to day) or **`app-debug-apk`** (debug build) from the finished run page.
3. Install the APK on the phone/tablet.

**With Android Studio:** open the `kotlin-app/` folder, wait for Gradle Sync, then Build APK.

Build notes:

- Builds require internet access (Gradle dependency resolution).
- Release signing uses a keystore from a separate private repository (`Sigkey-Apotek-Lestari`), supplied through GitHub Actions secrets.
- Online Mode turns on only when `kotlin-app/app/google-services.json` exists (the config of your own Firebase project — see `docs/ONLINE.md`); builds without it are fully local.

## Project structure

- `.github/workflows/main.yml` — the GitHub Actions workflow that builds the debug + release APKs.
- `kotlin-app/` — the Android project (Kotlin + Jetpack Compose):
  - `app/src/main/java/id/apotek/lestari/MainActivity.kt` — the app shell: tab navigation (official `ShortNavigationBar`), the Lainnya (More) page, screen routing.
  - `.../ui/screens/` — the screens, one file per area: `Screens.kt` (shared constants, helpers, and small components), `BerandaScreen.kt` (Home), `KasirScreen.kt` (Cashier), `StokScreen.kt` (Stock + medicine form), `KeuanganScreen.kt` (Finance + cash entry), `LabaScreen.kt` (Profit + Best Sellers), `PengingatScreen.kt` (Reminders), `RiwayatScreen.kt` (Sales History + invoice/receipt cards), `BackupScreen.kt` (Backup & Restore + About), `PbfScreen.kt` (Supplier Invoices).
  - `.../ui/theme/Theme.kt` — the Material Expressive theme, the generated color scheme, IDR/date formatting, and the stock-status scale.
  - `.../ui/` — also `BluetoothPrinter.kt` (ESC/POS printing), `ReceiptShare.kt` (receipt image sharing), `LogoData.kt` (the pharmacy logo shown on receipts).
  - `.../data/` — `Repository.kt` (business logic), `Models.kt`, `ExcelHelper.kt` (.xlsx import/export), `BackupHelper.kt` (JSON backup/restore), `SyncLogic.kt` + `OnlineSync.kt` (Firestore mapping, sync engine, cloud reset), and `local/` (Room database, entities, DAOs, migrations).

## FAQ

- **Is any data uploaded to a server?** Only if you sign in to the optional Online Mode: then a copy is mirrored to **your own** Firebase project, under your account only (`users/{uid}` in Cloud Firestore, protected by the checked-in `firestore.rules`). If you never sign in — or the build carries no Firebase config — nothing leaves the device.
- **How do I move to a new phone?** On the old phone: **Lainnya → Backup & Restore → Backup** and save the JSON file (for example to Google Drive). On the new phone, install the app and choose **Restore** with that file — all data is replaced in one transaction. With Online Mode there is a second way: sign in on the new phone with the same account and let it sync down.
- **The Bluetooth printer does not appear — what do I check?** Pair the printer in Android's Bluetooth settings first; the app prints only to already-paired printers and remembers the one you pick.
- **Which Excel format does stock import use?** Use the template the app provides in the Stock menu — imports match medicines by name + category + unit, so existing rows update instead of duplicating.

## Data & update safety notes

- The original web version of this app (client/server/PWA) used to live in an `app-source/` folder; it was archived out of the repository tree on 2026-11-02 and remains fully available in git history (for example at commit `849c545e`). The Android app in `kotlin-app/` is the active product. The older `README-APK.md` and `kotlin-app/README-KOTLIN.md` guides were consolidated into this README on 2026-11-02 (their texts described the original handover package and no longer matched the app).
- All real data (stock, sales, cash, purchases) lives **locally on the device** in the `apotek-lestari.db` database — never in this repo. The only copy that can exist off-device is the Online Mode mirror in your own Firebase project (see above). This repo contains program code only: no database, sales data, or credentials.
- As a safety net, whenever the app detects it has just been updated, the database file is **automatically copied before migrations run** (up to the 3 most recent copies in the app's internal storage), so pre-update data stays intact if a migration ever goes wrong.
- A manual backup is still recommended before major updates: **Lainnya (More) → Backup & Restore → Backup**.
