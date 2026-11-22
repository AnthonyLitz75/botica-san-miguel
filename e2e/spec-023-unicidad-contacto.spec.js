import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-unicidad-contacto.spec.js
 * Feature: Unicidad de teléfono y correo en Clientes y Proveedores con error por campo en modal
 * Botica San Miguel (AyE)
 */
test.describe('Spec 023: Unicidad de teléfono y correo en Clientes y Proveedores', () => {

  async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
  }

  test('Flujo Clientes: bloqueo de correo y teléfono duplicados en modal y persistencia de edición propia', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    const ts = Date.now().toString().slice(-6);
    const dniA = `7${ts}1`;
    const telA = `95${ts}1`;
    const correoA = `cliente.alfa.${ts}@example.com`;
    const nombreA = `Cliente Alfa ${ts}`;

    const dniB = `7${ts}2`;
    const telB = `95${ts}2`;
    const correoB = `cliente.beta.${ts}@example.com`;
    const nombreB = `Cliente Beta ${ts}`;

    // 1. Crear cliente A con teléfono y correo únicos desde el modal
    await page.goto('/clientes');
    await page.waitForLoadState('networkidle');
    await page.getByRole('link', { name: /Nuevo cliente/i }).click();

    const modalCliente = page.locator('#modal-nuevo-cliente');
    await expect(modalCliente).toHaveClass(/activo|on/);

    await modalCliente.locator('#modal-numeroDocumento').fill(dniA);
    await modalCliente.locator('#modal-nombreCompleto').fill(nombreA);
    await modalCliente.locator('#modal-telefono').fill(` +51 ${telA.slice(0, 3)} ${telA.slice(3, 6)} ${telA.slice(6)} `);
    await modalCliente.locator('#modal-correo').fill(` ${correoA.toUpperCase()} `);

    await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/clientes') && resp.request().method() === 'POST'),
      modalCliente.getByRole('button', { name: /Guardar cliente/i }).click()
    ]);

    // Verificamos que se haya registrado y aparezca en el listado
    await page.goto('/clientes', { waitUntil: 'domcontentloaded' });
    await expect(page.locator(`text=${nombreA}`)).toBeVisible();

    // 2. Intentar crear cliente B con el mismo correo de A
    await page.getByRole('link', { name: /Nuevo cliente/i }).click();
    await expect(modalCliente).toHaveClass(/activo|on/);

    await modalCliente.locator('#modal-numeroDocumento').fill(dniB);
    await modalCliente.locator('#modal-nombreCompleto').fill(nombreB);
    await modalCliente.locator('#modal-telefono').fill(telB);
    await modalCliente.locator('#modal-correo').fill(correoA); // Mismo correo que A

    await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/clientes') && resp.request().method() === 'POST'),
      modalCliente.getByRole('button', { name: /Guardar cliente/i }).click()
    ]);

    // El modal NO se cierra, conserva lo escrito
    await expect(modalCliente).toHaveClass(/activo|on/);
    await expect(modalCliente.locator('#modal-numeroDocumento')).toHaveValue(dniB);
    await expect(modalCliente.locator('#modal-nombreCompleto')).toHaveValue(nombreB);
    await expect(modalCliente.locator('#modal-correo')).toHaveValue(correoA);

    // Error bajo el campo correo con el nombre de A
    const inputCorreo = modalCliente.locator('#modal-correo');
    await expect(inputCorreo).toHaveAttribute('aria-invalid', 'true');
    await expect(inputCorreo.locator('xpath=ancestor::div[contains(@class, "inp")]')).toHaveClass(/err/);

    const errorCorreo = modalCliente.locator('#error-modal-correo');
    await expect(errorCorreo).toBeVisible();
    await expect(errorCorreo).toHaveText(`Este correo ya está registrado a nombre de ${nombreA}.`);

    // Foco en el primer campo con error
    await expect(inputCorreo).toBeFocused();

    // Captura de pantalla para la evidencia obligatoria
    await page.screenshot({ path: 'e2e/evidencias/visual/23-unicidad-contacto.png' });

    // 3. Cambiar correo a uno libre, pero poner el teléfono de A
    await modalCliente.locator('#modal-correo').fill(correoB);
    await modalCliente.locator('#modal-telefono').fill(`+51 ${telA}`); // Mismo teléfono que A

    await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/clientes') && resp.request().method() === 'POST'),
      modalCliente.getByRole('button', { name: /Guardar cliente/i }).click()
    ]);

    // El modal NO se cierra
    await expect(modalCliente).toHaveClass(/activo|on/);
    const inputTelefono = modalCliente.locator('#modal-telefono');
    await expect(inputTelefono).toHaveAttribute('aria-invalid', 'true');
    await expect(inputTelefono.locator('xpath=ancestor::div[contains(@class, "inp")]')).toHaveClass(/err/);

    const errorTelefono = modalCliente.locator('#error-modal-telefono');
    await expect(errorTelefono).toBeVisible();
    await expect(errorTelefono).toHaveText(`Este teléfono ya está registrado a nombre de ${nombreA}.`);
    await expect(inputTelefono).toBeFocused();

    // Cerrar el modal
    await modalCliente.locator('.btn-cerrar-modal').first().click();
    await expect(modalCliente).not.toHaveClass(/activo|on/);

    // 4. Editar cliente A sin cambiar teléfono/correo -> guarda con éxito
    await page.locator(`tr:has-text("${nombreA}") a[title="Editar cliente"]`).click();
    await expect(modalCliente).toHaveClass(/activo|on/);
    await expect(modalCliente.locator('h3')).toHaveText(`Editar ${nombreA}`);

    await modalCliente.getByRole('button', { name: /Guardar cambios/i }).click();
    await expect(modalCliente).not.toHaveClass(/activo|on/);
    await expect(page.locator(`tr:has-text("${nombreA}")`)).toBeVisible();
  });

  test('Flujo Proveedores: bloqueo de correo y teléfono duplicados en modal y edición propia', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    const ts = Date.now().toString().slice(-6);
    const rucA = `20${ts}001`;
    const telA = `0145${ts.slice(-4)}`;
    const correoA = `prov.alfa.${ts}@laboratorio.pe`;
    const razonA = `Laboratorios Alfa ${ts} S.A.C.`;

    const rucB = `20${ts}002`;
    const telB = `0146${ts.slice(-4)}`;
    const correoB = `prov.beta.${ts}@laboratorio.pe`;
    const razonB = `Distribuidora Beta ${ts} S.A.C.`;

    // 1. Crear proveedor A con teléfono y correo únicos desde el modal
    await page.goto('/proveedores');
    await page.waitForLoadState('networkidle');
    await page.getByRole('link', { name: /Nuevo proveedor/i }).click();

    const modalProv = page.locator('#modal-nuevo-proveedor');
    await expect(modalProv).toHaveClass(/activo|on/);

    await modalProv.locator('#modal-ruc').fill(rucA);
    await modalProv.locator('#modal-razonSocial').fill(razonA);
    await modalProv.locator('#modal-telefono-prov').fill(` (01) 45-${ts.slice(-4)} `);
    await modalProv.locator('#modal-correo-prov').fill(` ${correoA.toUpperCase()} `);
    await modalProv.locator('#modal-direccion').fill('Av. Los Fresnos 123');

    await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/proveedores') && resp.request().method() === 'POST'),
      modalProv.getByRole('button', { name: /Guardar proveedor/i }).click()
    ]);

    // Verificamos que se haya registrado en el listado
    await page.goto('/proveedores');
    await page.waitForLoadState('networkidle');
    await expect(page.locator(`text=${razonA}`)).toBeVisible();

    // 2. Intentar crear proveedor B con el mismo correo de A
    await page.getByRole('link', { name: /Nuevo proveedor/i }).click();
    await expect(modalProv).toHaveClass(/activo|on/);

    await modalProv.locator('#modal-ruc').fill(rucB);
    await modalProv.locator('#modal-razonSocial').fill(razonB);
    await modalProv.locator('#modal-telefono-prov').fill(telB);
    await modalProv.locator('#modal-correo-prov').fill(correoA); // Mismo correo que A

    await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/proveedores') && resp.request().method() === 'POST'),
      modalProv.getByRole('button', { name: /Guardar proveedor/i }).click()
    ]);

    // El modal NO se cierra
    await expect(modalProv).toHaveClass(/activo|on/);
    await expect(modalProv.locator('#modal-ruc')).toHaveValue(rucB);
    await expect(modalProv.locator('#modal-razonSocial')).toHaveValue(razonB);
    await expect(modalProv.locator('#modal-correo-prov')).toHaveValue(correoA);

    // Error bajo el campo correo con la razón social de A
    const inputCorreo = modalProv.locator('#modal-correo-prov');
    await expect(inputCorreo).toHaveAttribute('aria-invalid', 'true');
    await expect(inputCorreo.locator('xpath=ancestor::div[contains(@class, "inp")]')).toHaveClass(/err/);

    const errorCorreo = modalProv.locator('#error-modal-correo-prov');
    await expect(errorCorreo).toBeVisible();
    await expect(errorCorreo).toHaveText(`Este correo ya está registrado a nombre de ${razonA}.`);
    await expect(inputCorreo).toBeFocused();

    // 3. Cambiar correo a uno libre, poner el teléfono de A
    await modalProv.locator('#modal-correo-prov').fill(correoB);
    await modalProv.locator('#modal-telefono-prov').fill(telA); // Mismo teléfono que A

    await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/proveedores') && resp.request().method() === 'POST'),
      modalProv.getByRole('button', { name: /Guardar proveedor/i }).click()
    ]);

    // El modal NO se cierra
    await expect(modalProv).toHaveClass(/activo|on/);
    const inputTel = modalProv.locator('#modal-telefono-prov');
    await expect(inputTel).toHaveAttribute('aria-invalid', 'true');
    await expect(inputTel.locator('xpath=ancestor::div[contains(@class, "inp")]')).toHaveClass(/err/);

    const errorTel = modalProv.locator('#error-modal-telefono-prov');
    await expect(errorTel).toBeVisible();
    await expect(errorTel).toHaveText(`Este teléfono ya está registrado a nombre de ${razonA}.`);
    await expect(inputTel).toBeFocused();

    // Cerrar el modal
    await modalProv.locator('.btn-cerrar-modal').first().click();
    await expect(modalProv).not.toHaveClass(/activo|on/);

    // 4. Editar proveedor A sin cambiar teléfono/correo -> guarda con éxito
    await page.locator(`tr:has-text("${razonA}") a[title="Editar proveedor"]`).click();
    await expect(modalProv).toHaveClass(/activo|on/);
    await expect(modalProv.locator('h3')).toHaveText(`Editar ${razonA}`);

    await modalProv.getByRole('button', { name: /Guardar cambios/i }).click();
    await expect(modalProv).not.toHaveClass(/activo|on/);
    await expect(page.locator(`tr:has-text("${razonA}")`)).toBeVisible();
  });
});
