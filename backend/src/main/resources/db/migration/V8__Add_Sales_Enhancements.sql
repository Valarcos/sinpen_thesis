-- V8: Add recargo_global column to ventas table
-- This column represents a global surcharge (e.g., tax or credit card fees)
-- applied to the sale total. Formula: Total = Subtotal - Descuento + Recargo
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS recargo_global REAL DEFAULT 0;

-- Track whether a cheque was given for an initial sale or to pay off an existing debt
ALTER TABLE alertas_cheques ADD COLUMN IF NOT EXISTS tipo_origen TEXT DEFAULT 'VENTA' CHECK(tipo_origen IN ('VENTA', 'DEUDA_FIADO'));
ALTER TABLE alertas_cheques ADD COLUMN IF NOT EXISTS pago_deuda_id INTEGER REFERENCES pagos_deuda(id) ON DELETE SET NULL;

-- (Merged V13): Add version column for Optimistic Locking
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS version INTEGER DEFAULT 0;

-- (Merged V14): Add saldo_generado column for change tracking/in-store credit
ALTER TABLE ventas ADD COLUMN IF NOT EXISTS saldo_generado REAL NOT NULL DEFAULT 0;
