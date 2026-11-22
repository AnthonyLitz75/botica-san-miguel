package com.farmacia.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardaConfirmNativoTest {

    private static final Pattern PATRON_CONFIRM = Pattern.compile("\\bconfirm\\s*\\(");

    @Test
    @DisplayName("Guarda de regresión: Ninguna plantilla ni archivo JS debe contener llamadas a confirm() nativo")
    void ningunaPlantillaNiScriptDebeTenerConfirmNativo() throws IOException {
        Path baseResources = Path.of("src", "main", "resources");
        Path templatesDir = baseResources.resolve("templates");
        Path jsDir = baseResources.resolve("static").resolve("js");

        List<String> infracciones = new ArrayList<>();

        inspeccionarDirectorio(templatesDir, infracciones);
        inspeccionarDirectorio(jsDir, infracciones);

        assertThat(infracciones)
                .withFailMessage("Se encontraron llamadas a confirm() nativo del navegador en recursos ejecutables:\n%s",
                        String.join("\n", infracciones))
                .isEmpty();
    }

    private void inspeccionarDirectorio(Path directorio, List<String> infracciones) throws IOException {
        if (!Files.exists(directorio)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(directorio)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String nombre = path.getFileName().toString().toLowerCase();
                        return nombre.endsWith(".html") || nombre.endsWith(".js");
                    })
                    .forEach(archivo -> verificarArchivo(archivo, infracciones));
        }
    }

    private void verificarArchivo(Path archivo, List<String> infracciones) {
        try {
            List<String> lineas = Files.readAllLines(archivo);
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i).trim();
                // Ignorar comentarios de una sola línea en JS/CSS o Java
                if (linea.startsWith("//") || linea.startsWith("*") || linea.startsWith("/*") || linea.startsWith("<!--")) {
                    continue;
                }
                // Si la línea contiene confirm( fuera de comentarios
                if (PATRON_CONFIRM.matcher(linea).find()) {
                    infracciones.add(String.format("Archivo: %s, Línea %d: %s", archivo, i + 1, linea));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo archivo: " + archivo, e);
        }
    }
}
