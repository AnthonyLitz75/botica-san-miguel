import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

/**
 * e2e/spec-023-ordenes-compra.spec.js
 * AJUSTE 023 (paso 4A): Pulido de Órdenes de compra (listado, detalle, nueva, recepción)
 * Botica San Miguel (AyE)
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
}

async function asegurarDirectorioEvidencias() {
  const dir = path.join(process.cwd(), 'e2e', 'evidencias', 'visual');
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  return dir;
}

test.describe('AJUSTE 023 (paso 4A): Órdenes de compra', () => {

  test('a y b) Detalle: alineación dd vs dt (m-0), subtítulo, botón cancelar secundario y modal cancelar', async ({ page }) => {
    test.setTimeout(60000);
    const evidenciasDir = await asegurarDirectorioEvidencias();
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // Creamos una orden propia para no alterar OC-DEMO-001
    await page.goto('/ordenes-compra/nuevo', { waitUntil: 'domcontentloaded' });
    await page.locator('#idProveedor').selectOption({ index: 1 });
    await page.locator('#medicamento').selectOption({ index: 1 });
    await page.locator('#cantidad').fill('10');
    await page.locator('#costo').fill('12.50');
    await page.locator('#observaciones').fill('Orden para prueba detalle y cancelar');

    await Promise.all([
      page.waitForURL(url => url.pathname.startsWith('/ordenes-compra/') && !url.pathname.endsWith('/nuevo')),
      page.getByRole('button', { name: /Emitir orden/i }).filter({ visible: true }).click()
    ]);

    const urlDetalle = page.url();
    const idOrdenMatch = urlDetalle.match(/\/ordenes-compra\/(\d+)/);
    expect(idOrdenMatch).not.toBeNull();

    // a) Detalle: subtítulo nuevo
    await expect(page.locator('.sub')).toHaveText('Revisa el proveedor, el estado y lo que pediste.');

    // a) Detalle: alineación dd vs dt (m-0, margen 0 +-2px)
    const primerDt = page.locator('dl dt').first();
    const primerDd = page.locator('dl dd').first();
    await expect(primerDt).toBeVisible();
    await expect(primerDd).toBeVisible();

    const dtBox = await primerDt.boundingBox();
    const ddBox = await primerDd.boundingBox();
    expect(dtBox).not.toBeNull();
    expect(ddBox).not.toBeNull();
    expect(Math.abs(dtBox.x - ddBox.x)).toBeLessThanOrEqual(2);

    // a) Detalle: botón cancelar estilo secundario peligro (fondo blanco, no rojo sólido)
    const btnCancelar = page.getByRole('button', { name: /Cancelar orden/i });
    await expect(btnCancelar).toBeVisible();
    const btnBg = await btnCancelar.evaluate(el => window.getComputedStyle(el).backgroundColor);
    // Fondo debe ser blanco (rgb(255, 255, 255))
    expect(btnBg).toBe('rgb(255, 255, 255)');

    // Captura de evidencia 28-orden-detalle.png
    await page.screenshot({ path: path.join(evidenciasDir, '28-orden-detalle.png') });

    // b) Cancelar con modal: click en "Cancelar orden" abre #modal-confirmar
    await btnCancelar.click();
    const modalConfirmar = page.locator('#modal-confirmar');
    await expect(modalConfirmar).toHaveClass(/(?:^|\s)(activo|on)(?:\s|$)/);
    await expect(modalConfirmar.locator('#modal-confirmar-titulo')).toContainText('Cancelar la orden');
    await expect(modalConfirmar.locator('#modal-confirmar-mensaje')).toContainText('La orden pasará a Cancelada y ya no se podrá recibir.');
    await expect(modalConfirmar.locator('#modal-confirmar-cancelar')).toHaveText('No, volver');

    // "No, volver" cierra sin cancelar
    await modalConfirmar.locator('#modal-confirmar-cancelar').click();
    await expect(modalConfirmar).not.toHaveClass(/(?:^|\s)(activo|on)(?:\s|$)/);
    await expect(page.locator('.badge.estado-registro')).toContainText('PENDIENTE');

    // Cancelar definitivamente
    await btnCancelar.click();
    await expect(modalConfirmar).toHaveClass(/(?:^|\s)(activo|on)(?:\s|$)/);
    await Promise.all([
      page.waitForLoadState('domcontentloaded'),
      modalConfirmar.locator('#modal-confirmar-accion').click()
    ]);

    // La orden pasa a CANCELADA, desaparece el botón Cancelar y el badge es de peligro
    await expect(page.locator('.badge.estado-registro')).toContainText('CANCELADA');
    await expect(page.getByRole('button', { name: /Cancelar orden/i })).toHaveCount(0);
  });

  test('c) Nueva orden: dos columnas a 1600x900, resumen en vivo y emisión de orden', async ({ page }) => {
    test.setTimeout(60000);
    const evidenciasDir = await asegurarDirectorioEvidencias();
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/ordenes-compra/nuevo', { waitUntil: 'domcontentloaded' });

    // Verificar diseño a dos columnas (formulario a la izquierda, resumen a la derecha)
    const cardFormulario = page.locator('form > div.card').first();
    const cardResumen = page.locator('.card:has(h3:has-text("Resumen de la orden"))');
    await expect(cardFormulario).toBeVisible();
    await expect(cardResumen).toBeVisible();

    const formBox = await cardFormulario.boundingBox();
    const resumenBox = await cardResumen.boundingBox();
    expect(formBox).not.toBeNull();
    expect(resumenBox).not.toBeNull();
    expect(resumenBox.x).toBeGreaterThan(formBox.x + formBox.width - 20);

    // Títulos de sección normales (no mayúsculas)
    await expect(page.locator('legend').first()).toContainText('Datos del proveedor');
    await expect(page.locator('legend').nth(1)).toContainText('Medicamento solicitado');

    // Nota bajo proveedor en texto normal
    await expect(page.locator('.campo-ayuda')).toHaveText('La orden queda como Pendiente y no cambia el stock hasta que registres la recepción.');

    // Sin Volver a órdenes en la cabecera
    await expect(page.locator('.head a:has-text("Volver a órdenes")')).toHaveCount(0);

    // Resumen en vivo: seleccionar proveedor y medicamento, escribir cantidad y costo
    await page.locator('#idProveedor').selectOption({ index: 1 });
    await page.locator('#medicamento').selectOption({ index: 1 });
    await page.locator('#cantidad').fill('20');
    await page.locator('#costo').fill('15.50');

    // Resumen actualizado
    await expect(page.locator('#resumen-proveedor')).not.toHaveText('— Sin seleccionar —');
    await expect(page.locator('#resumen-medicamento')).not.toHaveText('— Sin seleccionar —');
    await expect(page.locator('#resumen-calculo')).toHaveText('20 × S/ 15.50');

    const totalEstimado = page.locator('#importe-previo');
    await expect(totalEstimado).toHaveText('S/ 310.00');
    await expect(totalEstimado).toHaveAttribute('aria-live', 'polite');

    // Botones visibles sin scroll en la tarjeta de resumen
    const btnEmitirDesktop = cardResumen.getByRole('button', { name: /Emitir orden/i });
    const btnCancelarDesktop = cardResumen.getByRole('link', { name: /Cancelar/i });
    await expect(btnEmitirDesktop).toBeVisible();
    await expect(btnCancelarDesktop).toBeVisible();

    // Captura de evidencia 29-orden-nueva.png
    await page.screenshot({ path: path.join(evidenciasDir, '29-orden-nueva.png') });

    // Emitir orden
    await Promise.all([
      page.waitForURL(url => url.pathname.startsWith('/ordenes-compra/') && !url.pathname.endsWith('/nuevo')),
      btnEmitirDesktop.click()
    ]);

    await expect(page.locator('.badge.estado-registro')).toContainText('PENDIENTE');
  });

  test('d) Recepción: sin alert al cargar, pista neutra, rellenado automático y recepción parcial', async ({ page }) => {
    test.setTimeout(60000);
    const evidenciasDir = await asegurarDirectorioEvidencias();
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 1. Crear una orden propia de 10 unidades
    await page.goto('/ordenes-compra/nuevo', { waitUntil: 'domcontentloaded' });
    await page.locator('#idProveedor').selectOption({ index: 1 });
    await page.locator('#medicamento').selectOption({ index: 1 });
    await page.locator('#cantidad').fill('10');
    await page.locator('#costo').fill('8.00');

    await Promise.all([
      page.waitForURL(url => url.pathname.startsWith('/ordenes-compra/') && !url.pathname.endsWith('/nuevo')),
      page.getByRole('button', { name: /Emitir orden/i }).filter({ visible: true }).click()
    ]);

    const urlDetalle = page.url();
    const idOrden = urlDetalle.match(/\/ordenes-compra\/(\d+)/)[1];

    // 2. Ir a registrar recepción
    await page.goto(`/ordenes-compra/${idOrden}/recepcion`, { waitUntil: 'domcontentloaded' });

    // Al cargar NO hay ningún role="alert" visible en pantalla
    await expect(page.locator('[role="alert"]:visible')).toHaveCount(0);

    // Pista informativa neutra visible
    const pista = page.locator('#pista-recepcion');
    await expect(pista).toBeVisible();
    await expect(pista).toHaveText('Marca los productos que llegaron para poder guardar la recepción.');

    // Frase en texto normal
    await expect(page.locator('p:has-text("Selecciona solo las líneas que llegaron en esta entrega física.")')).toBeVisible();

    // Casilla sin texto visible 'Recibir' y con aria-label
    const checkbox = page.locator('.recepcion-incluir').first();
    await expect(checkbox).toHaveAttribute('aria-label', /^Recibir /);
    await expect(page.locator('.recepcion-selector span:has-text("Recibir")')).toHaveCount(0);

    // Al marcar la casilla: cantidad se rellena automáticamente con su saldo pendiente (10)
    const inputCantidad = page.locator('.recepcion-cantidad').first();
    await expect(inputCantidad).toBeDisabled();
    await checkbox.check();
    await expect(inputCantidad).toBeEnabled();
    await expect(inputCantidad).toHaveValue('10');

    // Verificar que el foco sigue en el checkbox
    await expect(checkbox).toBeFocused();

    // Si desmarca, se limpia y deshabilita
    await checkbox.uncheck();
    await expect(inputCantidad).toBeDisabled();
    await expect(inputCantidad).toHaveValue('');

    // Marcar de nuevo y cambiar a 5 (recepción parcial)
    await checkbox.check();
    await expect(inputCantidad).toHaveValue('10');
    await inputCantidad.fill('5');

    // Completar lote y vencimiento
    await page.locator('[data-campo="numeroLote"]').first().fill('L-E2E-REC-01');
    await page.locator('[data-campo="fechaVencimiento"]').first().fill('2028-12-31');

    // Nota de recepción dentro de la tarjeta general a todo el ancho
    const notaRecepcion = page.locator('.recepcion-nota');
    await expect(notaRecepcion).toBeVisible();
    await page.locator('#motivo').fill('Entrega parcial E2E');

    // Captura de evidencia 30-recepcion.png
    await page.screenshot({ path: path.join(evidenciasDir, '30-recepcion.png') });

    // Guardar recepción
    await Promise.all([
      page.waitForURL(url => url.pathname === `/ordenes-compra/${idOrden}`),
      page.getByRole('button', { name: /Guardar recepción/i }).click()
    ]);

    // La orden pasa a PARCIALMENTE_RECIBIDA
    await expect(page.locator('.badge.estado-registro')).toContainText(/PARCIAL/i);

    // Saldo pendiente restante debe ser 5
    await expect(page.locator('table tbody')).toContainText('5');
  });

  test('e) Responsivo: a 320 px no hay scroll horizontal global en listado, detalle, nueva ni recepción', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 320, height: 600 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 1. Listado
    await page.goto('/ordenes-compra', { waitUntil: 'domcontentloaded' });
    let scrollOk = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
    expect(scrollOk).toBe(true);

    // 2. Nueva orden
    await page.goto('/ordenes-compra/nuevo', { waitUntil: 'domcontentloaded' });
    scrollOk = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
    expect(scrollOk).toBe(true);

    // Crear orden para verificar detalle y recepcion
    await page.locator('#idProveedor').selectOption({ index: 1 });
    await page.locator('#medicamento').selectOption({ index: 1 });
    await page.locator('#cantidad').fill('5');
    await page.locator('#costo').fill('10.00');

    await Promise.all([
      page.waitForURL(url => url.pathname.startsWith('/ordenes-compra/') && !url.pathname.endsWith('/nuevo')),
      page.getByRole('button', { name: /Emitir orden/i }).filter({ visible: true }).click()
    ]);

    // 3. Detalle
    scrollOk = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
    expect(scrollOk).toBe(true);

    const idOrden = page.url().match(/\/ordenes-compra\/(\d+)/)[1];

    // 4. Recepción
    await page.goto(`/ordenes-compra/${idOrden}/recepcion`, { waitUntil: 'domcontentloaded' });
    scrollOk = await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1);
    expect(scrollOk).toBe(true);
  });

});
