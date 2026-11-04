// Top-level build file — Apotik Lestari (Kotlin native, no version catalog)
plugins {
    id("com.android.application") version "9.1.1" apply false
    // Sejak AGP 9 Kotlin melekat di AGP (built-in Kotlin): plugin
    // org.jetbrains.kotlin.android tidak lagi dipasang di modul — baris
    // ini hanya mematok versi KGP yang dipakai AGP (bawaan AGP 9.1.1
    // adalah 2.2.10; di sini dinaikkan ke 2.4.10, pasangan compileSdk 37).
    id("org.jetbrains.kotlin.android") version "2.4.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.9" apply false
}
