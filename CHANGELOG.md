# Changelog

All notable changes to Apotek Lestari are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed

- Audit 2026-11-16 (round 2, M1–M22): backup now carries buy prices (HPP survives restore); backup restore and sync pull cross-check sale totals against item subtotals; money aggregates sum as Long and clamp to Int; the expiry-date form guard is reachable again and the last picked date survives unticking the no-expiry checkbox; PBF date/due-date validation; sale tombstones also tombstone their items; database v17 backfills the item-table updatedAt stamps; unit tests grown to 47 JVM tests.

## [1.3.0] - 2026-11-16

### Added

- Cash-received quick cards in the cashier cart (Tunai): exact amount plus the
  next three 50k round-ups above the total, each card showing its change.
- Backup restore preview (file date and record counts shown before any data
  is replaced) and last-backup info on the Backup screen.
- Medicine form reorganised into Identity / Price / Stock & Expiry cards.
- Supplier-invoice form reorganised into Invoice / Items / Summary cards;
  expanded purchase cards show a Subtotal / VAT / Total breakdown.
- Unit test suite grown to 44 JVM tests covering money math, backup
  round-trips, Excel round-trips, due-date logic, sync conflict rules,
  automatic-backup file naming, and supplier sync mapping.
- Sales recap export to Excel (J5): an Export Excel button under Profit
  writes a `.xlsx` with two sheets, "Rekap Harian" (per day) and
  "Per Obat" (per medicine).
- Online Mode: optional Google sign-in mirrors all data to the user's own
  Firebase project (Firestore, per-account tree under `users/{uid}/`) with
  sync columns and soft deletes on all seven tables, last-write-wins
  conflicts, automatic sync a few seconds after any change, and a 6-hour
  periodic worker. See `docs/ONLINE.md`.
- Reset Semua Data on the More page: wipes the cloud tree first (a failure
  aborts before anything local is touched), then clears the local tables;
  the account stays signed in.
- A floating top-centre "Synced" badge confirms sign-in and manual syncs; background
  syncs and ordinary app opens now sync silently.
- Account-switch consolidation: signing in with a different account marks
  every local row dirty, so the full local data set uploads into that
  account's cloud tree.
- Gradle wrapper (`gradlew` / `gradlew.bat`, Gradle 9.6.0) added, so the
  project also builds locally in Android Studio.
- About screen shows live statistics: medicine and transaction counts.
- Scheduled automatic backup: the same JSON backup written daily or
  weekly by WorkManager to a folder chosen on the Backup screen
  (persistable folder permission, silent on success, schedule
  re-asserted on every app start); the Backup screen also has a
  one-time Back Up Now run and a last-run stamp.
- PBF supplier directory: saved supplier names and contacts on a new
  Pemasok page (Lainnya), with a one-time import from past purchase
  records; the PBF form now picks the supplier from the list (with an
  inline add form) instead of free typing. Suppliers sync like the
  other tables (Firestore collection `pemasok`) and ride along in JSON
  backup/restore.

### Changed

- Toolchain upgraded: AGP 9.5.1, Gradle 9.6.0, Kotlin 2.4.21, compileSdk 37,
  targetSdk 36 (from 34), Compose BOM 2026.09.00 / Material 3 1.5.0-rc01,
  Apache POI 5.5.1, Navigation Compose 2.10.2, Lifecycle 2.11.0,
  Coroutines 1.11.0, Room 2.8.5, google-services 4.6.0.
- GitHub Actions upgraded (checkout v7, setup-java v6, upload-artifact v7,
  gradle/actions v6) and Dependabot enabled (weekly PRs, hand-merged).
- Profit report restored with buy prices (HPP, 15 Nov 2026): Gross Profit
  = Revenue - HPP (buy-price snapshot per item sold); Net Profit = Gross
  Profit - Operating Costs, shown with its net margin, a revenue/expense
  breakdown, and a daily net chart. buyPrice is carried by sync, backup,
  and Excel.
- Home: the revenue hero and money card merged into one panel with a
  Today / This Month period switch.
- Cashier medicine list: compact two-line rows (name + price, then
  category/unit + stock status).
- Cashier add-medicine control is now a compact "+ Tambah Obat" button in
  the Pick/Sort row (E2, 32dp); the round floating button (TA1) is gone.
- Excel stock template/export revamped (X1+X2+X3): fixed column widths
  (expiry 18, not 8), frozen header row + autofilter, Category/Unit
  dropdown validation, thousand-separator number formats, and expiry
  written as real Excel dates.
