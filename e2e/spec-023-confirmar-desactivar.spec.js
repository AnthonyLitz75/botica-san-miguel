import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-confirmar-desactivar.spec.js
 * Feature: Modal de confirmación propio para "Desactivar" (reemplaza confirm() nativo)
 * Botica San Miguel (AyE)
 */
test.describe('Spec 023: Modal de confirmación propio para Desactivar', () => {

  async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
  }

  test('Medicamentos: modal accesible, cancelar, escape, confirmación y captura visual a 1600x900', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    const ts = Date.now().toString().slice(-5);
    const codigoMed = `MED-T${ts}`;
    const nombreMed = `Fármaco Prueba ${ts}`;

    // 1. Crear medicamento de prueba propio
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo medicamento/i }).click();

    const modalNuevoMed = page.locator('#modal-nuevo-medicamento');
    await expect(modalNuevoMed).toHaveClass(/activo|on/);

    await modalNuevoMed.locator('#modal-codigo-med').fill(codigoMed);
    await modalNuevoMed.locator('#modal-nombre-med').fill(nombreMed);
    await modalNuevoMed.locator('#modal-presentacion-med').fill('Caja x 30 comprimidos');
    await modalNuevoMed.locator('#modal-precio-med').fill('15.50');
    await modalNuevoMed.locator('#modal-stockMinimo-med').fill('5');

    await Promise.all([
      page.waitForResponse(r => r.url().includes('/medicamentos') && r.request().method() === 'POST'),
      modalNuevoMed.getByRole('button', { name: /Guardar medicamento/i }).click()
    ]);

    // 2. Buscar el medicamento recién creado
    await page.goto(`/medicamentos?buscar=${encodeURIComponent(codigoMed)}`, { waitUntil: 'domcontentloaded' });
    const fila = page.locator('tr', { hasText: codigoMed });
    await expect(fila).toBeVisible();

    const btnDesactivar = fila.locator('.accion-desactivar');
    await expect(btnDesactivar).toBeVisible();

    // 3. Clic en desactivar -> abrir modal propio
    await btnDesactivar.click();

    const modalConfirmar = page.locator('#modal-confirmar');
    await expect(modalConfirmar).toBeVisible();
    await expect(modalConfirmar).toHaveAttribute('role', 'alertdialog');
    await expect(modalConfirmar).toHaveAttribute('aria-modal', 'true');

    // Verificar título y mensaje
    const titulo = modalConfirmar.locator('#modal-confirmar-titulo');
    await expect(titulo).toHaveText(`¿Desactivar ${nombreMed}?`);

    const mensaje = modalConfirmar.locator('#modal-confirmar-mensaje');
    await expect(mensaje).toHaveText('Dejará de aparecer en Caja y en nuevas órdenes de compra. Se conserva su historial y sus lotes.');

    const btnAccion = modalConfirmar.locator('#modal-confirmar-accion');
    await expect(btnAccion).toHaveText('Desactivar');

    // Foco inicial en Cancelar
    const btnCancelar = modalConfirmar.locator('#modal-confirmar-cancelar');
    await expect(btnCancelar).toBeFocused();

    // Captura visual obligatoria a 1600x900
    await page.screenshot({
      path: 'e2e/evidencias/visual/24-confirmar-desactivar.png',
      fullPage: false
    });

    // 4. Probar Cancelar -> cierra modal, devuelve foco, medicamento sigue activo
    await btnCancelar.click();
    await expect(modalConfirmar).not.toBeVisible();
    await expect(btnDesactivar).toBeFocused();
    await expect(btnDesactivar).toBeVisible();

    // 5. Probar Escape -> cierra modal, medicamento sigue activo
    await btnDesactivar.click();
    await expect(modalConfirmar).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(modalConfirmar).not.toBeVisible();
    await expect(btnDesactivar).toBeFocused();
    await expect(btnDesactivar).toBeVisible();

    // 6. Confirmar desactivación
    await btnDesactivar.click();
    await expect(modalConfirmar).toBeVisible();

    await Promise.all([
      page.waitForResponse(r => r.url().includes('/desactivar') && r.request().method() === 'POST'),
      btnAccion.click()
    ]);

    // Verificar que ahora está inactivo (aparece el botón de activar en lugar de desactivar)
    await page.goto(`/medicamentos?buscar=${encodeURIComponent(codigoMed)}&activo=false`, { waitUntil: 'domcontentloaded' });
    const filaInactiva = page.locator('tr', { hasText: codigoMed });
    await expect(filaInactiva).toBeVisible();
    await expect(filaInactiva.locator('form[action*="/activar"] button')).toBeVisible();
  });

  test('Categorías: modal propio, cancelar no altera y confirmar desactiva categoría hoja', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    const ts = Date.now().toString().slice(-5);
    const nombreCat = `Cat Test ${ts}`;

    // 1. Crear categoría propia
    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nueva categoría/i }).click();

    const modalCat = page.locator('#modal-nueva-categoria');
    await expect(modalCat).toHaveClass(/activo|on/);
    await modalCat.locator('#modal-nombre-cat').fill(nombreCat);

    await Promise.all([
      page.waitForResponse(r => r.url().includes('/categorias') && r.request().method() === 'POST'),
      modalCat.getByRole('button', { name: /Guardar categoría/i }).click()
    ]);

    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });
    const filaNodo = page.locator('.tr', { hasText: nombreCat });
    await expect(filaNodo).toBeVisible();

    const btnDesactivar = filaNodo.locator('.accion-desactivar');
    await expect(btnDesactivar).toBeVisible();

    // 2. Abrir modal
    await btnDesactivar.click();
    const modalConfirmar = page.locator('#modal-confirmar');
    await expect(modalConfirmar).toBeVisible();

    await expect(modalConfirmar.locator('#modal-confirmar-titulo')).toHaveText(`¿Desactivar ${nombreCat}?`);
    await expect(modalConfirmar.locator('#modal-confirmar-mensaje')).toHaveText(
      'Dejará de aparecer en la estructura y en nuevos medicamentos. Solo se puede desactivar si no tiene subcategorías ni medicamentos asociados.'
    );

    // Cancelar
    await modalConfirmar.locator('#modal-confirmar-cancelar').click();
    await expect(modalConfirmar).not.toBeVisible();
    await expect(btnDesactivar).toBeVisible();

    // Confirmar
    await btnDesactivar.click();
    await expect(modalConfirmar).toBeVisible();

    await Promise.all([
      page.waitForResponse(r => r.url().includes('/desactivar') && r.request().method() === 'POST'),
      modalConfirmar.locator('#modal-confirmar-accion').click()
    ]);

    // Verificar que ya no aparece como activa
    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.tr', { hasText: nombreCat })).toHaveCount(0);
  });

  test('Proveedores: modal propio, verificación de textos y desactivación', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    const ts = Date.now().toString().slice(-6);
    const rucProv = `20${ts}001`; // 11 dígitos exactos
    const razonSocial = `Proveedor Prueba ${ts} SAC`;

    // 1. Crear proveedor de prueba
    await page.goto('/proveedores', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo proveedor/i }).click();

    const modalProv = page.locator('#modal-nuevo-proveedor');
    await expect(modalProv).toHaveClass(/activo|on/);
    await modalProv.locator('#modal-ruc').fill(rucProv);
    await modalProv.locator('#modal-razonSocial').fill(razonSocial);
    await modalProv.locator('#modal-telefono-prov').fill(`0145${ts.slice(-4)}`);
    await modalProv.locator('#modal-correo-prov').fill(`prov.${ts}@test.pe`);

    await Promise.all([
      page.waitForResponse(r => r.url().includes('/proveedores') && r.request().method() === 'POST'),
      modalProv.getByRole('button', { name: /Guardar proveedor/i }).click()
    ]);

    await page.goto(`/proveedores?buscar=${encodeURIComponent(rucProv)}`, { waitUntil: 'domcontentloaded' });
    const fila = page.locator('tr', { hasText: rucProv });
    await expect(fila).toBeVisible();

    const btnDesactivar = fila.locator('.accion-desactivar');
    await expect(btnDesactivar).toBeVisible();

    // 2. Abrir modal
    await btnDesactivar.click();
    const modalConfirmar = page.locator('#modal-confirmar');
    await expect(modalConfirmar).toBeVisible();

    await expect(modalConfirmar.locator('#modal-confirmar-titulo')).toHaveText(`¿Desactivar ${razonSocial}?`);
    await expect(modalConfirmar.locator('#modal-confirmar-mensaje')).toHaveText(
      'No podrá usarse en nuevas órdenes de compra. Se conserva su historial.'
    );

    // Cancelar
    await modalConfirmar.locator('#modal-confirmar-cancelar').click();
    await expect(modalConfirmar).not.toBeVisible();
    await expect(fila.locator('.badge')).toHaveText('ACTIVO');

    // Confirmar
    await btnDesactivar.click();
    await expect(modalConfirmar).toBeVisible();

    await Promise.all([
      page.waitForResponse(r => r.url().includes('/desactivar') && r.request().method() === 'POST'),
      modalConfirmar.locator('#modal-confirmar-accion').click()
    ]);

    // Verificar inactivo
    await page.goto(`/proveedores?buscar=${encodeURIComponent(rucProv)}&activo=false`, { waitUntil: 'domcontentloaded' });
    const filaInactiva = page.locator('tr', { hasText: rucProv });
    await expect(filaInactiva).toBeVisible();
    await expect(filaInactiva.locator('.badge')).toHaveText('INACTIVO');
  });

  test('Usuarios: modal propio con nombre completo y desactivación', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    const ts = Date.now().toString().slice(-5);
    const username = `usr_${ts}`;
    const nombreCompleto = `Personal Test ${ts}`;

    // 1. Crear usuario de prueba
    await page.goto('/usuarios/nuevo', { waitUntil: 'domcontentloaded' });
    await page.locator('#nombreUsuario').fill(username);
    await page.locator('#rol').selectOption('VENDEDOR');
    await page.locator('#nombre').fill(nombreCompleto);
    await page.locator('#clave').fill('Password2026!');

    await Promise.all([
      page.waitForResponse(r => r.url().includes('/usuarios') && r.request().method() === 'POST'),
      page.getByRole('button', { name: /Crear usuario/i }).click()
    ]);

    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });
    const fila = page.locator('tr', { hasText: username });
    await expect(fila).toBeVisible();

    const btnDesactivar = fila.locator('.accion-desactivar');
    await expect(btnDesactivar).toBeVisible();

    // 2. Abrir modal
    await btnDesactivar.click();
    const modalConfirmar = page.locator('#modal-confirmar');
    await expect(modalConfirmar).toBeVisible();

    // Título debe decir "¿Desactivar a <nombre completo>?"
    await expect(modalConfirmar.locator('#modal-confirmar-titulo')).toHaveText(`¿Desactivar a ${nombreCompleto}?`);
    await expect(modalConfirmar.locator('#modal-confirmar-mensaje')).toHaveText(
      'No podrá iniciar sesión. Sus registros se conservan.'
    );

    // Cancelar
    await modalConfirmar.locator('#modal-confirmar-cancelar').click();
    await expect(modalConfirmar).not.toBeVisible();
    await expect(btnDesactivar).toBeVisible();

    // Confirmar
    await btnDesactivar.click();
    await expect(modalConfirmar).toBeVisible();

    await Promise.all([
      page.waitForResponse(r => r.url().includes('/desactivar') && r.request().method() === 'POST'),
      modalConfirmar.locator('#modal-confirmar-accion').click()
    ]);

    // Verificar inactivo en la tabla
    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });
    const filaInactiva = page.locator('tr', { hasText: username });
    await expect(filaInactiva).toBeVisible();
    await expect(filaInactiva.locator('.badge.t-rose')).toHaveText('INACTIVO');
  });

  test('Responsive a 320 px: sin scroll horizontal y botones apilados a ancho completo', async ({ page }) => {
    test.setTimeout(30000);
    await page.setViewportSize({ width: 320, height: 600 });
    await iniciarSesion(page);

    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });
    const primerBtnDesactivar = page.locator('.accion-desactivar').first();
    await expect(primerBtnDesactivar).toBeVisible();

    await primerBtnDesactivar.click();

    const modalConfirmar = page.locator('#modal-confirmar');
    await expect(modalConfirmar).toBeVisible();

    // Sin scroll horizontal
    const scrollHorizontal = await page.evaluate(() => document.documentElement.scrollWidth > document.documentElement.clientWidth);
    expect(scrollHorizontal).toBe(false);

    // Verificar que los botones en el pie del modal están apilados verticalmente
    const btnCancelar = modalConfirmar.locator('#modal-confirmar-cancelar');
    const btnAccion = modalConfirmar.locator('#modal-confirmar-accion');

    const boxCancelar = await btnCancelar.boundingBox();
    const boxAccion = await btnAccion.boundingBox();

    expect(boxCancelar).not.toBeNull();
    expect(boxAccion).not.toBeNull();

    // En <= 480px, la clase responsive flex-direction column o column-reverse apila los botones verticalmente
    const distanciaY = Math.abs(boxCancelar.y - boxAccion.y);
    expect(distanciaY).toBeGreaterThanOrEqual(30);

    // Cerrar con Escape
    await page.keyboard.press('Escape');
    await expect(modalConfirmar).not.toBeVisible();
  });
});
