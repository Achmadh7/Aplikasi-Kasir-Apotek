import { integer, sqliteTable, text } from "drizzle-orm/sqlite-core";

export const medicines = sqliteTable("medicines", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  name: text("name").notNull(),
  category: text("category").notNull().default("Umum"),
  unit: text("unit").notNull().default("Strip"),
  buyPrice: integer("buy_price").notNull().default(0),
  sellPrice: integer("sell_price").notNull().default(0),
  stock: integer("stock").notNull().default(0),
  minStock: integer("min_stock").notNull().default(10),
  expiryDate: text("expiry_date"),
  barcode: text("barcode"),
  createdAt: integer("created_at", { mode: "timestamp_ms" }).notNull().$defaultFn(() => new Date()),
});

export const sales = sqliteTable("sales", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  invoiceNo: text("invoice_no").notNull(),
  total: integer("total").notNull(),
  paymentMethod: text("payment_method").notNull().default("Tunai"),
  amountPaid: integer("amount_paid").notNull().default(0),
  changeAmount: integer("change_amount").notNull().default(0),
  createdAt: integer("created_at", { mode: "timestamp_ms" }).notNull().$defaultFn(() => new Date()),
});

export const saleItems = sqliteTable("sale_items", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  saleId: integer("sale_id").notNull(),
  medicineId: integer("medicine_id").notNull(),
  medicineName: text("medicine_name").notNull(),
  qty: integer("qty").notNull(),
  price: integer("price").notNull(),
  buyPrice: integer("buy_price").notNull().default(0),
  subtotal: integer("subtotal").notNull(),
});

export const cashEntries = sqliteTable("cash_entries", {
  id: integer("id").primaryKey({ autoIncrement: true }),
  type: text("type", { enum: ["in", "out"] }).notNull(),
  category: text("category").notNull(),
  description: text("description").notNull(),
  amount: integer("amount").notNull(),
  source: text("source").notNull().default("manual"),
  saleId: integer("sale_id"),
  createdAt: integer("created_at", { mode: "timestamp_ms" }).notNull().$defaultFn(() => new Date()),
});
