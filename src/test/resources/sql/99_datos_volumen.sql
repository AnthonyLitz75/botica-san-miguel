-- =============================================================================
-- Spec 010 (US-4 / CA-4.1): Datos de volumen para pruebas de rendimiento (RNF-06).
-- Genera:
--   - 5 000 medicamentos
--   - 20 000 lotes (4 lotes en promedio por medicamento, con vencimientos escalonados)
--   - 50 000 ventas con sus detalles y movimientos de salida asociados
-- =============================================================================

-- 1. Usuario vendedor para asociar las ventas de volumen
INSERT INTO usuario (nombre, nombre_usuario, clave_hash, rol, activo)
VALUES ('Vendedor Rendimiento', 'vendedor_volumen', '$2a$10$volumenhashdummy', 'VENDEDOR', TRUE)
ON CONFLICT (nombre_usuario) DO NOTHING;

-- 2. 5 000 Medicamentos
INSERT INTO medicamento (codigo, nombre, concentracion, presentacion, unidad_control, precio_venta, stock_minimo, activo)
SELECT
    'VOL-MED-' || lpad(i::text, 6, '0'),
    'Medicamento Volumen ' || i,
    '500 mg',
    'Caja x 30',
    'UNIDAD',
    (5.00 + (i % 50)::numeric(12,2)),
    10,
    TRUE
FROM generate_series(1, 5000) AS i
ON CONFLICT (codigo) DO NOTHING;

-- 3. 20 000 Lotes distribuidos entre los 5 000 medicamentos
INSERT INTO lote (id_medicamento, numero_lote, fecha_vencimiento, stock_actual)
SELECT
    (SELECT MIN(id_medicamento) FROM medicamento WHERE codigo = 'VOL-MED-000001') + ((i - 1) % 5000),
    'LT-VOL-' || lpad(i::text, 7, '0'),
    CURRENT_DATE + ((i % 500) - 30) * INTERVAL '1 day',
    ((i % 80) + 10)
FROM generate_series(1, 20000) AS i
ON CONFLICT (id_medicamento, numero_lote) DO NOTHING;

-- 4. 50 000 Ventas
INSERT INTO venta (id_usuario, fecha_hora, subtotal, descuento_total, total, numero_comprobante)
SELECT
    (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'vendedor_volumen' LIMIT 1),
    CURRENT_TIMESTAMP - ((i % 180) || ' days')::interval - ((i % 86400) || ' seconds')::interval,
    20.00,
    0.00,
    20.00,
    'B001-' || lpad((90000000 + i)::text, 8, '0')
FROM generate_series(1, 50000) AS i
ON CONFLICT (numero_comprobante) DO NOTHING;

-- 5. 50 000 Detalles de venta
INSERT INTO detalle_venta (id_venta, id_lote, cantidad, precio_unitario, descuento_importe, importe)
SELECT
    v.id_venta,
    (SELECT MIN(id_lote) FROM lote WHERE numero_lote = 'LT-VOL-0000001') + ((row_number() OVER (ORDER BY v.id_venta) - 1) % 20000),
    2,
    10.00,
    0.00,
    20.00
FROM venta v
WHERE v.numero_comprobante LIKE 'B001-9%';

-- 6. 50 000 Movimientos de inventario tipo SALIDA
INSERT INTO movimiento_inventario (id_usuario, id_venta, tipo, fecha_hora, motivo)
SELECT
    v.id_usuario,
    v.id_venta,
    'SALIDA',
    v.fecha_hora,
    'Salida por venta volumen'
FROM venta v
WHERE v.numero_comprobante LIKE 'B001-9%'
ON CONFLICT (id_venta) DO NOTHING;

-- 7. 50 000 Detalles de movimiento
INSERT INTO detalle_movimiento (id_movimiento, id_lote, id_detalle_orden, cantidad)
SELECT
    m.id_movimiento,
    dv.id_lote,
    NULL,
    dv.cantidad
FROM movimiento_inventario m
JOIN detalle_venta dv ON dv.id_venta = m.id_venta
WHERE m.motivo = 'Salida por venta volumen';
