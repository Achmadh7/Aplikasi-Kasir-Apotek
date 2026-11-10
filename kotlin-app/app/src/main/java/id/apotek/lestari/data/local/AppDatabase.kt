package id.apotek.lestari.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- Entities: data tersimpan di HP (SQLite via Room) ----

@Entity(tableName = "medicines", indices = [Index(value = ["barcode"], unique = true)])
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String,
    val unit: String,
    val buyPrice: Int,
    val sellPrice: Int,
    // Harga jual grosir (permintaan Achmad 2026-10-29); 0 = belum diatur,
    // Kasir hanya menawarkan pilihan Grosir bila nilainya > 0.
    @ColumnInfo(defaultValue = "0") val wholesalePrice: Int = 0,
    val stock: Int,
    val expiryDate: String?, // "yyyy-MM-dd" atau null
    val barcode: String?,
    val createdAt: String, // ISO UTC mis. "2026-10-26T00:15:00.000Z",
    // Kolom sinkronisasi Mode Online (DB v13): remoteId = identitas baris
    // di Firestore; updatedAt = cap ubah terakhir (ms epoch) untuk aturan
    // last-write-wins; terhapus = 1 berarti tombstone (hapus lunak);
    // kotor = 1 berarti belum tersinkron ke awan.
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val terhapus: Int = 0,
    @ColumnInfo(defaultValue = "1") val kotor: Int = 1
)

@Entity(tableName = "sales", indices = [Index(value = ["invoiceNo"])])
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceNo: String,
    val total: Int,
    val paymentMethod: String,
    val amountPaid: Int,
    val changeAmount: Int,
    val createdAt: String, // ISO UTC
    // Kolom sinkronisasi Mode Online (DB v13): remoteId = identitas baris
    // di Firestore; updatedAt = cap ubah terakhir (ms epoch) untuk aturan
    // last-write-wins; terhapus = 1 berarti tombstone (hapus lunak);
    // kotor = 1 berarti belum tersinkron ke awan.
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val terhapus: Int = 0,
    @ColumnInfo(defaultValue = "1") val kotor: Int = 1
)

@Entity(tableName = "sale_items")
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val saleId: Int,
    val medicineId: Int,
    val medicineName: String, // snapshot nama — riwayat tetap utuh walau obat dihapus
    val category: String = "", // snapshot kategori saat transaksi
    val unit: String = "", // snapshot satuan saat transaksi
    val qty: Int,
    val price: Int, // harga jual saat transaksi
    // Jenis harga yang dipakai: "ECER" atau "GROSIR" (permintaan Achmad
    // 2026-10-29 — ditampilkan di invoice). Penjualan lama = "ECER".
    @ColumnInfo(defaultValue = "ECER") val priceType: String = "ECER",
    val buyPrice: Int, // harga beli saat transaksi (snapshot HPP)
    val subtotal: Int,
    // Kolom sinkronisasi Mode Online (DB v13): remoteId = identitas baris
    // di Firestore; updatedAt = cap ubah terakhir (ms epoch) untuk aturan
    // last-write-wins; terhapus = 1 berarti tombstone (hapus lunak);
    // kotor = 1 berarti belum tersinkron ke awan.
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val terhapus: Int = 0,
    @ColumnInfo(defaultValue = "1") val kotor: Int = 1
)

@Entity(tableName = "cash_entries")
data class CashEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "in" | "out"
    val category: String,
    val description: String,
    val amount: Int,
    val source: String, // "sale" | "manual"
    val createdAt: String, // ISO UTC
    // Kolom sinkronisasi Mode Online (DB v13): remoteId = identitas baris
    // di Firestore; updatedAt = cap ubah terakhir (ms epoch) untuk aturan
    // last-write-wins; terhapus = 1 berarti tombstone (hapus lunak);
    // kotor = 1 berarti belum tersinkron ke awan.
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val terhapus: Int = 0,
    @ColumnInfo(defaultValue = "1") val kotor: Int = 1
)

