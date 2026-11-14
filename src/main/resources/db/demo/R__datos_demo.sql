-- Datos de demostracion. SOLO DEMOSTRACION LOCAL: las claves publicas NO son aptas para produccion.
-- Migracion repetible de Flyway: solo se carga con el perfil "demo"
-- (spring.flyway.locations incluye classpath:db/demo en application-demo.properties).
-- Se ejecuta despues de todas las migraciones versionadas y de nuevo cada vez que cambie este archivo.
-- Es idempotente: si alguna tabla ya tiene datos, no inserta nada.
-- Flyway la ejecuta dentro de una transaccion: no usar BEGIN/COMMIT.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
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
       OR EXISTS (SELECT 1 FROM detalle_movimiento) THEN
        RAISE NOTICE 'La base ya tiene datos: no se cargan los datos de demostracion.';
        RETURN;
    END IF;

    INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol) VALUES
    ('Administrador de prueba', 'admin_demo',
     '{bcrypt}' || crypt('AdminDemo2026!', gen_salt('bf', 10)), 'ADMINISTRADOR'),
    ('Almacenero de prueba', 'almacen_demo',
     '{bcrypt}' || crypt('FarmaciaDemo2026!', gen_salt('bf', 10)), 'ALMACENERO'),
    ('Compras de prueba', 'compras_demo',
     '{bcrypt}' || crypt('ComprasDemo2026!', gen_salt('bf', 10)), 'COMPRAS'),
    ('Vendedor de prueba', 'vendedor_demo',
     '{bcrypt}' || crypt('VendedorDemo2026!', gen_salt('bf', 10)), 'VENDEDOR');

    INSERT INTO medicamento
    (codigo, nombre, concentracion, presentacion, unidad_control, precio_venta, stock_minimo)
    VALUES
    ('MED-001', 'Paracetamol', '500 mg', 'Caja de 20 tabletas', 'CAJA', 8.50, 10),
    ('MED-002', 'Ibuprofeno', '400 mg', 'Caja de 10 tabletas', 'CAJA', 13.50, 5);

    INSERT INTO proveedor (ruc, razon_social, telefono, correo, direccion) VALUES
    ('20000000001', 'Distribuidora de prueba', '999000111',
     'proveedor@example.com', 'Direccion de prueba'),
    ('20000000002', 'Distribuidora Demos Dos', '999000333',
     'compras2@example.com', 'Direccion de prueba 2');

    INSERT INTO lote (id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
    SELECT id_medicamento, 'PAR-DEMO-A', CURRENT_DATE + INTERVAL '2 years', 30
    FROM medicamento WHERE codigo = 'MED-001';

    WITH nuevo_movimiento AS (
        INSERT INTO movimiento_inventario (id_usuario, tipo, motivo)
        SELECT id_usuario, 'ENTRADA', 'Existencias iniciales de demostracion'
        FROM usuario WHERE nombre_usuario = 'almacen_demo'
        RETURNING id_movimiento
    )
    INSERT INTO detalle_movimiento (id_movimiento, id_lote, cantidad)
    SELECT m.id_movimiento, l.id_lote, 30
    FROM nuevo_movimiento m CROSS JOIN lote l
    JOIN medicamento med ON med.id_medicamento = l.id_medicamento
    WHERE med.codigo = 'MED-001' AND l.numero_lote = 'PAR-DEMO-A';

    WITH nueva_orden AS (
        INSERT INTO orden_compra
        (numero_orden, id_usuario, id_proveedor, total_estimado, observaciones)
        SELECT 'OC-DEMO-001', u.id_usuario, p.id_proveedor, 50.00,
               'Orden de compra de demostracion'
        FROM usuario u CROSS JOIN proveedor p
        WHERE u.nombre_usuario = 'compras_demo' AND p.ruc = '20000000001'
        RETURNING id_orden
    )
    INSERT INTO detalle_orden_compra
    (id_orden, id_medicamento, cantidad_solicitada, costo_unitario, importe_estimado)
    SELECT o.id_orden, m.id_medicamento, 10, 5.00, 50.00
    FROM nueva_orden o CROSS JOIN medicamento m WHERE m.codigo = 'MED-001';
END;
$$;
