# Registro de Diferencias de Diseño e Interfaz

Este documento registra discrepancias observadas entre las implementaciones funcionales y los bocetos de referencia (`docs/bocetos/png/` y archivos HTML de diseño), sirviendo como bitácora de alineación visual para fases de diseño dedicadas.

---

## Pendiente para spec 023

Alineación fina de tokens de color detectados durante la revisión visual de la especificación 019 (CA-V.1 / T1911):

| Elemento / Componente | Selector CSS | Archivo(s) de Referencia | Valor Actual en App | Valor Esperado en Boceto (Spec 023) |
| :--- | :--- | :--- | :--- | :--- |
| **Tarjeta de Cliente (Caja POS):** Nombre del cliente | `.cli-info b`, `#nombre-cliente-mostrado` | `src/main/resources/templates/ventas/preparacion.html` | `#0f172a` (Slate 900) | `ink` (`#10201b`) |
| **Tarjeta de Cliente (Caja POS):** Subtítulo / DNI / Compras | `.cli-info .lbl`, `#doc-cliente-mostrado` | `src/main/resources/templates/ventas/preparacion.html`, `src/main/resources/static/js/modulos/pos-buscador-cli.js` | `#64748b` (Slate 500) | `ink-3` (`#5b6d66`) |
| **Badge Numérico Compras (Listado Clientes):** Fondo y texto | `table tbody td .num` | `src/main/resources/templates/clientes/listado.html` | Fondo: `#f1f5f9` (Slate 100)<br>Texto: `#334155` (Slate 700) | Tonos institucionales `pine` / `ink`:<br>Fondo: `pine-50` (`#e9f4f0`)<br>Texto: `pine-700` (`#166a55`) o `ink` (`#10201b`) |

> **Nota:** La aplicación de estos tokens se posterga formalmente para la **Spec 023 (Consolidación de Sistema de Diseño y Tokens)** para mantener estabilidad en la suite CSS y no introducir cambios de estilo no planificados en la spec 019.
