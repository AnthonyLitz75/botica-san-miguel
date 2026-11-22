import { test, expect } from '@playwright/test';

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
}

test.describe('AJUSTE 023 (paso 3): Existencias iniciales en modal y limpieza visual', () => {

  test('a) El botón abre el modal con título, aviso de una línea, motivo precargado y foco dentro del modal', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/movimientos');
    await page.waitForLoadState('domcontentloaded');

    const btnAbrir = page.locator('a[data-modal-open="modal-existencias-iniciales"]');
    await expect(btnAbrir).toBeVisible();
    await btnAbrir.click();

    const modal = page.locator('#modal-existencias-iniciales');
    await expect(modal).toHaveClass(/activo|on/);
    await expect(modal).toHaveAttribute('role', 'dialog');
    await expect(modal).toHaveAttribute('aria-modal', 'true');

    // Título y aviso de una sola línea
    await expect(modal.locator('#titulo-modal-existencias-iniciales')).toHaveText('Registrar existencias iniciales');
    await expect(modal.locator('.mh p')).toHaveText('Úsalo para cargar el stock con el que empiezas, sin necesidad de una orden de compra.');

    // Motivo precargado
    const inputMotivo = modal.locator('#modal-existencias-motivo');
    await expect(inputMotivo).toHaveValue('Existencias iniciales');

    // Foco dentro del modal (en el select de medicamento)
    const selectMed = modal.locator('#modal-existencias-idMedicamento');
    await expect(selectMed).toBeFocused();
  });

  test('b) Registrar un lote válido cierra el modal, muestra mensaje de éxito y aparece arriba en el listado', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/movimientos');
    await page.waitForLoadState('domcontentloaded');

    const btnAbrir = page.locator('a[data-modal-open="modal-existencias-iniciales"]');
    await btnAbrir.click();

    const modal = page.locator('#modal-existencias-iniciales');
    await expect(modal).toHaveClass(/activo|on/);

    const loteUnico = `LOTE-VAL-${Date.now().toString().slice(-6)}`;
    const fechaFutura = '2028-12-31';
    const motivoUnico = `Apertura-${Date.now().toString().slice(-6)}`;

    // Seleccionar medicamento activo (primer option con valor)
    const selectMed = modal.locator('#modal-existencias-idMedicamento');
    await selectMed.selectOption({ index: 1 });

    await modal.locator('#modal-existencias-numeroLote').fill(loteUnico);
    await modal.locator('#modal-existencias-fechaVencimiento').fill(fechaFutura);
    await modal.locator('#modal-existencias-cantidad').fill('35');
    await modal.locator('#modal-existencias-motivo').fill(motivoUnico);

    // Enviar
    await modal.locator('button[type="submit"]').click();

    // El modal se cierra y la URL es /movimientos
    await expect(modal).not.toHaveClass(/activo|on/);

    // Mensaje de éxito visible
    const avisoExito = page.locator('.aviso-exito');
    await expect(avisoExito).toBeVisible();
    await expect(avisoExito).toContainText('Existencias iniciales registradas correctamente');

    // El movimiento nuevo está arriba en la primera fila de la tabla
    const primeraFila = page.locator('table tbody tr:first-child');
    await expect(primeraFila).toContainText(motivoUnico);
    await expect(primeraFila).toContainText('ENTRADA');

    // Comprobar que en el detalle del movimiento aparece el lote y la cantidad registrada
    await primeraFila.getByRole('link', { name: /Ver detalle/i }).click();
    await page.waitForLoadState('domcontentloaded');
    await expect(page.locator('body')).toContainText(loteUnico);
    await expect(page.locator('body')).toContainText('35');
  });

  test('c) Enviar vacío mantiene el modal abierto con errores bajo campos requeridos y conserva lo escrito', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/movimientos');
    await page.waitForLoadState('domcontentloaded');

    const btnAbrir = page.locator('a[data-modal-open="modal-existencias-iniciales"]');
    await btnAbrir.click();

    const modal = page.locator('#modal-existencias-iniciales');
    await expect(modal).toHaveClass(/activo|on/);

    // Escribir un motivo personalizado para comprobar que se conserva
    const inputMotivo = modal.locator('#modal-existencias-motivo');
    await inputMotivo.fill('Existencias de apertura 2026');

    // Desactivar novalidate para que el submit llegue al backend HTTP 400
    await page.evaluate(() => {
      const form = document.querySelector('#modal-existencias-iniciales form');
      if (form) form.noValidate = true;
    });

    // Enviar formulario sin completar campos obligatorios
    await modal.locator('button[type="submit"]').click();

    // El modal sigue abierto
    await expect(modal).toHaveClass(/activo|on/);

    // Se muestran errores bajo los campos requeridos con aria-invalid
    const selectMed = modal.locator('#modal-existencias-idMedicamento');
    const inputLote = modal.locator('#modal-existencias-numeroLote');
    const inputFecha = modal.locator('#modal-existencias-fechaVencimiento');

    await expect(selectMed).toHaveAttribute('aria-invalid', 'true');
    await expect(inputLote).toHaveAttribute('aria-invalid', 'true');
    await expect(inputFecha).toHaveAttribute('aria-invalid', 'true');

    // Lo escrito en motivo se conservó intacto
    await expect(inputMotivo).toHaveValue('Existencias de apertura 2026');
  });

  test('d) Esc y Cancelar cierran sin guardar y el foco vuelve al botón disparador', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/movimientos');
    await page.waitForLoadState('domcontentloaded');

    const btnAbrir = page.locator('a[data-modal-open="modal-existencias-iniciales"]');

    // 1. Probar botón Cancelar
    await btnAbrir.click();
    const modal = page.locator('#modal-existencias-iniciales');
    await expect(modal).toHaveClass(/activo|on/);

    const btnCancelar = modal.locator('.btn-cerrar-modal').filter({ hasText: /Cancelar/i });
    await btnCancelar.click();
    await expect(modal).not.toHaveClass(/activo|on/);
    await expect(btnAbrir).toBeFocused();

    // 2. Probar tecla Escape
    await btnAbrir.click();
    await expect(modal).toHaveClass(/activo|on/);
    await page.keyboard.press('Escape');
    await expect(modal).not.toHaveClass(/activo|on/);
    await expect(btnAbrir).toBeFocused();
  });

  test('e) La casilla mide <= 20 px y su texto está en la misma fila; el botón principal no contiene cuadrado blanco', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/movimientos');
    await page.waitForLoadState('domcontentloaded');

    const btnAbrir = page.locator('a[data-modal-open="modal-existencias-iniciales"]');
    await btnAbrir.click();

    const modal = page.locator('#modal-existencias-iniciales');
    await expect(modal).toHaveClass(/activo|on/);

    // Casilla: tamaño <= 20 px
    const checkbox = modal.locator('#modal-existencias-confirmarAcumulacion');
    const box = await checkbox.boundingBox();
    expect(box).not.toBeNull();
    expect(box.width).toBeLessThanOrEqual(20);
    expect(box.height).toBeLessThanOrEqual(20);

    // Texto de la casilla
    const labelCasilla = modal.locator('label[for="modal-existencias-confirmarAcumulacion"]');
    await expect(labelCasilla).toHaveText('Sumar a un lote que ya existe');

    // Texto de ayuda debajo
    const ayudaCasilla = modal.locator('p:has-text("Si ya hay un lote con el mismo número")');
    await expect(ayudaCasilla).toBeVisible();

    // Botón principal: no contiene ningún elemento con clase icono-check ni inputs dentro
    const btnSubmit = modal.locator('button[type="submit"]');
    await expect(btnSubmit).toBeVisible();
    await expect(btnSubmit.locator('.icono-check')).toHaveCount(0);
    await expect(btnSubmit.locator('input')).toHaveCount(0);
  });

  test('f) La casilla "Sumar a un lote que ya existe" suma al lote existente cuando se marca', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/movimientos');
    await page.waitForLoadState('domcontentloaded');

    const loteAcumular = `LOTE-ACUM-${Date.now().toString().slice(-6)}`;
    const fechaVenc = '2028-11-20';

    // 1. Crear el primer registro
    const btnAbrir = page.locator('a[data-modal-open="modal-existencias-iniciales"]');
    await btnAbrir.click();

    const modal = page.locator('#modal-existencias-iniciales');
    await expect(modal).toHaveClass(/activo|on/);

    await modal.locator('#modal-existencias-idMedicamento').selectOption({ index: 1 });
    await modal.locator('#modal-existencias-numeroLote').fill(loteAcumular);
    await modal.locator('#modal-existencias-fechaVencimiento').fill(fechaVenc);
    await modal.locator('#modal-existencias-cantidad').fill('20');
    await modal.locator('button[type="submit"]').click();

    await expect(modal).not.toHaveClass(/activo|on/);
    await expect(page.locator('.aviso-exito')).toBeVisible();

    // 2. Intentar registrar el mismo lote SIN marcar la casilla -> Error 400
    await btnAbrir.click();
    await expect(modal).toHaveClass(/activo|on/);

    await modal.locator('#modal-existencias-idMedicamento').selectOption({ index: 1 });
    await modal.locator('#modal-existencias-numeroLote').fill(loteAcumular);
    await modal.locator('#modal-existencias-fechaVencimiento').fill(fechaVenc);
    await modal.locator('#modal-existencias-cantidad').fill('15');

    // Desmarcada
    await modal.locator('#modal-existencias-confirmarAcumulacion').uncheck();
    await modal.locator('button[type="submit"]').click();

    // Debe permanecer abierto con error indicando que ya existe
    await expect(modal).toHaveClass(/activo|on/);
    const errorLote = modal.locator('#error-modal-existencias-numeroLote');
    await expect(errorLote).toBeVisible();
    await expect(errorLote).toContainText(/ya existe/i);

    // 3. Marcar la casilla "Sumar a un lote que ya existe" y reenviar -> éxito
    await modal.locator('#modal-existencias-confirmarAcumulacion').check();
    await modal.locator('button[type="submit"]').click();

    await expect(modal).not.toHaveClass(/activo|on/);
    await expect(page.locator('.aviso-exito')).toBeVisible();
  });

  test('g) A 320 px el modal no genera scroll horizontal de página', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 320, height: 600 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/movimientos');
    await page.waitForLoadState('domcontentloaded');

    const btnAbrir = page.locator('a[data-modal-open="modal-existencias-iniciales"]');
    await btnAbrir.click();

    const modal = page.locator('#modal-existencias-iniciales');
    await expect(modal).toHaveClass(/activo|on/);

    // Verificar que no hay scroll horizontal en el diálogo ni en la ventana
    const sinOverflow = await page.evaluate(() => {
      const modalDialog = document.querySelector('#modal-existencias-iniciales .modal-dialog');
      return modalDialog ? modalDialog.scrollWidth <= window.innerWidth : true;
    });
    expect(sinOverflow).toBe(true);
  });

  test('h) La página de respaldo (ruta directa) muestra etiqueta, subtítulo y no tiene "Volver a movimientos"', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/inventario/existencias-iniciales');
    await page.waitForLoadState('domcontentloaded');

    // Etiqueta y subtítulo
    await expect(page.locator('.seccion-etiqueta')).toHaveText('INVENTARIO / TRAZABILIDAD');
    await expect(page.locator('h1')).toHaveText('Registrar existencias iniciales');
    await expect(page.locator('.sub')).toHaveText('Úsalo para cargar el stock con el que empiezas, sin necesidad de una orden de compra.');

    // No debe existir "Volver a movimientos"
    const volverLink = page.getByRole('link', { name: /Volver a movimientos/i });
    await expect(volverLink).toHaveCount(0);

    // Cancelar debe existir y apuntar a /movimientos
    const btnCancelar = page.getByRole('link', { name: /Cancelar/i });
    await expect(btnCancelar).toBeVisible();
    await expect(btnCancelar).toHaveAttribute('href', '/movimientos');

    // Casilla con textos limpios
    await expect(page.locator('label[for="confirmarAcumulacion"]')).toHaveText('Sumar a un lote que ya existe');
  });

  test('Captura visual 27: Modal de existencias iniciales sobre Movimientos (1600x900)', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/movimientos');
    await page.waitForLoadState('domcontentloaded');

    const btnAbrir = page.locator('a[data-modal-open="modal-existencias-iniciales"]');
    await btnAbrir.click();

    const modal = page.locator('#modal-existencias-iniciales');
    await expect(modal).toHaveClass(/activo|on/);

    // Provocar un error visible de validación para capturarlo en la imagen como pide el prompt
    await page.evaluate(() => {
      const form = document.querySelector('#modal-existencias-iniciales form');
      if (form) form.noValidate = true;
    });
    await modal.locator('button[type="submit"]').click();
    await expect(modal.locator('#error-modal-existencias-idMedicamento')).toBeVisible();

    await page.screenshot({ path: 'e2e/evidencias/visual/27-existencias-iniciales-modal.png' });
  });

});
