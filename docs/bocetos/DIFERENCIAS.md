# Registro de Diferencias de Diseño e Interfaz — Botica San Miguel

Este documento registra el estado definitivo de alineación visual entre las implementaciones funcionales del sistema y los bocetos de referencia (`docs/bocetos/png/` y archivos HTML de diseño), tras la ejecución de la **Spec 023 (Alineación Visual y Consolidación de Sistema de Diseño)**.

---

## 1. Discrepancias Resueltas en la Spec 023

Durante la ejecución de la especificación 023 se unificaron y aplicaron estrictamente los tokens oficiales del sistema de diseño a todos los componentes y pantallas:

| Elemento / Componente | Selector CSS | Archivo(s) Modificado(s) | Resolución Aplicada |
| :--- | :--- | :--- | :--- |
| **Tarjeta de Cliente (Caja POS):** Nombre del cliente | `.cli-info b`, `#nombre-cliente-mostrado` | `templates/ventas/preparacion.html`, `static/css/modulos/caja-pos.css` | Color: `var(--ink)` (`#10201b`), tamaño: `13.5px`, peso: `700`. |
| **Tarjeta de Cliente (Caja POS):** Subtítulo / DNI / Compras | `.cli-info .lbl`, `.cli .lbl`, `#doc-cliente-mostrado` | `templates/ventas/preparacion.html`, `static/css/modulos/caja-pos.css` | Color: `var(--ink-3)` (`#5b6d66`), tamaño: `11.5px`. |
| **Selector Método de Pago (Caja POS):** Botones del selector `.seg div` | `#selector-metodo-pago button`, `.seg div` | `templates/ventas/preparacion.html`, `static/css/modulos/caja-pos.css` | Radio: `10px`, padding: `10px 6px`, fuente: `13px`, borde: `1px solid #cfd8d4`, peso: `700`. |
| **Badge Numérico Compras (Clientes):** Caja numérica `.num` | `table tbody td .num`, `.num` | `templates/clientes/listado.html`, `static/css/componentes/badges.css` | Fondo: `#f1f4f3`, borde: `1px solid #dfe6e3`, radio: `8px`, tamaño mínimo: `34×28 px`, peso: `700`, texto: `var(--ink)`. |
| **Avatar con Iniciales (Clientes):** Círculo `.ini` | `table tbody td .ini`, `.ini` | `templates/clientes/listado.html`, `static/css/componentes/badges.css` | Fondo: `var(--pine-100)` (`#d3ebe3`), texto: `var(--pine-800)` (`#143a31`), tamaño: `34×34 px`, radio: `50%`, peso: `800`, fuente: `12px`. |
| **Chips Método de Pago (Historial):** Estilo visual | `table tbody td .chip` | `templates/ventas/historial.html`, `static/css/componentes/badges.css` | Chip monocromático neutro: fondo `#eef2f0`, texto `var(--ink-2)` (`#33463f`), radio: `8px`, padding: `3px 9px`, peso: `600`. |
| **Tarjeta de Ticket Térmico:** Dimensiones y esquinas | `.ticket`, `.comprobante-ticket` | `static/css/modulos/ticket-print.css` | Ancho: `340px`, padding: `22px 20px`, fuente: `12.5px`, fondo esquina: `linear-gradient(135deg,transparent 8px,#fff 0)`, sombra: `0 10px 30px rgba(0,0,0,.15)`. |
| **Separador de Ticket Térmico:** Línea punteada | `.ticket hr` | `static/css/modulos/ticket-print.css` | Borde superior: `1px dashed #555555`, margen: `10px 0`. |
| **Detalle de Lote y Vencimiento (Ticket):** Color de texto secundario | `.ticket .font-mono` | `templates/ventas/comprobante.html` | Estilo: `color: #555555`, fuente monoespaciada heredada del ticket (`12.5px`). |
| **Línea PAGO (Ticket Térmico):** Tipografía y color | `.ticket .l`, `#metodo-pago-comprobante` | `templates/ventas/comprobante.html` | Fuente: `ui-monospace`, color `#111111` heredado de `.ticket`. |
| **Chips de Estado (Órdenes de Compra):** Filtros rápidos | `div[role="navigation"] a.btn` | `templates/ordenes-compra/listado.html` | Radio: `10px`, padding: `7px 14px`, tamaño fuente: `13.5px`, peso: `700`. Activo: `background: var(--pine-700)`, texto blanco; Inactivo: fondo blanco, texto `var(--ink-2)`. |
| **Razón Social de Proveedor (Órdenes de Compra):** | `span.nombre-proveedor` | `templates/ordenes-compra/listado.html` | Marcado con `font-bold` (`700`), color: `var(--ink)`. |
| **Badges de Resultado (Auditoría):** Píldoras de estado | `td.c span.badge`, `.t-ok`, `.t-rose`, `.t-amber` | `static/css/componentes/badges.css` | Radio: `999px`, padding: `3px 10px`, fuente: `11.5px`, peso: `800`, `letter-spacing: .02em`, punto `::before` de `6×6 px`. |
| **Badge ROL ADMINISTRADOR (Usuarios):** Clase y color | `span.badge.t-pur` | `templates/usuarios/listado.html`, `static/css/componentes/badges.css` | Clase `.t-pur` con fondo `#f3e8ff` y texto `#7e22ce`. |
| **Columna Estado en "Mis Ventas":** | `table thead th`, `table tbody td` | `templates/ventas/mis-ventas.html` | Se eliminó la columna fija de estado (siempre decía EMITIDA) homogeneizándola con `historial.html`. |

