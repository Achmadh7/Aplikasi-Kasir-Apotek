// Modul generator Baseline Profile (optimasi startup, 2026-11-15).
//
// Modul com.android.test yang menarget :app — TIDAK menghasilkan APK
// sendiri, hanya menjalankan generator + benchmark di HP yang tersambung
// (POCO F5, API 33+, tanpa root). Profil dihasilkan lewat tugas
// :app:generateBaselineProfile, lalu disalin plugin ke
// app/src/main/generated/baselineProfiles/.
//
// Versi terverifikasi dari metadata Google Maven:
// benchmark-macro-junit4 1.5.0, test-ext-junit 1.3.0,
// uiautomator 2.4.1, runner 1.7.0.
//
// AGP 9.5 punya built-in Kotlin: modul ini TIDAK menerapkan
// org.jetbrains.kotlin.android (menerapkannya adalah error).
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "id.apotek.lestari.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Modul yang diprofilkan.
    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.1")
    implementation("androidx.test:runner:1.7.0")
}
