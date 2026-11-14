-- V2 (spec 002): movimiento_inventario en 3FN, coherencia del detalle e indices.
-- Origen: scripts/03_migracion_3fn.sql del paquete de specs (probado en PostgreSQL 16).
-- Flyway la ejecuta en una transaccion: sin BEGIN/COMMIT. No editar una vez aplicada.

-- A. 3FN: el proveedor de una recepcion se obtiene siempre por orden_compra ------------
ALTER TABLE movimiento_inventario DROP CONSTRAINT chk_movimiento_compra;
ALTER TABLE movimiento_inventario DROP COLUMN id_proveedor;
ALTER TABLE movimiento_inventario
ADD CONSTRAINT chk_movimiento_compra CHECK (
    id_orden IS NULL
    OR (tipo = 'ENTRADA' AND id_venta IS NULL)
);

-- B. Coherencia entre el movimiento y su detalle (regla que un CHECK no puede expresar) --
CREATE OR REPLACE FUNCTION fn_validar_detalle_movimiento() RETURNS trigger AS $$
DECLARE
    v_orden INTEGER;
BEGIN
    SELECT id_orden INTO v_orden
    FROM movimiento_inventario
    WHERE id_movimiento = NEW.id_movimiento;

    IF v_orden IS NOT NULL THEN
        IF NEW.id_detalle_orden IS NULL THEN
            RAISE EXCEPTION 'Una recepcion de compra exige id_detalle_orden';
        END IF;
        IF NOT EXISTS (
            SELECT 1
            FROM detalle_orden_compra d
            JOIN lote l ON l.id_medicamento = d.id_medicamento
            WHERE d.id_detalle_orden = NEW.id_detalle_orden
              AND d.id_orden = v_orden
              AND l.id_lote = NEW.id_lote
        ) THEN
            RAISE EXCEPTION 'La linea o el lote no corresponden a la orden del movimiento';
        END IF;
    ELSIF NEW.id_detalle_orden IS NOT NULL THEN
        RAISE EXCEPTION 'Solo una recepcion de compra puede referenciar una linea de orden';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_detalle_movimiento_origen
BEFORE INSERT OR UPDATE ON detalle_movimiento
FOR EACH ROW EXECUTE FUNCTION fn_validar_detalle_movimiento();

-- C. Indices (PostgreSQL no indexa automaticamente las claves foraneas) -----------------
CREATE INDEX idx_lote_fefo ON lote (id_medicamento, fecha_vencimiento, id_lote)
    WHERE stock_actual > 0;
CREATE INDEX idx_detalle_venta_venta ON detalle_venta (id_venta);
CREATE INDEX idx_detalle_venta_lote ON detalle_venta (id_lote);
CREATE INDEX idx_detalle_movimiento_movimiento ON detalle_movimiento (id_movimiento);
CREATE INDEX idx_detalle_movimiento_lote ON detalle_movimiento (id_lote);
CREATE INDEX idx_detalle_movimiento_linea ON detalle_movimiento (id_detalle_orden)
    WHERE id_detalle_orden IS NOT NULL;
CREATE INDEX idx_movimiento_orden ON movimiento_inventario (id_orden)
    WHERE id_orden IS NOT NULL;
CREATE INDEX idx_movimiento_fecha ON movimiento_inventario (fecha_hora DESC);
CREATE INDEX idx_venta_fecha ON venta (fecha_hora DESC);
CREATE INDEX idx_orden_estado ON orden_compra (estado);
