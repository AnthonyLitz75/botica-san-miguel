package com.farmacia.model;

public record ClienteResumenVista(
        Integer idCliente,
        String tipoDocumento,
        String numeroDocumento,
        String nombreCompleto,
        String telefono,
        String correo,
        long compras,
        boolean esFrecuente) {

    public ClienteResumenVista(Integer idCliente, String tipoDocumento, String numeroDocumento,
            String nombreCompleto, String telefono, String correo, long compras) {
        this(idCliente, tipoDocumento, numeroDocumento, nombreCompleto, telefono, correo, compras, false);
    }

    public ClienteResumenVista conFrecuencia(boolean frecuente) {
        return new ClienteResumenVista(idCliente, tipoDocumento, numeroDocumento, nombreCompleto, telefono, correo, compras, frecuente);
    }

    public String iniciales() {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return "CL";
        }
        String limpio = nombreCompleto.strip();
        String[] partes = limpio.split("\\s+");
        if (partes.length == 1) {
            return partes[0].substring(0, Math.min(2, partes[0].length())).toUpperCase();
        }
        String upper = limpio.toUpperCase();
        boolean esEntidad = upper.contains("S.A.") || upper.contains("SAC") || upper.contains("S.R.L.")
                || upper.contains("E.I.R.L.") || upper.startsWith("CLÍNICA") || upper.startsWith("CLINICA")
                || upper.startsWith("BOTICA") || upper.startsWith("FARMACIA") || upper.startsWith("DISTRIBUIDORA");
        char primera = Character.toUpperCase(partes[0].charAt(0));
        char segunda = Character.toUpperCase((partes.length == 4 && !esEntidad) ? partes[2].charAt(0) : partes[1].charAt(0));
        return "" + primera + segunda;
    }
}
