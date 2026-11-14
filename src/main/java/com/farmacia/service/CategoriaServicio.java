package com.farmacia.service;

import com.farmacia.estructuras.ArbolCategorias;
import com.farmacia.model.Categoria;
import com.farmacia.repository.CategoriaRepositorio;
import com.farmacia.repository.MedicamentoRepositorio;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para la gestión y navegación jerárquica de categorías de medicamentos (spec 009).
 */
@Service
public class CategoriaServicio {

    private final CategoriaRepositorio categoriaRepositorio;
    private final AuditoriaServicio auditoriaServicio;
    private final MedicamentoRepositorio medicamentoRepositorio;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public CategoriaServicio(
            CategoriaRepositorio categoriaRepositorio,
            AuditoriaServicio auditoriaServicio,
            MedicamentoRepositorio medicamentoRepositorio,
            Clock clock) {
        this.categoriaRepositorio = categoriaRepositorio;
        this.auditoriaServicio = auditoriaServicio;
        this.medicamentoRepositorio = medicamentoRepositorio;
        this.clock = clock;
    }

    public CategoriaServicio(CategoriaRepositorio categoriaRepositorio, AuditoriaServicio auditoriaServicio) {
        this(categoriaRepositorio, auditoriaServicio, null, Clock.systemDefaultZone());
    }

    /**
     * Reconstruye en memoria el árbol n-ario con todas las categorías activas en tiempo O(n).
     * Utiliza un recorrido en anchura (BFS) para garantizar la inserción en orden topológico (padres antes que hijos).
     */
    public ArbolCategorias arbol() {
        List<Categoria> categorias = categoriaRepositorio.listarActivas();
        ArbolCategorias arbol = new ArbolCategorias();

        List<Categoria> raices = new ArrayList<>();
        Map<Integer, List<Categoria>> porPadre = new LinkedHashMap<>();

        for (Categoria cat : categorias) {
            if (cat.idPadre() == null) {
                raices.add(cat);
            } else {
                porPadre.computeIfAbsent(cat.idPadre(), k -> new ArrayList<>()).add(cat);
            }
        }

        Queue<Categoria> cola = new ArrayDeque<>(raices);
        while (!cola.isEmpty()) {
            Categoria actual = cola.poll();
            arbol.insertar(actual.idCategoria(), actual.nombre(), actual.idPadre());
            List<Categoria> hijos = porPadre.getOrDefault(actual.idCategoria(), List.of());
            cola.addAll(hijos);
        }

        return arbol;
    }

    public List<Categoria> listarActivas() {
        return categoriaRepositorio.listarActivas();
    }

    public List<Categoria> listarTodas() {
        return categoriaRepositorio.listarTodas();
    }

    public Optional<Categoria> buscarPorId(Integer id) {
        return categoriaRepositorio.buscarPorId(id);
    }

    /**
     * Registra una nueva categoría validando unicidad en el nivel y audita el evento (CA-2.1, CA-2.3, CA-2.4).
     */
    @Transactional(rollbackFor = Exception.class)
    public Integer crear(String nombre, Integer idPadre) {
        validarNombre(nombre);

        if (idPadre != null) {
            Categoria padre = categoriaRepositorio.buscarPorId(idPadre)
                    .orElseThrow(() -> new IllegalArgumentException("La categoría padre no existe: " + idPadre));
            if (!padre.activo()) {
                throw new IllegalArgumentException("La categoría padre no se encuentra activa");
            }
        }

        validarUnicidadNombre(idPadre, nombre, null);

        Integer id = categoriaRepositorio.guardar(nombre, idPadre);
        String detalle = "nombre: " + nombre.trim() + (idPadre != null ? "; padre: " + idPadre : "; raíz");
        auditoriaServicio.registrarCambio(AccionAuditoria.CREAR, "CATEGORIA", id, detalle);
        return id;
    }

