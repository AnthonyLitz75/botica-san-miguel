-- Migracion V5: categorias jerarquicas de medicamentos.
CREATE TABLE categoria (
    id_categoria INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    id_padre INTEGER NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_categoria_padre FOREIGN KEY (id_padre) REFERENCES categoria (id_categoria),
    CONSTRAINT chk_categoria_padre CHECK (id_padre IS NULL OR id_padre <> id_categoria)
);

CREATE UNIQUE INDEX uq_categoria_padre_nombre ON categoria (COALESCE(id_padre, 0), lower(nombre));

ALTER TABLE medicamento ADD COLUMN id_categoria INTEGER NULL REFERENCES categoria (id_categoria);

CREATE INDEX idx_medicamento_categoria ON medicamento (id_categoria);
