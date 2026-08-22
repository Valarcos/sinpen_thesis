-- 1. UBICACIONES
INSERT INTO ubicaciones (id, nombre) VALUES
                                         (1, 'Salón Principal'), (2, 'Depósito'), (3, 'Vidriera');;

-- 2. METODOS DE PAGO
INSERT INTO metodos_pago (id, acronimo, descripcion) VALUES
                                                         (1, 'E', 'Efectivo'),
                                                         (2, 'TCM', 'Tarjeta Crédito Macro'),
                                                         (3, 'TCS', 'Tarjeta Crédito Santander'),
                                                         (4, 'TCG', 'Tarjeta Crédito Galicia'),
                                                         (5, 'TBC', 'Transferencia Claudia'),
                                                         (6, 'TBS', 'Transferencia Silvia'),
                                                         (7, 'TBF', 'Transferencia Fico'),
                                                         (8, 'TBMP', 'MercadoPago'),
                                                         (9, 'TBH', 'Transferencia Habitualitá'),
                                                         (10, 'TB3', 'Transferencia a Terceros');;

-- 3. PRODUCTOS
INSERT INTO productos (id, codigo, descripcion, precio_costo, precio_minorista, precio_mayorista, cantidad_stock) VALUES
                                                                                                                      (1, 'ART-MUSC-N', 'Musculosa Negra - Básica', 10000.0, 56800.0, 45000.0, 0),
                                                                                                                      (2, 'ART-REM-CUE', 'Remera Cuello Desflecado', 21000.0, 69800.0, 55000.0, 0),
                                                                                                                      (3, 'ART-PANT-GAB', 'Pantalón Gabardina Beige', 15000.0, 45000.0, 35000.0, 0),
                                                                                                                      (4, 'ART-SWEATER', 'Sweater Lana Merino', 25000.0, 75000.0, 60000.0, 0),
                                                                                                                      (5, 'ACC-CINT', 'Cinturón Cuero', 5000.0, 12500.0, 10000.0, 0);;

