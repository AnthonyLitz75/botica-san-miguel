package com.farmacia.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizadorContactoTest {

    @Test
    @DisplayName("Normalizar correo convierte a minusculas, hace trim y vacios/blancos a null")
    void normalizarCorreo() {
        assertThat(NormalizadorContacto.normalizarCorreo(null)).isNull();
        assertThat(NormalizadorContacto.normalizarCorreo("")).isNull();
        assertThat(NormalizadorContacto.normalizarCorreo("   ")).isNull();
        assertThat(NormalizadorContacto.normalizarCorreo("  Juan@Gmail.com ")).isEqualTo("juan@gmail.com");
        assertThat(NormalizadorContacto.normalizarCorreo("VENTAS@LABORATORIO.COM.PE")).isEqualTo("ventas@laboratorio.com.pe");
    }

    @Test
    @DisplayName("Normalizar telefono conserva solo digitos, quita prefijo 51 de 11 digitos y vacios/blancos a null")
    void normalizarTelefono() {
        assertThat(NormalizadorContacto.normalizarTelefono(null)).isNull();
        assertThat(NormalizadorContacto.normalizarTelefono("")).isNull();
        assertThat(NormalizadorContacto.normalizarTelefono("   ")).isNull();
        assertThat(NormalizadorContacto.normalizarTelefono("abc--")).isNull();
        assertThat(NormalizadorContacto.normalizarTelefono("+51 958 145 341")).isEqualTo("958145341");
        assertThat(NormalizadorContacto.normalizarTelefono("51958145341")).isEqualTo("958145341");
        assertThat(NormalizadorContacto.normalizarTelefono("(01) 456-7890")).isEqualTo("014567890");
        assertThat(NormalizadorContacto.normalizarTelefono("987 654 321")).isEqualTo("987654321");
    }
}
