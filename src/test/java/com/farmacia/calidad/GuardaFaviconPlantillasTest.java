package com.farmacia.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardaFaviconPlantillasTest {

    private static final Pattern PATRON_HEAD = Pattern.compile("<head\\b", Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("EST-01: Toda plantilla con <head debe contener rel=\"icon\" o incluir el fragmento head")
    void todaPlantillaConHeadDebeTenerFaviconOIncluirFragmentoHead() throws IOException {
        Path templatesDir = Path.of("src", "main", "resources", "templates");
        List<String> infracciones = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(templatesDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".html"))
                    .forEach(path -> {
                        try {
                            String contenido = Files.readString(path);
                            if (PATRON_HEAD.matcher(contenido).find()) {
                                boolean tieneRelIcon = contenido.toLowerCase(Locale.ROOT).contains("rel=\"icon\"")
                                        || contenido.toLowerCase(Locale.ROOT).contains("rel='icon'");
                                boolean incluyeFragmentoHead = contenido.contains("fragmentos/head");

                                if (!tieneRelIcon && !incluyeFragmentoHead) {
                                    infracciones.add(path.toString().replace('\\', '/')
                                            + " contiene <head pero no define rel=\"icon\" ni incluye fragmentos/head");
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Error leyendo plantilla: " + path, e);
                        }
                    });
        }

        assertThat(infracciones)
                .withFailMessage("Se encontraron plantillas con <head sin favicon ni inclusión de fragmentos/head:\n%s",
                        String.join("\n", infracciones))
                .isEmpty();
    }
}
