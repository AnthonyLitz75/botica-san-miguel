# Botica San Miguel

Proyecto académico para el curso de Algoritmos y Estructura de Datos.

Sistema web de gestión de inventario y compras para una botica.
Se encuentra en desarrollo y no está preparado para uso en producción.

## Funciones implementadas

- Inicio y cierre de sesión.
- Control de acceso por roles.
- Gestión de medicamentos y proveedores.
- Consulta de lotes y existencias.
- Registro de salidas de inventario.
- Consulta de movimientos y sus detalles.
- Creación y consulta de órdenes de compra.

## Roles

- Administrador.
- Almacenero.
- Compras.
- Vendedor.

Las funciones disponibles dependen del rol del usuario.

## Tecnologías

- Java y Spring Boot.
- Spring Security.
- Thymeleaf.
- JDBC.
- PostgreSQL.
- HTML y CSS.

## Estado del proyecto

Primera entrega parcial. Algunas funciones están pendientes.

Este repositorio contiene el código fuente, no una aplicación
publicada en internet.

## Base de datos

La aplicación requiere PostgreSQL y las variables de entorno
DB_USER y DB_PASSWORD.

Los scripts de tablas y datos de demostración están en `scripts/`.
Consulta `GUIA_INSTALACION.txt` para instalar y ejecutar el proyecto.
