# Guía Maestra del Entorno Docker de Desarrollo Local — Botica San Miguel

Esta guía detalla la arquitectura, puesta en marcha, flujo de desarrollo en vivo (**Hot Reload / LiveReload**), compilación incremental automática, depuración remota y verificación con **Playwright** del sistema web **Botica San Miguel (Farmacia AED)** en Docker.

---

## 1. Arquitectura de Contenedores de Desarrollo

El entorno separa la persistencia, el backend y el vigilante de estilos en 3 contenedores coordinados sobre una red interna `farmacia-dev-network`:

| Servicio | Contenedor | Imagen Base | Puertos Host : Contenedor | Descripción |
| :--- | :--- | :--- | :--- | :--- |
| **`database`** | `farmacia-dev-database` | `postgres:16-alpine` | **`127.0.0.1:5433:5432`** | Motor relacional PostgreSQL con persistencia en volumen nombrado `farmacia_db_dev_data` y healthcheck (`pg_isready`). Expuesto exclusivamente en loopback local (`127.0.0.1`) en el puerto host `5433` para evitar conflicto con instancias en `5432` y bloquear accesos desde redes externas/WiFi. |
| **`backend`** | `farmacia-dev-backend` | `eclipse-temurin:25-jdk` | **`127.0.0.1:8080:8080`**<br>**`127.0.0.1:5005:5005`**<br>**`127.0.0.1:35729:35729`** | Servidor Spring Boot 4.1.2 sobre Java 25 con perfil `dev`. Supervisado por `entrypoint-dev.sh` con manejo de señales `SIGTERM`/`SIGINT`, watcher incremental de Java (`dev-java-watcher.sh`), Spring Boot DevTools coordinado con archivo trigger, depuración remota JDWP (5005) y servidor WebSocket LiveReload (35729), todos confinados estrictamente a loopback local (`127.0.0.1`). |
| **`frontend-watcher`** | `farmacia-dev-frontend-watcher` | `node:22-alpine` | Interno | Vigilante que ejecuta Tailwind CLI en modo `--watch --poll` con polling habilitado para sincronización instantánea en Windows 11 / WSL2. |

---

## 2. Puesta en Marcha Rápida

### Opción A: Script ergonómico en Windows PowerShell (`docker-dev.ps1`)
```powershell
# Iniciar todos los servicios en segundo plano y construir imágenes
.\docker-dev.ps1 up

# Ver estado de los contenedores
.\docker-dev.ps1 status

# Ver logs en vivo (todos o de un servicio específico)
.\docker-dev.ps1 logs
.\docker-dev.ps1 logs backend
.\docker-dev.ps1 logs frontend-watcher

# Ejecutar la suite de pruebas E2E de Playwright
.\docker-dev.ps1 test

# Detener los contenedores (preservando datos de la base de datos)
.\docker-dev.ps1 down
```

### Opción B: Script en Linux / macOS / WSL2 (`docker-dev.sh`)
```bash
./docker-dev.sh up
./docker-dev.sh status
./docker-dev.sh logs backend
./docker-dev.sh test
./docker-dev.sh down
```

### Opción C: Comandos directos con Docker Compose
```powershell
# Levantar servicios
docker compose -f docker-compose.dev.yml up -d --build

# Consultar estado
docker compose -f docker-compose.dev.yml ps

# Ejecutar tests E2E locales
npm run test:e2e

# Detener servicios
docker compose -f docker-compose.dev.yml down
```

---

## 3. URLs, Servicios y Credenciales de Acceso

