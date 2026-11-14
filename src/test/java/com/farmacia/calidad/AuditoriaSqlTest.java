package com.farmacia.calidad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueba estática de seguridad contra Inyección SQL (RNF-03, CA-1.2).
 * Analiza el código fuente de los repositorios para garantizar que ninguna sentencia SQL
 * concatene variables directamente en lugar de usar parámetros posicionales '?'.
 */
class AuditoriaSqlTest {

    private static final Path RUTA_REPOSITORIOS = Paths.get("src/main/java/com/farmacia/repository");

    // Patrón que detecta el cierre de un literal String seguido de '+' y un identificador de variable/método.
    // Es estrictamente lineal O(n) al no usar comodines libres ni cuantificadores ambiguos (cumple java:S8786).
    private static final Pattern PATRON_CONCATENACION_VARIABLE = Pattern.compile("\"\\s*\\+\\s*[a-zA-Z_]");

    // Palabras clave SQL para verificar si la concatenación ocurre en contexto de consulta de base de datos
    private static final Pattern PATRON_CLAVE_SQL = Pattern.compile(
            "\\b(?:SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|AND|SET)\\b"
    );

    // Lista blanca documentada de invocaciones seguras a métodos generadores de WHERE (CA-1.2, RNF-03)
    // Estos métodos no concatenan datos de usuario; solo devuelven fragmentos fijos con marcadores '?'
    private static final List<String> LISTA_BLANCA_METODOS_SEGUROS = List.of(
            "condiciones(filtro, parametros)",
            "condiciones(filtro, params)"
    );

    @Test
    @DisplayName("CA-1.2: Ningún repositorio Java concatena variables de usuario en consultas SQL")
    void repositoriosNoTienenConcatenacionSql() throws IOException {
        assertTrue(Files.exists(RUTA_REPOSITORIOS), "La ruta de repositorios debe existir: " + RUTA_REPOSITORIOS);

        List<String> infracciones = new ArrayList<>();

        try (Stream<Path> archivos = Files.walk(RUTA_REPOSITORIOS)) {
            archivos.filter(p -> p.toString().endsWith(".java")).forEach(archivo -> {
                try {
                    List<String> lineas = Files.readAllLines(archivo);
                    for (int i = 0; i < lineas.size(); i++) {
                        String linea = lineas.get(i).trim();
                        // Ignorar comentarios
                        if (linea.startsWith("//") || linea.startsWith("*") || linea.startsWith("/*")) {
                            continue;
                        }
                        if (detectarConcatenacionInsegura(linea)) {
                            infracciones.add(String.format("%s (Línea %d): %s", archivo.getFileName(), i + 1, linea));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Error al leer archivo: " + archivo, e);
                }
            });
        }

        assertTrue(infracciones.isEmpty(),
                "Se detectaron concatenaciones sospechosas en sentencias SQL:\n" + String.join("\n", infracciones));
    }

    @Test
    @DisplayName("CA-1.2 (Comprobación de falla intencional): El detector identifica correctamente una inyección simulada")
    void detectorFallaAnteConcatenacionSimulada() {
        String codigoInseguro1 = "String sql = \"SELECT * FROM usuario WHERE nombre = '\" + inputUsuario + \"'\";";
        String codigoInseguro2 = "where.append(\" AND u.nombre = '\" + filtro.usuario());";
        String codigoSeguro1 = "where.append(\" AND u.nombre = ?\"); params.add(filtro.usuario());";
        String codigoSeguro2 = "String sql = \"\"\" SELECT * FROM medicamento WHERE id_categoria IN (%s) \"\"\".formatted(placeholders);";

        assertTrue(detectarConcatenacionInsegura(codigoInseguro1), "Debe detectar inyección en SELECT");
        assertTrue(detectarConcatenacionInsegura(codigoInseguro2), "Debe detectar inyección en StringBuilder.append");
        assertFalse(detectarConcatenacionInsegura(codigoSeguro1), "No debe dar falso positivo en PreparedStatement seguro con '?'");
        assertFalse(detectarConcatenacionInsegura(codigoSeguro2), "No debe dar falso positivo en placeholders '?'");
    }

    private boolean detectarConcatenacionInsegura(String linea) {
        for (String metodoSeguro : LISTA_BLANCA_METODOS_SEGUROS) {
            if (linea.contains(metodoSeguro)) {
                return false;
            }
        }
        Matcher matcherConcat = PATRON_CONCATENACION_VARIABLE.matcher(linea);
        while (matcherConcat.find()) {
            // Analizar la porción previa a la concatenación para confirmar si estamos dentro de una sentencia SQL
            String previo = linea.substring(0, matcherConcat.start() + 1);
            if (PATRON_CLAVE_SQL.matcher(previo).find()) {
                return true;
            }
        }
        return false;
    }
}
