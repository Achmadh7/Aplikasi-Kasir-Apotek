# Architecture — Apotek Lestari

A short map of the project: code structure, database, and the main data
flows. Written from the code at the current head (app version 1.2.0).

## Overview

Apotek Lestari is a **fully offline** pharmacy cashier and inventory app:
all data lives locally on the phone (Room/SQLite), with no server and no
accounts. A single app module (`kotlin-app/app`) and a single Activity host
the whole app; `MainActivity.kt` handles navigation with a Material 3
Expressive bottom bar (ShortNavigationBar): **Beranda (Home), Kasir (POS),
Stok (Stock), Keuangan (Finance)**, plus a **Lainnya (More)** sheet (Profit,
Reminders, Sales History, PBF Invoices, Best Sellers, Backup & Restore,
About). The app is bilingual ID/EN (the `tr(id, en)` function in the theme)
and supports dark/light mode.

## Tech stack

- Kotlin 2.2.20, Jetpack Compose, Material 3 Expressive (material3 1.5.0-alpha08)
- Fidelity colour scheme from seed `#0061A4`
- Room (local database, version 12) — no networking at all
- Apache POI 5.2.5 for Excel (.xlsx) import/export
- Android Gradle Plugin 8.11.1, Gradle 8.13, JDK 17
- minSdk 26, targetSdk 34, compileSdk 36

## Project structure

```
kotlin-app/
  build.gradle.kts            top-level plugin versions only
  app/
    build.gradle.kts          app config + release signing + signing guard
    src/main/java/id/apotek/lestari/
      MainActivity.kt         the only Activity: theme, navigation, bottom bar
      data/
        Models.kt             data models (Medicine, Sale, CashEntry, Purchase, ...)
        Repository.kt         the single entry point for business logic & queries
        ExcelHelper.kt        stock Excel template/import/export
        BackupHelper.kt       JSON backup/restore
        local/AppDatabase.kt  Room entities, DAOs, migrations, DatabaseProvider
      ui/
        theme/                Theme.kt — colour scheme, tr() ID/EN, stockLabel()
        screens/              one file per screen (BerandaScreen, KasirScreen,
                              StokScreen, KeuanganScreen, LabaScreen, PbfScreen,
                              PengingatScreen, RiwayatScreen, BackupScreen)
                              + Screens.kt (shared components & small screens)
        BluetoothPrinter.kt   ESC/POS receipt printing to a paired thermal printer
        ReceiptShare.kt       receipt rendered as an image for sharing
        LogoData.kt           pharmacy logo (Base64) for the header & receipts
```

Screens never touch the DAOs directly — everything goes through
`Repository`, so business rules (stock limits, price snapshots, cash
sources) stay consistent across screens.

## Database (Room, version 12)

| Table | Contents |
|---|---|
| `medicines` | Medicines: name, category, unit, buy/sell/wholesale price, stock, expiry date, barcode (unique index) |
| `sales` | Sales: invoice number `INV - dd/MM/yy` (NON-unique index — same-day sales share the displayed number), total, payment method, paid/change |
| `sale_items` | Sale lines: name, qty, price snapshot, subtotal, `priceType` ECER (retail) / GROSIR (wholesale) |
| `cash_entries` | Cash in/out: `type` in/out, `source` (`sale` = from a sale, `manual` = recorded by hand) |
| `purchases` | PBF invoices: supplier name, invoice number, total including VAT (PPN), CASH/KREDIT, due date |
| `purchase_items` | PBF invoice line items |

- Sequential migrations 1 → 12 are registered in `AppDatabase.kt`; never
  change the schema without adding a new migration.
- Update safety net: `DatabaseProvider` copies the database file to
  `filesDir/db-snapshots/` (newest 3 kept) before Room opens, whenever the
  package update time changed — the copies vanish on uninstall, so the JSON
  backup remains the primary protection.
- DAOs: `MedicineDao`, `SaleDao`, `CashDao`, `PurchaseDao`.

## Main data flows

**Sales (Kasir).** The cart holds a qty per medicine (it cannot exceed
stock; `Repository.createSale` also rejects over-stock sales, so stock can
never go negative) → the charged price is snapshotted into
`sale_items.price` (retail or wholesale per line) → medicine stock is
decremented → one `cash_entries` row of type `in` with `source = "sale"` is
created → invoice and receipt (image, share, or Bluetooth thermal print).

**Stock.** Add/edit via the form, or Excel import (matched on
Name+Category+Unit; the template and export use 8 columns including
Wholesale Price). Stock status is purely count-based: 0 = Out, 1–9 = Low,
10–15 = Medium, 16+ = Safe (`stockLabel()` in Theme.kt doubles as the
internal logic key).

**Finance & profit.** Cash Balance = all cash in − all cash out. The Home
screen's "Income Today" counts only sales (`source = "sale"`); manual cash
entries affect only the Cash Balance. Profit is sales-based: revenue − cost
of goods at the current buy price per medicine sold.

**PBF invoices.** Purchase records only: they do **not** change stock,
cash, buy prices, profit, or the Cash Balance. Credit (KREDIT) purchases
carry a due date, can be marked paid (becoming CASH), and bills due within
7 days surface in Reminders and on the Home reminder card. Spending
summaries count CASH only.

**Backup & restore.** A single JSON file `{version, app, medicines, sales,
saleItems, cashEntries, purchases, purchaseItems}` via the Storage Access
Framework (can be saved to Drive). Restore replaces all data inside one
Room transaction, after confirmation.

## Build & release

CI (`.github/workflows/main.yml`, run manually) builds the debug APK, then
checks out the keystore from the private Sigkey repo, writes
`signing.properties` from GitHub Secrets, and builds the release APK
(`app-release-apk`). `app/build.gradle.kts` reads that `signing.properties`;
the `verifyReleaseSigning` guard fails the release build when signing is
incomplete — a release APK can never silently ship signed with the debug
key.
