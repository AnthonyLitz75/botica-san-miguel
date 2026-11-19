package com.farmacia.calidad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T1903: Prueba de contenido: los scripts de la caja no deben contener el dato falso '14 compras'.
 * (CA-1.5, RNF-02).
 */
class PreparacionVentaContenidoJsTest {

    private static final Path RUTA_JS = Paths.get("src/main/resources/static/js");

    @Test
    @DisplayName("CA-1.5: Ningún archivo JavaScript de la caja contiene el texto '14 compras'")
    void scriptsCajaNoContienenLiteral14Compras() throws IOException {
        assertTrue(Files.exists(RUTA_JS), "La ruta de scripts debe existir: " + RUTA_JS);

        try (Stream<Path> archivos = Files.walk(RUTA_JS)) {
            archivos.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".js"))
                    .forEach(archivo -> {
                        try {
                            String contenido = Files.readString(archivo);
                            assertFalse(contenido.contains("14 compras"),
                                    "El archivo " + archivo + " contiene el texto fijo '14 compras' (CA-1.5)");
                        } catch (IOException e) {
                            throw new RuntimeException("Error leyendo " + archivo, e);
                        }
                    });
        }
    }
}
