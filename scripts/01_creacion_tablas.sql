-- Instalacion nueva: ejecutar una sola vez en una base vacia.
-- No borra ni reemplaza tablas existentes. Ante errores, ejecutar ROLLBACK.
BEGIN;

CREATE TABLE usuario (
    id_usuario INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    nombre_usuario VARCHAR(50) NOT NULL UNIQUE,
    clave_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_usuario_rol
        CHECK (rol IN (
            'ADMINISTRADOR',
            'VENDEDOR',
            'ALMACENERO',
            'COMPRAS'
        ))
);


CREATE TABLE medicamento (
    id_medicamento INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo VARCHAR(50) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    concentracion VARCHAR(100),
    presentacion VARCHAR(100) NOT NULL,
    unidad_control VARCHAR(30) NOT NULL,
    precio_venta NUMERIC(12,2) NOT NULL,
    stock_minimo INTEGER NOT NULL DEFAULT 0,
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_medicamento_precio
        CHECK (precio_venta >= 0),

    CONSTRAINT chk_medicamento_stock_minimo
        CHECK (stock_minimo >= 0)
);


CREATE TABLE lote (
    id_lote INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_medicamento INTEGER NOT NULL,
    numero_lote VARCHAR(50) NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    stock_actual INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_lote_medicamento
        FOREIGN KEY (id_medicamento)
        REFERENCES medicamento (id_medicamento),

    CONSTRAINT uq_lote_medicamento_numero
        UNIQUE (id_medicamento, numero_lote),

    CONSTRAINT chk_lote_stock
        CHECK (stock_actual >= 0)
);


CREATE TABLE proveedor (
    id_proveedor INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ruc VARCHAR(11) NOT NULL UNIQUE,
    razon_social VARCHAR(150) NOT NULL,
    telefono VARCHAR(20),
    correo VARCHAR(150),
    direccion VARCHAR(200),
    activo BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_proveedor_ruc
        CHECK (ruc ~ '^[0-9]{11}$')
);


CREATE TABLE cliente (
    id_cliente INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo_documento VARCHAR(20) NOT NULL,
    numero_documento VARCHAR(20) NOT NULL,
    nombre_completo VARCHAR(150) NOT NULL,
    telefono VARCHAR(20),
    correo VARCHAR(150),

    CONSTRAINT uq_cliente_documento
        UNIQUE (tipo_documento, numero_documento)
);


CREATE TABLE orden_compra (
    id_orden INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_orden VARCHAR(30) NOT NULL UNIQUE,
    id_usuario INTEGER NOT NULL,
    id_proveedor INTEGER NOT NULL,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(25) NOT NULL DEFAULT 'PENDIENTE',
    total_estimado NUMERIC(12,2) NOT NULL DEFAULT 0,
    observaciones VARCHAR(500),

    CONSTRAINT fk_orden_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario),

    CONSTRAINT fk_orden_proveedor
        FOREIGN KEY (id_proveedor)
        REFERENCES proveedor (id_proveedor),

    CONSTRAINT chk_orden_estado
        CHECK (estado IN (
            'PENDIENTE',
            'PARCIALMENTE_RECIBIDA',
            'RECIBIDA',
            'CANCELADA'
        )),

    CONSTRAINT chk_orden_total
        CHECK (total_estimado >= 0)
);


CREATE TABLE detalle_orden_compra (
    id_detalle_orden INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_orden INTEGER NOT NULL,
    id_medicamento INTEGER NOT NULL,
    cantidad_solicitada INTEGER NOT NULL,
    costo_unitario NUMERIC(12,2) NOT NULL,
    importe_estimado NUMERIC(12,2) NOT NULL,

    CONSTRAINT fk_detalle_orden_compra
        FOREIGN KEY (id_orden)
        REFERENCES orden_compra (id_orden),

    CONSTRAINT fk_detalle_orden_medicamento
        FOREIGN KEY (id_medicamento)
        REFERENCES medicamento (id_medicamento),

    CONSTRAINT uq_detalle_orden_medicamento
        UNIQUE (id_orden, id_medicamento),

    CONSTRAINT chk_detalle_orden_cantidad
        CHECK (cantidad_solicitada > 0),

    CONSTRAINT chk_detalle_orden_costo
        CHECK (costo_unitario >= 0),

    CONSTRAINT chk_detalle_orden_importe
        CHECK (
            importe_estimado = cantidad_solicitada * costo_unitario
        )
);


