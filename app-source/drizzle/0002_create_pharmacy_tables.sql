CREATE TABLE medicines (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  name TEXT NOT NULL,
  category TEXT NOT NULL DEFAULT 'Umum',
  unit TEXT NOT NULL DEFAULT 'Strip',
  buy_price INTEGER NOT NULL DEFAULT 0,
  sell_price INTEGER NOT NULL DEFAULT 0,
  stock INTEGER NOT NULL DEFAULT 0,
  min_stock INTEGER NOT NULL DEFAULT 10,
  expiry_date TEXT,
  created_at INTEGER NOT NULL
);
--> statement-breakpoint
CREATE TABLE sales (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  invoice_no TEXT NOT NULL,
  total INTEGER NOT NULL,
  payment_method TEXT NOT NULL DEFAULT 'Tunai',
  amount_paid INTEGER NOT NULL DEFAULT 0,
  change_amount INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
--> statement-breakpoint
CREATE TABLE sale_items (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sale_id INTEGER NOT NULL,
  medicine_id INTEGER NOT NULL,
  medicine_name TEXT NOT NULL,
  qty INTEGER NOT NULL,
  price INTEGER NOT NULL,
  subtotal INTEGER NOT NULL
);
--> statement-breakpoint
CREATE TABLE cash_entries (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  type TEXT NOT NULL,
  category TEXT NOT NULL,
  description TEXT NOT NULL,
  amount INTEGER NOT NULL,
  source TEXT NOT NULL DEFAULT 'manual',
  sale_id INTEGER,
  created_at INTEGER NOT NULL
);
