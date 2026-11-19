package com.farmacia.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "botica")
@Validated
public class DatosBotica {

    @NotBlank(message = "El nombre de la botica es obligatorio")
    private String nombre;

    @NotBlank(message = "El RUC de la botica es obligatorio")
    @Pattern(regexp = "^\\d{11}$", message = "El RUC de la botica debe tener exactamente 11 dígitos numéricos")
    private String ruc;

    @NotBlank(message = "La dirección de la botica es obligatoria")
    private String direccion;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getRuc() {
        return ruc;
    }

    public void setRuc(String ruc) {
        this.ruc = ruc;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }
}
