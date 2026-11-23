package com.farmacia.calidad;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardaOrtografiaAtributosTest {

    private static final List<String> PALABRAS_PROHIBIDAS_SIN_TILDE = List.of(
            "\\bcodigo\\b",
            "\\btelefono\\b",
            "\\bdireccion\\b",
            "\\boperacion\\b",
            "\\bcancelacion\\b",
            "\\brecepcion\\b",
            "\\bubicacion\\b",
            "\\bcategoria\\b",
            "\\bnumero\\b",
            "\\bmetodo\\b",
            "\\bsesion\\b",
            "\\bcontrasena\\b",
            "\\bmedico\\b",
            "\\bfarmaceutico\\b"
    );

    @Test
    @DisplayName("Guarda: los atributos visibles en plantillas HTML y la tabla de movimientos no deben contener faltas ni términos obsoletos")
    void plantillasHtml_atributosVisibles_noDebenTenerFaltasOrtograficas() throws IOException {
        Path templatesDir = Path.of("src", "main", "resources", "templates");
        assertTrue(Files.exists(templatesDir), "El directorio de plantillas debe existir");

        List<String> infracciones = new ArrayList<>();
        Pattern patronAtributos = Pattern.compile("(?:placeholder|title|aria-label|alt)\\s*=\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);

        try (Stream<Path> stream = Files.walk(templatesDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".html"))
                  .forEach(p -> {
                      try {
                          List<String> lineas = Files.readAllLines(p);
                          for (int i = 0; i < lineas.size(); i++) {
                              String linea = lineas.get(i);
                              Matcher matcher = patronAtributos.matcher(linea);
                              while (matcher.find()) {
                                  String valor = matcher.group(1);
                                  // Verificar que en movimientos/listado.html no se use "kardex" en aria-label
                                  if (valor.toLowerCase().contains("kardex") && valor.toLowerCase().contains("inventario")) {
                                      infracciones.add(String.format("%s:%d [aria-label/attr] contiene 'kardex': %s", p.getFileName(), i + 1, valor));
                                  }
                              }
                          }
                      } catch (IOException e) {
                          throw new RuntimeException(e);
                      }
                  });
        }

        // Verificar además que movimientos/listado.html tenga explícitamente "Tabla de movimientos de inventario"
        Path listadoMovimientos = templatesDir.resolve(Path.of("movimientos", "listado.html"));
        String contenidoMovimientos = Files.readString(listadoMovimientos);
        if (!contenidoMovimientos.contains("Tabla de movimientos de inventario")) {
            infracciones.add("movimientos/listado.html debe contener el atributo o texto 'Tabla de movimientos de inventario'");
        }

        assertTrue(infracciones.isEmpty(), "Se encontraron infracciones de ortografía o accesibilidad en plantillas:\n" + String.join("\n", infracciones));
    }

    @Test
    @DisplayName("Guarda: los mensajes de validación y controladores Java no deben contener palabras clave sin tilde")
    void codigoJava_mensajesValidacionYControladores_debenTenerTildes() throws IOException {
        Path javaDir = Path.of("src", "main", "java", "com", "farmacia");
        assertTrue(Files.exists(javaDir), "El directorio de fuentes Java debe existir");

        List<String> infracciones = new ArrayList<>();
        List<String> literalesErroneos = List.of(
                "El codigo es obligatorio",
                "Ya existe un medicamento con este codigo",
                "El medicamento no es valido",
                "El proveedor no es valido",
                "El detalle no puede estar vacio",
                "11 digitos",
                "La razon social es obligatoria",
                "La razon social admite",
                "El telefono admite",
                "Ingresa un correo valido",
                "La direccion admite",
                "Selecciona un medicamento valido",
                "El costo unitario no es valido"
        );

        try (Stream<Path> stream = Files.walk(javaDir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> p.toString().endsWith(".java"))
                  .forEach(p -> {
                      try {
                          String contenido = Files.readString(p);
                          for (String err : literalesErroneos) {
                              if (contenido.contains(err)) {
                                  infracciones.add(String.format("%s contiene mensaje sin tilde: '%s'", p.getFileName(), err));
                              }
                          }
                      } catch (IOException e) {
                          throw new RuntimeException(e);
                      }
                  });
        }

        assertTrue(infracciones.isEmpty(), "Se encontraron mensajes sin tilde en código Java:\n" + String.join("\n", infracciones));
    }
}
