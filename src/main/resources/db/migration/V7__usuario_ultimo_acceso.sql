-- Migración V7: Añadir columna ultimo_acceso a la tabla usuario (Spec 020)
ALTER TABLE usuario ADD COLUMN ultimo_acceso TIMESTAMP NULL;