// Catatan pembelian dari PBF — MURNI catatan (keputusan Achmad 2026-10-29):
// tidak mengubah stok obat, tidak membuat entri kas, tidak mengubah harga.
@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val pbfName: String, // nama perusahaan PBF (distributor)
    // Nomor faktur dari PBF (permintaan Achmad 2026-10-29); "" = tidak diisi.
    @ColumnInfo(defaultValue = "") val invoiceNo: String = "",
    val date: String, // "yyyy-MM-dd" — tanggal pembelian yang dipilih pengguna
    val total: Int, // total belanja TERMASUK ppn
    // Jumlah PPN (Rp) dari faktur (permintaan Achmad 2026-10-29); 0 = tanpa PPN.
    @ColumnInfo(defaultValue = "0") val ppn: Int = 0,
    // Cara bayar: "CASH" / "KREDIT" (permintaan Achmad 2026-10-30). Hanya
    // CASH yang dihitung ringkasan Belanja Hari Ini/Bulan Ini; catatan lama
    // dianggap CASH lewat DEFAULT kolom.
    @ColumnInfo(defaultValue = "CASH") val paymentType: String = "CASH",
    // Tanggal jatuh tempo pembayaran Kredit "yyyy-MM-dd" (permintaan Achmad
    // 2026-10-30); "" = tidak diisi / bukan Kredit. Murni informasi hitung
    // mundur di kartu — tidak memengaruhi ringkasan.
    @ColumnInfo(defaultValue = "") val dueDate: String = "",
    val createdAt: String, // ISO UTC — kapan catatan dibuat
    // Kolom sinkronisasi Mode Online (DB v13): remoteId = identitas baris
    // di Firestore; updatedAt = cap ubah terakhir (ms epoch) untuk aturan
    // last-write-wins; terhapus = 1 berarti tombstone (hapus lunak);
    // kotor = 1 berarti belum tersinkron ke awan.
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val terhapus: Int = 0,
    @ColumnInfo(defaultValue = "1") val kotor: Int = 1
)

@Entity(tableName = "purchase_items")
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val purchaseId: Int,
    val medicineName: String, // teks bebas (snapshot nama) — tidak terikat data Stok
    val qty: Int,
    // Satuan pembelian (Strip/Botol/Box/Vial/...) — dipilih di formulir PBF
    // supaya jelas qty itu dalam satuan apa (permintaan Achmad 2026-10-29).
    @ColumnInfo(defaultValue = "Strip") val unit: String = "Strip",
    val buyPrice: Int, // harga satuan dari PBF
    val subtotal: Int,
    // Kolom sinkronisasi Mode Online (DB v13): remoteId = identitas baris
    // di Firestore; updatedAt = cap ubah terakhir (ms epoch) untuk aturan
    // last-write-wins; terhapus = 1 berarti tombstone (hapus lunak);
    // kotor = 1 berarti belum tersinkron ke awan.
    @ColumnInfo(defaultValue = "") val remoteId: String = "",
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = 0,
    @ColumnInfo(defaultValue = "0") val terhapus: Int = 0,
    @ColumnInfo(defaultValue = "1") val kotor: Int = 1
)

