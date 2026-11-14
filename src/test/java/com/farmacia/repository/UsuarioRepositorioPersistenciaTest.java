package com.farmacia.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.farmacia.model.Usuario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UsuarioRepositorioPersistenciaTest extends AbstractRepositorioIntegrationTest {

    @Autowired
    private UsuarioRepositorio repositorio;

    @Test
    @DisplayName("insertar y buscarPorId / buscarPorNombreUsuario recupera usuario con campos correctos")
    void insertarYBuscarRecuperaUsuarioCorrectamente() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "user_" + sufijo;

        Integer id = repositorio.insertar("Juan Perez", username, "{bcrypt}$hash123", "VENDEDOR");
        assertNotNull(id);
        assertTrue(id > 0);

        Optional<Usuario> porId = repositorio.buscarPorId(id);
        assertTrue(porId.isPresent());
        Usuario u1 = porId.get();
        assertEquals("Juan Perez", u1.nombre());
        assertEquals(username, u1.nombreUsuario());
        assertEquals("{bcrypt}$hash123", u1.claveHash());
        assertEquals("VENDEDOR", u1.rol());
        assertTrue(u1.activo());

        Optional<Usuario> porUser = repositorio.buscarPorNombreUsuario(username);
        assertTrue(porUser.isPresent());
        assertEquals(id, porUser.get().idUsuario());
    }

    @Test
    @DisplayName("listar devuelve todos los usuarios ordenados y listarActivos filtra los inactivos")
    void listarYListarActivosFiltranCorrectamente() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Integer id1 = repositorio.insertar("Alfa User", "alfa_" + sufijo, "{bcrypt}h1", "ALMACENERO");
        Integer id2 = repositorio.insertar("Beta User", "beta_" + sufijo, "{bcrypt}h2", "COMPRAS");

        repositorio.cambiarEstado(id2, false);

        List<Usuario> todos = repositorio.listar();
        assertTrue(todos.stream().anyMatch(u -> u.idUsuario().equals(id1) && u.activo()));
        assertTrue(todos.stream().anyMatch(u -> u.idUsuario().equals(id2) && !u.activo()));

        List<Usuario> activos = repositorio.listarActivos();
        assertTrue(activos.stream().anyMatch(u -> u.idUsuario().equals(id1)));
        assertFalse(activos.stream().anyMatch(u -> u.idUsuario().equals(id2)));
    }

    @Test
    @DisplayName("actualizar modifica nombre y rol sin alterar clave ni nombre_usuario")
    void actualizarModificaNombreYRol() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String username = "edit_" + sufijo;
        Integer id = repositorio.insertar("Nombre Original", username, "{bcrypt}claveOriginal", "VENDEDOR");

        boolean exito = repositorio.actualizar(id, "Nombre Editado", "COMPRAS");
        assertTrue(exito);

        Usuario modificado = repositorio.buscarPorId(id).orElseThrow();
        assertEquals("Nombre Editado", modificado.nombre());
        assertEquals("COMPRAS", modificado.rol());
        assertEquals(username, modificado.nombreUsuario());
        assertEquals("{bcrypt}claveOriginal", modificado.claveHash());
    }

    @Test
    @DisplayName("actualizarClave modifica la clave hash sin tocar los demas datos")
    void actualizarClaveModificaHash() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Integer id = repositorio.insertar("Clave User", "clave_" + sufijo, "{bcrypt}vieja", "VENDEDOR");

        boolean exito = repositorio.actualizarClave(id, "{bcrypt}nuevaClaveHash999");
        assertTrue(exito);

        Usuario modificado = repositorio.buscarPorId(id).orElseThrow();
        assertEquals("{bcrypt}nuevaClaveHash999", modificado.claveHash());
        assertEquals("Clave User", modificado.nombre());
    }

    @Test
    @DisplayName("cambiarEstado activa y desactiva correctamente")
    void cambiarEstadoModificaActivo() {
        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Integer id = repositorio.insertar("Estado User", "estado_" + sufijo, "{bcrypt}hash", "ALMACENERO");

        assertTrue(repositorio.cambiarEstado(id, false));
        assertFalse(repositorio.buscarPorId(id).orElseThrow().activo());

        assertTrue(repositorio.cambiarEstado(id, true));
        assertTrue(repositorio.buscarPorId(id).orElseThrow().activo());
    }

    @Test
    @DisplayName("contarAdministradoresActivos cuenta solo usuarios con rol ADMINISTRADOR y activo=true")
    void contarAdministradoresActivosCuentaCorrectamente() {
        int inicial = repositorio.contarAdministradoresActivos();

        String sufijo = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        Integer idAdminActivo = repositorio.insertar("Admin Nuevo", "adm_" + sufijo, "{bcrypt}h", "ADMINISTRADOR");
        Integer idAdminInactivo = repositorio.insertar("Admin Baja", "admbaja_" + sufijo, "{bcrypt}h", "ADMINISTRADOR");
        repositorio.cambiarEstado(idAdminInactivo, false);

        Integer idVendedor = repositorio.insertar("Vendedor", "vend_" + sufijo, "{bcrypt}h", "VENDEDOR");

        int despues = repositorio.contarAdministradoresActivos();
        assertEquals(inicial + 1, despues);
    }
}
