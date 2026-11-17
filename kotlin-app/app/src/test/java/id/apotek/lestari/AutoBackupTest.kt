package id.apotek.lestari

import id.apotek.lestari.data.FrekuensiBackup
import id.apotek.lestari.data.namaFileBackupOtomatis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Backup otomatis terjadwal (G6): nama file + pemetaan frekuensi. */
class AutoBackupTest {

    @Test
    fun namaFile_memakaiTanggalJamDanAkhiranJson() {
        val tanggal = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.parse("2026-11-12")!!
        // R5 (audit 2026-11-15): stempel jam-menit-detik ditambahkan.
        // R3 (audit 2026-11-18): + milidetik (SSS) — tengah malam = 000.
        assertEquals("backup-otomatis-2026-11-12-000000000.json", namaFileBackupOtomatis(tanggal))
    }

    @Test
    fun namaFile_tanpaArgumen_memakaiHariIni() {
        // R3 (audit 2026-11-18): nama memakai milidetik, jadi kesamaan
        // persis dengan "sekarang" yang ditangkap terpisah bisa meleset
        // bila batas milidetik terlewati di antara dua panggilan — cek
        // bentuknya saja (tanggal hari ini + 9 digit jam + .json).
        val hariIni = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }.format(Date())
        val nama = namaFileBackupOtomatis()
        assertTrue(nama.startsWith("backup-otomatis-$hariIni-"))
        assertTrue(nama.matches(Regex("backup-otomatis-\\d{4}-\\d{2}-\\d{2}-\\d{9}\\.json")))
    }

    @Test
    fun frekuensi_dariString_harianDanMingguan() {
        assertEquals(FrekuensiBackup.HARIAN, FrekuensiBackup.dariString("harian"))
        assertEquals(FrekuensiBackup.MINGGUAN, FrekuensiBackup.dariString("mingguan"))
    }

    @Test
    fun frekuensi_dariString_tidakDikenal_jatuhKeMingguan() {
        assertEquals(FrekuensiBackup.MINGGUAN, FrekuensiBackup.dariString(null))
        assertEquals(FrekuensiBackup.MINGGUAN, FrekuensiBackup.dariString("bulanan"))
    }

    @Test
    fun frekuensi_keString_bulat() {
        assertEquals("harian", FrekuensiBackup.HARIAN.keString())
        assertEquals("mingguan", FrekuensiBackup.MINGGUAN.keString())
    }
}
