# Registro de Diferencias de Diseño e Interfaz

Este documento registra discrepancias observadas entre las implementaciones funcionales y los bocetos de referencia (`docs/bocetos/png/` y archivos HTML de diseño), sirviendo como bitácora de alineación visual para fases de diseño dedicadas.

---

## Pendiente para spec 023

Alineación fina de tokens de color, tipografía y dimensiones detectados durante la revisión visual de la especificación 019 (CA-V.1 / T1911):

| Elemento / Componente | Selector CSS | Archivo(s) de Referencia | Valor Actual en App | Valor Esperado en Boceto (Spec 023) |
| :--- | :--- | :--- | :--- | :--- |
| **Tarjeta de Cliente (Caja POS):** Nombre del cliente | `.cli-info b`, `#nombre-cliente-mostrado` | `src/main/resources/templates/ventas/preparacion.html`, `src/main/resources/static/css/modulos/caja-pos.css` | Color: `#0f172a` (Slate 900) / `#1e2926` (`--texto-principal`)<br>Tamaño: `13.5px`<br>Peso: `700` | Color: `var(--ink)` (`#10201b`)<br>Tamaño: `13.5px`<br>Peso: `700`<br>*(heredado del `body` en `01-caja.html`)* |
| **Tarjeta de Cliente (Caja POS):** Subtítulo / DNI / Compras | `.cli-info .lbl`, `.cli .lbl`, `#doc-cliente-mostrado` | `src/main/resources/templates/ventas/preparacion.html`, `src/main/resources/static/css/modulos/caja-pos.css` | Color: `#64748b` (Slate 500)<br>Tamaño: `12px` | Color: `var(--ink-3)` (`#5b6d66`)<br>Tamaño: `11.5px`<br>*(regla `.lbl` en `01-caja.html`)* |
| **Badge Numérico Compras (Listado Clientes):** Caja numérica `.num` | `table tbody td .num`, `.num` | `src/main/resources/templates/clientes/listado.html`, `src/main/resources/static/css/componentes/badges.css` | Fondo: `#f1f5f9` (Slate 100) / `#f1f4f3`<br>Borde: `1px solid #dfe6e3` (`var(--line)`)<br>Radio: `8px`<br>Dimensiones: `min-width: 34px`, `height: 28px`<br>Peso: `700`<br>Color de texto: `#334155` (Slate 700) | Fondo: `#f1f4f3`<br>Borde: `1px solid #dfe6e3` (`var(--line)`)<br>Radio: `8px`<br>Tamaño mínimo: `34×28 px`<br>Peso: `700`<br>Texto: color base `var(--ink)` (`#10201b`)<br>*(regla `.num` en `10-clientes.html`)* |
| **Avatar con Iniciales (Listado Clientes):** Círculo `.ini` | `table tbody td .ini`, `.ini` | `src/main/resources/templates/clientes/listado.html`, `src/main/resources/static/css/componentes/badges.css` | Fondo: `#d3ebe3`<br>Texto: `#143a31`<br>Dimensiones: `34×34 px`<br>Radio: `50%`<br>Peso: `800`<br>Tamaño fuente: `12px` | Fondo: `var(--pine-100)` (`#d3ebe3`)<br>Texto: `var(--pine-800)` (`#143a31`)<br>Dimensiones: `34×34 px`<br>Radio: `50%`<br>Peso: `800`<br>Tamaño fuente: `12px`<br>*(regla `.ini` en `10-clientes.html`)* |

> **Nota:** La aplicación y unificación estricta de estos tokens se posterga formalmente para la **Spec 023 (Consolidación de Sistema de Diseño y Tokens)** para garantizar la consistencia global del framework CSS modular y Tailwind sin introducir cambios de estilo no planificados en la spec 019.
