-- Datos de demostracion. SOLO DEMOSTRACION LOCAL: las claves publicas NO son aptas para produccion.
-- Migracion repetible de Flyway: solo se carga con el perfil "demo"
-- (spring.flyway.locations incluye classpath:db/demo en application-demo.properties).
-- Se ejecuta despues de todas las migraciones versionadas y de nuevo cada vez que cambie este archivo.
-- Es idempotente: si alguna tabla ya tiene datos, no inserta nada.
-- Flyway la ejecuta dentro de una transaccion: no usar BEGIN/COMMIT.
--
-- Todos los datos son ficticios (personas, documentos, telefonos y correos @example.com).
-- Fechas relativas a la fecha actual: lotes con CURRENT_DATE + n; ventas, ordenes y movimientos
-- con la hora de Lima (NOW() AT TIME ZONE 'America/Lima') menos n dias.
-- Coherencia: el stock de cada lote es exactamente entradas - salidas de detalle_movimiento.
-- Se fija el stock final de cada lote y las existencias iniciales se calculan al final para cuadrar
-- con las recepciones, las ventas y las mermas. No inserta filas en auditoria.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    v_ahora       TIMESTAMP := NOW() AT TIME ZONE 'America/Lima';
    v_hoy         TIMESTAMP := date_trunc('day', NOW() AT TIME ZONE 'America/Lima');
    v_admin       INTEGER;
    v_vendedor    INTEGER;
    v_almacen     INTEGER;
    v_compras     INTEGER;
    v_juan        INTEGER;
    v_otros       INTEGER[];
    v_pool        INTEGER[];
    v_tam_pool    INTEGER;
    v_venta       INTEGER;
    v_movimiento  INTEGER;
    v_numero      VARCHAR(30);
    v_fecha       TIMESTAMP;
    v_cliente     INTEGER;
    v_usuario     INTEGER;
    v_metodo      VARCHAR(12);
    v_lineas      INTEGER;
    v_lote        INTEGER;
    v_cantidad    INTEGER;
    v_descuento   NUMERIC(12,2);
    v_recepcion   RECORD;
    v_merma       RECORD;
    v_errores     INTEGER;
