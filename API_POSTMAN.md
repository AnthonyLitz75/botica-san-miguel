# Guía de la API REST de Botica San Miguel: Pruebas con Postman

La aplicación web convive con una API RESTful que responde en formato JSON y opera sobre la misma base de datos relacional PostgreSQL. Para realizar pruebas locales, inicia la aplicación (`http://localhost:8080`).

---

## 1. Autenticación HTTP Basic

Todas las rutas bajo `/api/**` requieren autenticación **HTTP Basic**:
* En Postman, en la pestaña **Authorization**, selecciona el tipo **Basic Auth**.
* Ingresa el **Username** y **Password** de una cuenta activa con los permisos requeridos.
* Roles y credenciales de demostración local:
  * `admin_demo` / `AdminDemo2026!` (ADMINISTRADOR)
  * `vendedor_demo` / `VendedorDemo2026!` (VENDEDOR)
  * `almacen_demo` / `FarmaciaDemo2026!` (ALMACENERO)
  * `compras_demo` / `ComprasDemo2026!` (COMPRAS)

---

## 2. API de Medicamentos (`/api/medicamentos`)

* **Permisos:** Consulta (`GET`) disponible para todos los roles; Creación, edición y desactivación exclusiva para `ADMINISTRADOR`.

### Consultar Medicamentos
* `GET /api/medicamentos`: Lista todos los medicamentos activos.
* `GET /api/medicamentos?activo=false`: Lista medicamentos inactivos.
* `GET /api/medicamentos/{id}`: Obtiene el detalle de un medicamento específico por su ID.

### Crear Medicamento
* **Método:** `POST /api/medicamentos`
* **Body (raw JSON):**
  ```json
  {
    "codigo": "MED-POSTMAN-01",
    "nombre": "Amoxicilina 500mg",
    "concentracion": "500 mg",
    "presentacion": "Caja de 30 cápsulas",
    "unidadControl": "CAJA",
    "precioVenta": 18.50,
    "stockMinimo": 10
  }
  ```
* **Respuesta:** `201 Created` con el objeto registrado y cabecera `Location`.

### Editar Medicamento
* **Método:** `PUT /api/medicamentos/{id}`
* **Body (raw JSON):** Mismos campos que la creación con los valores actualizados.
* **Respuesta:** `200 OK`.

### Desactivar Medicamento (Baja Lógica)
* **Método:** `DELETE /api/medicamentos/{id}`
* **Respuesta:** `204 No Content`. Cambia `activo` a `false` preservando la integridad del historial.

---

## 3. API de Clientes (`/api/clientes`)

* **Permisos:** Disponible para `ADMINISTRADOR` y `VENDEDOR`.

### Consultar Clientes
* `GET /api/clientes`: Lista los clientes registrados.
* `GET /api/clientes?buscar=Carlos`: Búsqueda rápida por número de documento (DNI/RUC) o nombre.
* `GET /api/clientes/{id}`: Obtiene los datos del cliente por su ID interno.

### Registrar Cliente
* **Método:** `POST /api/clientes`
* **Body (raw JSON):**
  ```json
  {
    "tipoDocumento": "DNI",
    "numeroDocumento": "45678901",
    "nombreCompleto": "Carlos Sanchez Gomez",
    "telefono": "987654321",
    "correo": "carlos.sanchez@example.com"
  }
  ```
* **Validaciones:**
  * `tipoDocumento`: `DNI` (exactamente 8 dígitos numéricos) o `RUC` (exactamente 11 dígitos numéricos).
  * `numeroDocumento`: Obligatorio y único en el sistema.
  * `nombreCompleto`: Obligatorio, hasta 150 caracteres.
  * `telefono` y `correo`: Opcionales.
* **Respuesta:** `201 Created` con el cliente guardado y su ID asignado.

### Actualizar Cliente
* **Método:** `PUT /api/clientes/{id}`
* **Body (raw JSON):**
  ```json
  {
    "tipoDocumento": "DNI",
    "numeroDocumento": "45678901",
    "nombreCompleto": "Carlos Sanchez Gomez Actualizado",
    "telefono": "999888777",
    "correo": "carlos.nuevo@example.com"
  }
  ```
* **Respuesta:** `200 OK` con los datos actualizados.

---

## 4. API de Alertas de Inventario (`/api/alertas`)

* **Permisos:** Disponible para todos los roles autenticados (`ADMINISTRADOR`, `ALMACENERO`, `COMPRAS`, `VENDEDOR`).

### Panel Consolidado de Alertas
* `GET /api/alertas`
* **Respuesta:** `200 OK` con un objeto que agrupa:
  * `agotados`: Medicamentos activos con stock disponible igual a 0.
  * `stockBajo`: Medicamentos con existencias vigentes $\le$ stock mínimo.
  * `vencidos`: Lotes vencidos según la fecha actual del reloj del sistema.
  * `porVencer`: Lotes vigentes cuya fecha de vencimiento cae dentro del horizonte preventivo de 90 días (configurable con `farmacia.alertas.dias-por-vencer`).

### Sub-recursos Específicos
* `GET /api/alertas/stock`: Devuelve únicamente la lista de medicamentos en situación crítica (agotados y stock bajo).
* `GET /api/alertas/reposicion`: Devuelve medicamentos que requieren reposición calculada (restando del faltante las órdenes de compra en tránsito).
* `GET /api/alertas/vencidos`: Lista detallada de lotes vencidos con existencias físicas no dadas de baja.
* `GET /api/alertas/por-vencer`: Lista detallada de lotes por vencer en los próximos 90 días ordenados por fecha ascendente (FEFO).

---

## 5. Códigos de Respuesta HTTP y Manejo de Errores

| Código | Descripción | Causa habitual |
| :---: | :--- | :--- |
| **`200 OK`** | Operación exitosa | Consulta o actualización completada. |
| **`201 Created`** | Recurso creado | Alta de medicamento o cliente con cabecera `Location`. |
| **`204 No Content`** | Sin contenido | Desactivación lógica de un medicamento completada. |
| **`400 Bad Request`** | Solicitud inválida | JSON mal formado o incumplimiento de validaciones Jakarta (ej. DNI no numérico, precio negativo). |
| **`401 Unauthorized`** | No autenticado | Credenciales HTTP Basic ausentes o incorrectas. |
| **`403 Forbidden`** | Acceso denegado | El rol autenticado no cuenta con privilegios para el endpoint (ej. Vendedor intentando crear medicamentos). |
| **`404 Not Found`** | No encontrado | El ID solicitado no existe en la base de datos. |
| **`409 Conflict`** | Conflicto de unicidad | El código de medicamento o el número de documento de cliente ya se encuentra registrado. |
