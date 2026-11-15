package id.apotek.lestari.baselineprofile

import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pengukur startup (bukan generator): bandingkan `timeToInitialDisplayMs`
 * sebelum dan sesudah Baseline Profile ada. Jalankan sebagai tes
 * instrumentasi di HP yang sama, dari Android Studio (klik gutter di
 * samping `startup`) atau `./gradlew :baseline-profile:connectedBenchmarkReleaseAndroidTest`.
 * Angka yang dibandingkan: median timeToInitialDisplayMs.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startup() = rule.measureRepeated(
        packageName = "id.apotek.lestari",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}
