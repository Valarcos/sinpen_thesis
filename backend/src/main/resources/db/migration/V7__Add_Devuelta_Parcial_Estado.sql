-- ============================================================
-- V7: Add DEVUELTA_PARCIAL state to ventas
-- Explicitly drops the existing CHECK constraint on ventas.estado
-- and recreates it to include the new 'DEVUELTA_PARCIAL' state.
-- ============================================================

ALTER TABLE ventas DROP CONSTRAINT ventas_estado_check;
ALTER TABLE ventas ADD CONSTRAINT ventas_estado_check CHECK (estado IN ('ACTIVA', 'ANULADA', 'PENDIENTE', 'CANCELADA_PENDIENTE', 'DEVUELTA_PARCIAL'));
