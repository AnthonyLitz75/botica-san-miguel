package com.farmacia.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardaIconosDefinidosTest {

    private static final Pattern PATRON_CSS_ICONO = Pattern.compile("\\.icono-([a-zA-Z0-9_-]+)");
    private static final Pattern PATRON_CLASS_ATTR = Pattern.compile("(?:th:)?class(?:append)?\\s*=\\s*\"([^\"]*)\"|(?:th:)?class(?:append)?\\s*=\\s*'([^']*)'");

    // Modificadores o clases excepcionales justificadas que no son iconos en sí mismos
    private static final Set<String> CLASES_PERMITIDAS = Collections.emptySet();

    @Test
    @DisplayName("Guarda de regresión: Todas las clases icono-* usadas en plantillas deben estar definidas en los CSS")
    void todosLosIconosUsadosEnPlantillasDebenEstarDefinidosEnCss() throws IOException {
        Path baseResources = Path.of("src", "main", "resources");
        Path cssDir = baseResources.resolve("static").resolve("css");
        Path templatesDir = baseResources.resolve("templates");

        Set<String> iconosDefinidos = recolectarIconosDefinidosEnCss(cssDir);

        List<String> infracciones = new ArrayList<>();
        inspeccionarPlantillas(templatesDir, iconosDefinidos, infracciones);

        assertThat(infracciones)
                .withFailMessage("Se encontraron clases 'icono-*' en plantillas sin definición en CSS:\n%s",
                        String.join("\n", infracciones))
                .isEmpty();
    }

    private Set<String> recolectarIconosDefinidosEnCss(Path cssDir) throws IOException {
        Set<String> iconos = new HashSet<>();
        if (!Files.exists(cssDir)) {
            return iconos;
        }

        try (Stream<Path> stream = Files.walk(cssDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> {
                        String nombre = path.getFileName().toString();
                        return nombre.endsWith(".css") && !nombre.equals("tailwind.css") && !nombre.equals("tailwind-input.css");
                    })
                    .forEach(path -> {
                        try {
                            String contenido = Files.readString(path);
                            Matcher matcher = PATRON_CSS_ICONO.matcher(contenido);
                            while (matcher.find()) {
                                iconos.add(matcher.group(1));
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Error leyendo archivo CSS: " + path, e);
                        }
                    });
        }
        return iconos;
    }

    private void inspeccionarPlantillas(Path templatesDir, Set<String> iconosDefinidos, List<String> infracciones) throws IOException {
        if (!Files.exists(templatesDir)) {
            return;
        }

        try (Stream<Path> stream = Files.walk(templatesDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .sorted()
                    .forEach(path -> verificarPlantilla(path, iconosDefinidos, infracciones));
        }
    }

    private void verificarPlantilla(Path archivo, Set<String> iconosDefinidos, List<String> infracciones) {
        try {
            List<String> lineas = Files.readAllLines(archivo);
            for (int i = 0; i < lineas.size(); i++) {
                String linea = lineas.get(i).trim();
                if (linea.startsWith("<!--")) {
                    continue;
                }

                Matcher classMatcher = PATRON_CLASS_ATTR.matcher(linea);
                while (classMatcher.find()) {
                    String valorAtributo = classMatcher.group(1) != null ? classMatcher.group(1) : classMatcher.group(2);
                    String[] tokens = valorAtributo.split("[\\s+,'\":?|{}()\\[\\]]+");
                    for (String token : tokens) {
                        if (token.startsWith("icono-")) {
                            String nombreIcono = token.substring("icono-".length());
                            if (!nombreIcono.isEmpty() && !CLASES_PERMITIDAS.contains(nombreIcono) && !iconosDefinidos.contains(nombreIcono)) {
                                infracciones.add(String.format("Archivo: %s, Línea %d: icono-%s", archivo, i + 1, nombreIcono));
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error leyendo plantilla HTML: " + archivo, e);
        }
    }
}
