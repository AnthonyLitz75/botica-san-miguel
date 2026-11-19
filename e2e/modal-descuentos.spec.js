import { test, expect } from '@playwright/test';

test.describe('Suite E2E: Modal de Descuentos en Caja POS (AyE)', () => {

  test.beforeEach(async ({ page }) => {
    // 1. Autenticación como admin_demo
    await page.goto('/acceso/login');
    const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
    const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => url.pathname.includes('/medicamentos'));

    // 2. Navegar a la pantalla de Caja POS
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();

    // 3. Aislamiento: si la canasta contiene líneas de una prueba anterior, cancelarla para empezar limpios
    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    if (await btnCancelarVenta.isEnabled()) {
      await btnCancelarVenta.click();
      await expect(page.locator('#estado-vacio')).toBeVisible();
    }

    // 4. Agregar un medicamento con stock para habilitar la canasta (Paracetamol 500 mg, P.U. S/ 8.50)
    const inputBuscador = page.locator('#buscar-caja');
    await inputBuscador.fill('para');
    const primeraOpcion = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(primeraOpcion).toBeVisible();
    await primeraOpcion.click();

    // Verificar que la canasta contenga 1 producto
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#contador-canasta')).toContainText('1 producto');
  });

  // ==========================================================================
  // A. Apertura y presentación
  // ==========================================================================
  test('A. debe abrir el modal desde la canasta y presentar controles en modalidad Monto por defecto', async ({ page }) => {
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await expect(btnEditarDesc).toBeVisible();
    await btnEditarDesc.click();

    const backdrop = page.locator('#modal-descuento-backdrop');
    await expect(backdrop).toBeVisible();

    // Verificar encabezado y controles principales
    await expect(page.locator('#modal-descuento-titulo')).toHaveText('Descuentos');
    await expect(page.locator('.modal-desc-subtitulo')).toHaveText('Aplica un descuento a tu venta');
    await expect(page.locator('#modal-descuento-producto-nombre')).toHaveText('Paracetamol');
    await expect(page.locator('#modal-descuento-cerrar')).toBeVisible();

    // Pestaña Monto activa por defecto
    const tabMonto = page.locator('#tab-descuento-monto');
    const tabPorcentaje = page.locator('#tab-descuento-porcentaje');
    await expect(tabMonto).toHaveClass(/tab-activa/);
    await expect(tabPorcentaje).toHaveClass(/tab-inactiva/);

    // Campo de entrada y sufijo
    await expect(page.locator('#label-input-descuento')).toHaveText('Monto del descuento (S/)');
    await expect(page.locator('#indicador-unidad-descuento')).toHaveText('S/');
    await expect(page.locator('#input-valor-descuento')).toHaveValue('0.00');

    // Botones de acción
    await expect(page.locator('#btn-cancelar-descuento')).toBeVisible();
    await expect(page.locator('#btn-aplicar-descuento')).toBeVisible();

    // Captura de evidencia
    await page.screenshot({ path: 'e2e/evidencias/03d-modal-descuento-monto.png' });
  });

  // ==========================================================================
  // B. Cancelación
  // ==========================================================================
  test('B. debe cancelar la operación sin modificar el descuento ni emitir peticiones PUT', async ({ page }) => {
    let peticionPutEmitida = false;
    page.on('request', (req) => {
      if (req.method() === 'PUT' && req.url().includes('/descuento')) {
        peticionPutEmitida = true;
      }
    });

    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    const textoOriginal = await btnEditarDesc.textContent();

    await btnEditarDesc.click();
    const backdrop = page.locator('#modal-descuento-backdrop');
    await expect(backdrop).toBeVisible();

    // Escribir un monto de descuento
    const inputDescuento = page.locator('#input-valor-descuento');
    await inputDescuento.fill('2.50');

    // Pulsar Cancelar
    const btnCancelar = page.locator('#btn-cancelar-descuento');
    await btnCancelar.click();

    // Comprobar que el modal se cerró
    await expect(backdrop).toBeHidden();

    // Comprobar que el texto de la canasta no cambió y no hubo petición PUT
    await expect(btnEditarDesc).toHaveText(textoOriginal || '—');
    expect(peticionPutEmitida).toBe(false);
  });

  // ==========================================================================
  // C. Cierre mediante X
  // ==========================================================================
  test('C. debe cerrar mediante el botón X sin aplicar cambios a la canasta', async ({ page }) => {
    let peticionPutEmitida = false;
    page.on('request', (req) => {
      if (req.method() === 'PUT' && req.url().includes('/descuento')) {
        peticionPutEmitida = true;
      }
    });

    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    const textoOriginal = await btnEditarDesc.textContent();

    await btnEditarDesc.click();
    const backdrop = page.locator('#modal-descuento-backdrop');
    await expect(backdrop).toBeVisible();

    await page.locator('#input-valor-descuento').fill('3.00');

    // Pulsar botón X
    const btnCerrar = page.locator('#modal-descuento-cerrar');
    await btnCerrar.click();

    await expect(backdrop).toBeHidden();
    await expect(btnEditarDesc).toHaveText(textoOriginal || '—');
    expect(peticionPutEmitida).toBe(false);
  });

  // ==========================================================================
  // D. Cierre mediante Escape
  // ==========================================================================
  test('D. debe cerrar con la tecla Escape sin modificar el estado', async ({ page }) => {
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await btnEditarDesc.click();

    const backdrop = page.locator('#modal-descuento-backdrop');
    await expect(backdrop).toBeVisible();

    await page.locator('#input-valor-descuento').fill('4.00');

    // Pulsar tecla Escape
    await page.keyboard.press('Escape');

    await expect(backdrop).toBeHidden();
    await expect(btnEditarDesc).toHaveText('—');
  });

  // ==========================================================================
  // E. Cierre mediante backdrop
  // ==========================================================================
  test('E. debe cerrar al hacer clic en el backdrop exterior', async ({ page }) => {
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await btnEditarDesc.click();

    const backdrop = page.locator('#modal-descuento-backdrop');
    await expect(backdrop).toBeVisible();

    // Clic en la esquina superior izquierda del backdrop (fuera de la tarjeta del modal)
    await backdrop.click({ position: { x: 10, y: 10 } });

    await expect(backdrop).toBeHidden();
    await expect(btnEditarDesc).toHaveText('—');
  });

  // ==========================================================================
  // F. Validaciones de límites e inconsistencias
  // ==========================================================================
  test('F. debe validar valores negativos, límites superiores y estado del botón aplicar', async ({ page }) => {
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await btnEditarDesc.click();

    const backdrop = page.locator('#modal-descuento-backdrop');
    await expect(backdrop).toBeVisible();

    const input = page.locator('#input-valor-descuento');
    const mensajeError = page.locator('#mensaje-error-descuento');
    const btnAplicar = page.locator('#btn-aplicar-descuento');

    // 1. Valor negativo
    await input.fill('-5');
    await expect(mensajeError).toBeVisible();
    await expect(mensajeError).toHaveText('El descuento no puede ser un valor negativo.');
    await expect(btnAplicar).toBeDisabled();
    await page.screenshot({ path: 'e2e/evidencias/03g-modal-error-negativo.png' });

    // 2. Descuento superior al subtotal (subtotal es S/ 8.50)
    await input.fill('15.00');
    await expect(mensajeError).toBeVisible();
    await expect(mensajeError).toContainText('El descuento no puede superar el subtotal');
    await expect(btnAplicar).toBeDisabled();
    await page.screenshot({ path: 'e2e/evidencias/03h-modal-error-supera-subtotal.png' });

    // 3. Porcentaje superior al 100%
    await page.locator('#tab-descuento-porcentaje').click();
    await input.fill('120');
    await expect(mensajeError).toBeVisible();
    await expect(mensajeError).toHaveText('El porcentaje de descuento no puede ser mayor a 100%.');
    await expect(btnAplicar).toBeDisabled();

    // 4. Campo vacío (asume 0.00 sin error, botón habilitado)
    await input.fill('');
    await expect(mensajeError).toBeHidden();
    await expect(btnAplicar).toBeEnabled();

    // 5. Valor cero (válido)
    await input.fill('0');
    await expect(mensajeError).toBeHidden();
    await expect(btnAplicar).toBeEnabled();

    // 6. Descuento igual al 100% del subtotal (válido)
    await input.fill('100');
    await expect(mensajeError).toBeHidden();
    await expect(btnAplicar).toBeEnabled();
    await expect(page.locator('#texto-previa-nuevo-total')).toContainText('Nuevo total: S/ 0.00');
  });

  // ==========================================================================
  // G. Cambio de modalidad
  // ==========================================================================
  test('G. debe convertir reactivamente entre modalidad Monto y Porcentaje', async ({ page }) => {
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await btnEditarDesc.click();

    const input = page.locator('#input-valor-descuento');
    const tabMonto = page.locator('#tab-descuento-monto');
    const tabPorcentaje = page.locator('#tab-descuento-porcentaje');

    // Introducir S/ 1.70 en monto (sobre subtotal S/ 8.50 representa exactamente 20%)
    await input.fill('1.70');
    await expect(page.locator('#texto-previa-descuento')).toContainText('Se aplicará un descuento de S/ 1.70');
    await expect(page.locator('#texto-previa-nuevo-total')).toContainText('Nuevo total: S/ 6.80');

    // Cambiar a porcentaje
    await tabPorcentaje.click();
    await expect(tabPorcentaje).toHaveClass(/tab-activa/);
    await expect(page.locator('#label-input-descuento')).toHaveText('Porcentaje de descuento (%)');
    await expect(page.locator('#indicador-unidad-descuento')).toHaveText('%');
    await expect(input).toHaveValue('20');
    await expect(page.locator('#texto-previa-descuento')).toContainText('Se aplicará un descuento de S/ 1.70');

    // Regresar a monto
    await tabMonto.click();
    await expect(tabMonto).toHaveClass(/tab-activa/);
    await expect(page.locator('#label-input-descuento')).toHaveText('Monto del descuento (S/)');
    await expect(page.locator('#indicador-unidad-descuento')).toHaveText('S/');
    await expect(input).toHaveValue('1.70');
    await expect(page.locator('#texto-previa-nuevo-total')).toContainText('Nuevo total: S/ 6.80');
  });

  // ==========================================================================
  // H. Edición de descuentos existentes
  // ==========================================================================
  test('H. debe abrir el modal con el descuento existente, permitir modificarlo y actualizar la canasta', async ({ page }) => {
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();

    // 1. Aplicar descuento inicial de S/ 2.00
    await btnEditarDesc.click();
    await page.locator('#input-valor-descuento').fill('2.00');
    await page.locator('#btn-aplicar-descuento').click();

    // Verificar en la canasta
    await expect(btnEditarDesc).toContainText('- S/ 2.00');
    await expect(page.locator('#total-venta')).toHaveText('S/ 6.50');

    // 2. Volver a abrir el modal
    await btnEditarDesc.click();
    const backdrop = page.locator('#modal-descuento-backdrop');
    await expect(backdrop).toBeVisible();

    // Comprobar que recupera el valor existente
    const input = page.locator('#input-valor-descuento');
    await expect(input).toHaveValue('2.00');
    await expect(page.locator('#texto-previa-descuento')).toContainText('Se aplicará un descuento de S/ 2.00');
    await expect(page.locator('#texto-previa-nuevo-total')).toContainText('Nuevo total: S/ 6.50');

    // 3. Modificarlo a S/ 3.50
    await input.fill('3.50');
    await page.locator('#btn-aplicar-descuento').click();

    await expect(backdrop).toBeHidden();
    await expect(btnEditarDesc).toContainText('- S/ 3.50');
    await expect(page.locator('#total-venta')).toHaveText('S/ 5.00');
  });

  // ==========================================================================
  // I. Cantidades superiores a uno
  // ==========================================================================
  test('I. debe calcular el descuento sobre el subtotal completo en líneas con múltiples unidades', async ({ page }) => {
    // Agregar 2 unidades adicionales seleccionando nuevamente el medicamento desde el buscador
    const inputBuscador = page.locator('#buscar-caja');
    const primeraOpcion = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();

    // Segunda unidad
    await inputBuscador.fill('para');
    await expect(primeraOpcion).toBeVisible();
    await primeraOpcion.click();

    // Tercera unidad
    await inputBuscador.fill('para');
    await expect(primeraOpcion).toBeVisible();
    await primeraOpcion.click();

    // Verificar que la cantidad en el stepper sea 3 y el subtotal sea 3 * S/ 8.50 = S/ 25.50
    const cantidadBadge = page.locator('.stepper b').first();
    await expect(cantidadBadge).toHaveText('3');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 25.50');
    await expect(page.locator('#subtotal-cant')).toHaveText('3');

    // Abrir modal de descuentos
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await btnEditarDesc.click();

    // Cambiar a porcentaje y aplicar 10%
    await page.locator('#tab-descuento-porcentaje').click();
    await page.locator('#input-valor-descuento').fill('10');

    // 10% de S/ 25.50 = S/ 2.55, Nuevo total = S/ 22.95
    await expect(page.locator('#texto-previa-descuento')).toContainText('Se aplicará un descuento de S/ 2.55');
    await expect(page.locator('#texto-previa-nuevo-total')).toContainText('Nuevo total: S/ 22.95');

    // Captura de evidencia
    await page.screenshot({ path: 'e2e/evidencias/03i-modal-descuento-varias-unidades.png' });

    // Aplicar
    await page.locator('#btn-aplicar-descuento').click();
    await expect(page.locator('#modal-descuento-backdrop')).toBeHidden();

    // Comprobar la canasta actualizada
    await expect(btnEditarDesc).toContainText('- S/ 2.55');
    await expect(page.locator('#total-venta')).toHaveText('S/ 22.95');
  });

  // ==========================================================================
  // J. Mejora contextual: nombre dinámico del medicamento
  // ==========================================================================
  test('J. debe mostrar dinámicamente el nombre del medicamento seleccionado y alternar al abrir otro', async ({ page }) => {
    // 1. Agregar un segundo medicamento (Ibuprofeno)
    const inputBuscador = page.locator('#buscar-caja');
    await inputBuscador.fill('ibu');
    const opcionIbu = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(opcionIbu).toBeVisible();
    await opcionIbu.click();

    // Comprobar que hay 2 productos en la canasta
    await expect(page.locator('#contador-canasta')).toContainText('2 productos');

    const filasDescuento = page.locator('.btn-editar-descuento');
    await expect(filasDescuento).toHaveCount(2);

    const btnDescParacetamol = filasDescuento.nth(0);
    const btnDescIbuprofeno = filasDescuento.nth(1);
    const nombreEnModal = page.locator('#modal-descuento-producto-nombre');
    const backdrop = page.locator('#modal-descuento-backdrop');

    // 2. Abrir modal del primer producto (Paracetamol)
    await btnDescParacetamol.click();
    await expect(backdrop).toBeVisible();
    await expect(nombreEnModal).toHaveText('Paracetamol');
    await page.screenshot({ path: 'e2e/evidencias/03j-modal-nombre-paracetamol.png' });

    // 3. Cerrar el modal
    await page.locator('#modal-descuento-cerrar').click();
    await expect(backdrop).toBeHidden();

    // 4. Abrir modal del segundo producto (Ibuprofeno)
    await btnDescIbuprofeno.click();
    await expect(backdrop).toBeVisible();
    await expect(nombreEnModal).toHaveText('Ibuprofeno');
    await page.screenshot({ path: 'e2e/evidencias/03k-modal-nombre-ibuprofeno.png' });

    // 5. Cerrar el modal
    await page.locator('#btn-cancelar-descuento').click();
    await expect(backdrop).toBeHidden();
  });

});
