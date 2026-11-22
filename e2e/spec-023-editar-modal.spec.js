import { test, expect } from '@playwright/test';

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
}

test.describe('AJUSTE 023: Editar en modal y subcategorías en árbol', () => {

  test('Medicamentos: lápiz abre modal con valores precargados, código readonly, validación y foco al cancelar', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/medicamentos');
    await page.waitForLoadState('domcontentloaded');

    const modalMed = page.locator('#modal-nuevo-medicamento');
    const primerLapiz = page.locator('table tbody tr:first-child a[title="Editar medicamento"]');
    await expect(primerLapiz).toBeVisible();

    // 1. Abrir modal desde lápiz
    await primerLapiz.click();
    await expect(modalMed).toHaveClass(/activo|on/);
    await expect(modalMed.locator('h3')).toContainText('Editar ');

    // 2. Campo código es readonly
    const inputCodigo = modalMed.locator('#modal-codigo-med');
    await expect(inputCodigo).toHaveAttribute('readonly');
    const valorCodigo = await inputCodigo.inputValue();
    expect(valorCodigo.length).toBeGreaterThan(0);

    // 3. Foco inicial en primer campo editable (nombre)
    const inputNombre = modalMed.locator('#modal-nombre-med');
    await expect(inputNombre).toBeFocused();

    // 4. Intentar guardar con nombre solo de espacios -> Error HTTP 400 y modal sigue abierto
    await inputNombre.fill('   ');
    await modalMed.locator('button[type="submit"]').click();

    await expect(modalMed).toHaveClass(/activo|on/);
    await expect(inputNombre).toHaveAttribute('aria-invalid', 'true');
    await expect(modalMed.locator('#error-modal-nombre-med')).toBeVisible();

    // 5. Cancelar con tecla Escape -> modal se cierra y foco vuelve al lápiz
    await page.keyboard.press('Escape');
    await expect(modalMed).not.toHaveClass(/activo|on/);
    await expect(primerLapiz).toBeFocused();

    // 6. Botón verde "+ Nuevo medicamento" abre en modo crear
    const btnNuevo = page.getByRole('link', { name: /Nuevo medicamento/i });
    await btnNuevo.click();
    await expect(modalMed).toHaveClass(/activo|on/);
    await expect(modalMed.locator('h3')).toHaveText('Nuevo medicamento');
    await expect(modalMed.locator('#modal-codigo-med')).not.toHaveAttribute('readonly');
    await modalMed.locator('.btn-cerrar-modal').first().click();
    await expect(modalMed).not.toHaveClass(/activo|on/);
  });

  test('Clientes: lápiz abre modal precargado y guarda con éxito manteniendo listado', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/clientes');
    await page.waitForLoadState('domcontentloaded');

    const modalCli = page.locator('#modal-nuevo-cliente');
    const primerLapiz = page.locator('table tbody tr:first-child a[title="Editar cliente"]');
    await expect(primerLapiz).toBeVisible();

    await primerLapiz.click();
    await expect(modalCli).toHaveClass(/activo|on/);
    await expect(modalCli.locator('h3')).toContainText('Editar ');

    const inputDoc = modalCli.locator('#modal-numeroDocumento');
    expect((await inputDoc.inputValue()).length).toBeGreaterThan(0);

    const inputNombre = modalCli.locator('#modal-nombreCompleto');
    const nombreOriginal = await inputNombre.inputValue();

    // Guardar cambios sin modificar
    await modalCli.getByRole('button', { name: /Guardar cambios/i }).click();
    await expect(modalCli).not.toHaveClass(/activo|on/);
    await expect(page.locator(`text=${nombreOriginal}`).first()).toBeVisible();
  });

  test('Proveedores: lápiz abre modal precargado y guarda con éxito', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/proveedores');
    await page.waitForLoadState('domcontentloaded');

    const modalProv = page.locator('#modal-nuevo-proveedor');
    const primerLapiz = page.locator('table tbody tr:first-child a[title="Editar proveedor"]');
    await expect(primerLapiz).toBeVisible();

    await primerLapiz.click();
    await expect(modalProv).toHaveClass(/activo|on/);
    await expect(modalProv.locator('h3')).toContainText('Editar ');

    const inputRuc = modalProv.locator('#modal-ruc');
    expect((await inputRuc.inputValue()).length).toBe(11);

    await modalProv.getByRole('button', { name: /Guardar cambios/i }).click();
    await expect(modalProv).not.toHaveClass(/activo|on/);
  });

  test('Usuarios: lápiz abre modal con "Editar a <nombre>", sin contraseña y usuario readonly', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/usuarios');
    await page.waitForLoadState('domcontentloaded');

    const modalUser = page.locator('#modal-nuevo-usuario');
    const primerLapiz = page.locator('table tbody tr:first-child a[title="Editar usuario"]');
    await expect(primerLapiz).toBeVisible();

    await primerLapiz.click();
    await expect(modalUser).toHaveClass(/activo|on/);
    await expect(modalUser.locator('h3')).toContainText('Editar a ');

    // Campo usuario readonly
    const inputLogin = modalUser.locator('#modal-nombreUsuario');
    await expect(inputLogin).toHaveAttribute('readonly');

    // Campo contraseña oculto
    const contenedorClave = modalUser.locator('#modal-contenedor-clave');
    await expect(contenedorClave).toBeHidden();

    // Botón cancelar
    await modalUser.locator('.btn-cerrar-modal').first().click();
    await expect(modalUser).not.toHaveClass(/activo|on/);
  });

  test('Categorías: "+" abre subcategoría con padre, textos sin jerga y resaltado tras guardar', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/categorias');
    await page.waitForLoadState('domcontentloaded');

    const modalCat = page.locator('#modal-nueva-categoria');

    // 1. Textos sin jerga en modal
    const primerPlus = page.locator('.tr:first-child a.boton-subcategoria');
    await expect(primerPlus).toBeVisible();
    await primerPlus.click();

    await expect(modalCat).toHaveClass(/activo|on/);
    await expect(modalCat.locator('h3')).toContainText('Nueva subcategoría de ');
    await expect(modalCat.locator('.mh p')).toHaveText('Ponle un nombre y, si quieres, elige a qué categoría pertenece.');
    await expect(modalCat.locator('label[for="modal-padre-cat"]')).toHaveText('Categoría principal (opcional)');
    await expect(modalCat.locator('#modal-padre-cat option[value=""]')).toHaveText('— Ninguna (será una categoría principal) —');
    await expect(modalCat.locator('.help:has-text("Déjalo vacío")')).toHaveText('Déjalo vacío si es un grupo principal.');

    // 2. Crear subcategoría y verificar resaltado
    const ts = Date.now().toString().slice(-4);
    const nombreSub = `SubTest_${ts}`;
    await modalCat.locator('#modal-nombre-cat').fill(nombreSub);
    await modalCat.locator('button[type="submit"]').click();

    await expect(modalCat).not.toHaveClass(/activo|on/);

    // Verificar que aparece en el árbol y tiene la clase categoria-resaltada
    const filaNueva = page.locator(`.tr:has-text("${nombreSub}")`);
    await expect(filaNueva).toBeVisible();
    await expect(filaNueva).toHaveClass(/categoria-resaltada/);

    // 3. Botón lápiz de la nueva categoría abre editar
    const lapizNueva = filaNueva.locator('a.boton-icono');
    await lapizNueva.click();
    await expect(modalCat).toHaveClass(/activo|on/);
    await expect(modalCat.locator('h3')).toHaveText(`Editar ${nombreSub}`);
    await modalCat.locator('.btn-cerrar-modal').first().click();
  });

  test('Responsive: modal de edición en Medicamentos a 320 px sin scroll horizontal', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 320, height: 600 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    await page.goto('/medicamentos');
    await page.waitForLoadState('domcontentloaded');

    const primerLapiz = page.locator('table tbody tr:first-child a[title="Editar medicamento"]');
    await primerLapiz.click();

    const modalMed = page.locator('#modal-nuevo-medicamento');
    await expect(modalMed).toHaveClass(/activo|on/);

    const sinScroll = await page.evaluate(() => {
      return document.documentElement.scrollWidth <= window.innerWidth;
    });
    expect(sinScroll).toBe(true);
  });

  test('Capturas visuales requeridas 25 y 26 a 1600x900', async ({ page }) => {
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 25-editar-modal.png: modal de editar Medicamento abierto sobre el listado
    await page.goto('/medicamentos');
    await page.waitForLoadState('domcontentloaded');
    const primerLapiz = page.locator('table tbody tr:first-child a[title="Editar medicamento"]');
    await primerLapiz.click();
    const modalMed = page.locator('#modal-nuevo-medicamento');
    await expect(modalMed).toHaveClass(/activo|on/);
    await page.waitForTimeout(500);
    await page.screenshot({ path: 'e2e/evidencias/visual/25-editar-modal.png' });
    await page.keyboard.press('Escape');

    // 26-subcategoria-modal.png: modal "Nueva subcategoría de ..." abierto sobre el árbol
    await page.goto('/categorias');
    await page.waitForLoadState('domcontentloaded');
    const primerPlus = page.locator('.tr:first-child a.boton-subcategoria');
    await primerPlus.click();
    const modalCat = page.locator('#modal-nueva-categoria');
    await expect(modalCat).toHaveClass(/activo|on/);
    await page.waitForTimeout(500);
    await page.screenshot({ path: 'e2e/evidencias/visual/26-subcategoria-modal.png' });
  });
});
