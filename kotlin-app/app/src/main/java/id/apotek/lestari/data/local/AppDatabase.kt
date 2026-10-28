package id.apotek.lestari.data.local

import android.content.Context
import androidx.room.Dao
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
    val stock: Int,
    val minStock: Int,
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

@Database(
    entities = [MedicineEntity::class, SaleEntity::class, SaleItemEntity::class, CashEntryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicineDao(): MedicineDao
    abstract fun saleDao(): SaleDao
    abstract fun cashDao(): CashDao
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
                    ).addMigrations(MIGRATION_1_2).build()
                }
            }
        }
    }

    fun isInitialized(): Boolean = _db != null
}
