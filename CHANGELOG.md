# Changelog

All notable changes to Apotek Lestari are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

Changes on `main` after the 1.2.0 version bump; they ship with the next version.

### Added

- Cash-received quick cards in the cashier cart (Tunai): exact amount plus the
  next three 50k round-ups above the total, each card showing its change.
- Backup restore preview (file date and record counts shown before any data
  is replaced) and last-backup info on the Backup screen.
- Medicine form reorganised into Identity / Price / Stock & Expiry cards with
  a live margin strip (and a warning when the selling price is not above the
  buying price).
- Supplier-invoice form reorganised into Invoice / Items / Summary cards;
  expanded purchase cards show a Subtotal / VAT / Total breakdown.
- CI now runs Android Lint (report-only) and uploads the report as an artifact.
- Unit test suite grown to 19 JVM tests covering money math, backup
  round-trips, Excel round-trips, and due-date logic.

### Changed

- Toolchain upgraded: AGP 9.1.1, Gradle 9.3.1, Kotlin 2.4.10, compileSdk 37,
  targetSdk 36 (from 34), Compose BOM 2026.09.00 / Material 3 1.5.0-beta02,
  Apache POI 5.5.1, Navigation Compose 2.10.2, Lifecycle 2.11.0,
  Coroutines 1.11.0.
- Profit report: gross and net profit now sit side by side as two hero cards
  with margins, above a revenue / COGS / operating-cost breakdown.
- Home: the revenue hero and money card merged into one panel with a
  Today / This Month period switch.
- Cashier medicine list: compact two-line rows (name + price, then
  category/unit + stock status).
- Cart sheet: one total + payment-method panel on top (neutral styling with
  a blue accent stripe); the receipt preview is folded by default and the
  printed receipt animates in after a successful payment.
- Recording cash (Catat Kas) expands inline under the balance card instead
  of opening a bottom sheet.
- Supplier-invoice summary split into two side-by-side cards (Today / This
  Month) with a new empty state.
- History entries preview their first items while collapsed.
- Sub-page navigation (from the More menu) now uses a full-width iOS-style
  slide; tab switching keeps the shorter directional fade.
- Theme switching animates as a circular reveal from the tapped row; the
  About page was redesigned and reads the version from BuildConfig instead
  of a hand-written string.
- Screen titles now match their menu/tab labels exactly.

### Removed

- The unused INTERNET permission (the app has no network code) and stale
  ProGuard rules left over from long-removed libraries.

### Fixed

- Release signing verified end to end in CI; the release artifact is named
  `Apotek Lestari-<version>-Release`.
- Android Lint reports 0 errors (explicit SecurityException handling around
  Bluetooth device reads).
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
