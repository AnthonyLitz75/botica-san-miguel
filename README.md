<div align="center">

<img src="src/main/resources/static/images/logo-botica-san-miguel-256.png" alt="Logo de Botica San Miguel" width="120" />

# Botica San Miguel

**Sistema web integral de inventario con rotación FEFO, compras, preparación de ventas y reportería para botica.**

![Java](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.2-6DB33F?logo=springboot&logoColor=white)
![Spring Security](https://img.shields.io/badge/Spring_Security-RBAC-6DB33F?logo=springsecurity&logoColor=white)
![Thymeleaf](https://img.shields.io/badge/Thymeleaf-SSR-005F0F?logo=thymeleaf&logoColor=white)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-3.4-06B6D4?logo=tailwindcss&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-compose_dev-2496ED?logo=docker&logoColor=white)

[Requisitos](#requisitos) ·
[Inicio rápido](#inicio-rápido-con-docker) ·
[Variables de entorno](#variables-de-entorno) ·
[Pruebas](#pruebas-y-calidad) ·
[Módulos y roles](#funcionalidades-por-rol) ·
[Seguridad](#seguridad-y-calidad)

</div>

---

## Descripción

Botica San Miguel es una aplicación web concebida para la operativa comercial y logística diaria de una farmacia independiente: catálogo de medicamentos con categorización jerárquica, control de lotes con fechas de vencimiento bajo regla FEFO (*First Expired, First Out*), existencias iniciales, salidas por merma, órdenes de compra con recepción por lotes, ventas en mostrador con reversión mediante pila (LIFO), comprobantes internos correlativos (B001), bitácora inmutable de auditoría y reportes gerenciales con exportación CSV bajo norma RFC 4180.

## Requisitos

Las versiones oficiales del entorno se extraen directamente de los descriptores de configuración del proyecto:

- **Java JDK 25:** Establecido en `pom.xml` (`<java.version>25</java.version>`). Requiere JDK completo, no solo JRE.
- **Spring Boot 4.1.2:** Gestionado en `pom.xml` (`<version>4.1.2</version>`).
- **PostgreSQL 16 (versión usada en las pruebas):** Servicio local en puerto 5432 o contenedor `postgres:16-alpine` definido en `docker-compose.dev.yml`.
- **Node.js (LTS 18+) y npm:** Requerido para la compilación de estilos mediante Tailwind CLI (`package.json`).
- **Docker y Docker Compose:** Para despliegue multi-contenedor y ejecución de pruebas de integración con Testcontainers.
- **Maven Wrapper:** Incluido en la raíz (`./mvnw` en Linux/macOS y `.\mvnw.cmd` en Windows); no es necesario instalar Maven manualmente.

## Inicio rápido con Docker

Para levantar la base de datos, el backend con recarga en vivo y el compilador continuo de Tailwind en un único paso:

```bash
docker compose -f docker-compose.dev.yml up -d --build
```

Comprueba que el entorno está operativo abriendo en tu navegador:
**[http://localhost:8080/acceso/login](http://localhost:8080/acceso/login)**

### Cuentas de demostración

Los datos demo se insertan automáticamente mediante la migración repetible `src/main/resources/db/demo/R__datos_demo.sql` únicamente cuando el perfil activo incluye `dev` o `demo`:

| Rol | Usuario | Contraseña | Pantalla de inicio |
| :--- | :--- | :--- | :--- |
| **ADMINISTRADOR** | `admin_demo` | `AdminDemo2026!` | Catálogo (`/medicamentos`), reportes y administración |
| **VENDEDOR** | `vendedor_demo` | `VendedorDemo2026!` | Caja POS (`/ventas/nueva`) y Mis ventas (`/ventas/mias`) |
| **ALMACENERO** | `almacen_demo` | `FarmaciaDemo2026!` | Inventario, recepciones (`/ordenes-compra`) y alertas |
| **COMPRAS** | `compras_demo` | `ComprasDemo2026!` | Proveedores y emisión de órdenes de compra |

> [!WARNING]
> Estas cuentas y contraseñas son de uso público para pruebas locales. Los datos de demostración solo se cargan en perfiles `dev` o `demo` cuando las tablas están vacías. **Nunca utilices estas credenciales en entornos de producción.**

Para detener los contenedores preservando los datos:

```bash
docker compose -f docker-compose.dev.yml down
```

## Ejecución sin Docker

Si dispones de PostgreSQL local (servicio en puerto 5432) y Node.js:

1. Crea una base de datos vacía (ej. `farmacia_aed`). Flyway aplicará automáticamente las migraciones versionadas (`V1` a `V5`).
2. Instala las dependencias de Node.js en la raíz:
   ```bash
   npm install
   ```
3. Arranca con datos de demostración precargados:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
   ```
   *(En Windows PowerShell: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"`)*

## Variables de entorno

La aplicación lee variables de entorno directamente del sistema operativo o de la línea de comandos, y **no carga archivos .env por sí sola**.

Configuraciones admitidas por la aplicación (`application.properties` y `application-dev.properties`):

| Variable | Propósito | Valor por defecto | Obligatoria en producción |
| :--- | :--- | :--- | :---: |
| `DB_URL` | URL JDBC de conexión a PostgreSQL | `jdbc:postgresql://localhost:5432/farmacia_aed` | Sí (si cambia host/puerto) |
| `DB_USER` | Usuario de conexión a la base de datos | `postgres` | Sí |
| `DB_PASSWORD` | Contraseña del usuario de base de datos | *(Sin valor por defecto en producción)* | **Sí** |
| `PORT` | Puerto de escucha HTTP del servidor web | `8080` | No |
| `SPRING_PROFILES_ACTIVE` | Perfiles de Spring Boot activos | *(Vacío en producción)* | No |
| `DIAS_POR_VENCER` | Ventana preventiva en días para alerta por vencer | `90` | No |
| `BOTICA_NOMBRE` | Razón social institucional para comprobantes | *(Ficticio en dev/demo)* | **Sí** |
| `BOTICA_RUC` | RUC institucional de 11 dígitos numéricos | *(Ficticio en dev/demo)* | **Sí** |
| `BOTICA_DIRECCION` | Dirección física de la botica para tickets | *(Ficticio en dev/demo)* | **Sí** |
| `CLIENTE_FRECUENTE_MINIMO_COMPRAS` | Umbral mínimo para badge de Cliente Frecuente | `10` | No |

> [!NOTE]
> En los perfiles `dev` y `demo`, los tickets y reportes cargan datos institucionales ficticios de prueba. En el perfil estándar de producción, la aplicación valida estrictamente la presencia de `BOTICA_NOMBRE`, `BOTICA_RUC` (11 dígitos) y `BOTICA_DIRECCION`, deteniendo el arranque si alguna de ellas falta.

## Notas de despliegue en producción

Para despliegues productivos (Render, AWS, VPS o contenedores aislados):

1. **Cookie de sesión segura:** Al publicar bajo HTTPS, habilita `SERVER_SERVLET_SESSION_COOKIE_SECURE=true` para marcar `JSESSIONID` con la bandera `Secure`.
2. **Proxy inverso confiable:** Detrás de un terminador TLS o balanceador (Nginx, Traefik, Cloudflare), configura `SERVER_FORWARD_HEADERS_STRATEGY=framework` para interpretar `X-Forwarded-For` y registrar la IP real del cliente en la bitácora de auditoría.
3. **Perfil de producción limpio:** No actives los perfiles `dev` ni `demo`. En producción solo se ejecutan las migraciones canónicas de `src/main/resources/db/migration/` (`V1__` a `V5__`).
4. **Rotación de secretos:** Configura credenciales robustas mediante `DB_PASSWORD` y asegúrate de cambiar o no cargar contraseñas demo.

## Pruebas y calidad

El repositorio cuenta con validación automatizada en múltiples niveles:

```bash
# 1. Compilación de estilos CSS minificados con Tailwind
npm ci
npm run build:css

# 2. Suite de pruebas backend (Unitarias, Controladores y Persistencia con Testcontainers)
./mvnw test

# 3. Suite de pruebas funcionales y de accesibilidad End-to-End (Playwright)
npx playwright test
```

- **Pruebas en Maven:** **1080 pruebas automatizadas** en verde. JaCoCo exige y verifica umbrales mínimos de cobertura del **85% en instrucciones** y **70% en ramas** (valores reales actuales: **90%** en instrucciones y **71%** en ramas).
- **Pruebas en Playwright:** **200 pruebas en 34 archivos** (`npm run test:e2e`). Requieren la aplicación en funcionamiento en `http://localhost:8080`.
- **Scripts de Node (`package.json`):**
  - `npm run build:css`: Compila y minifica `tailwind.css`.
  - `npm run watch:css`: Compilador en modo escucha para desarrollo en vivo.
  - `npm run test:e2e`: Ejecuta Playwright en modo headless.
  - `npm run test:e2e:headed`: Ejecuta Playwright con interfaz de navegador visible.
  - `npm run test:e2e:report`: Muestra el reporte interactivo HTML de Playwright.

> [!IMPORTANT]
> Las pruebas E2E crean registros en la base de datos de desarrollo. Para reiniciar el entorno Docker con una base completamente limpia antes de una corrida E2E, ejecuta:  
> `docker compose -f docker-compose.dev.yml down -v` seguido de `up -d --build`.  
> *(Ten en cuenta que `down -v` elimina los volúmenes nombrados y los datos acumulados).*

## Funcionalidades por rol

El sistema implementa control de acceso basado en roles (RBAC) con seguridad por defecto:

| Módulo / Funcionalidad | ADMINISTRADOR | ALMACENERO | COMPRAS | VENDEDOR |
| :--- | :---: | :---: | :---: | :---: |
| **Catálogo de Medicamentos** (`/medicamentos`) | Total | Solo lectura | Solo lectura | Solo lectura |
| **Categorías en Árbol N-ario** (`/categorias`) | Total | Solo lectura | Solo lectura | Solo lectura |
| **Lotes y Rotación FEFO** (`/medicamentos/{id}/lotes`) | Total | Total | Solo lectura | Solo lectura |
| **Kardex de Movimientos** (`/movimientos`) | Total | Total | No | No |
| **Existencias Iniciales** (`/inventario/existencias-iniciales`) | Total | Total | No | No |
| **Salidas y Mermas de Lotes** (`/salidas/nueva`) | Total | Total | No | No |
| **Gestión de Clientes** (`/clientes`) | Total | No | No | Total |
| **Gestión de Proveedores** (`/proveedores`) | Total | Solo lectura | Total | No |
| **Órdenes de Compra (Emisión/Cancelación)** (`/ordenes-compra`) | Total | Solo lectura | Total | No |
| **Recepción de Compras por Lote** (`/ordenes-compra/{id}/recepcion`) | Total | Total | No | No |
| **Caja POS y Venta FEFO** (`/ventas/nueva`) | Total | No | No | Total |
| **Comprobantes Internos** (`/ventas/{id}`) | Todas | No | No | Solo propias |
| **Historial General de Ventas** (`/ventas`) | Total | No | No | No |
| **Mis Ventas del Mes** (`/ventas/mias`) | No | No | No | Total |
| **Tablero de Alertas** (`/alertas`) | Total | Total | Total | Total |
| **Reportes Gerenciales y CSV** (`/reportes`) | Total | No | No | No |
| **Bitácora de Auditoría Inmutable** (`/auditoria`) | Total | No | No | No |
| **Gestión de Usuarios** (`/usuarios`) | Total | No | No | No |

## Seguridad y calidad

- **Seguridad por Defecto:** Todas las rutas web y API deniegan el acceso por defecto (HTTP 403) a menos que estén explícitamente autorizadas en `SeguridadConfiguracion.java`.
- **Protección CSRF:** Activa en todas las operaciones mutativas del cliente web.
- **Cookies de Sesión:** Protegidas con banderas `HttpOnly` y atributo `SameSite=Lax`.
- **Cabeceras HTTP Defensivas:** Incluye `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` y `Referrer-Policy: strict-origin-when-cross-origin`.
- **Auditoría Transaccional:** Registro inmutable en PostgreSQL (`auditoria`) con trigger que rechaza `UPDATE` y `DELETE`.
- **Accesibilidad (A11y):** Evaluada con `@axe-core/playwright` sin violaciones críticas en las pantallas principales.
- **Limitación Conocida:** No se aplica cabecera `Content-Security-Policy` (CSP) estricta debido al uso de scripts y estilos en línea en fragmentos Thymeleaf; su migración hacia nonces dinámicos está catalogada como mejora futura.

## Estructura del proyecto

La arquitectura de estilos desacopla la entrada modular de Tailwind CSS:
- `src/main/resources/static/css/tailwind-input.css` actúa como manifiesto maestro e importa los estilos propios estructurados en `base/`, `layout/`, `componentes/` y `modulos/`.
- La compilación genera el archivo minificado `src/main/resources/static/css/tailwind.css`.
- Los archivos `estilos.css` e `inventario.css` se mantienen únicamente como stubs de compatibilidad hacia atrás que redirigen a `tailwind.css`.

```text
botica-san-miguel/
├── src/
│   ├── main/
│   │   ├── java/com/farmacia/
│   │   │   ├── config/        # Seguridad web/API, sesiones y navegación compartida
│   │   │   ├── controller/    # Controladores MVC y REST
│   │   │   ├── service/       # Lógica de negocio, FEFO, transacciones y auditoría
│   │   │   ├── repository/    # Consultas parametrizadas JDBC directas
│   │   │   ├── estructuras/   # ListaEnlazada, PilaDeshacer, ColaPrioridadLotes, ArbolCategorias
│   │   │   ├── model/         # Entidades del dominio
│   │   │   └── dto/           # Formularios y transferencia de datos
│   │   └── resources/
│   │       ├── templates/     # Vistas y fragmentos Thymeleaf
│   │       ├── static/        # CSS compilado, JS sin frameworks, imágenes corporativas
│   │       ├── db/migration/  # Migraciones Flyway del esquema (V1__ a V5__)
│   │       └── db/demo/       # Datos ficticios de prueba (R__datos_demo.sql)
│   └── test/java/             # 1080 pruebas unitarias, de integración y Testcontainers
├── e2e/                       # 200 pruebas automatizadas Playwright en 34 archivos
├── POSTMAN/                   # Colección oficial de endpoints REST
├── API_POSTMAN.md             # Guía de uso de la API REST
├── DOCKER_DEV.md              # Guía avanzada del entorno Docker de desarrollo
├── GUIA_INSTALACION.txt       # Guía complementaria para Windows
├── docs/bocetos/DIFERENCIAS.md # Registro de alineación visual con bocetos
├── docker-compose.dev.yml     # Orquestación de desarrollo (PostgreSQL 16, Spring Boot, Watcher)
├── package.json               # Configuración de Tailwind CLI y Playwright
├── tailwind.config.js         # Tokens y paleta corporativa
└── pom.xml                    # Descriptores Maven, dependencias y reglas JaCoCo
```

## Documentación adicional

- [`GUIA_INSTALACION.txt`](GUIA_INSTALACION.txt): Pasos detallados para ejecución en Windows PowerShell con base local.
- [`DOCKER_DEV.md`](DOCKER_DEV.md): Arquitectura detallada de contenedores, LiveReload, depuración remota y watchers.
- [`API_POSTMAN.md`](API_POSTMAN.md): Catálogo de endpoints REST bajo `/api/**` con autenticación HTTP Basic.
- Colección Postman: Carpeta [`POSTMAN/`](POSTMAN/) con ejemplos interactivos de consulta y mutación.
- [`docs/bocetos/DIFERENCIAS.md`](docs/bocetos/DIFERENCIAS.md): Registro oficial de decisiones y alineación visual frente a bocetos de diseño.
