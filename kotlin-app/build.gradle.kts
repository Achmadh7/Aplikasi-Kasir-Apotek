// Top-level build file — Apotik Lestari (Kotlin native, no version catalog)
plugins {
    id("com.android.application") version "9.5.1" apply false
    // Sejak AGP 9 Kotlin melekat di AGP (built-in Kotlin): plugin
    // org.jetbrains.kotlin.android tidak lagi dipasang di modul — baris
    // ini hanya mematok versi KGP yang dipakai AGP (di sini 2.4.21,
    // pasangan compileSdk 37).
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false // R35 (audit 2026-11-15): KSP 2.3.9 -> 2.3.12, satu patch tertinggal dari lini 2.3; tetap berpasangan valid dengan Kotlin 2.4.21 (KSP2 terpisah dari versi Kotlin persis).
    // Mode Online (Firebase): plugin google-services HANYA diterapkan di
    // modul app bila file google-services.json ada (lihat app/build).
    id("com.google.gms.google-services") version "4.6.0" apply false
}
