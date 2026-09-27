package com.farmacia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
public class SeguridadConfiguracion {

        @Bean
        public PasswordEncoder passwordEncoder() {
                return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                                .authorizeHttpRequests(autorizacion -> autorizacion
                                                .requestMatchers("/css/**", "/images/**", "/error").permitAll()

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/medicamentos",
                                                                "/medicamentos/{idMedicamento}/lotes")
                                                .hasAnyRole(
                                                                "ADMINISTRADOR",
                                                                "VENDEDOR",
                                                                "ALMACENERO",
                                                                "COMPRAS")

                                                .requestMatchers("/medicamentos", "/medicamentos/**")
                                                .hasRole("ADMINISTRADOR")

                                                .requestMatchers(HttpMethod.GET, "/proveedores")
                                                .hasAnyRole(
                                                                "ADMINISTRADOR",
                                                                "COMPRAS",
                                                                "ALMACENERO")

                                                .requestMatchers("/proveedores", "/proveedores/**")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS")

                                                .requestMatchers(HttpMethod.GET, "/movimientos", "/movimientos/**")
                                                .hasAnyRole("ADMINISTRADOR", "ALMACENERO")
                                                .requestMatchers("/movimientos", "/movimientos/**")
                                                .denyAll()

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/lotes/{idLote}/salida")
                                                .hasAnyRole("ADMINISTRADOR", "ALMACENERO")

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/lotes/{idLote}/salida")
                                                .hasAnyRole("ADMINISTRADOR", "ALMACENERO")

                                                .requestMatchers("/lotes/**")
                                                .denyAll()

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/ordenes-compra",
                                                                "/ordenes-compra/{idOrden:[0-9]+}")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS", "ALMACENERO")

                                                .requestMatchers(HttpMethod.GET, "/ordenes-compra/nuevo")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS")

                                                .requestMatchers(HttpMethod.POST, "/ordenes-compra")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS")

                                                .requestMatchers("/ordenes-compra", "/ordenes-compra/**")
                                                .denyAll()

                                                .anyRequest().authenticated())
                                .formLogin(formulario -> formulario
                                                .loginPage("/login")
                                                .loginProcessingUrl("/login")
                                                .defaultSuccessUrl("/medicamentos", true)
                                                .failureUrl("/login?error")
                                                .permitAll())
                                .logout(salida -> salida
                                                .logoutSuccessUrl("/login?logout")
                                                .permitAll());

                return http.build();
        }
}
