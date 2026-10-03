# API de medicamentos: pruebas con Postman

La interfaz web sigue disponible. Esta API responde JSON y usa la misma base de datos.
Para probarla localmente, inicia la aplicacion y usa `http://localhost:8080` como base.

## Autenticacion

En Postman, selecciona **Authorization > Basic Auth** e ingresa el usuario y la
contrasena de una cuenta activa. Todos los roles pueden consultar medicamentos;
solo el administrador puede crearlos, editarlos y desactivarlos.

No guardes contrasenas en la coleccion de Postman ni uses las cuentas de
demostracion publicas en un despliegue de internet. Fuera de tu equipo, usa HTTPS.

## Consultar: GET

- `GET /api/medicamentos`: lista los medicamentos activos.
- `GET /api/medicamentos?activo=false`: lista los inactivos.
- `GET /api/medicamentos/1`: obtiene un medicamento por su ID, incluso si esta inactivo.

## Crear: POST

Envia `POST /api/medicamentos` con **Body > raw > JSON**:

```json
{
  "codigo": "MED-POSTMAN-01",
  "nombre": "Medicamento de prueba",
  "concentracion": "500 mg",
  "presentacion": "Caja de 10 unidades",
  "unidadControl": "CAJA",
  "precioVenta": 12.50,
  "stockMinimo": 5
}
```

La respuesta es `201 Created`; conserva el `idMedicamento` recibido para los
siguientes pasos. El codigo debe ser unico. La otra unidad admitida es `FRASCO`.

## Editar: PUT

Envia `PUT /api/medicamentos/{idMedicamento}` con **Body > raw > JSON** y todos
los campos del ejemplo anterior. Cambia, por ejemplo, el nombre o el precio.
La respuesta es `200 OK` con el medicamento actualizado.

## Desactivar: DELETE

Envia `DELETE /api/medicamentos/{idMedicamento}`. La respuesta es
`204 No Content`. Esta operacion no borra la fila: cambia `activo` a `false`
para conservar el historial. Compruebalo con `GET /api/medicamentos/{idMedicamento}`.

## Respuestas de error habituales

| Codigo | Significado |
| --- | --- |
| `400` | Datos invalidos o JSON incorrecto. |
| `401` | Faltan credenciales o son incorrectas. |
| `403` | El rol no tiene permiso para esa operacion. |
| `404` | No existe ese ID. |
| `409` | El codigo del medicamento ya existe. |

Usa un registro de prueba nuevo; no desactives medicamentos que tus companeros
necesitan para su demostracion.