    /**
     * Actualiza el nombre y/o padre de una categoría previniendo la formación de ciclos (CA-2.1, CA-2.3, CA-2.4).
     */
    @Transactional(rollbackFor = Exception.class)
    public void actualizar(Integer id, String nombre, Integer nuevoIdPadre) {
        Objects.requireNonNull(id, "El id de categoría no puede ser nulo");
        validarNombre(nombre);

        Categoria actual = categoriaRepositorio.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada con id: " + id));

        if (nuevoIdPadre != null) {
            if (nuevoIdPadre.equals(id)) {
                throw new IllegalArgumentException("Una categoría no puede ser padre de sí misma");
            }
            Categoria nuevoPadre = categoriaRepositorio.buscarPorId(nuevoIdPadre)
                    .orElseThrow(() -> new IllegalArgumentException("La categoría padre no existe: " + nuevoIdPadre));
            if (!nuevoPadre.activo()) {
                throw new IllegalArgumentException("La categoría padre no se encuentra activa");
            }

            // Detección y prevención de ciclos mediante el árbol en memoria
            ArbolCategorias arbolActual = arbol();
            if (arbolActual.contiene(id) && arbolActual.descendientesIds(id).contains(nuevoIdPadre)) {
                throw new IllegalArgumentException("No se puede mover la categoría a uno de sus descendientes para evitar ciclos");
            }
        }

        validarUnicidadNombre(nuevoIdPadre, nombre, id);

        categoriaRepositorio.actualizar(id, nombre, nuevoIdPadre);
        String detalle = "nombre: " + nombre.trim() + "; padre: " + (nuevoIdPadre != null ? nuevoIdPadre : "raíz");
        auditoriaServicio.registrarCambio(AccionAuditoria.EDITAR, "CATEGORIA", id, detalle);
    }

    /**
     * Desactiva una categoría asegurando que no tenga subcategorías ni medicamentos activos (CA-2.2, CA-2.4).
     */
    @Transactional(rollbackFor = Exception.class)
    public void desactivar(Integer id) {
        Categoria actual = categoriaRepositorio.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada con id: " + id));

        if (!actual.activo()) {
            return;
        }

        if (categoriaRepositorio.contarHijosActivos(id) > 0) {
            throw new IllegalStateException("No se puede desactivar la categoría porque tiene subcategorías activas");
        }

        if (categoriaRepositorio.contarMedicamentosActivos(id) > 0) {
            throw new IllegalStateException("No se puede desactivar la categoría porque tiene medicamentos activos asociados");
        }

        categoriaRepositorio.actualizarEstado(id, false);
        auditoriaServicio.registrarCambio(AccionAuditoria.DESACTIVAR, "CATEGORIA", id, "nombre: " + actual.nombre());
    }

    private void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre de la categoría no puede estar vacío");
        }
    }

    private void validarUnicidadNombre(Integer idPadre, String nombre, Integer idExcluir) {
        Optional<Categoria> existente = categoriaRepositorio.buscarPorPadreYNombre(idPadre, nombre);
        if (existente.isPresent() && !existente.get().idCategoria().equals(idExcluir)) {
            throw new IllegalArgumentException("Ya existe una categoría con el nombre '" + nombre.trim() + "' en este nivel");
        }
    }

    /**
     * Calcula la suma de stock disponible de medicamentos activos de toda la rama (CA-3.1).
     * No cuenta lotes vencidos según la fecha de corte del reloj.
     */
    public int stockPorRama(Integer idCategoria) {
        if (idCategoria == null || medicamentoRepositorio == null) {
            return 0;
        }
        ArbolCategorias arbol = arbol();
        if (!arbol.contiene(idCategoria)) {
            return 0;
        }
        List<Integer> idsRama = arbol.descendientesIds(idCategoria);
        return medicamentoRepositorio.sumarStockDisponiblePorCategorias(idsRama, LocalDate.now(clock));
    }

    /**
     * Cuenta los medicamentos en alerta de la rama completa (stock bajo mínimo o agotado).
     */
    public int alertasPorRama(Integer idCategoria) {
        if (idCategoria == null || medicamentoRepositorio == null) {
            return 0;
        }
        ArbolCategorias arbol = arbol();
        if (!arbol.contiene(idCategoria)) {
            return 0;
        }
        List<Integer> idsRama = arbol.descendientesIds(idCategoria);
        return medicamentoRepositorio.contarAlertasPorCategorias(idsRama, LocalDate.now(clock));
    }
}
