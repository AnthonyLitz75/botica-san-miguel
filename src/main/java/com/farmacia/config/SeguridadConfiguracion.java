package com.farmacia.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;
import java.time.Clock;

@Configuration
@EnableWebSecurity
public class SeguridadConfiguracion {

        @Bean
        public PasswordEncoder passwordEncoder() {
                return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }

        @Bean
        public Clock clock() {
                return Clock.systemDefaultZone();
        }

        @Bean
        @Order(1)
        public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http)
                        throws Exception {
                http
                                .securityMatcher("/api/**")
                                .authorizeHttpRequests(autorizacion -> autorizacion
                                                .requestMatchers(HttpMethod.GET, "/api/medicamentos",
                                                                "/api/medicamentos/*")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR", "ALMACENERO", "COMPRAS")
                                                .requestMatchers(HttpMethod.POST, "/api/medicamentos")
                                                .hasRole("ADMINISTRADOR")
                                                .requestMatchers(HttpMethod.PUT, "/api/medicamentos/*")
                                                .hasRole("ADMINISTRADOR")
                                                .requestMatchers(HttpMethod.DELETE, "/api/medicamentos/*")
                                                .hasRole("ADMINISTRADOR")
                                                .requestMatchers(HttpMethod.GET, "/api/clientes", "/api/clientes/*")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.POST, "/api/clientes")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.PUT, "/api/clientes/*")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.GET, "/api/alertas", "/api/alertas/**")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS", "ALMACENERO", "VENDEDOR")
                                                .anyRequest().denyAll())
                                .httpBasic(Customizer.withDefaults())
                                .sessionManagement(
                                                sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .csrf(csrf -> csrf.disable());

                return http.build();
        }

        @Bean
        @Order(2)
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
                                                                "/salidas/nueva",
                                                                "/lotes/{idLote}/salida")
                                                .hasAnyRole("ADMINISTRADOR", "ALMACENERO")

                                                .requestMatchers(
                                                                HttpMethod.POST,
                                                                "/salidas/nueva",
                                                                "/lotes/{idLote}/salida")
                                                .hasAnyRole("ADMINISTRADOR", "ALMACENERO")

                                                .requestMatchers("/lotes/**")
                                                .denyAll()

                                                .requestMatchers(HttpMethod.GET, "/alertas")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS", "ALMACENERO", "VENDEDOR")

                                                .requestMatchers("/clientes", "/clientes/**")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/ordenes-compra",
                                                                "/ordenes-compra/{idOrden:[0-9]+}")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS", "ALMACENERO")

                                                .requestMatchers(HttpMethod.GET, "/ordenes-compra/nuevo")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS")

                                                .requestMatchers(HttpMethod.POST, "/ordenes-compra")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS")

                                                .requestMatchers(HttpMethod.GET, "/ordenes-compra/{idOrden:[0-9]+}/recepcion")
                                                .hasAnyRole("ADMINISTRADOR", "ALMACENERO")
                                                .requestMatchers(HttpMethod.POST, "/ordenes-compra/{idOrden:[0-9]+}/recepcion")
                                                .hasAnyRole("ADMINISTRADOR", "ALMACENERO")
                                                .requestMatchers(HttpMethod.POST, "/ordenes-compra/{idOrden:[0-9]+}/cancelar")
                                                .hasAnyRole("ADMINISTRADOR", "COMPRAS")

                                                .requestMatchers("/ordenes-compra", "/ordenes-compra/**")
                                                .denyAll()

                                                .requestMatchers(HttpMethod.GET, "/ventas/preparacion")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.POST, "/ventas/preparacion", "/ventas/preparacion/deshacer")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.DELETE, "/ventas/preparacion/{idMedicamento:[0-9]+}")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")

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