// ---- DAOs ----

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines WHERE terhapus = 0 ORDER BY id DESC")
    suspend fun getAll(): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE id = :id AND terhapus = 0")
    suspend fun getById(id: Int): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE barcode = :barcode AND terhapus = 0 LIMIT 1")
    suspend fun findByBarcode(barcode: String): MedicineEntity?

    // Tanpa saringan terhapus: dipakai jalur sinkronisasi (tombstone ikut)
    // dan pemeriksaan barcode saat menghidupkan lagi obat yang terhapus.
    @Query("SELECT * FROM medicines WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcodeAny(barcode: String): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun getByIdAny(id: Int): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE kotor = 1")
    suspend fun getKotor(): List<MedicineEntity>

    @Insert
    suspend fun insert(medicine: MedicineEntity): Long

    @Update
    suspend fun update(medicine: MedicineEntity): Int

    // Hapus lunak sejak DB v13: baris menjadi tombstone supaya penghapusan
    // ikut tersinkron; penghapusan fisik terjadi lewat purge() setelah
    // tombstone terkirim.
    @Query("UPDATE medicines SET terhapus = 1, kotor = 1, updatedAt = :now WHERE id = :id")
    suspend fun deleteById(id: Int, now: Long): Int

    @Query("UPDATE medicines SET kotor = 0 WHERE remoteId = :remoteId AND updatedAt = :updatedAt")
    suspend fun tandaiBersih(remoteId: String, updatedAt: Long): Int

    @Query("DELETE FROM medicines WHERE terhapus = 1 AND kotor = 0")
    suspend fun purge(): Int

    @Query("DELETE FROM medicines")
    suspend fun deleteAll()

    @Query("UPDATE medicines SET stock = stock - :qty, kotor = 1, updatedAt = :now WHERE id = :id AND stock >= :qty AND terhapus = 0")
    suspend fun decrementStock(id: Int, qty: Int, now: Long): Int
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales WHERE terhapus = 0 ORDER BY id DESC")
    suspend fun getAll(): List<SaleEntity>

    @Insert
    suspend fun insert(sale: SaleEntity): Long

    @Update
    suspend fun update(sale: SaleEntity): Int

    @Query("SELECT * FROM sales WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): SaleEntity?

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getByIdAny(id: Int): SaleEntity?

    @Query("SELECT * FROM sales WHERE kotor = 1")
    suspend fun getKotor(): List<SaleEntity>

    @Query("UPDATE sales SET kotor = 0 WHERE remoteId = :remoteId AND updatedAt = :updatedAt")
    suspend fun tandaiBersih(remoteId: String, updatedAt: Long): Int

    @Query("DELETE FROM sales WHERE terhapus = 1 AND kotor = 0")
    suspend fun purge(): Int

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId AND terhapus = 0 ORDER BY id")
    suspend fun getItemsForSale(saleId: Int): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE terhapus = 0")
    suspend fun getAllItems(): List<SaleItemEntity>

    @Insert
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Update
    suspend fun updateItem(item: SaleItemEntity): Int

    @Query("SELECT * FROM sale_items WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getItemByRemoteId(remoteId: String): SaleItemEntity?

    @Query("SELECT * FROM sale_items WHERE kotor = 1")
    suspend fun getKotorItems(): List<SaleItemEntity>

    @Query("UPDATE sale_items SET kotor = 0 WHERE remoteId = :remoteId AND updatedAt = :updatedAt")
    suspend fun tandaiBersihItem(remoteId: String, updatedAt: Long): Int

    @Query("DELETE FROM sale_items WHERE terhapus = 1 AND kotor = 0")
    suspend fun purgeItems(): Int

    @Query("DELETE FROM sales")
    suspend fun deleteAll()

    @Query("DELETE FROM sale_items")
    suspend fun deleteAllItems()
}

@Dao
interface CashDao {
    @Query("SELECT * FROM cash_entries WHERE terhapus = 0 ORDER BY id DESC")
    suspend fun getAll(): List<CashEntryEntity>

    @Insert
    suspend fun insert(entry: CashEntryEntity): Long

    @Update
    suspend fun update(entry: CashEntryEntity): Int

    @Query("SELECT * FROM cash_entries WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): CashEntryEntity?

    @Query("SELECT * FROM cash_entries WHERE kotor = 1")
    suspend fun getKotor(): List<CashEntryEntity>

    // Hapus lunak sejak DB v13 (tombstone ikut tersinkron).
    @Query("UPDATE cash_entries SET terhapus = 1, kotor = 1, updatedAt = :now WHERE id = :id")
    suspend fun deleteById(id: Int, now: Long): Int

    @Query("UPDATE cash_entries SET kotor = 0 WHERE remoteId = :remoteId AND updatedAt = :updatedAt")
    suspend fun tandaiBersih(remoteId: String, updatedAt: Long): Int

    @Query("DELETE FROM cash_entries WHERE terhapus = 1 AND kotor = 0")
    suspend fun purge(): Int

    @Query("DELETE FROM cash_entries")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_entries WHERE type = 'in' AND terhapus = 0")
    suspend fun sumIn(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_entries WHERE type = 'out' AND terhapus = 0")
    suspend fun sumOut(): Int
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases WHERE terhapus = 0 ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<PurchaseEntity>

    @Query("SELECT * FROM purchase_items WHERE terhapus = 0 ORDER BY id")
    suspend fun getAllItems(): List<PurchaseItemEntity>

    @Insert
    suspend fun insert(purchase: PurchaseEntity): Long

    @Update
    suspend fun update(purchase: PurchaseEntity): Int

    @Insert
    suspend fun insertItems(items: List<PurchaseItemEntity>)

    @Update
    suspend fun updateItem(item: PurchaseItemEntity): Int

    @Query("SELECT * FROM purchases WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getByIdAny(id: Int): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE kotor = 1")
    suspend fun getKotor(): List<PurchaseEntity>

    @Query("SELECT * FROM purchase_items WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getItemByRemoteId(remoteId: String): PurchaseItemEntity?

    @Query("SELECT * FROM purchase_items WHERE kotor = 1")
    suspend fun getKotorItems(): List<PurchaseItemEntity>

    // Hapus lunak sejak DB v13 (tombstone ikut tersinkron).
    @Query("UPDATE purchases SET terhapus = 1, kotor = 1, updatedAt = :now WHERE id = :id")
    suspend fun deleteById(id: Int, now: Long): Int

    @Query("UPDATE purchase_items SET terhapus = 1, kotor = 1, updatedAt = :now WHERE purchaseId = :purchaseId")
    suspend fun deleteItemsForPurchase(purchaseId: Int, now: Long): Int

    @Query("UPDATE purchases SET kotor = 0 WHERE remoteId = :remoteId AND updatedAt = :updatedAt")
    suspend fun tandaiBersih(remoteId: String, updatedAt: Long): Int

    @Query("UPDATE purchase_items SET kotor = 0 WHERE remoteId = :remoteId AND updatedAt = :updatedAt")
    suspend fun tandaiBersihItem(remoteId: String, updatedAt: Long): Int

    @Query("DELETE FROM purchases WHERE terhapus = 1 AND kotor = 0")
    suspend fun purge(): Int

    @Query("DELETE FROM purchase_items WHERE terhapus = 1 AND kotor = 0")
    suspend fun purgeItems(): Int

    @Query("UPDATE purchases SET paymentType = :paymentType, kotor = 1, updatedAt = :now WHERE id = :id")
    suspend fun updatePaymentType(id: Int, paymentType: String, now: Long): Int

    @Query("UPDATE purchases SET dueDate = :dueDate, kotor = 1, updatedAt = :now WHERE id = :id")
    suspend fun updateDueDate(id: Int, dueDate: String, now: Long): Int

    @Query("DELETE FROM purchases")
    suspend fun deleteAll()

    @Query("DELETE FROM purchase_items")
    suspend fun deleteAllItems()
}

// ---- Database ----

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // C8: kolom snapshot kategori & satuan di sale_items
        db.execSQL("ALTER TABLE sale_items ADD COLUMN category TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE sale_items ADD COLUMN unit TEXT NOT NULL DEFAULT ''")
        // A9: dedupe barcode duplikat (nullable -> yang duplikat di-null-kan),
        // sisakan satu baris per barcode (id terkecil)
        db.execSQL("UPDATE medicines SET barcode = NULL WHERE id NOT IN (SELECT MIN(id) FROM medicines WHERE barcode IS NOT NULL GROUP BY barcode)")
        db.execSQL("CREATE UNIQUE INDEX index_medicines_barcode ON medicines(barcode)")
        // A9: dedupe invoiceNo duplikat (tidak nullable -> sufiks dengan id),
        // sisakan satu baris per invoiceNo (id terkecil)
        db.execSQL("UPDATE sales SET invoiceNo = invoiceNo || '-' || id WHERE id NOT IN (SELECT MIN(id) FROM sales GROUP BY invoiceNo)")
        db.execSQL("CREATE UNIQUE INDEX index_sales_invoiceNo ON sales(invoiceNo)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Stok minimum dihapus sepenuhnya (keputusan Achmad 2026-10-29): kolom
        // minStock dibuang dari tabel medicines. SQLite lama di Android belum
        // mendukung DROP COLUMN, jadi tabel dibuat ulang tanpa kolom itu,
        // data disalin, tabel lama dibuang, lalu indeks unik barcode dibuat
        // lagi. Semua baris obat dipertahankan apa adanya.
        db.execSQL("CREATE TABLE IF NOT EXISTS `medicines_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `unit` TEXT NOT NULL, `buyPrice` INTEGER NOT NULL, `sellPrice` INTEGER NOT NULL, `stock` INTEGER NOT NULL, `expiryDate` TEXT, `barcode` TEXT, `createdAt` TEXT NOT NULL)")
        db.execSQL("INSERT INTO `medicines_new` (`id`, `name`, `category`, `unit`, `buyPrice`, `sellPrice`, `stock`, `expiryDate`, `barcode`, `createdAt`) SELECT `id`, `name`, `category`, `unit`, `buyPrice`, `sellPrice`, `stock`, `expiryDate`, `barcode`, `createdAt` FROM `medicines`")
        db.execSQL("DROP TABLE IF EXISTS `medicines`")
        db.execSQL("ALTER TABLE `medicines_new` RENAME TO `medicines`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_medicines_barcode` ON `medicines` (`barcode`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Fitur PBF: dua tabel BARU untuk catatan pembelian (purchases +
        // purchase_items). Murni penambahan tabel — tidak menyentuh tabel
        // yang sudah ada, jadi tidak ada data lama yang berubah.
        db.execSQL("CREATE TABLE IF NOT EXISTS `purchases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pbfName` TEXT NOT NULL, `date` TEXT NOT NULL, `total` INTEGER NOT NULL, `createdAt` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `purchase_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purchaseId` INTEGER NOT NULL, `medicineName` TEXT NOT NULL, `qty` INTEGER NOT NULL, `buyPrice` INTEGER NOT NULL, `subtotal` INTEGER NOT NULL)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Kolom satuan (unit) untuk item pembelian PBF. Catatan lama diberi
        // satuan bawaan "Strip" lewat DEFAULT kolom.
        db.execSQL("ALTER TABLE `purchase_items` ADD COLUMN `unit` TEXT NOT NULL DEFAULT 'Strip'")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Kolom harga jual grosir untuk obat. Obat lama bernilai 0 (belum
        // diatur) lewat DEFAULT kolom.
        db.execSQL("ALTER TABLE `medicines` ADD COLUMN `wholesalePrice` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Jenis harga (ECER/GROSIR) per item penjualan, untuk keterangan di
        // invoice. Penjualan lama dianggap ECER lewat DEFAULT kolom.
        db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `priceType` TEXT NOT NULL DEFAULT 'ECER'")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Nomor faktur untuk catatan pembelian PBF (menu "Faktur PBF").
        // Catatan lama kosong lewat DEFAULT kolom.
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `invoiceNo` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Jumlah PPN (Rp) untuk catatan Faktur PBF. Catatan lama 0 (tanpa
        // PPN) lewat DEFAULT kolom; total lama tidak berubah.
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `ppn` INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Cara bayar (CASH/KREDIT) untuk catatan Faktur PBF. Catatan lama
        // dianggap CASH lewat DEFAULT kolom, jadi total ringkasan lama
        // tidak berubah.
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `paymentType` TEXT NOT NULL DEFAULT 'CASH'")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Tanggal jatuh tempo untuk pembelian Kredit Faktur PBF. Catatan
        // lama kosong ('') lewat DEFAULT kolom — tidak ada jatuh tempo.
        db.execSQL("ALTER TABLE `purchases` ADD COLUMN `dueDate` TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Mode Online (2026-11-10): kolom sinkronisasi di keenam tabel —
        // remoteId (identitas baris di Firestore), updatedAt (cap ubah
        // terakhir, ms epoch, untuk last-write-wins), terhapus (tombstone
        // hapus lunak), kotor (belum tersinkron). Baris lama diberi
        // remoteId turunan dari ID lokalnya dan updatedAt dari createdAt
        // bila terbaca; kotor = 1 supaya semuanya terunggah pada
        // sinkronisasi pertama. Tidak ada data yang berubah selain kolom
        // baru ini.
        val tabel = listOf(
            "medicines" to "obat", "sales" to "jual", "sale_items" to "jualitem",
            "cash_entries" to "kas", "purchases" to "pbf", "purchase_items" to "pbfitem"
        )
        for ((nama, awalan) in tabel) {
            db.execSQL("ALTER TABLE `$nama` ADD COLUMN `remoteId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$nama` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$nama` ADD COLUMN `terhapus` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `$nama` ADD COLUMN `kotor` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("UPDATE `$nama` SET `remoteId` = '$awalan-' || `id`")
        }
        for (nama in listOf("medicines", "sales", "cash_entries", "purchases")) {
            db.execSQL("UPDATE `$nama` SET `updatedAt` = COALESCE(CAST(strftime('%s', `createdAt`) AS INTEGER), 0) * 1000")
        }
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Format invoice baru (2026-11-01) memakai satu nomor yang sama
        // untuk semua penjualan di hari yang sama ("INV - Minggu / 01 /
        // November"), jadi index invoiceNo tidak boleh unik lagi — dulu
        // unik karena nomornya dibuat dari jam sampai milidetik. Index
        // biasa dipertahankan untuk pencarian.
        db.execSQL("DROP INDEX IF EXISTS `index_sales_invoiceNo`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_invoiceNo` ON `sales` (`invoiceNo`)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Harga Beli dihapus total (permintaan Achmad 2026-11-10): nilai
        // tersimpan di medicines dan snapshot-nya di sale_items
        // dikosongkan. Kolomnya dibiarkan ada (tidak dibaca lagi oleh
        // layar/perhitungan mana pun) supaya migrasi ini murah dan aman.
        db.execSQL("UPDATE medicines SET buyPrice = 0")
        db.execSQL("UPDATE sale_items SET buyPrice = 0")
    }
}

@Database(
    entities = [MedicineEntity::class, SaleEntity::class, SaleItemEntity::class, CashEntryEntity::class, PurchaseEntity::class, PurchaseItemEntity::class],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun saleDao(): SaleDao
    abstract fun cashDao(): CashDao
    abstract fun purchaseDao(): PurchaseDao
}

object DatabaseProvider {
    @Volatile
    private var _db: AppDatabase? = null

    val db: AppDatabase
        get() = _db ?: throw IllegalStateException("DatabaseProvider belum diinisialisasi. Panggil init() terlebih dahulu.")

    // Jaring pengaman update (permintaan Achmad 2026-11-01): setiap kali
    // aplikasi terdeteksi baru diperbarui, berkas database disalin dulu
    // SEBELUM Room membukanya (migrasi berjalan saat database pertama kali
    // dibuka), jadi kalau suatu migrasi kelak bermasalah, data dari sebelum
    // update masih utuh tersimpan dan bisa dipulihkan. Deteksi update
    // memakai lastUpdateTime paket — BUKAN versionCode, karena versionCode
    // build masih 1. Disimpan maksimal 3 salinan terakhir di penyimpanan
    // internal aplikasi. Kegagalan menyalin tidak boleh menghalangi
    // aplikasi berjalan, jadi semua dibungkus try-catch.
    private fun snapshotDatabaseBeforeUpdate(context: Context) {
        try {
            val prefs = context.getSharedPreferences("apotek_prefs", Context.MODE_PRIVATE)
            val lastUpdate = try {
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            } catch (e: Exception) { 0L }
            if (lastUpdate == 0L || prefs.getLong("last_update_time", -1L) == lastUpdate) return
            val dbFile = context.getDatabasePath("apotek-lestari.db")
            if (dbFile.exists()) {
                val dir = File(context.filesDir, "db-snapshots").apply { mkdirs() }
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val target = File(dir, "apotek-lestari-$stamp.db")
                dbFile.copyTo(target, overwrite = true)
                // Berkas pendamping WAL ikut disalin supaya salinan lengkap.
                for (suffix in listOf("-wal", "-shm")) {
                    val side = File(dbFile.path + suffix)
                    if (side.exists()) side.copyTo(File(target.path + suffix), overwrite = true)
                }
                dir.listFiles { f -> f.name.endsWith(".db") }
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(3)
                    ?.forEach { f -> f.delete(); File(f.path + "-wal").delete(); File(f.path + "-shm").delete() }
            }
            prefs.edit().putLong("last_update_time", lastUpdate).apply()
        } catch (e: Exception) {
            // Abaikan: snapshot gagal tidak boleh menghalangi aplikasi.
        }
    }

    fun init(context: Context) {
        snapshotDatabaseBeforeUpdate(context)
        if (_db == null) {
            synchronized(this) {
                if (_db == null) {
                    _db = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "apotek-lestari.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14).build()
                }
            }
        }
    }

    fun isInitialized(): Boolean = _db != null
}
