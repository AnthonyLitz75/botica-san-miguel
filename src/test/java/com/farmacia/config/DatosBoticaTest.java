package com.farmacia.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class DatosBoticaTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DatosBotica.class)
    static class TestConfig {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("CA-2.3: Falla el arranque si falta botica.ruc")
    void arranque_falla_sinRuc() {
        contextRunner
                .withPropertyValues(
                        "botica.nombre=Botica San Miguel",
                        "botica.direccion=Av. Universitaria 1234 - Lima"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ruc");
                });
    }

    @Test
    @DisplayName("CA-2.3: Falla el arranque con RUC de longitud distinta de 11")
    void arranque_falla_conRucInvalido() {
        contextRunner
                .withPropertyValues(
                        "botica.nombre=Botica San Miguel",
                        "botica.ruc=1234567890",
                        "botica.direccion=Av. Universitaria 1234 - Lima"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("ruc");
                });
    }

    @Test
    @DisplayName("CA-2.3: Falla el arranque si falta botica.nombre o botica.direccion")
    void arranque_falla_siFaltanOtrasPropiedades() {
        contextRunner
                .withPropertyValues(
                        "botica.ruc=20601234567"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("nombre");
                });
    }

    @Test
    @DisplayName("CA-2.1: Arranca exitosamente con propiedades válidas")
    void arranque_exitoso_conPropiedadesValidas() {
        contextRunner
                .withPropertyValues(
                        "botica.nombre=Botica San Miguel",
                        "botica.ruc=20601234567",
                        "botica.direccion=Av. Universitaria 1234 - Lima"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DatosBotica datos = context.getBean(DatosBotica.class);
                    assertThat(datos.getNombre()).isEqualTo("Botica San Miguel");
                    assertThat(datos.getRuc()).isEqualTo("20601234567");
                    assertThat(datos.getDireccion()).isEqualTo("Av. Universitaria 1234 - Lima");
                });
    }
}
