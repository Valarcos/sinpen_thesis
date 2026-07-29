-- Add razon_descuento to detalles_venta to support specific pricing and discount reasons
ALTER TABLE detalles_venta ADD COLUMN razon_descuento TEXT;
