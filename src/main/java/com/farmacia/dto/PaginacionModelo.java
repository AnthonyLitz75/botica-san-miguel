package com.farmacia.dto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Modelo de paginación reutilizable para listados largos (Spec 023).
 */
public record PaginacionModelo(
        int pagina,
        int paginas,
        int total,
        int tamano,
        int desde,
        int hasta,
        String urlAnterior,
        String urlSiguiente,
        List<ElementoPaginacion> elementos
) {

    public record ElementoPaginacion(
            String texto,
            Integer numeroPagina,
            String url,
            boolean esActual,
            boolean esSeparador
    ) {
        public static ElementoPaginacion pagina(int numero, String url, boolean esActual) {
            return new ElementoPaginacion(String.valueOf(numero), numero, url, esActual, false);
        }

        public static ElementoPaginacion separador() {
            return new ElementoPaginacion("…", null, null, false, true);
        }
    }

    public static PaginacionModelo crear(int paginaSolicitada, int totalElementos, int tamanoPagina,
                                         String rutaBase, Map<String, ?> parametros) {
        int total = Math.max(0, totalElementos);
        int tamano = tamanoPagina > 0 ? tamanoPagina : 25;
        int paginas = Math.max(1, (total + tamano - 1) / tamano);
        int pagina = Math.min(Math.max(1, paginaSolicitada), paginas);

        int desde = total == 0 ? 0 : (pagina - 1) * tamano + 1;
        int hasta = total == 0 ? 0 : Math.min(pagina * tamano, total);

        String urlAnterior = pagina > 1 ? construirUrl(rutaBase, parametros, pagina - 1) : null;
        String urlSiguiente = pagina < paginas ? construirUrl(rutaBase, parametros, pagina + 1) : null;

        List<ElementoPaginacion> elementos;
        if (paginas <= 1) {
            elementos = List.of();
        } else {
            elementos = new ArrayList<>();
            TreeSet<Integer> numeros = new TreeSet<>();
            numeros.add(1);
            numeros.add(paginas);
            if (pagina - 1 >= 1) {
                numeros.add(pagina - 1);
            }
            numeros.add(pagina);
            if (pagina + 1 <= paginas) {
                numeros.add(pagina + 1);
            }

            Integer anterior = null;
            for (Integer num : numeros) {
                if (anterior != null && num - anterior > 1) {
                    elementos.add(ElementoPaginacion.separador());
                }
                String url = construirUrl(rutaBase, parametros, num);
                elementos.add(ElementoPaginacion.pagina(num, url, num == pagina));
                anterior = num;
            }
        }

        return new PaginacionModelo(pagina, paginas, total, tamano, desde, hasta,
                urlAnterior, urlSiguiente, Collections.unmodifiableList(elementos));
    }

    public static String construirUrl(String rutaBase, Map<String, ?> parametros, int numeroPagina) {
        StringBuilder sb = new StringBuilder(rutaBase != null ? rutaBase : "");
        boolean tieneParametros = false;
        if (parametros != null) {
            for (Map.Entry<String, ?> entry : parametros.entrySet()) {
                String clave = entry.getKey();
                if ("pagina".equalsIgnoreCase(clave)) {
                    continue;
                }
                Object valor = entry.getValue();
                if (valor == null) {
                    continue;
                }
                String strValor = valor.toString().trim();
                if (strValor.isEmpty()) {
                    continue;
                }
                sb.append(tieneParametros ? "&" : "?");
                sb.append(URLEncoder.encode(clave, StandardCharsets.UTF_8));
                sb.append("=");
                sb.append(URLEncoder.encode(strValor, StandardCharsets.UTF_8));
                tieneParametros = true;
            }
        }
        sb.append(tieneParametros ? "&" : "?");
        sb.append("pagina=").append(numeroPagina);
        return sb.toString();
    }
}
