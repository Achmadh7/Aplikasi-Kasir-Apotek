package id.apotek.lestari.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generator Baseline Profile Apotek Lestari (optimasi startup, 2026-11-15).
 *
 * Dijalankan lewat tugas Gradle `:app:generateBaselineProfile` (bukan
 * langsung sebagai tes) di HP fisik yang tersambung — POCO F5, API 33+,
 * tanpa root. Hasilnya disalin plugin ke
 * `app/src/main/generated/baselineProfiles/` (baseline-prof.txt +
 * startup-prof.txt); commit kedua berkas itu supaya APK release
 * berikutnya memakai profilnya.
 *
 * Jalur yang diprofilkan = jalur yang dijalani tes di bawah ini:
 * cold start -> gerbang login (dilewati sebagai tamu) -> Beranda,
 * lalu tab Kasir -> Stok -> Beranda. Regenerate profil setelah
 * redesain layar, naik versi Compose, atau sebelum rilis.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = "id.apotek.lestari",
        includeInStartupProfile = true
    ) {
        // Cold start: dari Home ke gerbang login / Beranda.
        pressHome()
        startActivityAndWait()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        lewatiGerbangJikaAda(device)
        device.wait(Until.hasObject(By.text("Beranda")), 5_000)
    }

    @Test
    fun perjalananUtama() = rule.collect(
        packageName = "id.apotek.lestari",
        includeInStartupProfile = false
    ) {
        pressHome()
        startActivityAndWait()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        lewatiGerbangJikaAda(device)
        // Jelajahi tab utama supaya jalurnya ikut terkompilasi lebih awal.
        for (tab in listOf("Kasir", "Stok", "Beranda")) {
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }
    }

    /**
     * Gerbang login L6: tombol "Lewati — Pakai Tanpa Akun". Kalau HP
     * sudah masuk (akun tersimpan), gerbang tidak muncul — lewati saja.
     */
    private fun lewatiGerbangJikaAda(device: UiDevice) {
        if (device.wait(Until.hasObject(By.textStartsWith("Lewati")), 5_000)) {
            device.findObject(By.textStartsWith("Lewati"))?.click()
            device.wait(Until.hasObject(By.text("Kasir")), 5_000)
        }
    }
}
