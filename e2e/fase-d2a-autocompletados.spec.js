import { test, expect } from '@playwright/test';

test.describe('Fase D.2A: Corrección de Autocompletados en Caja POS (H-03 y H-04)', () => {

  test.beforeEach(async ({ page }) => {
    // Autenticación como admin_demo
    await page.goto('/acceso/login');
    const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
    const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => url.pathname.includes('/medicamentos'));

    // Navegar a Caja
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();

    // Limpiar preparación previa si existía
    const btnCancelar = page.locator('#btn-cancelar-venta');
    if (await btnCancelar.isEnabled()) {
      await btnCancelar.click();
      await expect(page.locator('#estado-vacio')).toBeVisible();
    }
  });

  // ==========================================================================
  // H-03: Autocompletado de Medicamentos
  // ==========================================================================
  test('H-03: no debe mostrar sugerencias con búsqueda vacía ni con espacios, y debe ocultarse al seleccionar', async ({ page }) => {
    const inputBuscar = page.locator('#buscar-caja');
    const sugerenciasCaja = page.locator('#sugerencias-caja');

    // 1. Estado inicial al cargar la página: debe estar OCULTO
    await expect(sugerenciasCaja).toBeHidden();
    await page.screenshot({ path: 'e2e/evidencias/d2a-h03-busqueda-vacia-oculta.png' });

    // 2. Al enfocar el input vacío: debe permanecer OCULTO
    await inputBuscar.focus();
    await expect(sugerenciasCaja).toBeHidden();

    // 3. Al ingresar únicamente espacios en blanco: debe permanecer OCULTO
    await inputBuscar.fill('   ');
    await expect(sugerenciasCaja).toBeHidden();

    // 4. Al ingresar consulta válida: debe MOSTRARSE
    await inputBuscar.fill('para');
    await expect(sugerenciasCaja).toBeVisible();
    const opciones = sugerenciasCaja.locator('.o:not([style*="not-allowed"])');
    await expect(opciones.first()).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/d2a-h03-busqueda-activa-desplegada.png' });

    // 5. Al seleccionar un medicamento: debe OCULTARSE y agregarse a la canasta
    await opciones.first().click();
    await expect(sugerenciasCaja).toBeHidden();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#contador-canasta')).toContainText('1 producto');
    await expect(inputBuscar).toHaveValue('');
    await page.screenshot({ path: 'e2e/evidencias/d2a-h03-post-seleccion-oculta.png' });

    // 6. Al volver a escribir y borrar el texto: debe volver a OCULTARSE
    await inputBuscar.fill('ibu');
    await expect(sugerenciasCaja).toBeVisible();
    await inputBuscar.fill('');
    await expect(sugerenciasCaja).toBeHidden();
  });

  // ==========================================================================
  // H-04: Superposición del Autocompletado de Clientes
  // ==========================================================================
  test('H-04: el autocompletado de clientes no debe superponerse con el resumen del cliente (Desktop 1440x900)', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });

    const inputCliente = page.locator('#cliente-caja');
    const sugerenciasCliente = page.locator('#sugerencias-cliente');
    const tarjetaResumen = page.locator('#tarjeta-resumen-cliente');

    await expect(tarjetaResumen).toBeVisible();
    await expect(sugerenciasCliente).toBeHidden();

    // Desplegar sugerencias de clientes
    await inputCliente.fill('alberto');
    await expect(sugerenciasCliente).toBeVisible();
    await expect(tarjetaResumen).toBeVisible();

    const boxSugg = await sugerenciasCliente.boundingBox();
    const boxResumen = await tarjetaResumen.boundingBox();

    expect(boxSugg).not.toBeNull();
    expect(boxResumen).not.toBeNull();

    // CRITERIO H-04: El desplegable NO debe superponerse ni ocultar la tarjeta de resumen
    // El borde inferior del desplegable debe estar por encima o alineado con el inicio del resumen, sin solape
    expect(boxSugg.y + boxSugg.height).toBeLessThanOrEqual(boxResumen.y + 1);

    await page.screenshot({ path: 'e2e/evidencias/d2a-desktop-1440-autocompletado-cliente.png' });
  });

  test('H-04: el autocompletado de clientes debe posicionarse sin solape en Tablet (768x1024) y Móvil (375x667)', async ({ page }) => {
    const inputCliente = page.locator('#cliente-caja');
    const sugerenciasCliente = page.locator('#sugerencias-cliente');
    const tarjetaResumen = page.locator('#tarjeta-resumen-cliente');

    // 1. Tablet 768x1024
    await page.setViewportSize({ width: 768, height: 1024 });
    await inputCliente.fill('alberto');
    await expect(sugerenciasCliente).toBeVisible();
    await expect(tarjetaResumen).toBeVisible();

    const boxSuggTab = await sugerenciasCliente.boundingBox();
    const boxResumenTab = await tarjetaResumen.boundingBox();
    expect(boxSuggTab.y + boxSuggTab.height).toBeLessThanOrEqual(boxResumenTab.y + 1);
    await page.screenshot({ path: 'e2e/evidencias/d2a-tablet-768-autocompletado-cliente.png' });

    // 2. Móvil 375x667
    await page.setViewportSize({ width: 375, height: 667 });
    await expect(sugerenciasCliente).toBeVisible();
    await expect(tarjetaResumen).toBeVisible();

    const boxSuggMob = await sugerenciasCliente.boundingBox();
    const boxResumenMob = await tarjetaResumen.boundingBox();
    expect(boxSuggMob.y + boxSuggMob.height).toBeLessThanOrEqual(boxResumenMob.y + 1);
    await page.screenshot({ path: 'e2e/evidencias/d2a-mobile-375-autocompletado-cliente.png' });
  });

  test('H-03 y H-04: selección mediante teclado (ArrowDown, Enter) en medicamentos y clientes', async ({ page }) => {
    // 1. Selección de Medicamento con teclado
    const inputBuscar = page.locator('#buscar-caja');
    const sugerenciasCaja = page.locator('#sugerencias-caja');
    await inputBuscar.fill('para');
    await expect(sugerenciasCaja).toBeVisible();

    await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');

    await expect(sugerenciasCaja).toBeHidden();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#contador-canasta')).toContainText('1 producto');
    await expect(inputBuscar).toHaveValue('');

    // 2. Selección de Cliente con teclado
    const inputCliente = page.locator('#cliente-caja');
    const sugerenciasCliente = page.locator('#sugerencias-cliente');
    await inputCliente.fill('alberto');
    await expect(sugerenciasCliente).toBeVisible();

    await page.keyboard.press('Enter');
    await expect(sugerenciasCliente).toBeHidden();
    await expect(page.locator('#nombre-cliente-mostrado')).toContainText('Alberto');
  });

});
