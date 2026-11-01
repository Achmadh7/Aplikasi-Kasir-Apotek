import java.net.URL
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Signing — pola Auriya: file signing.properties (gitignored) di root
// project (kotlin-app/) dibaca di sini. Tanpa file itu, build release
// memakai debug signing supaya tetap bisa di-build untuk testing.
val signingPropertiesFile = rootProject.file("signing.properties")
val signingProperties =
    Properties().apply {
        if (signingPropertiesFile.exists()) {
            signingPropertiesFile.inputStream().use { load(it) }
        }
    }

// Font Poppins (OFL, gratis) — diunduh saat build dari google/fonts, karena
// file .ttf biner tidak bisa di-push lewat API teks. Disimpan di direktori
// generated (build/generated/poppins/res/font) supaya repo tetap bersih.
// Dipakai build/generated/poppins/res (bukan generated/res) agar tidak
// bertabrakan dengan output resource AGP sendiri di build/generated/res.
val poppinsResDir = layout.buildDirectory.dir("generated/poppins/res")
val poppinsFontDir = poppinsResDir.map { it.dir("font") }
val downloadPoppinsFonts = tasks.register("downloadPoppinsFonts") {
    val outDir = poppinsFontDir.get().asFile
    outputs.dir(outDir)
    doLast {
        outDir.mkdirs()
        val base = "https://raw.githubusercontent.com/google/fonts/main/ofl/poppins"
        listOf("Regular", "Medium", "SemiBold", "Bold").forEach { weight ->
            val out = outDir.resolve("poppins_${weight.lowercase()}.ttf")
            if (!out.exists() || out.length() == 0L) {
                try {
                    URL("$base/Poppins-$weight.ttf").openStream().use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    throw GradleException(
                        "Gagal mengunduh font Poppins-$weight.ttf: ${e.message}. Pastikan build punya akses internet.",
                        e
                    )
                }
            }
        }
    }
}
tasks.named("preBuild") { dependsOn(downloadPoppinsFonts) }

android {
    namespace = "id.apotek.lestari"
    compileSdk = 36

    sourceSets {
        getByName("main") {
            res.srcDirs("src/main/res", poppinsResDir)
        }
    }

    defaultConfig {
        applicationId = "id.apotek.lestari"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (signingProperties.isNotEmpty()) {
            create("release") {
                storeFile = signingProperties.getProperty("KEYSTORE_PATH")?.let { rootProject.file(it) }
                storePassword = signingProperties.getProperty("KEYSTORE_PASSWORD")
                keyAlias = signingProperties.getProperty("KEY_ALIAS")
                keyPassword = signingProperties.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8: perkecil + obfuscate kode rilis (dan buang resource yang tidak
            // terpakai) supaya APK lebih kecil. Aturan keep ada di proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig =
                if (signingProperties.isNotEmpty()) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha08")
    implementation("androidx.compose.material3:material3-window-size-class:1.5.0-alpha08")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Database lokal (Room) — data tersimpan di HP, tanpa server
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")

    // Excel (.xlsx) untuk Import / Export / Template Stok — Apache POI
    implementation("org.apache.poi:poi-ooxml:5.2.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