---

## 2. Diferencias Aceptadas (con Justificación Técnica)

Las siguientes diferencias entre la aplicación funcional y los bocetos estáticos se mantienen formalmente aceptadas por razones arquitectónicas, funcionales o de accesibilidad:

| Elemento / Pantalla | Valor en Boceto | Implementación Real | Justificación Técnica |
| :--- | :--- | :--- | :--- |
| **Botón de Exportación (Historial de Ventas, Boceto 09)** | Botón "Exportar CSV" (`.btn.s`) en la cabecera | Botón "Nueva venta" (`.btn.p`) | **El boceto muestra una función que no existe en este módulo.** La exportación de datos a CSV en ventas pertenece a reportes analíticos (`/reportes/ventas.csv`), mientras que en el flujo de mostrador el operador requiere acceso expedito para iniciar una nueva venta. |
| **Columnas Subtotal y Descuento (Historial de Ventas, Boceto 09)** | Omite desglose (salta directo de Pago a Total) | Columnas `Subtotal` y `Descuento` visibles en la tabla | **Decisión explícita del usuario y requerimiento contable:** Mantiene la trazabilidad y auditoría de los descuentos por línea aplicados en mostrador (RF-14/15). |
| **Gráfico de Ventas: Scroll interno responsivo (Reportes, Boceto 07)** | Contenedor rígido directo sin scroll | Contenedor `.grafico-scroll-contenedor` con `overflow-x: auto; max-width: 100%` | **Regla de Accesibilidad Fase D (H-01):** Garantiza que en pantallas ultra angostas (320 px) la página completa mantenga `scrollWidth <= innerWidth` sin scroll horizontal global. |
| **Gráfico de Ventas: Aviso para rangos >31 días (Reportes, Boceto 07)** | Muestra siempre 7 barras estáticas fijas | Banner de advertencia *"Acota el rango a 31 días para ver el gráfico"* si el rango excede 31 días | **Requerimiento Funcional (Spec 021, CA-1.5):** Previene la saturación visual y sobrecarga de procesamiento en servidor al consultar períodos extensos (ej. anuales). |
| **Gráfico de Ventas: Alturas dinámicas y fechas (Reportes, Boceto 07)** | Alturas estáticas en px (`85px`, `62px`) | Alturas dinámicas en porcentaje calculadas contra `maximoDia` con serie continua | **Datos reales variables del servidor:** Preserva la cronología completa de la serie de tiempo sin huecos ni saltos (Spec 021). |
| **Último Acceso en Usuarios (Gestión de Usuarios, Boceto 12)** | Texto simple `Hoy 10:30` | Celda con atributo accesible `title="dd/MM/yyyy HH:mm"` y texto relativo | **Criterio de Aceptación (Spec 020, CA-2.2):** Permite inspeccionar fecha y hora exacta sin saturar la tabla visualmente. |
| **Acciones de Fila en Historial de Ventas (Boceto 09)** | Dos botones de icono `.ibtn` (ojo e impresora) | Enlace `.lnk` "Comprobante" hacia `/ventas/{id}` | **Arquitectura de Vistas (Spec 016/017):** La vista `/ventas/{id}` centraliza la visualización y la impresión térmica formal de 80 mm con `@media print`, garantizando consistencia. |