CREATE TABLE venta (
    id_venta INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario INTEGER NOT NULL,
    id_cliente INTEGER,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    subtotal NUMERIC(12,2) NOT NULL,
    descuento_total NUMERIC(12,2) NOT NULL DEFAULT 0,
    total NUMERIC(12,2) NOT NULL,
    numero_comprobante VARCHAR(30) NOT NULL UNIQUE,

    CONSTRAINT fk_venta_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario),

    CONSTRAINT fk_venta_cliente
        FOREIGN KEY (id_cliente)
        REFERENCES cliente (id_cliente),

    CONSTRAINT chk_venta_subtotal
        CHECK (subtotal >= 0),

    CONSTRAINT chk_venta_descuento
        CHECK (
            descuento_total >= 0
            AND descuento_total <= subtotal
        ),

    CONSTRAINT chk_venta_total
        CHECK (total = subtotal - descuento_total)
);


CREATE TABLE detalle_venta (
    id_detalle_venta INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_venta INTEGER NOT NULL,
    id_lote INTEGER NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(12,2) NOT NULL,
    descuento_importe NUMERIC(12,2) NOT NULL DEFAULT 0,
    importe NUMERIC(12,2) NOT NULL,

    CONSTRAINT fk_detalle_venta
        FOREIGN KEY (id_venta)
        REFERENCES venta (id_venta),

    CONSTRAINT fk_detalle_venta_lote
        FOREIGN KEY (id_lote)
        REFERENCES lote (id_lote),

    CONSTRAINT chk_detalle_venta_cantidad
        CHECK (cantidad > 0),

    CONSTRAINT chk_detalle_venta_precio
        CHECK (precio_unitario >= 0),

    CONSTRAINT chk_detalle_venta_descuento
        CHECK (
            descuento_importe >= 0
            AND descuento_importe <= cantidad * precio_unitario
        ),

    CONSTRAINT chk_detalle_venta_importe
        CHECK (
            importe = cantidad * precio_unitario - descuento_importe
        )
);


CREATE TABLE movimiento_inventario (
    id_movimiento INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario INTEGER NOT NULL,
    id_proveedor INTEGER,
    id_venta INTEGER UNIQUE,
    id_orden INTEGER,
    tipo VARCHAR(10) NOT NULL,
    fecha_hora TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo VARCHAR(250) NOT NULL,

    CONSTRAINT fk_movimiento_usuario
        FOREIGN KEY (id_usuario)
        REFERENCES usuario (id_usuario),

    CONSTRAINT fk_movimiento_proveedor
        FOREIGN KEY (id_proveedor)
        REFERENCES proveedor (id_proveedor),

    CONSTRAINT fk_movimiento_venta
        FOREIGN KEY (id_venta)
        REFERENCES venta (id_venta),

    CONSTRAINT fk_movimiento_orden
        FOREIGN KEY (id_orden)
        REFERENCES orden_compra (id_orden),

    CONSTRAINT chk_movimiento_tipo
        CHECK (tipo IN ('ENTRADA', 'SALIDA')),

    CONSTRAINT chk_movimiento_venta
        CHECK (
            id_venta IS NULL
            OR (tipo = 'SALIDA' AND id_orden IS NULL)
        ),

    CONSTRAINT chk_movimiento_compra
        CHECK (
            id_orden IS NULL
            OR (
                tipo = 'ENTRADA'
                AND id_venta IS NULL
                AND id_proveedor IS NOT NULL
            )
        )
);


CREATE TABLE detalle_movimiento (
    id_detalle_movimiento INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_movimiento INTEGER NOT NULL,
    id_lote INTEGER NOT NULL,
    id_detalle_orden INTEGER,
    cantidad INTEGER NOT NULL,

    CONSTRAINT fk_detalle_movimiento
        FOREIGN KEY (id_movimiento)
        REFERENCES movimiento_inventario (id_movimiento),

    CONSTRAINT fk_detalle_movimiento_lote
        FOREIGN KEY (id_lote)
        REFERENCES lote (id_lote),

    CONSTRAINT fk_detalle_movimiento_orden
        FOREIGN KEY (id_detalle_orden)
        REFERENCES detalle_orden_compra (id_detalle_orden),

    CONSTRAINT chk_detalle_movimiento_cantidad
        CHECK (cantidad > 0)
);

COMMIT;