-- 4. STOCK (Assign stock using subqueries to find IDs)
INSERT INTO stock_por_ubicacion (id, producto_id, ubicacion_id, cantidad) VALUES
                                                                              (1, (SELECT id FROM productos WHERE codigo='ART-MUSC-N'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 10),
                                                                              (2, (SELECT id FROM productos WHERE codigo='ART-REM-CUE'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 8),
                                                                              (3, (SELECT id FROM productos WHERE codigo='ART-PANT-GAB'), (SELECT id FROM ubicaciones WHERE nombre='Depósito'), 5),
                                                                              (4, (SELECT id FROM productos WHERE codigo='ART-SWEATER'), (SELECT id FROM ubicaciones WHERE nombre='Salón Principal'), 3),
                                                                              (5, (SELECT id FROM productos WHERE codigo='ACC-CINT'), (SELECT id FROM ubicaciones WHERE nombre='Vidriera'), 2);;

-- 5. SYSTEM USER & ROLES (Must exist BEFORE ventas - FK dependency)
-- REMOVE BEFORE PRODUCTION
-- Password: YakuNeveVala97
INSERT INTO usuarios (id, nombre, email, password_hash, rol) VALUES (0, 'SYSTEM', 'system@localhost', 'DISABLED', 'ADMIN');;

-- 6. EXPORTED USERS FROM SQLITE (Preserving IDs and relationships)
-- ID 1: admin@centralizesys.com (Exported)
-- ID 2: empleado@test.com (Test User preserved)
-- ID 7: marcosachavalmbaj@gmail.com (Exported with updated Hash/Role)
-- pragma: allowlist secret
-- sonar.issue.ignore.multicriteria squid:S8215
INSERT INTO usuarios (id, nombre, email, password_hash, rol) VALUES
                                                                 (1, 'Administrador', 'admin@centralizesys.com', '$2a$10$placeholderHash', 'EMPLEADO'),
                                                                 (2, 'Empleado Prueba', 'empleado@test.com', '$2a$10$1Hbj4W.yzq4r5JjdmAfviO3lPFP8L.P86zoWvMM5bZpCBtMrt7ECy', 'EMPLEADO'),
                                                                 (7, 'Administrador', 'marcosachavalmbaj@gmail.com', '$2a$10$lXbQfCXd4RpUG9GoHWuGi.KmkxpxhT5Cx66Gr0ScTfEoL6FNMDrtu', 'ADMIN');;

-- 7. HISTORIAL DE VENTA (Now safe: usuarios exist)
INSERT INTO ventas (id, fecha, cliente_nombre, total_venta, usuario_id) VALUES
                                                                            (1, '2023-10-01', 'Ingrid Peña', 56800.0, (SELECT id FROM usuarios WHERE email='admin@centralizesys.com')),
                                                                            (2, '2023-10-02', 'Maria Gonzalez', 69800.0, (SELECT id FROM usuarios WHERE email='admin@centralizesys.com'));;

-- ALTER TABLE ventas ADD COLUMN descuento_global REAL DEFAULT 0;
-- ALTER TABLE ventas ADD COLUMN tipo_venta TEXT NOT NULL DEFAULT 'MINORISTA';
--
--
-- CREATE TABLE IF NOT EXISTS pagos_deuda (
--     id INTEGER PRIMARY KEY AUTOINCREMENT,
--     deuda_id INTEGER NOT NULL,
--     metodo_pago_id INTEGER NOT NULL,
--     monto REAL NOT NULL,
--     fecha_pago TEXT NOT NULL, -- YYYY-MM-DD
--     observaciones TEXT,
--     usuario_id INTEGER, -- Auditoria de quien cobró
--     FOREIGN KEY (deuda_id) REFERENCES deudores(id),
--     FOREIGN KEY (metodo_pago_id) REFERENCES metodos_pago(id),
--     FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
--

-- SAFE Dummy Data Generation Script
-- Usage: sqlite3 data/centralizesys.db < backend/scripts/dummy_data.sql

-- Uses INSERT OR IGNORE to prevent Unique Constraint Crashes if run multiple times.
-- IDs start at 100 to avoid conflict with default data (IDs 1-5).

-- DATA GENERATION SCRIPT FOR CENTRALIZESYS
-- Purpose: Populate database with Products and Sales history for testing.
-- Run this in your SQLite database tool (e.g., DB Browser for SQLite).

-- DATA GENERATION SCRIPT FOR CENTRALIZESYS
-- Purpose: Populate database with Products and Sales history for testing.
-- Run this in your SQLite database tool (e.g., DB Browser for SQLite).

-- DATA GENERATION SCRIPT FOR CENTRALIZESYS
-- Purpose: Populate database with Products and Sales history for testing.
-- Run this in your SQLite database tool (e.g., DB Browser for SQLite).

BEGIN TRANSACTION;;

-- 1. CLEANUP (Optional - Uncomment if you want to start fresh with these tables)
DELETE FROM pagos_venta WHERE venta_id >= 1000;;
DELETE FROM detalles_venta WHERE venta_id >= 1000;;
DELETE FROM ventas WHERE id >= 1000;;
DELETE FROM stock_por_ubicacion WHERE producto_id >= 100;;
DELETE FROM productos WHERE id >= 100;;

-- 2. INSERT DUMMY PRODUCTS (IDs 100-119)
INSERT INTO productos (id, codigo, descripcion, precio_costo, precio_minorista, precio_mayorista, cantidad_stock) VALUES
                                                                                                                      (100, 'DUMMY-1', 'Smartphone Samsung A1', 150.0, 200.0, 180.0, 100),
                                                                                                                      (101, 'DUMMY-2', 'Smartphone Samsung A2', 160.0, 220.0, 190.0, 100),
                                                                                                                      (102, 'DUMMY-3', 'Auriculares Sony XM', 50.0, 80.0, 70.0, 100),
                                                                                                                      (103, 'DUMMY-4', 'Cargador Rápido USBC', 10.0, 25.0, 20.0, 100),
                                                                                                                      (104, 'DUMMY-5', 'Funda Silicona Roja', 2.0, 10.0, 8.0, 100),
                                                                                                                      (105, 'DUMMY-6', 'Protector Pantalla Glass', 1.0, 5.0, 4.0, 100),
                                                                                                                      (106, 'DUMMY-7', 'Teclado Mecánico RGB', 40.0, 80.0, 65.0, 100),
                                                                                                                      (107, 'DUMMY-8', 'Mouse Gamer Logitech', 20.0, 45.0, 35.0, 100),
                                                                                                                      (108, 'DUMMY-9', 'Monitor LG 24 Pulgadas', 100.0, 150.0, 130.0, 100),
                                                                                                                      (109, 'DUMMY-10', 'Cable HDMI 2m', 3.0, 12.0, 10.0, 100),
                                                                                                                      (110, 'DUMMY-11', 'Soporte Monitor VESA', 15.0, 35.0, 28.0, 100),
                                                                                                                      (111, 'DUMMY-12', 'Notebook Dell Inspiron', 500.0, 750.0, 650.0, 100),
                                                                                                                      (112, 'DUMMY-13', 'Mochila Porta Notebook', 20.0, 50.0, 40.0, 100),
                                                                                                                      (113, 'DUMMY-14', 'Disco SSD 500GB', 30.0, 60.0, 50.0, 100),
                                                                                                                      (114, 'DUMMY-15', 'Memoria RAM 8GB DDR4', 25.0, 45.0, 38.0, 100),
                                                                                                                      (115, 'DUMMY-16', 'Pendrive 64GB Kingston', 5.0, 15.0, 12.0, 100),
                                                                                                                      (116, 'DUMMY-17', 'Webcam FullHD 1080p', 25.0, 55.0, 45.0, 100),
                                                                                                                      (117, 'DUMMY-18', 'Micrófono Condensador', 35.0, 70.0, 60.0, 100),
                                                                                                                      (118, 'DUMMY-19', 'Aro de Luz LED', 10.0, 30.0, 25.0, 100),
                                                                                                                      (119, 'DUMMY-20', 'Silla Gamer Ergonómica', 120.0, 250.0, 200.0, 100);;

-- 3. INSERT STOCK FOR DUMMY PRODUCTS (Using Ubicacion ID 1)
INSERT INTO stock_por_ubicacion (producto_id, ubicacion_id, cantidad)
SELECT id, 1, 50 FROM productos WHERE id >= 100 AND id <= 119;;

-- 4. INSERT HISTORIC SALES (IDs 1000+)
-- Dates:
-- Group A: Recent (Today/Yesterday)
-- Group B: Last 30 Days (e.g., -15 days)
-- Group C: Older (>30 days, <60 days)
-- Group D: Very Old (>70 days)

-- A. SALES - TODAY (5 Sales)
INSERT INTO ventas (id, fecha, cliente_nombre, total_venta, descuento_global, recargo_global, tipo_venta, usuario_id) VALUES
                                                                                                                          (1000, CURRENT_DATE, 'Juan Perez', 250.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1001, CURRENT_DATE, 'Maria Gomez', 45.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1002, CURRENT_DATE, 'Consumidor Final', 200.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1003, CURRENT_DATE, 'Empresa ABC', 1500.0, 10.0, 0, 'MAYORISTA', 1),
                                                                                                                          (1004, CURRENT_DATE, 'Lucas Rodriguez', 25.0, 0, 0, 'MINORISTA', 1);;

-- B. SALES - LAST 15 DAYS (5 Sales)
INSERT INTO ventas (id, fecha, cliente_nombre, total_venta, descuento_global, recargo_global, tipo_venta, usuario_id) VALUES
                                                                                                                          (1005, CURRENT_DATE - INTERVAL '5 days', 'Ana Martinez', 80.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1006, CURRENT_DATE - INTERVAL '10 days', 'Carlos Lopez', 90.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1007, CURRENT_DATE - INTERVAL '12 days', 'Sofia Silva', 220.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1008, CURRENT_DATE - INTERVAL '15 days', 'Consumidor Final', 12.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1009, CURRENT_DATE - INTERVAL '18 days', 'Miguel Torres', 750.0, 0, 0, 'MINORISTA', 1);;

-- C. SALES - 30 to 60 DAYS AGO (5 Sales)
INSERT INTO ventas (id, fecha, cliente_nombre, total_venta, descuento_global, recargo_global, tipo_venta, usuario_id) VALUES
                                                                                                                          (1010, CURRENT_DATE - INTERVAL '35 days', 'Valentina Ruiz', 60.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1011, CURRENT_DATE - INTERVAL '40 days', 'Mateo Fernandez', 45.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1012, CURRENT_DATE - INTERVAL '45 days', 'Consumidor Final', 215.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1013, CURRENT_DATE - INTERVAL '50 days', 'Isabella Diaz', 15.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1014, CURRENT_DATE - INTERVAL '55 days', 'Benjamin Costa', 55.0, 0, 0, 'MINORISTA', 1);;

-- D. SALES - 70+ DAYS AGO (5 Sales)
INSERT INTO ventas (id, fecha, cliente_nombre, total_venta, descuento_global, recargo_global, tipo_venta, usuario_id) VALUES
                                                                                                                          (1015, CURRENT_DATE - INTERVAL '70 days', 'Viejo Cliente 1', 200.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1016, CURRENT_DATE - INTERVAL '75 days', 'Viejo Cliente 2', 200.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1017, CURRENT_DATE - INTERVAL '80 days', 'Viejo Cliente 3', 200.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1018, CURRENT_DATE - INTERVAL '85 days', 'Viejo Cliente 4', 200.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1019, CURRENT_DATE - INTERVAL '90 days', 'Viejo Cliente 5', 200.0, 0, 0, 'MINORISTA', 1);;

-- E. BULK SALES - RECENT (25 Sales) - Ensure pagination triggers (Total > 20)
-- IDs 1020-1044
INSERT INTO ventas (id, fecha, cliente_nombre, total_venta, descuento_global, recargo_global, tipo_venta, usuario_id) VALUES
                                                                                                                          (1020, CURRENT_DATE - INTERVAL '1 day', 'Cliente Bulk 1', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1021, CURRENT_DATE - INTERVAL '1 day', 'Cliente Bulk 2', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1022, CURRENT_DATE - INTERVAL '2 days', 'Cliente Bulk 3', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1023, CURRENT_DATE - INTERVAL '2 days', 'Cliente Bulk 4', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1024, CURRENT_DATE - INTERVAL '2 days', 'Cliente Bulk 5', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1025, CURRENT_DATE - INTERVAL '3 days', 'Cliente Bulk 6', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1026, CURRENT_DATE - INTERVAL '3 days', 'Cliente Bulk 7', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1027, CURRENT_DATE - INTERVAL '3 days', 'Cliente Bulk 8', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1028, CURRENT_DATE - INTERVAL '4 days', 'Cliente Bulk 9', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1029, CURRENT_DATE - INTERVAL '4 days', 'Cliente Bulk 10', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1030, CURRENT_DATE - INTERVAL '5 days', 'Cliente Bulk 11', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1031, CURRENT_DATE - INTERVAL '5 days', 'Cliente Bulk 12', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1032, CURRENT_DATE - INTERVAL '6 days', 'Cliente Bulk 13', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1033, CURRENT_DATE - INTERVAL '6 days', 'Cliente Bulk 14', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1034, CURRENT_DATE - INTERVAL '7 days', 'Cliente Bulk 15', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1035, CURRENT_DATE - INTERVAL '7 days', 'Cliente Bulk 16', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1036, CURRENT_DATE - INTERVAL '8 days', 'Cliente Bulk 17', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1037, CURRENT_DATE - INTERVAL '8 days', 'Cliente Bulk 18', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1038, CURRENT_DATE - INTERVAL '9 days', 'Cliente Bulk 19', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1039, CURRENT_DATE - INTERVAL '9 days', 'Cliente Bulk 20', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1040, CURRENT_DATE - INTERVAL '10 days', 'Cliente Bulk 21', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1041, CURRENT_DATE - INTERVAL '10 days', 'Cliente Bulk 22', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1042, CURRENT_DATE - INTERVAL '11 days', 'Cliente Bulk 23', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1043, CURRENT_DATE - INTERVAL '11 days', 'Cliente Bulk 24', 50.0, 0, 0, 'MINORISTA', 1),
                                                                                                                          (1044, CURRENT_DATE - INTERVAL '12 days', 'Cliente Bulk 25', 50.0, 0, 0, 'MINORISTA', 1);;


-- 5. INSERT SALE DETAILS (One detail per sale for simplicity, covering ALL sales 1000-1019)
INSERT INTO detalles_venta (venta_id, producto_id, codigo_snapshot, descripcion_snapshot, cantidad, precio_lista, descuento_valor, precio_unitario, subtotal) VALUES
-- Sale 1000 ($250)
(1000, 119, 'DUMMY-20', 'Silla Gamer Ergonómica', 1, 250.0, 0, 250.0, 250.0),
-- Sale 1001 ($45)
(1001, 107, 'DUMMY-8', 'Mouse Gamer Logitech', 1, 45.0, 0, 45.0, 45.0),
-- Sale 1002 ($200)
(1002, 100, 'DUMMY-1', 'Smartphone Samsung A1', 1, 200.0, 0, 200.0, 200.0),
-- Sale 1003 ($1500)
(1003, 111, 'DUMMY-12', 'Notebook Dell Inspiron', 2, 750.0, 0, 750.0, 1500.0),
-- Sale 1004 ($25)
(1004, 103, 'DUMMY-4', 'Cargador Rápido USBC', 1, 25.0, 0, 25.0, 25.0),
-- Sale 1005 ($80)
(1005, 106, 'DUMMY-7', 'Teclado Mecánico RGB', 1, 80.0, 0, 80.0, 80.0),
-- Sale 1006 ($90)
(1006, 102, 'DUMMY-3', 'Auriculares Sony XM', 1, 80.0, 0, 80.0, 80.0),
(1006, 104, 'DUMMY-5', 'Funda Silicona Roja', 1, 10.0, 0, 10.0, 10.0),
-- Sale 1007 ($220)
(1007, 101, 'DUMMY-2', 'Smartphone Samsung A2', 1, 220.0, 0, 220.0, 220.0),
-- Sale 1008 ($12)
(1008, 109, 'DUMMY-10', 'Cable HDMI 2m', 1, 12.0, 0, 12.0, 12.0),
-- Sale 1009 ($750)
(1009, 111, 'DUMMY-12', 'Notebook Dell Inspiron', 1, 750.0, 0, 750.0, 750.0),
-- Sale 1010 ($60)
(1010, 113, 'DUMMY-14', 'Disco SSD 500GB', 2, 60.0, 0, 30.0, 60.0),
-- Sale 1011 ($45)
(1011, 114, 'DUMMY-15', 'Memoria RAM 8GB DDR4', 1, 45.0, 0, 45.0, 45.0),
-- Sale 1012 ($215)
(1012, 100, 'DUMMY-1', 'Smartphone Samsung A1', 1, 200.0, 0, 200.0, 200.0),
(1012, 115, 'DUMMY-16', 'Pendrive 64GB Kingston', 1, 15.0, 0, 15.0, 15.0),
-- Sale 1013 ($15)
(1013, 115, 'DUMMY-16', 'Pendrive 64GB Kingston', 1, 15.0, 0, 15.0, 15.0),
-- Sale 1014 ($55)
(1014, 116, 'DUMMY-17', 'Webcam FullHD 1080p', 1, 55.0, 0, 55.0, 55.0),
-- Sale 1015 ($200)
(1015, 100, 'DUMMY-1', 'Smartphone Samsung A1', 1, 200.0, 0, 200.0, 200.0),
-- Sale 1016 ($200)
(1016, 100, 'DUMMY-1', 'Smartphone Samsung A1', 1, 200.0, 0, 200.0, 200.0),
-- Sale 1017 ($200)
(1017, 100, 'DUMMY-1', 'Smartphone Samsung A1', 1, 200.0, 0, 200.0, 200.0),
-- Sale 1018 ($200)
(1018, 100, 'DUMMY-1', 'Smartphone Samsung A1', 1, 200.0, 0, 200.0, 200.0),
-- Sale 1019 ($200)
(1019, 100, 'DUMMY-1', 'Smartphone Samsung A1', 1, 200.0, 0, 200.0, 200.0),
-- Bulk Sales 1020-1044 (Generic Items)
(1020, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1021, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1022, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1023, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1024, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1025, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1026, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1027, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1028, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1029, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1030, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1031, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1032, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1033, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1034, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1035, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1036, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1037, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1038, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1039, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1040, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1041, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1042, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1043, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0),
(1044, 105, 'DUMMY-6', 'Protector Pantalla Glass', 10, 5.0, 0, 5.0, 50.0);;

-- 6. INSERT PAYMENTS (Mapping payments to ALL sales 1000-1044)
-- Assumes Metodo Pago ID 1 = Efectivo
INSERT INTO pagos_venta (venta_id, metodo_pago_id, monto) VALUES
                                                              (1000, 1, 250.0),
                                                              (1001, 1, 45.0),
                                                              (1002, 1, 200.0),
                                                              (1003, 1, 1500.0),
                                                              (1004, 1, 25.0),
                                                              (1005, 1, 80.0),
                                                              (1006, 1, 90.0),
                                                              (1007, 1, 220.0),
                                                              (1008, 1, 12.0),
                                                              (1009, 1, 750.0),
                                                              (1010, 1, 60.0),
                                                              (1011, 1, 45.0),
                                                              (1012, 1, 215.0),
                                                              (1013, 1, 15.0),
                                                              (1014, 1, 55.0),
                                                              (1015, 1, 200.0),
                                                              (1016, 1, 200.0),
                                                              (1017, 1, 200.0),
                                                              (1018, 1, 200.0),
                                                              (1019, 1, 200.0),
-- Recent Bulk
                                                              (1020, 1, 50.0),
                                                              (1021, 1, 50.0),
                                                              (1022, 1, 50.0),
                                                              (1023, 1, 50.0),
                                                              (1024, 1, 50.0),
                                                              (1025, 1, 50.0),
                                                              (1026, 1, 50.0),
                                                              (1027, 1, 50.0),
                                                              (1028, 1, 50.0),
                                                              (1029, 1, 50.0),
                                                              (1030, 1, 50.0),
                                                              (1031, 1, 50.0),
                                                              (1032, 1, 50.0),
                                                              (1033, 1, 50.0),
                                                              (1034, 1, 50.0),
                                                              (1035, 1, 50.0),
                                                              (1036, 1, 50.0),
                                                              (1037, 1, 50.0),
                                                              (1038, 1, 50.0),
                                                              (1039, 1, 50.0),
                                                              (1040, 1, 50.0),
                                                              (1041, 1, 50.0),
                                                              (1042, 1, 50.0),
                                                              (1043, 1, 50.0),
                                                              (1044, 1, 50.0);;

COMMIT;;
