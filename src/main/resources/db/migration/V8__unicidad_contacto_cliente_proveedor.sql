-- =============================================================================
-- Migración V8: Unicidad de teléfono y correo en Clientes y Proveedores (Spec 023)
-- =============================================================================
-- Reglas:
-- 1. Normalizar datos existentes:
--    - Correo: trim y minúsculas; cadenas vacías pasan a NULL.
--    - Teléfono: solo dígitos numéricos (se quita prefijo 51 si tiene 11 dígitos y empieza con 51); cadenas vacías pasan a NULL.
-- 2. Resolución de duplicados existentes sin fallar:
--    - Para cada teléfono o correo duplicado, se conserva el valor en el registro
--      más antiguo (menor id) y se anula (asigna NULL) en los posteriores.
--    - Estos campos son opcionales, por lo que preservar el registro original
--      y anular los duplicados garantiza la integridad antes de crear los índices.
-- 3. Índices únicos parciales (WHERE col IS NOT NULL):
--    - Permiten múltiples registros con NULL / vacío.
--    - Impiden duplicados con valores no nulos.
-- =============================================================================

-- Paso 1: Normalización de correos existentes (trim + minúsculas, vacío a NULL)
UPDATE cliente
SET correo = NULLIF(LOWER(TRIM(correo)), '')
WHERE correo IS NOT NULL;

UPDATE proveedor
SET correo = NULLIF(LOWER(TRIM(correo)), '')
WHERE correo IS NOT NULL;

-- Paso 2: Normalización de teléfonos existentes (solo dígitos, quitar +51 si aplica, vacío a NULL)
UPDATE cliente
SET telefono = NULLIF(
    CASE
        WHEN regexp_replace(telefono, '[^0-9]', '', 'g') ~ '^51[0-9]{9}$'
        THEN substring(regexp_replace(telefono, '[^0-9]', '', 'g') from 3)
        ELSE regexp_replace(telefono, '[^0-9]', '', 'g')
    END,
    ''
)
WHERE telefono IS NOT NULL;

UPDATE proveedor
SET telefono = NULLIF(
    CASE
        WHEN regexp_replace(telefono, '[^0-9]', '', 'g') ~ '^51[0-9]{9}$'
        THEN substring(regexp_replace(telefono, '[^0-9]', '', 'g') from 3)
        ELSE regexp_replace(telefono, '[^0-9]', '', 'g')
    END,
    ''
)
WHERE telefono IS NOT NULL;

-- Paso 3: Resolución de duplicados existentes
-- Conserva el valor en el registro con menor ID y asigna NULL a los duplicados posteriores.

-- Duplicados de teléfono en cliente:
UPDATE cliente c
SET telefono = NULL
WHERE c.telefono IS NOT NULL
  AND c.id_cliente > (
      SELECT MIN(c2.id_cliente)
      FROM cliente c2
      WHERE c2.telefono = c.telefono
  );

-- Duplicados de correo en cliente:
UPDATE cliente c
SET correo = NULL
WHERE c.correo IS NOT NULL
  AND c.id_cliente > (
      SELECT MIN(c2.id_cliente)
      FROM cliente c2
      WHERE c2.correo = c.correo
  );

-- Duplicados de teléfono en proveedor:
UPDATE proveedor p
SET telefono = NULL
WHERE p.telefono IS NOT NULL
  AND p.id_proveedor > (
      SELECT MIN(p2.id_proveedor)
      FROM proveedor p2
      WHERE p2.telefono = p.telefono
  );

-- Duplicados de correo en proveedor:
UPDATE proveedor p
SET correo = NULL
WHERE p.correo IS NOT NULL
  AND p.id_proveedor > (
      SELECT MIN(p2.id_proveedor)
      FROM proveedor p2
      WHERE p2.correo = p.correo
  );

-- Paso 4: Creación de índices únicos parciales
CREATE UNIQUE INDEX IF NOT EXISTS uq_cliente_correo
    ON cliente (correo)
    WHERE correo IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cliente_telefono
    ON cliente (telefono)
    WHERE telefono IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_proveedor_correo
    ON proveedor (correo)
    WHERE correo IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_proveedor_telefono
    ON proveedor (telefono)
    WHERE telefono IS NOT NULL;
