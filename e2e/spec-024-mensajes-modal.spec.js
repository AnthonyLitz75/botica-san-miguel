import { test, expect } from '@playwright/test';

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
}

test.describe('Spec 024: Avisos flash tras operaciones en modal AJAX', () => {

  test('Usuarios: crear y editar muestran banner de éxito .aviso-operacion.aviso-exito', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    await page.goto('/usuarios');
    await page.waitForLoadState('domcontentloaded');

    const ts = Date.now().toString().slice(-4);
    const loginUser = `user_p_${ts}`;

    // 1. Crear usuario
    const btnNuevo = page.locator('a[data-modal-open="modal-nuevo-usuario"][data-modo="crear"]');
    await btnNuevo.click();

    const modalUser = page.locator('#modal-nuevo-usuario');
    await expect(modalUser).toHaveClass(/activo|on/);
    await page.locator('#modal-nombre-user').fill(`Usuario Probe ${ts}`);
    await page.locator('#modal-nombreUsuario').fill(loginUser);
    await page.locator('#modal-clave-user').fill('ClaveValida2026!');
    await modalUser.locator('button[type="submit"]').click();

    const avisoCrear = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoCrear).toBeVisible({ timeout: 15000 });
    await expect(avisoCrear).toContainText('Usuario registrado correctamente.');

    // 2. Editar usuario
    let filaUser = page.locator(`table tbody tr:has(.mono:text("${loginUser}"))`);
    if (await filaUser.count() === 0) {
      await page.goto('/usuarios?pagina=2', { waitUntil: 'domcontentloaded' });
      filaUser = page.locator(`table tbody tr:has(.mono:text("${loginUser}"))`);
    }
    await expect(filaUser).toBeVisible();

    const lapizUser = filaUser.locator('a[title="Editar usuario"]');
    await lapizUser.click();
    await expect(modalUser).toHaveClass(/activo|on/);

    await page.locator('#modal-nombre-user').fill(`Usuario Probe ${ts} Modificado`);
    await modalUser.locator('button[type="submit"]').click();

    const avisoEditar = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoEditar).toBeVisible({ timeout: 15000 });
    await expect(avisoEditar).toContainText('Cambios del usuario guardados correctamente.');
  });

  test('Clientes: crear y editar muestran banner de éxito .aviso-operacion.aviso-exito', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    await page.goto('/clientes');
    await page.waitForLoadState('domcontentloaded');

    const ts = (Date.now() % 100000000).toString().padStart(8, '7');
    const nombreCliente = `Cliente Probe ${ts}`;

    // 1. Crear cliente
    const btnNuevo = page.locator('a[data-modal-open="modal-nuevo-cliente"][data-modo="crear"]');
    await btnNuevo.click();

    const modalCli = page.locator('#modal-nuevo-cliente');
    await expect(modalCli).toHaveClass(/activo|on/);
    await page.locator('#modal-numeroDocumento').fill(ts);
    await page.locator('#modal-nombreCompleto').fill(nombreCliente);
    await page.locator('#modal-telefono').fill(`9${ts.slice(0, 8)}`);
    await modalCli.locator('button[type="submit"]').click();

    const avisoCrear = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoCrear).toBeVisible({ timeout: 15000 });
    await expect(avisoCrear).toContainText('Cliente registrado correctamente.');

    // 2. Editar cliente
    const filaCli = page.locator(`table tbody tr:has-text("${nombreCliente}")`);
    await expect(filaCli).toBeVisible();

    const lapizCli = filaCli.locator('a[title="Editar cliente"]');
    await lapizCli.click();
    await expect(modalCli).toHaveClass(/activo|on/);

    await page.locator('#modal-nombreCompleto').fill(`${nombreCliente} Mod`);
    await modalCli.locator('button[type="submit"]').click();

    const avisoEditar = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoEditar).toBeVisible({ timeout: 15000 });
    await expect(avisoEditar).toContainText('Cambios del cliente guardados correctamente.');
  });

  test('Proveedores: crear y editar muestran banner de éxito .aviso-operacion.aviso-exito', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    await page.goto('/proveedores');
    await page.waitForLoadState('domcontentloaded');

    const ts = (Date.now() % 100000000).toString().padStart(8, '6');
    const ruc = `20${ts}1`;
    const razonSocial = `Drogueria Probe ${ts} SAC`;

    // 1. Crear proveedor
    const btnNuevo = page.locator('a[data-modal-open="modal-nuevo-proveedor"][data-modo="crear"]');
    await btnNuevo.click();

    const modalProv = page.locator('#modal-nuevo-proveedor');
    await expect(modalProv).toHaveClass(/activo|on/);
    await page.locator('#modal-ruc').fill(ruc);
    await page.locator('#modal-razonSocial').fill(razonSocial);
    await page.locator('#modal-telefono-prov').fill(`01${ts.slice(0, 7)}`);
    await page.locator('#modal-correo-prov').fill(`probe${ts}@drogueria.pe`);
    await modalProv.locator('button[type="submit"]').click();

    const avisoCrear = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoCrear).toBeVisible({ timeout: 15000 });
    await expect(avisoCrear).toContainText('Proveedor registrado correctamente.');

    // 2. Editar proveedor
    const filaProv = page.locator(`table tbody tr:has-text("${razonSocial}")`);
    await expect(filaProv).toBeVisible();

    const lapizProv = filaProv.locator('a[title="Editar proveedor"]');
    await lapizProv.click();
    await expect(modalProv).toHaveClass(/activo|on/);

    await page.locator('#modal-razonSocial').fill(`${razonSocial} Renovada`);
    await modalProv.locator('button[type="submit"]').click();

    const avisoEditar = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoEditar).toBeVisible({ timeout: 15000 });
    await expect(avisoEditar).toContainText('Cambios del proveedor guardados correctamente.');
  });

  test('Medicamentos: crear y editar muestran banner de éxito .aviso-operacion.aviso-exito', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    await page.goto('/medicamentos');
    await page.waitForLoadState('domcontentloaded');

    const ts = Date.now().toString().slice(-4);
    const codigoMed = `MED-PR-${ts}`;
    const nombreMed = `Fármaco Probe ${ts}`;

    // 1. Crear medicamento
    const btnNuevo = page.locator('a[data-modal-open="modal-nuevo-medicamento"][data-modo="crear"]');
    await btnNuevo.click();

    const modalMed = page.locator('#modal-nuevo-medicamento');
    await expect(modalMed).toHaveClass(/activo|on/);
    await page.locator('#modal-codigo-med').fill(codigoMed);
    await page.locator('#modal-nombre-med').fill(nombreMed);
    await page.locator('#modal-concentracion-med').fill('500 mg');
    await page.locator('#modal-presentacion-med').fill('Tableta');
    await page.locator('#modal-unidad-med').selectOption('CAJA');
    await page.locator('#modal-precio-med').fill('12.50');
    await page.locator('#modal-stockMinimo-med').fill('15');
    await modalMed.locator('button[type="submit"]').click();

    const avisoCrear = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoCrear).toBeVisible({ timeout: 15000 });
    await expect(avisoCrear).toContainText('Medicamento registrado correctamente.');

    // 2. Editar medicamento
    const filaMed = page.locator(`table tbody tr:has-text("${codigoMed}")`);
    await expect(filaMed).toBeVisible();

    const lapizMed = filaMed.locator('a[title="Editar medicamento"]');
    await lapizMed.click();
    await expect(modalMed).toHaveClass(/activo|on/);

    await page.locator('#modal-nombre-med').fill(`${nombreMed} Plus`);
    await modalMed.locator('button[type="submit"]').click();

    const avisoEditar = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoEditar).toBeVisible({ timeout: 15000 });
    await expect(avisoEditar).toContainText('Cambios del medicamento guardados correctamente.');
  });

  test('Categorías: crear principal, crear subcategoría y editar muestran banner de éxito', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page);

    await page.goto('/categorias');
    await page.waitForLoadState('domcontentloaded');

    const ts = Date.now().toString().slice(-4);
    const nombreCatPrincipal = `CatProbe_${ts}`;
    const nombreSubCat = `SubCatProbe_${ts}`;

    const modalCat = page.locator('#modal-nueva-categoria');

    // 1. Crear categoría principal
    const btnNueva = page.locator('a[data-modal-open="modal-nueva-categoria"][data-modo="crear"]');
    await btnNueva.click();
    await expect(modalCat).toHaveClass(/activo|on/);

    await page.locator('#modal-nombre-cat').fill(nombreCatPrincipal);
    await modalCat.locator('button[type="submit"]').click();

    const avisoCrear = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoCrear).toBeVisible({ timeout: 15000 });
    await expect(avisoCrear).toContainText('registrada exitosamente.');

    // 2. Crear subcategoría
    const filaPrincipal = page.locator(`.tr:has-text("${nombreCatPrincipal}")`);
    await expect(filaPrincipal).toBeVisible();

    const btnSub = filaPrincipal.locator('a.boton-subcategoria');
    await btnSub.click();
    await expect(modalCat).toHaveClass(/activo|on/);

    await page.locator('#modal-nombre-cat').fill(nombreSubCat);
    await modalCat.locator('button[type="submit"]').click();

    const avisoSub = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoSub).toBeVisible({ timeout: 15000 });
    await expect(avisoSub).toContainText('registrada exitosamente.');

    // 3. Editar subcategoría
    const filaSub = page.locator(`.tr:has-text("${nombreSubCat}")`);
    await expect(filaSub).toBeVisible();

    const lapizSub = filaSub.locator('a.boton-icono');
    await lapizSub.click();
    await expect(modalCat).toHaveClass(/activo|on/);

    await page.locator('#modal-nombre-cat').fill(`${nombreSubCat}_Mod`);
    await modalCat.locator('button[type="submit"]').click();

    const avisoEdit = page.locator('.aviso-operacion.aviso-exito');
    await expect(avisoEdit).toBeVisible({ timeout: 15000 });
    await expect(avisoEdit).toContainText('actualizada correctamente.');
  });
});
