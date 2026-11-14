-- V4 (spec 014): bitacora de auditoria de solo insercion.
-- Flyway la ejecuta en una transaccion: sin BEGIN/COMMIT. No editar una vez aplicada.
--
-- id_usuario NO es clave foranea: la bitacora es una fotografia historica. Guarda el nombre de usuario
-- escrito (los intentos fallidos pueden no corresponder a un usuario existente) y el id solo como dato.
-- Una FK con ON DELETE SET NULL obligaria a actualizar filas, cosa que el trigger prohibe.

CREATE TABLE auditoria (
    id_auditoria   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fecha_hora     TIMESTAMP NOT NULL,
    id_usuario     INTEGER NULL,
    nombre_usuario VARCHAR(50) NOT NULL,
    accion         VARCHAR(30) NOT NULL,
    entidad        VARCHAR(40) NULL,
    id_entidad     VARCHAR(40) NULL,
    resultado      VARCHAR(10) NOT NULL,
    detalle        VARCHAR(500) NULL,
    ip_origen      VARCHAR(45) NULL,

    CONSTRAINT chk_auditoria_accion CHECK (accion IN (
        'INICIO_SESION', 'INICIO_SESION_FALLIDO', 'CIERRE_SESION', 'ACCESO_DENEGADO',
        'CREAR', 'EDITAR', 'ACTIVAR', 'DESACTIVAR', 'CANCELAR', 'CONFIRMAR_VENTA',
        'RECEPCION', 'SALIDA', 'EXISTENCIA_INICIAL', 'CAMBIAR_CLAVE', 'CAMBIAR_ROL', 'EXPORTAR')),

    CONSTRAINT chk_auditoria_resultado CHECK (resultado IN ('EXITO', 'FALLO', 'DENEGADO'))
);

CREATE INDEX idx_auditoria_fecha ON auditoria (fecha_hora DESC);
CREATE INDEX idx_auditoria_usuario ON auditoria (nombre_usuario, fecha_hora DESC);

-- Inmutabilidad garantizada por la base: ninguna ruta de la aplicacion ni SQL manual puede
-- modificar o borrar un evento. (TRUNCATE no dispara triggers de fila; solo lo usa la limpieza de pruebas.)
CREATE OR REPLACE FUNCTION fn_auditoria_solo_insercion() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'La auditoria es de solo insercion';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_auditoria_solo_insercion
BEFORE UPDATE OR DELETE ON auditoria
FOR EACH ROW EXECUTE FUNCTION fn_auditoria_solo_insercion();
