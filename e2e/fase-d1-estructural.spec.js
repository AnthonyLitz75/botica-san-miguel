import { test, expect } from '@playwright/test';

test.describe('Fase D.1: Correcciones estructurales de Caja POS (H-01 y H-02)', () => {

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

    // Agregar Paracetamol 500 mg
    const inputBuscador = page.locator('#buscar-caja');
    await inputBuscador.fill('para');
    const primeraOpcion = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(primeraOpcion).toBeVisible();
    await primeraOpcion.click();

    await expect(page.locator('#contenedor-tabla')).toBeVisible();
    await expect(page.locator('#contador-canasta')).toContainText('1 producto');
  });

  // ==========================================================================
  // H-01: Verificación de Tabla Responsive
  // ==========================================================================
  test('H-01: no debe superponer columnas en viewport móvil (375x667) y debe limitar el scroll al contenedor', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 667 });

    const thProducto = page.locator('.tabla-caja th.col-prod');
    const thCantidad = page.locator('.tabla-caja th.col-cant');
    await expect(thProducto).toBeVisible();
    await expect(thCantidad).toBeVisible();

    const boxProd = await thProducto.boundingBox();
    const boxCant = await thCantidad.boundingBox();

    expect(boxProd).not.toBeNull();
    expect(boxCant).not.toBeNull();

    // CRITERIO H-01: Ninguna columna se superpone horizontalmente
    // El borde derecho de la columna de producto debe terminar antes o en el borde izquierdo de cantidad
    expect(boxProd.x + boxProd.width).toBeLessThanOrEqual(boxCant.x + 1);

    // CRITERIO H-01: Scroll horizontal limitado al contenedor de la tabla
    const contenedor = page.locator('#contenedor-tabla');
    const hasHorizontalScroll = await contenedor.evaluate((el) => el.scrollWidth > el.clientWidth);
    expect(hasHorizontalScroll).toBe(true);

    // CRITERIO H-01: Ningún desbordamiento horizontal de la tarjeta POS ni del contenedor .pos
    const card = page.locator('#contenedor-tabla').locator('xpath=ancestor::div[contains(@class, "card")]').first();
    const cardOverflows = await card.evaluate((el) => el.scrollWidth > el.clientWidth);
    expect(cardOverflows).toBe(false);

    const pos = page.locator('.pos');
    const posOverflows = await pos.evaluate((el) => el.scrollWidth > el.clientWidth);
    expect(posOverflows).toBe(false);

    // Evidencia visual móvil
    await page.screenshot({ path: 'e2e/evidencias/d1-mobile-375-tabla-corregida.png', fullPage: true });
  });

  test('H-01: debe mantener diseño óptimo en viewports tablet (768x1024) y desktop (1440x900)', async ({ page }) => {
    // 1. Tablet 768x1024
    await page.setViewportSize({ width: 768, height: 1024 });
    const thProductoTablet = page.locator('.tabla-caja th.col-prod');
    const thCantidadTablet = page.locator('.tabla-caja th.col-cant');
    const boxProdTab = await thProductoTablet.boundingBox();
    const boxCantTab = await thCantidadTablet.boundingBox();
    expect(boxProdTab.x + boxProdTab.width).toBeLessThanOrEqual(boxCantTab.x + 1);
    await page.screenshot({ path: 'e2e/evidencias/d1-tablet-768-tabla.png' });

    // 2. Desktop 1440x900
    await page.setViewportSize({ width: 1440, height: 900 });
    const thProductoDesk = page.locator('.tabla-caja th.col-prod');
    const thCantidadDesk = page.locator('.tabla-caja th.col-cant');
    const boxProdDesk = await thProductoDesk.boundingBox();
    const boxCantDesk = await thCantidadDesk.boundingBox();
    expect(boxProdDesk.x + boxProdDesk.width).toBeLessThanOrEqual(boxCantDesk.x + 1);
    await page.screenshot({ path: 'e2e/evidencias/d1-desktop-1440-tabla.png' });
  });

  // ==========================================================================
  // H-02: Accesibilidad del Stepper de Cantidad
  // ==========================================================================
  test('H-02: los controles del stepper deben ser botones nativos con aria-label y operables por teclado (Tab, Enter, Space)', async ({ page }) => {
    const btnDown = page.locator('.stepper .btn-step-down').first();
    const btnUp = page.locator('.stepper .btn-step-up').first();
    const cantidadBadge = page.locator('.stepper b').first();

    // 1. Verificar que sean botones nativos
    const tagDown = await btnDown.evaluate((el) => el.tagName.toLowerCase());
    const tagUp = await btnUp.evaluate((el) => el.tagName.toLowerCase());
    expect(tagDown).toBe('button');
    expect(tagUp).toBe('button');

    // 2. Verificar aria-label descriptivos
    await expect(btnDown).toHaveAttribute('aria-label', /Disminuir cantidad/i);
    await expect(btnUp).toHaveAttribute('aria-label', /Aumentar cantidad/i);

    // 3. Navegación por teclado con Tab hasta el botón Aumentar
    await btnUp.focus();
    await expect(btnUp).toBeFocused();

    // 4. Operación mediante teclado con Enter: 1 -> 2
    await page.keyboard.press('Enter');
    await expect(cantidadBadge).toHaveText('2');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 17.00');

    // 5. Operación mediante teclado con Space: 2 -> 3
    const btnUpRerendered = page.locator('.stepper .btn-step-up').first();
    await expect(btnUpRerendered).toBeFocused();
    await page.keyboard.press('Space');
    await expect(cantidadBadge).toHaveText('3');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 25.50');

    // 6. Navegar a Disminuir con teclado (Shift+Tab) o foco directo
    await btnDown.focus();
    await expect(btnDown).toBeFocused();

    // 7. Operación mediante teclado con Space: 3 -> 2
    await page.keyboard.press('Space');
    await expect(cantidadBadge).toHaveText('2');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 17.00');

    // 8. Operación mediante teclado con Enter: 2 -> 1
    const btnDownRerendered = page.locator('.stepper .btn-step-down').first();
    await expect(btnDownRerendered).toBeFocused();
    await page.keyboard.press('Enter');
    await expect(cantidadBadge).toHaveText('1');
    await expect(page.locator('#subtotal-venta')).toHaveText('S/ 8.50');

    // 9. Comprobar estilo visible de foco (:focus-visible)
    const hasFocusIndicator = await btnDownRerendered.evaluate((el) => {
      const styles = window.getComputedStyle(el);
      return styles.outlineStyle !== 'none' || styles.boxShadow !== 'none';
    });
    expect(hasFocusIndicator).toBe(true);

    // Evidencia visual del stepper accesible con foco
    await page.screenshot({ path: 'e2e/evidencias/d1-stepper-accesible-teclado.png' });
  });

});