BEGIN
    IF EXISTS (SELECT 1 FROM usuario)
       OR EXISTS (SELECT 1 FROM medicamento)
       OR EXISTS (SELECT 1 FROM lote)
       OR EXISTS (SELECT 1 FROM proveedor)
       OR EXISTS (SELECT 1 FROM cliente)
       OR EXISTS (SELECT 1 FROM orden_compra)
       OR EXISTS (SELECT 1 FROM detalle_orden_compra)
       OR EXISTS (SELECT 1 FROM venta)
       OR EXISTS (SELECT 1 FROM detalle_venta)
       OR EXISTS (SELECT 1 FROM movimiento_inventario)
       OR EXISTS (SELECT 1 FROM detalle_movimiento)
       OR EXISTS (SELECT 1 FROM categoria) THEN
        RAISE NOTICE 'La base ya tiene datos: no se cargan los datos de demostracion.';
        RETURN;
    END IF;

    -- 1. Usuarios (las 4 cuentas publicas de demostracion) --------------------------------------
    INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, ultimo_acceso) VALUES
    ('Administrador de prueba', 'admin_demo',
     '{bcrypt}' || crypt('AdminDemo2026!', gen_salt('bf', 10)), 'ADMINISTRADOR', (NOW() AT TIME ZONE 'America/Lima') - INTERVAL '2 hours'),
    ('Almacenero de prueba', 'almacen_demo',
     '{bcrypt}' || crypt('FarmaciaDemo2026!', gen_salt('bf', 10)), 'ALMACENERO', (NOW() AT TIME ZONE 'America/Lima') - INTERVAL '1 day'),
    ('Compras de prueba', 'compras_demo',
     '{bcrypt}' || crypt('ComprasDemo2026!', gen_salt('bf', 10)), 'COMPRAS', (NOW() AT TIME ZONE 'America/Lima') - INTERVAL '10 days'),
    ('Vendedor de prueba', 'vendedor_demo',
     '{bcrypt}' || crypt('VendedorDemo2026!', gen_salt('bf', 10)), 'VENDEDOR', NULL);

    SELECT id_usuario INTO v_admin FROM usuario WHERE nombre_usuario = 'admin_demo';
    SELECT id_usuario INTO v_vendedor FROM usuario WHERE nombre_usuario = 'vendedor_demo';
    SELECT id_usuario INTO v_almacen FROM usuario WHERE nombre_usuario = 'almacen_demo';
    SELECT id_usuario INTO v_compras FROM usuario WHERE nombre_usuario = 'compras_demo';

    -- 2. Categorias (arbol N-ario) ---------------------------------------------------------------
    INSERT INTO categoria (nombre, id_padre) VALUES
    ('Analgésicos', NULL),
    ('Antibióticos', NULL),
    ('Cardiovasculares', NULL),
    ('Antidiabéticos', NULL),
    ('Gastrointestinales', NULL),
    ('Antialérgicos', NULL),
    ('Vitaminas y suplementos', NULL);

    INSERT INTO categoria (nombre, id_padre)
    SELECT 'Antiinflamatorios', id_categoria FROM categoria WHERE nombre = 'Analgésicos' AND id_padre IS NULL;

    INSERT INTO categoria (nombre, id_padre)
    SELECT 'AINEs', id_categoria FROM categoria WHERE nombre = 'Antiinflamatorios';

    -- 3. Medicamentos (unidad de control: CAJA o FRASCO) -----------------------------------------
    INSERT INTO medicamento
    (codigo, nombre, concentracion, presentacion, unidad_control, precio_venta, stock_minimo, id_categoria)
    SELECT v.codigo, v.nombre, v.concentracion, v.presentacion, v.unidad, v.precio, v.minimo, c.id_categoria
    FROM (VALUES
        ('MED-001', 'Paracetamol', '500 mg', 'Caja de 20 tabletas', 'CAJA', 8.50, 10, 'Analgésicos'),
        ('MED-002', 'Ibuprofeno', '400 mg', 'Caja de 10 tabletas', 'CAJA', 13.50, 5, 'AINEs'),
        ('MED-003', 'Naproxeno', '550 mg', 'Caja de 10 tabletas', 'CAJA', 15.90, 5, 'AINEs'),
        ('MED-004', 'Diclofenaco', '50 mg', 'Caja de 20 tabletas', 'CAJA', 9.80, 8, 'AINEs'),
        ('MED-005', 'Ketorolaco', '10 mg', 'Caja de 10 tabletas', 'CAJA', 11.20, 5, 'AINEs'),
        ('MED-006', 'Metamizol', '500 mg', 'Caja de 20 tabletas', 'CAJA', 7.90, 10, 'Analgésicos'),
        ('MED-007', 'Dexametasona', '4 mg', 'Caja de 10 tabletas', 'CAJA', 6.50, 5, 'Antiinflamatorios'),
        ('MED-008', 'Prednisona', '20 mg', 'Caja de 20 tabletas', 'CAJA', 10.40, 5, 'Antiinflamatorios'),
        ('MED-009', 'Azitromicina', '500 mg', 'Caja de 3 tabletas', 'CAJA', 18.90, 10, 'Antibióticos'),
        ('MED-010', 'Ciprofloxacino', '500 mg', 'Caja de 10 tabletas', 'CAJA', 14.60, 8, 'Antibióticos'),
        ('MED-011', 'Cefalexina', '500 mg', 'Caja de 12 cápsulas', 'CAJA', 16.80, 8, 'Antibióticos'),
        ('MED-012', 'Losartán', '50 mg', 'Caja de 30 tabletas', 'CAJA', 12.90, 15, 'Cardiovasculares'),
        ('MED-013', 'Amlodipino', '5 mg', 'Caja de 30 tabletas', 'CAJA', 11.50, 15, 'Cardiovasculares'),
        ('MED-014', 'Atorvastatina', '20 mg', 'Caja de 30 tabletas', 'CAJA', 24.90, 10, 'Cardiovasculares'),
        ('MED-015', 'Enalapril', '10 mg', 'Caja de 20 tabletas', 'CAJA', 8.90, 10, 'Cardiovasculares'),
        ('MED-016', 'Amoxicilina', '500 mg', 'Caja de 30 cápsulas', 'CAJA', 12.00, 10, 'Antibióticos'),
        ('MED-017', 'Metformina', '850 mg', 'Caja de 30 tabletas', 'CAJA', 9.50, 20, 'Antidiabéticos'),
        ('MED-018', 'Glibenclamida', '5 mg', 'Caja de 30 tabletas', 'CAJA', 7.20, 15, 'Antidiabéticos'),
        ('MED-019', 'Omeprazol', '20 mg', 'Caja de 30 cápsulas', 'CAJA', 8.90, 20, 'Gastrointestinales'),
        ('MED-020', 'Domperidona', '10 mg', 'Caja de 20 tabletas', 'CAJA', 10.80, 8, 'Gastrointestinales'),
        ('MED-021', 'Loperamida', '2 mg', 'Caja de 12 tabletas', 'CAJA', 5.90, 10, 'Gastrointestinales'),
        ('MED-022', 'Sales de rehidratación oral', '27,9 g', 'Caja de 25 sobres', 'CAJA', 32.50, 15, 'Gastrointestinales'),
        ('MED-023', 'Loratadina', '10 mg', 'Caja de 10 tabletas', 'CAJA', 6.80, 10, 'Antialérgicos'),
        ('MED-024', 'Cetirizina', '10 mg', 'Caja de 10 tabletas', 'CAJA', 7.40, 10, 'Antialérgicos'),
        ('MED-025', 'Clorfenamina', '2 mg/5 mL', 'Frasco de 120 mL', 'FRASCO', 6.20, 10, 'Antialérgicos'),
        ('MED-026', 'Complejo B', 'B1 + B6 + B12', 'Caja de 30 tabletas', 'CAJA', 14.50, 10, 'Vitaminas y suplementos'),
        ('MED-027', 'Vitamina C', '1 g', 'Caja de 10 tabletas efervescentes', 'CAJA', 12.90, 10, 'Vitaminas y suplementos'),
        ('MED-028', 'Sulfato ferroso', '300 mg', 'Caja de 30 tabletas', 'CAJA', 9.90, 20, 'Vitaminas y suplementos')
    ) AS v (codigo, nombre, concentracion, presentacion, unidad, precio, minimo, categoria)
    JOIN categoria c ON c.nombre = v.categoria;

    -- 4. Proveedores y clientes (ficticios; telefonos y correos unicos por tabla, V8) ------------
    INSERT INTO proveedor (ruc, razon_social, telefono, correo, direccion) VALUES
    ('20512345671', 'Distribuidora Farmacéutica Andina S.A.C.', '945120301', 'ventas@andina.example.com', 'Av. Los Laureles 450, Lima'),
    ('20534567812', 'Droguería Salud Integral E.I.R.L.', '945120302', 'pedidos@saludintegral.example.com', 'Jr. Huancavelica 1180, Lima'),
    ('20456789123', 'Laboratorios del Pacífico S.A.', '945120303', 'atencion@delpacifico.example.com', 'Av. Argentina 2235, Callao'),
    ('20601987654', 'Medifarma Express S.A.C.', '945120304', 'contacto@medifarmaexpress.example.com', 'Calle Las Begonias 715, San Isidro'),
    ('20398765432', 'Insumos Médicos del Norte S.A.C.', '945120305', 'compras@insumosnorte.example.com', 'Av. España 1340, Trujillo');

    INSERT INTO cliente (tipo_documento, numero_documento, nombre_completo, telefono, correo) VALUES
    ('DNI', '45781236', 'Juan Pérez Quispe', '987654321', 'juan.perez@example.com'),
    ('DNI', '41236587', 'Alberto Ramírez Soto', '986512347', 'alberto.ramirez@example.com'),
    ('DNI', '47896512', 'María Fernández Huamán', '985214763', 'maria.fernandez@example.com'),
    ('DNI', '40125896', 'Rosa Mendoza Chávez', '984563217', 'rosa.mendoza@example.com'),
    ('DNI', '46325874', 'Carlos Gutiérrez Vargas', '983214569', 'carlos.gutierrez@example.com'),
    ('DNI', '43698521', 'Lucía Torres Rojas', '982145637', NULL),
    ('DNI', '48521476', 'Jorge Castillo Flores', '981236547', 'jorge.castillo@example.com'),
    ('DNI', '42587413', 'Ana Salazar Ramos', NULL, 'ana.salazar@example.com'),
    ('DNI', '44785236', 'Pedro Quispe Mamani', '979856321', 'pedro.quispe@example.com'),
    ('DNI', '49632587', 'Carmen Rivera Paredes', '978523146', 'carmen.rivera@example.com'),
    ('DNI', '45213698', 'Luis Vásquez Cárdenas', '977412589', 'luis.vasquez@example.com'),
    ('RUC', '20587412369', 'Consultorio Médico San Martín S.A.C.', '976325418', 'consultorio.sanmartin@example.com');

    SELECT id_cliente INTO v_juan FROM cliente WHERE numero_documento = '45781236';
    -- Clientes que compran de vez en cuando (Alberto Ramirez queda con 0 compras: "Cliente nuevo")
    SELECT array_agg(id_cliente ORDER BY numero_documento) INTO v_otros
    FROM cliente WHERE numero_documento NOT IN ('45781236', '41236587');

    -- 5. Lotes: stock final de cada lote y si abastece las ventas de demostracion ----------------
    -- Alertas resultantes: vencidos NAP-2311 y DOM-2310; por vencer DIC-2501, SRO-2502, CMB-2503 y
    -- VTC-2504; agotados Loperamida y Clorfenamina; stock bajo Azitromicina, Glibenclamida y
    -- Sulfato ferroso. Con 2 lotes vigentes (FEFO por tramos): Paracetamol, Ibuprofeno, Diclofenaco,
    -- Losartan, Metformina, Omeprazol y Cetirizina.
    -- "ventas" marca el lote de vencimiento mas proximo (FEFO) de cada medicamento vendido.
    CREATE TEMP TABLE demo_plan_lote (
        codigo        VARCHAR(50),
        numero_lote   VARCHAR(50),
        dias          INTEGER,
        stock_final   INTEGER,
        ventas        BOOLEAN
    ) ON COMMIT DROP;

    INSERT INTO demo_plan_lote (codigo, numero_lote, dias, stock_final, ventas) VALUES
    ('MED-001', 'PAR-DEMO-A', 365, 4, TRUE),
    ('MED-001', 'PAR-DEMO-B', 900, 40, FALSE),
    ('MED-002', 'IBU-2501', 200, 3, TRUE),
    ('MED-002', 'IBU-2502', 540, 36, FALSE),
    ('MED-003', 'NAP-2311', -55, 6, FALSE),
    ('MED-003', 'NAP-2508', 420, 24, TRUE),
    ('MED-004', 'DIC-2501', 25, 14, TRUE),
    ('MED-004', 'DIC-2512', 480, 30, FALSE),
    ('MED-005', 'KET-2504', 380, 20, TRUE),
    ('MED-006', 'MTZ-2503', 400, 45, TRUE),
    ('MED-007', 'DEX-2502', 350, 18, TRUE),
    ('MED-008', 'PRE-2504', 420, 16, FALSE),
    ('MED-009', 'AZI-2502', 300, 6, TRUE),
    ('MED-010', 'CIP-2503', 330, 25, TRUE),
    ('MED-011', 'CEF-2501', 280, 22, TRUE),
    ('MED-012', 'LOS-2412', 150, 5, TRUE),
    ('MED-012', 'LOS-2506', 600, 100, FALSE),
    ('MED-013', 'AML-2506', 620, 60, FALSE),
    ('MED-014', 'ATO-2504', 500, 40, TRUE),
    ('MED-015', 'ENA-2503', 360, 28, TRUE),
    ('MED-016', 'AMX-DEMO-016', 730, 500, TRUE),
    ('MED-017', 'MET-2502', 240, 8, TRUE),
    ('MED-017', 'MET-2508', 700, 100, FALSE),
    ('MED-018', 'GLI-2501', 260, 8, TRUE),
    ('MED-019', 'OME-2501', 120, 6, TRUE),
    ('MED-019', 'OME-2507', 700, 120, FALSE),
    ('MED-020', 'DOM-2310', -50, 4, FALSE),
    ('MED-020', 'DOM-2506', 450, 26, TRUE),
    ('MED-021', 'LOP-2405', 300, 0, TRUE),
    ('MED-022', 'SRO-2502', 40, 30, TRUE),
    ('MED-023', 'LOR-2503', 390, 35, TRUE),
    ('MED-024', 'CET-2503', 200, 12, TRUE),
    ('MED-024', 'CET-2511', 720, 40, FALSE),
    ('MED-025', 'CLO-2402', 260, 0, TRUE),
    ('MED-026', 'CMB-2503', 60, 18, TRUE),
    ('MED-027', 'VTC-2504', 80, 22, TRUE),
    ('MED-028', 'SUL-2503', 310, 12, TRUE);

    INSERT INTO lote (id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
    SELECT m.id_medicamento, p.numero_lote, CURRENT_DATE + p.dias, p.stock_final
    FROM demo_plan_lote p
    JOIN medicamento m ON m.codigo = p.codigo;

    -- 6. Ordenes de compra en estados variados y sus recepciones --------------------------------
    INSERT INTO orden_compra (numero_orden, id_usuario, id_proveedor, fecha_hora, estado, total_estimado, observaciones)
    SELECT v.numero, v_compras, p.id_proveedor, v_hoy - make_interval(days => v.dias) + INTERVAL '10 hours',
           v.estado, 0, v.observaciones
    FROM (VALUES
        ('OC-2026-0001', '20512345671', 55, 'RECIBIDA', 'Reposición mensual de cardiovasculares'),
        ('OC-2026-0002', '20534567812', 28, 'RECIBIDA', 'Recibida en dos entregas'),
        ('OC-2026-0003', '20456789123', 12, 'PARCIALMENTE_RECIBIDA', 'El proveedor entregará el saldo la próxima semana'),
        ('OC-2026-0004', '20601987654', 2, 'PENDIENTE', 'Reposición urgente por stock bajo'),
        ('OC-2026-0005', '20398765432', 15, 'CANCELADA', 'Cancelada: el proveedor no tenía stock disponible')
    ) AS v (numero, ruc, dias, estado, observaciones)
    JOIN proveedor p ON p.ruc = v.ruc;

    INSERT INTO detalle_orden_compra (id_orden, id_medicamento, cantidad_solicitada, costo_unitario, importe_estimado)
    SELECT o.id_orden, m.id_medicamento, v.cantidad, v.costo, v.cantidad * v.costo
    FROM (VALUES
        ('OC-2026-0001', 'MED-012', 100, 7.50),
        ('OC-2026-0001', 'MED-013', 60, 6.80),
        ('OC-2026-0002', 'MED-017', 100, 4.20),
        ('OC-2026-0002', 'MED-019', 120, 3.50),
        ('OC-2026-0003', 'MED-024', 60, 3.90),
        ('OC-2026-0003', 'MED-015', 30, 5.10),
        ('OC-2026-0004', 'MED-009', 40, 9.80),
        ('OC-2026-0004', 'MED-021', 60, 2.40),
        ('OC-2026-0005', 'MED-025', 30, 1.80)
    ) AS v (numero, codigo, cantidad, costo)
    JOIN orden_compra o ON o.numero_orden = v.numero
    JOIN medicamento m ON m.codigo = v.codigo;

    UPDATE orden_compra o
    SET total_estimado = d.total
    FROM (SELECT id_orden, SUM(importe_estimado) AS total FROM detalle_orden_compra GROUP BY id_orden) d
    WHERE d.id_orden = o.id_orden;

    -- Cada recepcion es un movimiento ENTRADA ligado a la orden; cada detalle referencia su linea.
    CREATE TEMP TABLE demo_recepcion (
        numero_orden  VARCHAR(30),
        dias          INTEGER,
        codigo        VARCHAR(50),
        numero_lote   VARCHAR(50),
        cantidad      INTEGER
    ) ON COMMIT DROP;

    INSERT INTO demo_recepcion VALUES
    ('OC-2026-0001', 50, 'MED-012', 'LOS-2506', 100),
    ('OC-2026-0001', 50, 'MED-013', 'AML-2506', 60),
    ('OC-2026-0002', 25, 'MED-017', 'MET-2508', 100),
    ('OC-2026-0002', 22, 'MED-019', 'OME-2507', 120),
    ('OC-2026-0003', 8, 'MED-024', 'CET-2511', 40);

    FOR v_recepcion IN
        SELECT DISTINCT r.numero_orden, r.dias, o.id_orden
        FROM demo_recepcion r JOIN orden_compra o ON o.numero_orden = r.numero_orden
        ORDER BY r.dias DESC
    LOOP
        INSERT INTO movimiento_inventario (id_usuario, id_orden, tipo, fecha_hora, motivo)
        VALUES (v_almacen, v_recepcion.id_orden, 'ENTRADA',
                v_hoy - make_interval(days => v_recepcion.dias) + INTERVAL '11 hours', 'Recepción de compra')
        RETURNING id_movimiento INTO v_movimiento;

        INSERT INTO detalle_movimiento (id_movimiento, id_lote, id_detalle_orden, cantidad)
        SELECT v_movimiento, l.id_lote, d.id_detalle_orden, r.cantidad
        FROM demo_recepcion r
        JOIN medicamento m ON m.codigo = r.codigo
        JOIN lote l ON l.id_medicamento = m.id_medicamento AND l.numero_lote = r.numero_lote
        JOIN detalle_orden_compra d ON d.id_orden = v_recepcion.id_orden AND d.id_medicamento = m.id_medicamento
        WHERE r.numero_orden = v_recepcion.numero_orden AND r.dias = v_recepcion.dias;
    END LOOP;

    -- 7. Salidas por merma (sin venta, con motivo) ----------------------------------------------
    FOR v_merma IN
        SELECT v.dias, v.cantidad, v.motivo, l.id_lote
        FROM (VALUES
            (40, 'MED-003', 'NAP-2311', 4, 'Baja parcial de lote vencido; el saldo queda pendiente de destrucción'),
            (30, 'MED-008', 'PRE-2504', 2, 'Merma por empaque deteriorado'),
            (18, 'MED-010', 'CIP-2503', 3, 'Merma por rotura de blíster en almacén'),
            (6, 'MED-014', 'ATO-2504', 2, 'Merma por caja aplastada durante el traslado')
        ) AS v (dias, codigo, numero_lote, cantidad, motivo)
        JOIN medicamento m ON m.codigo = v.codigo
        JOIN lote l ON l.id_medicamento = m.id_medicamento AND l.numero_lote = v.numero_lote
        ORDER BY v.dias DESC
    LOOP
        INSERT INTO movimiento_inventario (id_usuario, tipo, fecha_hora, motivo)
        VALUES (v_almacen, 'SALIDA', v_hoy - make_interval(days => v_merma.dias) + INTERVAL '16 hours', v_merma.motivo)
        RETURNING id_movimiento INTO v_movimiento;

        INSERT INTO detalle_movimiento (id_movimiento, id_lote, cantidad)
        VALUES (v_movimiento, v_merma.id_lote, v_merma.cantidad);
    END LOOP;

    -- 8. Ventas de los ultimos 45 dias (60 ventas, de la mas antigua a la mas reciente) ----------
    -- Cada venta: comprobante de la secuencia V3, lineas con precio historico (una por lote) y un
    -- unico movimiento SALIDA con id_venta y su detalle. Juan Perez Quispe suma 12 compras.
    SELECT array_agg(l.id_lote ORDER BY p.codigo), COUNT(*)
    INTO v_pool, v_tam_pool
    FROM demo_plan_lote p
    JOIN medicamento m ON m.codigo = p.codigo
    JOIN lote l ON l.id_medicamento = m.id_medicamento AND l.numero_lote = p.numero_lote
    WHERE p.ventas;

    FOR i IN 1..60 LOOP
        v_fecha := LEAST(
            v_hoy - make_interval(days => 44 - ((i - 1) * 45) / 60)
                  + make_interval(hours => 9 + (i * 5) % 11, mins => (i * 17) % 60),
            v_ahora - make_interval(mins => (61 - i) * 7));
        v_usuario := CASE WHEN i % 3 = 0 THEN v_admin ELSE v_vendedor END;
        v_metodo := CASE (i * 7) % 5 WHEN 0 THEN 'EFECTIVO' WHEN 1 THEN 'EFECTIVO'
                                     WHEN 2 THEN 'YAPE_PLIN' WHEN 3 THEN 'YAPE_PLIN' ELSE 'TARJETA' END;
        v_cliente := CASE WHEN i % 5 = 0 THEN v_juan
                          WHEN i % 2 = 1 THEN v_otros[((i - 1) / 2) % array_length(v_otros, 1) + 1]
                          ELSE NULL END;
        v_numero := 'B001-' || lpad(nextval('seq_numero_comprobante')::text, 8, '0');

        INSERT INTO venta (id_usuario, id_cliente, fecha_hora, subtotal, descuento_total, total,
                           numero_comprobante, metodo_pago)
        VALUES (v_usuario, v_cliente, v_fecha, 0, 0, 0, v_numero, v_metodo)
        RETURNING id_venta INTO v_venta;

        v_lineas := 1 + i % 3;
        FOR j IN 0..v_lineas - 1 LOOP
            v_lote := v_pool[(i * 5 + j * 7) % v_tam_pool + 1];
            v_cantidad := 1 + (i + 2 * j) % 3;
            v_descuento := CASE WHEN i % 7 = 0 AND j = 0 THEN 1.00 ELSE 0 END;
            INSERT INTO detalle_venta (id_venta, id_lote, cantidad, precio_unitario, descuento_importe, importe)
            SELECT v_venta, l.id_lote, v_cantidad, m.precio_venta, v_descuento,
                   v_cantidad * m.precio_venta - v_descuento
            FROM lote l JOIN medicamento m ON m.id_medicamento = l.id_medicamento
            WHERE l.id_lote = v_lote;
        END LOOP;

        UPDATE venta v
        SET subtotal = d.subtotal, descuento_total = d.descuento, total = d.total
        FROM (SELECT SUM(cantidad * precio_unitario) AS subtotal, SUM(descuento_importe) AS descuento,
                     SUM(importe) AS total
              FROM detalle_venta WHERE id_venta = v_venta) d
        WHERE v.id_venta = v_venta;

        INSERT INTO movimiento_inventario (id_usuario, id_venta, tipo, fecha_hora, motivo)
        VALUES (v_usuario, v_venta, 'SALIDA', v_fecha, 'Venta ' || v_numero)
        RETURNING id_movimiento INTO v_movimiento;

        INSERT INTO detalle_movimiento (id_movimiento, id_lote, cantidad)
        SELECT v_movimiento, id_lote, cantidad FROM detalle_venta WHERE id_venta = v_venta;
    END LOOP;

    -- 9. Existencias iniciales de apertura (ENTRADA sin orden): cuadran el stock final ----------
    SELECT COUNT(*) INTO v_errores
    FROM lote l
    WHERE l.stock_actual < (
        SELECT COALESCE(SUM(CASE WHEN mi.tipo = 'ENTRADA' THEN dm.cantidad ELSE -dm.cantidad END), 0)
        FROM detalle_movimiento dm JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento
        WHERE dm.id_lote = l.id_lote);
    IF v_errores > 0 THEN
        RAISE EXCEPTION 'Datos demo incoherentes: % lote(s) con mas entradas netas que stock final', v_errores;
    END IF;

    INSERT INTO movimiento_inventario (id_usuario, tipo, fecha_hora, motivo)
    VALUES (v_almacen, 'ENTRADA', v_hoy - INTERVAL '60 days' + INTERVAL '8 hours',
            'Existencias iniciales de apertura del inventario')
    RETURNING id_movimiento INTO v_movimiento;

    INSERT INTO detalle_movimiento (id_movimiento, id_lote, cantidad)
    SELECT v_movimiento, l.id_lote, l.stock_actual - n.neto
    FROM lote l
    JOIN LATERAL (
        SELECT COALESCE(SUM(CASE WHEN mi.tipo = 'ENTRADA' THEN dm.cantidad ELSE -dm.cantidad END), 0) AS neto
        FROM detalle_movimiento dm JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento
        WHERE dm.id_lote = l.id_lote
    ) n ON TRUE
    WHERE l.stock_actual - n.neto > 0;

    -- 10. Verificacion final: stock = entradas - salidas en todos los lotes ---------------------
    SELECT COUNT(*) INTO v_errores
    FROM lote l
    WHERE l.stock_actual <> (
        SELECT COALESCE(SUM(CASE WHEN mi.tipo = 'ENTRADA' THEN dm.cantidad ELSE -dm.cantidad END), 0)
        FROM detalle_movimiento dm JOIN movimiento_inventario mi ON mi.id_movimiento = dm.id_movimiento
        WHERE dm.id_lote = l.id_lote);
    IF v_errores > 0 THEN
        RAISE EXCEPTION 'Datos demo incoherentes: % lote(s) con stock distinto de entradas - salidas', v_errores;
    END IF;
END;
$$;

-- Asegurar fechas de ultimo acceso de Spec 020 para repetibilidad E2E en bases ya inicializadas
DO $$
BEGIN
    UPDATE usuario SET ultimo_acceso = CURRENT_TIMESTAMP - INTERVAL '2 hours' WHERE nombre_usuario = 'admin_demo' AND ultimo_acceso IS NULL;
    UPDATE usuario SET ultimo_acceso = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE nombre_usuario = 'almacen_demo' AND ultimo_acceso IS NULL;
    UPDATE usuario SET ultimo_acceso = TIMESTAMP '2026-11-12 10:00:00' WHERE nombre_usuario = 'compras_demo' AND ultimo_acceso IS NULL;
END;
$$;
