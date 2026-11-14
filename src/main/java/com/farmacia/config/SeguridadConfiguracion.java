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
        public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
                return new org.springframework.security.core.session.SessionRegistryImpl();
        }

        @Bean
        public org.springframework.security.web.session.HttpSessionEventPublisher httpSessionEventPublisher() {
                return new org.springframework.security.web.session.HttpSessionEventPublisher();
        }

        @Bean
        public Clock clock() {
                return Clock.systemDefaultZone();
        }

        @Bean
        @Order(1)
        @SuppressWarnings("java:S4502") // CSRF deshabilitado de forma segura: la API REST es stateless y autentica con HTTP Basic sin cookies de sesion
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

        /**
         * Publica AuthorizationDeniedEvent cuando una regla de rutas deniega el acceso; lo escucha
         * AuditoriaSeguridadOyente (spec 014, CA-2.4).
         */
        @Bean
        public org.springframework.security.authorization.AuthorizationEventPublisher publicadorEventosAutorizacion(
                        org.springframework.context.ApplicationEventPublisher publicador) {
                return new org.springframework.security.authorization.SpringAuthorizationEventPublisher(publicador);
        }

        @Bean
        @Order(2)
        public SecurityFilterChain securityFilterChain(HttpSecurity http)
                        throws Exception {

                http
                                .authorizeHttpRequests(autorizacion -> autorizacion
                                                .requestMatchers("/css/**", "/js/**", "/images/**", "/error", "/favicon.ico").permitAll()

                                                // Inicio: redirige a la pantalla principal (spec 013, CA-1.4)
                                                .requestMatchers(HttpMethod.GET, "/")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR", "ALMACENERO", "COMPRAS")

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

                                                // Existencias iniciales (spec 006): solo ADMINISTRADOR y ALMACENERO
                                                .requestMatchers("/inventario/existencias-iniciales")
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

                                                .requestMatchers(
                                                                HttpMethod.GET,
                                                                "/ventas/nueva",
                                                                "/ventas/preparacion")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.POST, "/ventas/preparacion", "/ventas/preparacion/deshacer")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.DELETE, "/ventas/preparacion/{idMedicamento:[0-9]+}")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                // Gestión de usuarios (spec 007): solo ADMINISTRADOR
                                                .requestMatchers("/usuarios", "/usuarios/**")
                                                .hasRole("ADMINISTRADOR")

                                                // Reportes de gestión (spec 008): solo ADMINISTRADOR
                                                .requestMatchers("/reportes", "/reportes/**")
                                                .hasRole("ADMINISTRADOR")

                                                // Auditoría (spec 014): solo ADMINISTRADOR
                                                .requestMatchers(HttpMethod.GET, "/auditoria", "/auditoria.csv")
                                                .hasRole("ADMINISTRADOR")

                                                // Historial general de ventas (spec 005): solo ADMINISTRADOR
                                                .requestMatchers(HttpMethod.GET, "/ventas")
                                                .hasRole("ADMINISTRADOR")

                                                // Mis ventas para el vendedor (spec 005, US-4): solo VENDEDOR
                                                .requestMatchers(HttpMethod.GET, "/ventas/mias")
                                                .hasRole("VENDEDOR")

                                                .requestMatchers(HttpMethod.PUT, "/ventas/preparacion/{idMedicamento:[0-9]+}/descuento")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.POST, "/ventas/confirmar")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")
                                                .requestMatchers(HttpMethod.GET, "/ventas/{idVenta:[0-9]+}")
                                                .hasAnyRole("ADMINISTRADOR", "VENDEDOR")

                                                // Seguridad por defecto (spec 013): toda ruta no declarada se deniega.
                                                // Una ruta nueva debe tener su regla aquí y su caso en MatrizRutasTest.
                                                .anyRequest().denyAll())
                                .formLogin(formulario -> formulario
                                                .loginPage("/login")
                                                .loginProcessingUrl("/login")
                                                .defaultSuccessUrl("/medicamentos", true)
                                                .failureUrl("/login?error")
                                                .permitAll())
                                .logout(salida -> salida
                                                .logoutSuccessUrl("/login?logout")
                                                .permitAll())
                                .sessionManagement(sesion -> sesion
                                                .maximumSessions(-1)
                                                .sessionRegistry(sessionRegistry())
                                                .expiredUrl("/login?desactivado"));

                return http.build();
        }
}