- **Aplicación Web:** [http://localhost:8080/acceso/login](http://localhost:8080/acceso/login)
- **Servidor LiveReload (WebSocket):** `ws://localhost:35729/livereload`
- **Depurador Remoto JVM (JDWP):** `localhost:5005`
- **PostgreSQL 16 (Acceso externo DBeaver, pgAdmin, DataGrip):**
  - **Host:** `localhost`
  - **Puerto:** **`5433`** *(puerto mapeado)*
  - **Base de Datos:** `farmacia_aed`
  - **Usuario:** `postgres`
  - **Contraseña:** `postgres`

### Cuentas Preconfiguradas (Perfil Demo):
| Rol | Usuario | Contraseña | Acceso Principal |
| :--- | :--- | :--- | :--- |
| **ADMINISTRADOR** | `admin_demo` | `AdminDemo2026!` | Acceso irrestricto, `/medicamentos`, `/usuarios`, `/reportes` |
| **VENDEDOR** | `vendedor_demo` | `VendedorDemo2026!` | `/ventas/nueva` (POS Caja FEFO), `/ventas/mias` |
| **ALMACENERO** | `almacen_demo` | `FarmaciaDemo2026!` | `/inventario`, `/ordenes-compra/{id}/recepcion`, `/alertas` |
| **COMPRAS** | `compras_demo` | `ComprasDemo2026!` | `/proveedores`, `/ordenes-compra` |

---

## 4. Ciclo de Desarrollo Asistido por IA

El entorno está optimizado para el ciclo:
$$\text{ESCRIBIR} \longrightarrow \text{DETECTAR} \longrightarrow \text{COMPILAR} \longrightarrow \text{RECARGAR} \longrightarrow \text{VERIFICAR} \longrightarrow \text{CORREGIR}$$

```
+--------------------------------------------------------------------------+
|  1. ESCRIBIR: Modificación en templates (.html), estilos (.css) o Java   |
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|  2. DETECTAR:                                                            |
|     - Tailwind Watcher detecta cambios en templates y CSS                |
|     - dev-java-watcher.sh detecta cambios en src/main/java (debounce 1s) |
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|  3. COMPILAR:                                                            |
|     - Tailwind CLI compila tailwind.css (< 400ms)                        |
|     - Maven compila incrementalmente: ./mvnw compile (< 2s)              |
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|  4. RECARGAR:                                                            |
|     - CSS: Hot Swap en caliente en el navegador (sin recargar página)    |
|     - HTML: Refresco automático vía sondeo de versión de plantilla       |
|     - Java: Spring DevTools detecta .reloadtrigger y reinicia en ~1s     |
|             El navegador se reconecta y refresca automáticamente         |
+--------------------------------------------------------------------------+
                                     |
                                     v
+--------------------------------------------------------------------------+
|  5. VERIFICAR:                                                           |
|     - Ejecución de pruebas E2E con Playwright (.\docker-dev.ps1 test)    |
|     - Inspección visual y capturas en e2e/evidencias/                    |
+--------------------------------------------------------------------------+
```

---

## 5. Recarga Automática Real del Navegador (LiveReload)

A diferencia de configuraciones convencionales que exigen F5 manual o extensiones de navegador, el entorno incorpora un cliente **LiveReload nativo e inteligente** en `src/main/resources/templates/fragmentos/head.html`:

1. **Inyección Condicional Segura:** Se activa **únicamente** cuando el perfil activo incluye `dev` (`th:if="${#arrays.contains(@environment.getActiveProfiles(), 'dev')}"`). En producción (Render / Cloud) no se inyecta ninguna línea de código.
2. **Hot CSS Reload sin refrescar la página:** Al modificar estilos o clases en `tailwind-input.css` o en plantillas HTML, el watcher regenera `tailwind.css`. El cliente en el navegador detecta el cambio en caliente y actualiza el elemento `<link rel="stylesheet">` agregando un timestamp (`?v=...`), logrando que los nuevos colores, espaciados y componentes se visualicen **instantáneamente sin perder el estado de formularios ni modales**.
3. **HTML Live Reload:** Cuando se modifica el DOM o el contenido de las plantillas Thymeleaf, el cliente detecta el cambio de versión y refresca automáticamente la pestaña activa.
4. **Resiliencia ante reinicios del backend:** Cuando Spring Boot se reinicia por cambios en código Java, el cliente LiveReload espera la reactivación del servidor y recarga la página en cuanto el backend responde.

---

## 6. Compilación Automática Incremental de Java y DevTools

El backend en `Dockerfile.dev` ejecuta `entrypoint-dev.sh`, el cual orquesta dos procesos:
1. **`spring-boot:run`** en primer plano sobre Java 25.
2. **`dev-java-watcher.sh`** en segundo plano.

### Cómo funciona el Java Watcher:
- Realiza un sondeo periódico (`polling` a 1.2s) de los timestamps de modificación de todos los archivos `.java` bajo `/app/src/main/java`.
- Aplica una ventana de **debounce de 1 segundo** para permitir que múltiples archivos guardados simultáneamente se compilen en una única pasada.
- Ejecuta `./mvnw compile -Dexec.skip=true` de forma incremental aprovechando la caché de dependencias en el volumen `farmacia_m2_cache`.
- **Coordinación con DevTools (`trigger-file`):**
  - Se configuró `spring.devtools.restart.trigger-file=.reloadtrigger` en `application-dev.properties`.
  - El watcher toca `target/classes/.reloadtrigger` **únicamente si la compilación de Maven fue 100% exitosa**.
  - Esto previene el problema clásico de DevTools reiniciando con clases a medio compilar.
- **Tolerancia a Fallos de Sintaxis:**
  - Si introduces un error de sintaxis en Java, Maven reporta el fallo detallado en el log del backend (`.\docker-dev.ps1 logs backend`).
  - El contenedor **permanece activo** y la aplicación sigue sirviendo peticiones con la última versión válida compilada.
  - Al corregir el error y guardar, el watcher detecta el cambio, recompila exitosamente y dispara el reinicio automático.

---

## 7. Verificación Automatizada con Microsoft Playwright

El proyecto cuenta con una suite completa de pruebas End-to-End con **Playwright** en `./e2e`, configurada contra `http://localhost:8080`:

### Ejecución de Pruebas:
```powershell
# Ejecución sin cabeza (headless, recomendada para CI y agentes)
.\docker-dev.ps1 test
# o bien:
npm run test:e2e

# Ejecución con interfaz gráfica de navegador
npm run test:e2e:headed

# Ver reporte interactivo HTML de Playwright
npm run test:e2e:report
```

### Pruebas Implementadas:
1. **`disponibilidad.spec.js`:** Verifica que el servidor responda código HTTP 200, título correcto, carga de estilos CSS, campos de formulario y ausencia de errores de consola.
2. **`autenticacion.spec.js`:** Valida el flujo completo de login con `admin_demo`, redirección hacia `/medicamentos`, renderizado del sidebar de navegación y captura de pantalla.
3. **`navegacion-modulos.spec.js`:** Valida la navegación a vistas clave como el Punto de Venta (`/ventas/nueva`) con su buscador interactivo y el tablero de alertas de inventario (`/alertas`).
4. **`livereload.spec.js`:** Verifica en un navegador real la conexión WebSocket con LiveReload (35729), la actualización en caliente de estilos CSS sin recargar la página y la recarga automática ante cambios en plantillas HTML.

### Evidencias Visuales:
Las capturas automáticas de las pruebas se generan en el directorio `e2e/evidencias/`:
- `01-login-cargado.png` — Pantalla de login inicial renderizada.
- `02-dashboard-admin.png` — Vista de catálogo tras autenticación exitosa.
- `03-preparacion-ventas.png` — Módulo POS Caja con buscador F2 y canasta.
- `04-alertas-inventario.png` — Tablero de alertas de stock y vencimiento FEFO.
- `05-livereload-verificado.png` — Comprobación visual de Hot Reload en tiempo real.

---

## 8. Depuración Remota JVM (Puerto 5005)

El backend expone el agente de depuración JDWP en el puerto `5005` mediante `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`.

### Configuración en IntelliJ IDEA:
1. **Run** > **Edit Configurations...** > **+** > **Remote JVM Debug**.
2. **Host:** `localhost` | **Port:** `5005` | **Use module classpath:** `farmacia-aed`.
3. Clic en **Debug**. Podrás colocar *breakpoints*, evaluar expresiones e inspeccionar hilos en vivo.

### Configuración en Visual Studio Code:
Agregar en `.vscode/launch.json`:
```json
{
  "type": "java",
  "name": "Attach to Docker JVM (Botica)",
  "request": "attach",
  "hostName": "localhost",
  "port": 5005
}
```

---

## 9. Persistencia de Datos y Operaciones de Mantenimiento

- **Persistencia de Base de Datos:** Los datos se resguardan en el volumen nombrado `farmacia_db_dev_data`. Al ejecutar `docker-dev.ps1 down` o `docker compose down`, las tablas, secuencias y datos demo **no se pierden**.
- **Caché de Dependencias Maven:** El volumen `farmacia_m2_cache` almacena el repositorio `.m2`, evitando descargar dependencias en cada reinicio.
- **Acceso directo a la consola PostgreSQL (`psql`):**
  ```powershell
  docker compose -f docker-compose.dev.yml exec database psql -U postgres -d farmacia_aed
  ```
- **Consola interactiva dentro del contenedor backend:**
  ```powershell
  .\docker-dev.ps1 shell backend
  ```
- **Reinicio selectivo de un contenedor:**
  ```powershell
  .\docker-dev.ps1 restart backend
  .\docker-dev.ps1 restart frontend-watcher
  ```

---

## 10. Solución de Problemas Frecuentes (Troubleshooting)

### 1. "Port 5432 is already in use"
- **Causa:** Tienes una instancia de PostgreSQL instalada en tu sistema host de Windows.
- **Solución:** Ya está previsto en la arquitectura; PostgreSQL en Docker está expuesto en el puerto host **`5433`**. Conéctate a `localhost:5433`.

### 2. "WebSocket connection to 'ws://localhost:35729/livereload' failed"
- **Causa:** El contenedor backend aún está inicializándose o el puerto 35729 no está expuesto.
- **Solución:** Verifica que el backend esté arriba con `.\docker-dev.ps1 status`. El cliente LiveReload reintentará conectarse automáticamente cada 3 segundos hasta que el backend esté listo.

### 3. Las modificaciones en Java no reinician la aplicación
- **Causa:** Hay un error de compilación de `javac` que impidió generar las clases y tocar `.reloadtrigger`.
- **Solución:** Revisa los logs con `.\docker-dev.ps1 logs backend`. Verás la traza exacta del compilador con el número de línea. Al corregir el archivo, el watcher recompilará automáticamente.

### 4. Permisos de scripts en Windows PowerShell
- **Causa:** La política de ejecución de scripts de PowerShell bloquea `.ps1`.
- **Solución:** Ejecuta en la terminal de PowerShell:
  ```powershell
  Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
  ```
