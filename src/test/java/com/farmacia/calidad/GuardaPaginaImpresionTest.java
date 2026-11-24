package com.farmacia.calidad;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardaPaginaImpresionTest {

    private static final Path TICKET_PRINT_CSS =
            Path.of("src", "main", "resources", "static", "css", "modulos", "ticket-print.css");

    private static final Pattern PATRON_COMENTARIO = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern PATRON_BLOQUE_PAGE = Pattern.compile("@page\\b[^{]*\\{([^}]*)\\}");
    private static final Pattern PATRON_DECLARACION_SIZE = Pattern.compile("(?<![\\w-])size\\s*:\\s*([^;}]*)");

    // Sintaxis de size: <length [0,∞]>{1,2} | auto | [ <page-size> || [ portrait | landscape ] ]
    private static final String LONGITUD = "\\d+(?:\\.\\d+)?(?:mm|cm|in|pt|px)";
    private static final String TAMANO_PAPEL = "(?:A3|A4|A5|B4|B5|JIS-B4|JIS-B5|letter|legal|ledger)";
    private static final String ORIENTACION = "(?:portrait|landscape)";
    private static final Pattern PATRON_SIZE_VALIDO = Pattern.compile(
            "auto"
                    + "|" + LONGITUD + "(?:\\s+" + LONGITUD + ")?"
                    + "|" + TAMANO_PAPEL + "(?:\\s+" + ORIENTACION + ")?"
                    + "|" + ORIENTACION + "(?:\\s+" + TAMANO_PAPEL + ")?",
            Pattern.CASE_INSENSITIVE);

    @Test
    @DisplayName("Guarda: ticket-print.css define una regla @page con una declaración size")
    void ticketPrint_defineReglaPageConSize() throws IOException {
        List<String> valores = declaracionesSize(leerSinComentarios()).stream().map(d -> d.valor).toList();

        assertThat(valores)
                .withFailMessage("%s debe contener una regla @page con una declaración size (ticket térmico de 80 mm)",
                        rutaLegible())
                .isNotEmpty();
    }

    @Test
    @DisplayName("Guarda: cada size de @page en ticket-print.css usa una sintaxis válida (sin '<longitud> auto')")
    void ticketPrint_sizeDePageTieneSintaxisValida() throws IOException {
        List<String> infracciones = new ArrayList<>();
        for (DeclaracionSize declaracion : declaracionesSize(leerSinComentarios())) {
            if (!esSizeValido(declaracion.valor)) {
                infracciones.add(String.format("Archivo: %s, Línea %d: size: %s", rutaLegible(), declaracion.linea,
                        declaracion.valor));
            }
        }

        assertThat(infracciones)
                .withFailMessage("Declaraciones size de @page inválidas (Chromium las descarta y la página sale en"
                        + " tamaño por defecto). Sintaxis válida: 1 o 2 longitudes, 'auto' sola, o un tamaño de papel"
                        + " con orientación opcional:\n%s", String.join("\n", infracciones))
                .isEmpty();
    }

    @Test
    @DisplayName("Guarda: el validador de size acepta valores válidos y rechaza '80mm auto'")
    void validadorSize_distingueValoresValidosEInvalidos() {
        assertThat(List.of("80mm 297mm", "80mm", "auto", "A4", "A4 portrait", "landscape letter", "3.15in 11.7in"))
                .allMatch(GuardaPaginaImpresionTest::esSizeValido);
        assertThat(List.of("80mm auto", "auto 80mm", "80 297", "A4 auto", "80mm 297mm 10mm", ""))
                .noneMatch(GuardaPaginaImpresionTest::esSizeValido);
    }

    private static boolean esSizeValido(String valor) {
        String v = valor.strip();
        if (v.endsWith("!important")) {
            v = v.substring(0, v.length() - "!important".length());
        }
        String normalizado = v.trim().replaceAll("\\s+", " ");
        return PATRON_SIZE_VALIDO.matcher(normalizado).matches();
    }

    private static String leerSinComentarios() throws IOException {
        assertThat(Files.exists(TICKET_PRINT_CSS)).withFailMessage("No existe %s", rutaLegible()).isTrue();
        String contenido = Files.readString(TICKET_PRINT_CSS);
        // Sustituye los comentarios por espacios conservando los saltos de línea para no alterar la numeración
        Matcher comentario = PATRON_COMENTARIO.matcher(contenido);
        StringBuilder limpio = new StringBuilder();
        while (comentario.find()) {
            comentario.appendReplacement(limpio, Matcher.quoteReplacement(comentario.group().replaceAll("[^\\n]", " ")));
        }
        comentario.appendTail(limpio);
        return limpio.toString();
    }

    private static List<DeclaracionSize> declaracionesSize(String css) {
        List<DeclaracionSize> declaraciones = new ArrayList<>();
        Matcher bloque = PATRON_BLOQUE_PAGE.matcher(css);
        while (bloque.find()) {
            Matcher size = PATRON_DECLARACION_SIZE.matcher(bloque.group(1));
            while (size.find()) {
                int posicion = bloque.start(1) + size.start();
                declaraciones.add(new DeclaracionSize(numeroLinea(css, posicion), size.group(1).trim()));
            }
        }
        return declaraciones;
    }

    private static int numeroLinea(String texto, int posicion) {
        int linea = 1;
        for (int i = 0; i < posicion; i++) {
            if (texto.charAt(i) == '\n') {
                linea++;
            }
        }
        return linea;
    }

    private static String rutaLegible() {
        return TICKET_PRINT_CSS.toString().replace('\\', '/');
    }

    private record DeclaracionSize(int linea, String valor) {
    }
}
