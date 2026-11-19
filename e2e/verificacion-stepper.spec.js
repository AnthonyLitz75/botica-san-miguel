import { test, expect } from '@playwright/test';

test.describe('Verificación del Stepper de Cantidad en Caja POS (AyE)', () => {

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

    // 3. Aislamiento: limpiar canasta si había preparación previa
    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    if (await btnCancelarVenta.isEnabled()) {
      await btnCancelarVenta.click();
      await expect(page.locator('#estado-vacio')).toBeVisible();
    }

    // 4. Agregar Paracetamol 500 mg (P.U. S/ 8.50)
    const inputBuscador = page.locator('#buscar-caja');
    await inputBuscador.fill('para');
    const primeraOpcion = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(primeraOpcion).toBeVisible();
    await primeraOpcion.click();

    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#contador-canasta')).toContainText('1 producto');
  });

  test('debe verificar incremento, decremento, no menor a 1 y coherencia de descuentos con el stepper', async ({ page }) => {
    const btnStepUp = page.locator('.btn-step-up').first();
    const btnStepDown = page.locator('.btn-step-down').first();
    const cantidadBadge = page.locator('.stepper b').first();
    const subtotalVenta = page.locator('#subtotal-venta');
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    const totalVenta = page.locator('#total-venta');

    // Estado inicial: Cantidad = 1, Subtotal = S/ 8.50, Total = S/ 8.50
    await expect(cantidadBadge).toHaveText('1');
    await expect(subtotalVenta).toHaveText('S/ 8.50');
    await expect(totalVenta).toHaveText('S/ 8.50');

    // 1. INCREMENTAR CANTIDAD: 1 -> 2 (invoca PUT /ventas/preparacion/1/cantidad)
    await btnStepUp.click();
    await expect(cantidadBadge).toHaveText('2');
    await expect(subtotalVenta).toHaveText('S/ 17.00');
    await expect(totalVenta).toHaveText('S/ 17.00');

    // Incrementar de nuevo: 2 -> 3
    await btnStepUp.click();
    await expect(cantidadBadge).toHaveText('3');
    await expect(subtotalVenta).toHaveText('S/ 25.50');
    await expect(totalVenta).toHaveText('S/ 25.50');
    await page.screenshot({ path: 'e2e/evidencias/04a-stepper-incrementado-3.png' });

    // 2. APLICAR DESCUENTO en el modal (S/ 6.00 sobre subtotal S/ 25.50)
    await btnEditarDesc.click();
    await page.locator('#input-valor-descuento').fill('6.00');
    await page.locator('#btn-aplicar-descuento').click();
    await expect(page.locator('#modal-descuento-backdrop')).toBeHidden();
    await expect(btnEditarDesc).toContainText('- S/ 6.00');
    await expect(totalVenta).toHaveText('S/ 19.50');

    // 3. MANTENER DESCUENTO PROPORCIONAL AL DECREMENTAR:
    // Cantidad: 3 -> 2. Subtotal anterior: S/ 25.50, nuevo subtotal: S/ 17.00.
    // Proporción: 6.00 * (17.00 / 25.50) = S/ 4.00. Nuevo total = S/ 13.00.
    await btnStepDown.click();
    await expect(cantidadBadge).toHaveText('2');
    await expect(subtotalVenta).toHaveText('S/ 17.00');
    await expect(btnEditarDesc).toContainText('- S/ 4.00');
    await expect(totalVenta).toHaveText('S/ 13.00');
    await page.screenshot({ path: 'e2e/evidencias/04b-stepper-decrementado-descuento-proporcional.png' });

    // 4. MANTENER DESCUENTO ABSOLUTO AL INCREMENTAR:
    // Cantidad: 2 -> 3. Al incrementar, el descuento de S/ 4.00 se mantiene fijo.
    // Nuevo subtotal = S/ 25.50, Descuento = S/ 4.00, Total = S/ 21.50.
    await btnStepUp.click();
    await expect(cantidadBadge).toHaveText('3');
    await expect(subtotalVenta).toHaveText('S/ 25.50');
    await expect(btnEditarDesc).toContainText('- S/ 4.00');
    await expect(totalVenta).toHaveText('S/ 21.50');
    await page.screenshot({ path: 'e2e/evidencias/04c-stepper-incrementado-descuento-fijo.png' });

    // 5. DECREMENTAR HASTA 1 Y VERIFICAR COMPORTAMIENTO INFERIOR A UNO:
    // 3 -> 2 (descuento proporcional: 4.00 * (17.00 / 25.50) = S/ 2.67, Total = S/ 14.33)
    await btnStepDown.click();
    await expect(cantidadBadge).toHaveText('2');
    await expect(subtotalVenta).toHaveText('S/ 17.00');
    await expect(btnEditarDesc).toContainText('- S/ 2.67');
    await expect(totalVenta).toHaveText('S/ 14.33');

    // 2 -> 1 (descuento proporcional: 2.67 * (8.50 / 17.00) = S/ 1.34, Total = S/ 7.16)
    await btnStepDown.click();
    await expect(cantidadBadge).toHaveText('1');
    await expect(subtotalVenta).toHaveText('S/ 8.50');
    await expect(btnEditarDesc).toContainText('- S/ 1.34');
    await expect(totalVenta).toHaveText('S/ 7.16');

    // Al estar en 1, hacer clic en decrement (-1):
    // El frontend detecta cant === 1 y retira la línea (no permite cantidad < 1)
    await btnStepDown.click();
    // La canasta debe quedar vacía (no existen cantidades 0 o negativas en la canasta)
    await expect(page.locator('#estado-vacio')).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/04d-stepper-retirar-en-uno.png' });
  });

});
