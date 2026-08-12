-- ============================================================
-- V6: Partial Returns and Client Tracking Refactor
-- This migration squashes the logic for Epic 2 (Returns & Clients)
--
-- Includes:
--   1. Create `clientes` table and migrate existing names from `ventas`
--   2. Add `cliente_id` to `ventas` and `deudores`
--   3. Add `SALDO` payment method
--   4. Create immutable `devoluciones_venta` ledger table
-- ============================================================

-- ------------------------------------------------------------
-- Part A: Client Refactor (formerly V7)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS clientes (
                                        id                  SERIAL PRIMARY KEY,
                                        nombre              TEXT NOT NULL,
                                        telefono            TEXT,
                                        dni                 TEXT,
                                        saldo_a_favor       REAL NOT NULL DEFAULT 0,
                                        activo              BOOLEAN NOT NULL DEFAULT TRUE,
                                        fecha_creacion      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        creado_por          INTEGER NOT NULL DEFAULT 0,
                                        actualizado_por     INTEGER NOT NULL DEFAULT 0,
                                        CONSTRAINT fk_clientes_creado_por    FOREIGN KEY (creado_por)    REFERENCES usuarios(id),
                                        CONSTRAINT fk_clientes_actualizado_por FOREIGN KEY (actualizado_por) REFERENCES usuarios(id)
);

INSERT INTO clientes (nombre, creado_por, actualizado_por)
SELECT DISTINCT cliente_nombre, 0, 0
FROM ventas
WHERE cliente_nombre IS NOT NULL AND cliente_nombre != ''
ON CONFLICT DO NOTHING;

ALTER TABLE ventas ADD COLUMN IF NOT EXISTS cliente_id INTEGER;
ALTER TABLE ventas ADD CONSTRAINT fk_ventas_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id);

UPDATE ventas v
SET cliente_id = (SELECT c.id FROM clientes c WHERE c.nombre = v.cliente_nombre LIMIT 1)
WHERE v.cliente_nombre IS NOT NULL AND v.cliente_nombre != '';

ALTER TABLE deudores ADD COLUMN IF NOT EXISTS cliente_id INTEGER;
ALTER TABLE deudores ADD CONSTRAINT fk_deudores_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id);

UPDATE deudores d
SET cliente_id = (SELECT v.cliente_id FROM ventas v WHERE v.id = d.venta_id)
WHERE d.cliente_id IS NULL;

CREATE OR REPLACE FUNCTION fn_update_cliente_timestamp()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_update_cliente_timestamp ON clientes;
CREATE TRIGGER trg_update_cliente_timestamp
    BEFORE UPDATE ON clientes
    FOR EACH ROW
EXECUTE FUNCTION fn_update_cliente_timestamp();

-- ------------------------------------------------------------
-- Part B: SALDO Payment Method (formerly V8)
-- ------------------------------------------------------------
INSERT INTO metodos_pago (acronimo, descripcion, activo)
VALUES ('SALDO', 'Saldo a Favor', TRUE)
ON CONFLICT (acronimo) DO NOTHING;

-- ------------------------------------------------------------
-- Part C: Devoluciones (Returns) Ledger (formerly V9)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS devoluciones_venta (
                                                  id                  SERIAL PRIMARY KEY,
                                                  venta_id            INTEGER NOT NULL,
                                                  detalle_venta_id    INTEGER NOT NULL,
                                                  cantidad_devuelta   INTEGER NOT NULL CHECK (cantidad_devuelta > 0),
                                                  monto_reembolsado   REAL NOT NULL CHECK (monto_reembolsado >= 0),
                                                  tipo_reembolso      TEXT NOT NULL CHECK (tipo_reembolso IN ('SALDO', 'EFECTIVO')),
                                                  observaciones       TEXT,
                                                  fecha_devolucion    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  fecha_creacion      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  fecha_actualizacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  creado_por          INTEGER NOT NULL DEFAULT 0,
                                                  actualizado_por     INTEGER NOT NULL DEFAULT 0,

                                                  CONSTRAINT fk_devoluciones_venta      FOREIGN KEY (venta_id)         REFERENCES ventas(id),
                                                  CONSTRAINT fk_devoluciones_detalle    FOREIGN KEY (detalle_venta_id)  REFERENCES detalles_venta(id),
                                                  CONSTRAINT fk_devoluciones_creado_por FOREIGN KEY (creado_por)        REFERENCES usuarios(id),
                                                  CONSTRAINT fk_devoluciones_actualizado_por FOREIGN KEY (actualizado_por) REFERENCES usuarios(id)
);

CREATE INDEX IF NOT EXISTS idx_devoluciones_venta_id       ON devoluciones_venta(venta_id);
CREATE INDEX IF NOT EXISTS idx_devoluciones_detalle_venta_id ON devoluciones_venta(detalle_venta_id);

CREATE OR REPLACE FUNCTION fn_update_devolucion_timestamp()
    RETURNS TRIGGER AS $$
BEGIN
    NEW.fecha_actualizacion = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_update_devolucion_timestamp ON devoluciones_venta;
CREATE TRIGGER trg_update_devolucion_timestamp
    BEFORE UPDATE ON devoluciones_venta
    FOR EACH ROW
EXECUTE FUNCTION fn_update_devolucion_timestamp();
