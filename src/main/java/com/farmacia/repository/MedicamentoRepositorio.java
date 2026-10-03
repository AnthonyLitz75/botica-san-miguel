package com.farmacia.repository;

import com.farmacia.model.Medicamento;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.farmacia.dto.MedicamentoFormulario;
import java.util.Optional;

@Repository
public class MedicamentoRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public MedicamentoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Medicamento> listarActivos() {
        return buscarActivos("");
    }

    public List<Medicamento> buscarActivos(String texto) {
        return buscarPorEstado(texto, true);
    }

    public List<Medicamento> buscarPorEstado(String texto, boolean activo) {
        String sql = """
                SELECT id_medicamento, codigo, nombre, concentracion,
                       presentacion, unidad_control, precio_venta,
                       stock_minimo, activo
                FROM medicamento
                WHERE activo = ?
                AND (
                    POSITION(LOWER(?) IN LOWER(nombre)) > 0
                    OR POSITION(LOWER(?) IN LOWER(codigo)) > 0
                )
                ORDER BY nombre, id_medicamento
                """;

        return jdbcTemplate.query(sql, (fila, numeroFila) -> new Medicamento(
                fila.getInt("id_medicamento"),
                fila.getString("codigo"),
                fila.getString("nombre"),
                fila.getString("concentracion"),
                fila.getString("presentacion"),
                fila.getString("unidad_control"),
                fila.getBigDecimal("precio_venta"),
                fila.getInt("stock_minimo"),
                fila.getBoolean("activo")), activo, texto, texto);
    }

    public Integer guardar(MedicamentoFormulario medicamentoFormulario) {
        String sql = """
                INSERT INTO medicamento (codigo, nombre, concentracion,
                                         presentacion, unidad_control,
                                         precio_venta, stock_minimo, activo)
                VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                RETURNING id_medicamento
                """;

        return jdbcTemplate.queryForObject(sql, Integer.class,
                medicamentoFormulario.codigo(),
                medicamentoFormulario.nombre(),
                medicamentoFormulario.concentracion(),
                medicamentoFormulario.presentacion(),
                medicamentoFormulario.unidadControl(),
                medicamentoFormulario.precioVenta(),
                medicamentoFormulario.stockMinimo());
    }

    public Optional<Medicamento> buscarPorId(Integer id) {
        String sql = """
                SELECT id_medicamento, codigo, nombre, concentracion,
                       presentacion, unidad_control, precio_venta,
                       stock_minimo, activo
                FROM medicamento
                WHERE id_medicamento = ?
                """;

        List<Medicamento> resultados = jdbcTemplate.query(
                sql,
                (fila, numeroFila) -> new Medicamento(
                        fila.getInt("id_medicamento"),
                        fila.getString("codigo"),
                        fila.getString("nombre"),
                        fila.getString("concentracion"),
                        fila.getString("presentacion"),
                        fila.getString("unidad_control"),
                        fila.getBigDecimal("precio_venta"),
                        fila.getInt("stock_minimo"),
                        fila.getBoolean("activo")),
                id);

        return resultados.stream().findFirst();
    }

    public int actualizar(Integer id, MedicamentoFormulario formulario) {
        String sql = """
                UPDATE medicamento
                SET codigo = ?,
                    nombre = ?,
                    concentracion = ?,
                    presentacion = ?,
                    unidad_control = ?,
                    precio_venta = ?,
                    stock_minimo = ?
                WHERE id_medicamento = ?
                """;

        return jdbcTemplate.update(
                sql,
                formulario.codigo(),
                formulario.nombre(),
                formulario.concentracion(),
                formulario.presentacion(),
                formulario.unidadControl(),
                formulario.precioVenta(),
                formulario.stockMinimo(),
                id);
    }

    public int desactivar(Integer id) {
        String sql = """
                UPDATE medicamento
                SET activo = FALSE
                WHERE id_medicamento = ?
                """;

        return jdbcTemplate.update(sql, id);
    }

    public int activar(Integer id) {
        String sql = """
                UPDATE medicamento
                SET activo = TRUE
                WHERE id_medicamento = ?
                """;

        return jdbcTemplate.update(sql, id);
    }
}
