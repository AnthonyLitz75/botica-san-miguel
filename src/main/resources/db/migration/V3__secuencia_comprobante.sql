-- V3 (spec 003): secuencia del numero de comprobante interno (boleta B001-########, no fiscal).
-- Origen: scripts/05_secuencia_comprobante.sql del paquete de specs.
-- Flyway la ejecuta en una transaccion: sin BEGIN/COMMIT. No editar una vez aplicada.
CREATE SEQUENCE seq_numero_comprobante START 1;
-- Uso: SELECT 'B001-' || lpad(nextval('seq_numero_comprobante')::text, 8, '0');
