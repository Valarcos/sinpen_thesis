-- ============================================================
-- V5: Add Audit Columns to productos table
-- Adds: fecha_creacion, fecha_actualizacion, creado_por, actualizado_por
-- Strategy:
--   1. Add nullable columns first (safe for existing rows)
--   2. Backfill all existing rows with the system user (id=0) and CURRENT_TIMESTAMP
--   3. Enforce NOT NULL after backfill
--   4. Add FK constraints to usuarios(id)
--   5. Create trigger to auto-update fecha_actualizacion on every UPDATE
-- ============================================================

-- Step 1: Add new columns as nullable to avoid lock issues on non-empty tables
ALTER TABLE productos ADD COLUMN IF NOT EXISTS fecha_creacion TIMESTAMP;
ALTER TABLE productos ADD COLUMN IF NOT EXISTS fecha_actualizacion TIMESTAMP;
ALTER TABLE productos ADD COLUMN IF NOT EXISTS creado_por INTEGER;
ALTER TABLE productos ADD COLUMN IF NOT EXISTS actualizado_por INTEGER;

-- Step 2: Backfill existing rows with the reserved System user (id=0).
-- Since id=0 is a known pre-existing sentinel in 'usuarios' (inserted in V1),
-- this is safe against FK violations.
UPDATE productos
SET
    fecha_creacion     = CURRENT_TIMESTAMP,
    fecha_actualizacion = CURRENT_TIMESTAMP,
    creado_por         = 0,
    actualizado_por    = 0
WHERE fecha_creacion IS NULL;

-- Step 3: Enforce NOT NULL constraints AFTER backfill and SET DEFAULTS
ALTER TABLE productos ALTER COLUMN fecha_creacion SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE productos ALTER COLUMN fecha_creacion SET NOT NULL;

ALTER TABLE productos ALTER COLUMN fecha_actualizacion SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE productos ALTER COLUMN fecha_actualizacion SET NOT NULL;

ALTER TABLE productos ALTER COLUMN creado_por SET DEFAULT 0;
ALTER TABLE productos ALTER COLUMN creado_por SET NOT NULL;

ALTER TABLE productos ALTER COLUMN actualizado_por SET DEFAULT 0;
ALTER TABLE productos ALTER COLUMN actualizado_por SET NOT NULL;

-- Step 4: Add FK constraints linking to the usuarios table.
-- Named explicitly to be identifiable and droppable in future migrations if needed.
DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_productos_creado_por') THEN
            ALTER TABLE productos
                ADD CONSTRAINT fk_productos_creado_por
                    FOREIGN KEY (creado_por) REFERENCES usuarios(id);
        END IF;
    END
$$;

DO $$
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_productos_actualizado_por') THEN
            ALTER TABLE productos
                ADD CONSTRAINT fk_productos_actualizado_por
                    FOREIGN KEY (actualizado_por) REFERENCES usuarios(id);
        END IF;
    END
$$;

-- Step 5: Create trigger function to auto-stamp fecha_actualizacion on UPDATE.
-- DB-level enforcement ensures correctness regardless of which client modifies the row.
CREATE OR REPLACE FUNCTION fn_update_producto_timestamp()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_update_producto_timestamp ON productos;
CREATE TRIGGER trg_update_producto_timestamp
    BEFORE UPDATE ON productos
    FOR EACH ROW
EXECUTE FUNCTION fn_update_producto_timestamp();
