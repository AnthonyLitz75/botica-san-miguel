package com.farmacia.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardaCodigosInternosTest {

    private static final Pattern PATRON_COMENTARIOS_HTML = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern PATRON_TAG_STYLE = Pattern.compile("<style[^>]*>.*?</style>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern PATRON_TAG_SCRIPT = Pattern.compile("<script[^>]*>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);

    private static final Pattern PATRON_CODIGOS_PROHIBIDOS = Pattern.compile(
            "\\b(RNF-\\d+|RF-\\d+|CU-\\d+|RBAC|BCrypt)\\b|Decisi[oó]n\\s+D-|\\bD-\\d+\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Test
    @DisplayName("Guarda unitaria: El extractor detecta RNF-01, RF-04, Decisión D-7, D-13, RBAC, BCrypt y CU-08 ignorando comentarios HTML y scripts")
    void detectorDebeIdentificarCodigosInternosEIgnorarComentariosYScripts() {
        String snippetProhibido = """
                <div>
                    <!-- Comentario interno con RNF-02 y Decisión D-1 que debe ignorarse -->
                    <script>const rbac = 'RBAC';</script>
                    <style>.test { color: red; /* CU-01 */ }</style>
                    <p>Cumple con RNF-01 y RF-04.</p>
                    <p>Decisión D-7 sobre bajas.</p>
                    <p>Aislado D-13 en texto.</p>
                    <p>Control de acceso RBAC seguro.</p>
                    <p>Cifrado con BCrypt en backend.</p>
                    <p>Caso de uso CU-08 del sistema.</p>
                </div>
                """;

        List<String> hallazgos = extraerCoincidencias(snippetProhibido);
        assertThat(hallazgos).contains(
                "RNF-01", "RF-04", "Decisión D-", "D-13", "RBAC", "BCrypt", "CU-08"
        );
        assertThat(hallazgos).doesNotContain("RNF-02", "D-1", "CU-01");
    }

    @Test
    @DisplayName("Guarda de calidad: Ninguna plantilla HTML debe contener códigos internos (RNF, RF, D-*, RBAC, BCrypt, CU-*) en texto visible")
    void plantillasHtmlNoDebenContenerCodigosInternosVisibles() throws IOException {
        Path templatesDir = Path.of("src", "main", "resources", "templates");

        List<String> infracciones = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(templatesDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".html"))
                    .forEach(path -> {
                        try {
                            String contenido = Files.readString(path);
                            String textoVisible = limpiarHtml(contenido);

                            String[] lineas = textoVisible.split("\\R");
                            for (int i = 0; i < lineas.length; i++) {
                                String linea = lineas[i];
                                Matcher matcher = PATRON_CODIGOS_PROHIBIDOS.matcher(linea);
                                while (matcher.find()) {
                                    String coincidencia = matcher.group();
                                    infracciones.add(String.format("%s [Línea %d]: '%s' -> %s",
                                            templatesDir.relativize(path).toString().replace('\\', '/'),
                                            i + 1,
                                            coincidencia,
                                            linea.trim()));
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException("Error al leer archivo: " + path, e);
                        }
                    });
        }

        assertThat(infracciones)
                .withFailMessage("Se encontraron códigos internos en plantillas HTML:\n%s",
                        String.join("\n", infracciones))
                .isEmpty();
    }

    private static String limpiarHtml(String html) {
        String sinComentarios = PATRON_COMENTARIOS_HTML.matcher(html).replaceAll("");
        String sinEstilos = PATRON_TAG_STYLE.matcher(sinComentarios).replaceAll("");
        return PATRON_TAG_SCRIPT.matcher(sinEstilos).replaceAll("");
    }

    private static List<String> extraerCoincidencias(String html) {
        String visible = limpiarHtml(html);
        List<String> encontrados = new ArrayList<>();
        Matcher matcher = PATRON_CODIGOS_PROHIBIDOS.matcher(visible);
        while (matcher.find()) {
            encontrados.add(matcher.group());
        }
        return encontrados;
    }
}
