package id.apotek.lestari

import id.apotek.lestari.data.FrekuensiBackup
import id.apotek.lestari.data.namaFileBackupOtomatis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Backup otomatis terjadwal (G6): nama file + pemetaan frekuensi. */
class AutoBackupTest {

    @Test
    fun namaFile_memakaiTanggalDanAkhiranJson() {
        val tanggal = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).parse("2026-11-12")!!
        assertEquals("backup-otomatis-2026-11-12.json", namaFileBackupOtomatis(tanggal))
    }

    @Test
    fun namaFile_tanpaArgumen_memakaiHariIni() {
        val hariIni = SimpleDateFormat("yyyy-MM-dd", Locale("id", "ID")).format(Date())
        assertEquals("backup-otomatis-$hariIni.json", namaFileBackupOtomatis())
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
