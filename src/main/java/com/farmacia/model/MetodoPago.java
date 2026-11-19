package com.farmacia.model;

/**
 * Métodos de pago permitidos en la venta (Spec 016).
 */
public enum MetodoPago {
    EFECTIVO("Efectivo"),
    YAPE_PLIN("Yape / Plin"),
    TARJETA("Tarjeta");

    private final String etiqueta;

    MetodoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
