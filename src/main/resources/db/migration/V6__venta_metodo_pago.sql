-- Migracion V6: metodo de pago en la venta (Spec 016).
-- Valores cerrados: EFECTIVO, YAPE_PLIN, TARJETA.
-- Ventas historicas quedan con NULL y no se asume EFECTIVO (RF-03).
ALTER TABLE venta ADD COLUMN metodo_pago VARCHAR(12) NULL;

ALTER TABLE venta ADD CONSTRAINT chk_venta_metodo_pago
    CHECK (metodo_pago IS NULL OR metodo_pago IN ('EFECTIVO', 'YAPE_PLIN', 'TARJETA'));
