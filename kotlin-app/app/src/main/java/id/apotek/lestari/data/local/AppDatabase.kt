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
    val createdAt: String // ISO UTC, mis. "2026-10-26T00:15:00.000Z"
)

@Entity(tableName = "sales", indices = [Index(value = ["invoiceNo"], unique = true)])
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val invoiceNo: String,
    val total: Int,
    val paymentMethod: String,
    val amountPaid: Int,
    val changeAmount: Int,
    val createdAt: String // ISO UTC
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
    val subtotal: Int
)

@Entity(tableName = "cash_entries")
data class CashEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String, // "in" | "out"
    val category: String,
    val description: String,
    val amount: Int,
    val source: String, // "sale" | "manual"
    val createdAt: String // ISO UTC
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
    val createdAt: String // ISO UTC — kapan catatan dibuat
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
    val subtotal: Int
)

// ---- DAOs ----

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY id DESC")
    suspend fun getAll(): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun getById(id: Int): MedicineEntity?

    @Query("SELECT * FROM medicines WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): MedicineEntity?

    @Insert
    suspend fun insert(medicine: MedicineEntity): Long

    @Update
    suspend fun update(medicine: MedicineEntity): Int

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    @Query("DELETE FROM medicines")
    suspend fun deleteAll()

    @Query("UPDATE medicines SET stock = stock - :qty WHERE id = :id AND stock >= :qty")
    suspend fun decrementStock(id: Int, qty: Int): Int
}

@Dao
interface SaleDao {
    @Query("SELECT * FROM sales ORDER BY id DESC")
    suspend fun getAll(): List<SaleEntity>

    @Insert
    suspend fun insert(sale: SaleEntity): Long

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY id")
    suspend fun getItemsForSale(saleId: Int): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items")
    suspend fun getAllItems(): List<SaleItemEntity>

    @Insert
    suspend fun insertItems(items: List<SaleItemEntity>)

    @Query("DELETE FROM sales")
    suspend fun deleteAll()

    @Query("DELETE FROM sale_items")
    suspend fun deleteAllItems()
}

@Dao
interface CashDao {
    @Query("SELECT * FROM cash_entries ORDER BY id DESC")
    suspend fun getAll(): List<CashEntryEntity>

    @Insert
    suspend fun insert(entry: CashEntryEntity): Long

    @Query("DELETE FROM cash_entries WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    @Query("DELETE FROM cash_entries")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_entries WHERE type = 'in'")
    suspend fun sumIn(): Int

    @Query("SELECT COALESCE(SUM(amount), 0) FROM cash_entries WHERE type = 'out'")
    suspend fun sumOut(): Int
}

@Dao
interface PurchaseDao {
    @Query("SELECT * FROM purchases ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<PurchaseEntity>

    @Query("SELECT * FROM purchase_items ORDER BY id")
    suspend fun getAllItems(): List<PurchaseItemEntity>

    @Insert
    suspend fun insert(purchase: PurchaseEntity): Long

    @Insert
    suspend fun insertItems(items: List<PurchaseItemEntity>)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun deleteItemsForPurchase(purchaseId: Int)

    @Query("UPDATE purchases SET paymentType = :paymentType WHERE id = :id")
    suspend fun updatePaymentType(id: Int, paymentType: String): Int

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

@Database(
    entities = [MedicineEntity::class, SaleEntity::class, SaleItemEntity::class, CashEntryEntity::class, PurchaseEntity::class, PurchaseItemEntity::class],
    version = 10,
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

    fun init(context: Context) {
        if (_db == null) {
            synchronized(this) {
                if (_db == null) {
                    _db = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "apotek-lestari.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
                }
            }
        }
    }

    fun isInitialized(): Boolean = _db != null
}