- Error reds unified on `colorScheme.error` (audit R28): the cash-out red,
  the "Habis" stock badge, and the failed-cloud icon/border no longer use
  hardcoded reds.
- Reminder card shadow fixed (PG8).
- Cart sheet: one total + payment-method panel on top (neutral styling with
  a blue accent stripe); the receipt preview is folded by default and the
  printed receipt animates in after a successful payment.
- Recording cash (Catat Kas) expands inline under the balance card instead
  of opening a bottom sheet.
- Supplier-invoice summary split into two side-by-side cards (Today / This
  Month) with a new empty state.
- History entries preview their first items while collapsed.
- Animations were audited down to a short kept set (tab transitions as a
  fade with a slight scale, amount count-up, chart bar growth, cashier
  fly-to-cart, the payment success check, and the sync badge); the theme
  circular reveal was removed, so theme switching is instant. Lainnya
  sub-pages use shared-axis slide transitions (30dp, S3).
- Login is Google-only: the gate is a dark premium screen (logo in a white
  circle with a cyan ring, white Google button, guest pass). The
  email/password form was removed.
- Screen headers unified into the N2 solid dark premium card with tonal
  icon chips (light mode uses the H6 light-blue card with navy titles);
  the More page uses the L1 centred-identity layout with grouped cards.
- Excel stock template uses 8 columns, including Harga Beli beside Harga Jual Grosir (X1+X2+X3); the export adds a 9th Barcode column. Older files without a Harga Beli column still import (read as 0).
- The About page was redesigned twice (T10, then T2: a single grouped card
  with refreshed content) and reads the version from BuildConfig instead
  of a hand-written string.
- Screen titles now match their menu/tab labels exactly.

### Removed

- Email/password sign-in: the login form, the app handlers, and the
  Firebase email functions.
- Foto Faktur OCR (photo invoice capture) removed at the owner's request —
  PBF entry is fully manual.
- Barcode camera scan removed at the owner's request.
- The MIT license: the About license card and dialog, the license text
  constant, and the LICENSE file (the repository carries no license file).
- Note: the INTERNET permission, removed earlier in this cycle when the
  app had no network code, was re-added for Online Mode. Stale ProGuard
  rules left over from long-removed libraries were also removed.

### Fixed

- Release signing verified end to end in CI; the release artifact is named
  `Apotek Lestari-<version>-Release`.
- Medicine dedup now matches on name + category + unit and only merges
  rows whose barcodes agree (both empty, or equal ignoring case and
  surrounding whitespace); rows with different barcodes stay separate.
- Data-extraction/backup rules now exclude the database and update snapshots
  from cloud backup; unsafe `!!` usages removed.

### Security

- Workflow runs with `permissions: contents: read`; release signing secrets
  verified working.

## [1.2.0] - 2026-11-04

### Added

- Supplier invoices (Faktur PBF): credit purchases with due dates,
  mark-as-paid, an outstanding-credit card, and due-date editing; reminder
  sections for bills due within 7 days (including overdue) and for credit
  purchases without a due date.
- Payment success phase in the cashier: an animated checkmark confirmation
  showing method, total, and change.

### Changed

- More (Lainnya) is now a full page with a two-column tile grid instead of a
  bottom sheet.
- Finance (Keuangan) screen redesigned.
- Amber warning labels darkened in light theme for readability.

## [1.1.0] - 2026-11-02

### Added

- Wholesale (Grosir) pricing per medicine, selectable per cart item, carried
  through receipts, history, and profit.
- Supplier invoice records (Faktur PBF) — records only; they never change
  stock or cash.
- Fixed medicine category and unit lists for Stock and PBF forms.
- Excel template/export gained the wholesale price column.

### Changed

- Stock status scale simplified to fixed bands (0 Out, 1–9 Low, 10–15 Medium,
  16+ Safe); the per-medicine minimum-stock setting was removed (database
  migrated accordingly).
- Repository renamed to Aplikasi-Kasir-Apotek; documentation consolidated
  into a single English README; MIT license added.

## [1.0.0] - 2026-10-27

Initial release.

### Added

- Sales cashier (cart, Cash/QRIS/Transfer, change calculation, receipts).
- Medicine stock with search, filters, and Excel import/export.
- Cash book (Keuangan) and profit report (Laba).
- Reminders for low stock and near-expiry medicines.
- Sales history with invoice details.
- JSON backup & restore through the system file picker.
- Bluetooth thermal receipt printing (ESC/POS) and receipt sharing.
- Fully offline Room database; dark theme by default.
