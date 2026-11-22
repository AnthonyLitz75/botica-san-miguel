import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-historial-pulido.spec.js
 * Verificación de presentación de Historial de ventas: pie de paginación, filtros en una fila,
 * descuento en cero ("—") y tooltips de acciones.
 * Botica San Miguel (AyE)
 */
test.describe('Spec 023: Pulido visual de Historial de ventas y paginación', () => {

  test.beforeEach(async ({ page }) => {
    test.setTimeout(60000);
  });

  async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
  }

  async function asegurarCanastaLimpia(page) {
    await page.goto('/ventas/nueva');
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();

    const btnCancelarVenta = page.locator('#btn-cancelar-venta');
    if (await btnCancelarVenta.isEnabled()) {
      await btnCancelarVenta.click();
      const modalConfirmar = page.locator('#btn-confirmar-cancelar-venta');
      if (await modalConfirmar.isVisible()) {
        await modalConfirmar.click();
      }
      await expect(page.locator('#estado-vacio')).toBeVisible();
    }
  }

  test('a) Pie de paginación en una fila en escritorio (1920 y 1280 px), apilado a 320 px sin scroll (Historial y Movimientos)', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    const pantallas = ['/ventas', '/movimientos'];

    for (const pantalla of pantallas) {
      // 1. Escritorio 1920x1080
      await page.setViewportSize({ width: 1920, height: 1080 });
      await page.goto(pantalla, { waitUntil: 'domcontentloaded' });

      const texto1920 = page.locator('.paginacion-contenedor .resultados-pie');
      const nav1920 = page.locator('.paginacion-contenedor .paginacion-nav');
      await expect(texto1920).toBeVisible();
      await expect(nav1920).toBeVisible();

      const boxT1920 = await texto1920.boundingBox();
      const boxN1920 = await nav1920.boundingBox();
      expect(boxT1920).not.toBeNull();
      expect(boxN1920).not.toBeNull();

      // Misma fila: centros verticales coinciden con tolerancia (<= 15px) y nav a la derecha del texto
      const centroT1920 = boxT1920.y + boxT1920.height / 2;
      const centroN1920 = boxN1920.y + boxN1920.height / 2;
      expect(Math.abs(centroT1920 - centroN1920), `Centros verticales en 1920px deben coincidir en ${pantalla}`).toBeLessThan(15);
      expect(boxN1920.x, `El nav debe ubicarse a la derecha del texto en 1920px en ${pantalla}`).toBeGreaterThanOrEqual(boxT1920.x + boxT1920.width - 10);

      // 2. Escritorio 1280x800
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto(pantalla, { waitUntil: 'domcontentloaded' });

      const texto1280 = page.locator('.paginacion-contenedor .resultados-pie');
      const nav1280 = page.locator('.paginacion-contenedor .paginacion-nav');
      const boxT1280 = await texto1280.boundingBox();
      const boxN1280 = await nav1280.boundingBox();

      const centroT1280 = boxT1280.y + boxT1280.height / 2;
      const centroN1280 = boxN1280.y + boxN1280.height / 2;
      expect(Math.abs(centroT1280 - centroN1280), `Centros verticales en 1280px deben coincidir en ${pantalla}`).toBeLessThan(15);
      expect(boxN1280.x, `El nav debe ubicarse a la derecha del texto en 1280px en ${pantalla}`).toBeGreaterThanOrEqual(boxT1280.x + boxT1280.width - 10);

      // 3. Móvil 320x640
      await page.setViewportSize({ width: 320, height: 640 });
      await page.goto(pantalla, { waitUntil: 'domcontentloaded' });

      const texto320 = page.locator('.paginacion-contenedor .resultados-pie');
      const nav320 = page.locator('.paginacion-contenedor .paginacion-nav');
      const boxT320 = await texto320.boundingBox();
      const boxN320 = await nav320.boundingBox();

      // En móvil están apilados: nav abajo del texto
      expect(boxN320.y, `En 320px el nav debe estar debajo del texto en ${pantalla}`).toBeGreaterThanOrEqual(boxT320.y + boxT320.height - 2);

      // Sin scroll horizontal de página a 320 px
      const scrollValido = await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth);
      expect(scrollValido, `No debe existir scroll horizontal global a 320px en ${pantalla}`).toBe(true);
    }
  });

  test('b) Filtros de Historial en una sola fila a 1280 y 1920 px', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    for (const ancho of [1920, 1280]) {
      await page.setViewportSize({ width: ancho, height: 900 });
      await page.goto('/ventas', { waitUntil: 'domcontentloaded' });

      const campoDesde = page.locator('#desde').locator('..');
      const btnFiltrar = page.locator('form button[type="submit"]', { hasText: 'Filtrar' });
      const btnLimpiar = page.locator('form a.btn.s', { hasText: 'Limpiar' });

      await expect(campoDesde).toBeVisible();
      await expect(btnFiltrar).toBeVisible();
      await expect(btnLimpiar).toBeVisible();

      const boxDesde = await campoDesde.boundingBox();
      const boxFiltrar = await btnFiltrar.boundingBox();
      const boxLimpiar = await btnLimpiar.boundingBox();

      expect(boxDesde).not.toBeNull();
      expect(boxFiltrar).not.toBeNull();
      expect(boxLimpiar).not.toBeNull();

      // Misma fila: alineación vertical con tolerancia de 15px
      expect(Math.abs(boxDesde.y - boxFiltrar.y), `Campo Desde y botón Filtrar deben estar en la misma fila a ${ancho}px`).toBeLessThan(15);
      expect(Math.abs(boxFiltrar.y - boxLimpiar.y), `Botón Filtrar y botón Limpiar deben estar en la misma fila a ${ancho}px`).toBeLessThan(15);
    }
  });

  test('c1) Tooltips exactos en botones de acción de Historial (/ventas)', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });

    const primerFila = page.locator('table tbody tr').first();
    const btnVer = primerFila.locator('a.ibtn', { hasText: '' }).filter({ has: page.locator('svg path[d*="M2 12"]') });
    const btnImprimir = primerFila.locator('a.ibtn', { hasText: '' }).filter({ has: page.locator('svg path[d*="M6 18"]') });

    await expect(btnVer).toHaveAttribute('title', 'Ver comprobante');
    await expect(btnImprimir).toHaveAttribute('title', 'Imprimir comprobante');
  });

  test('c2) Tooltips exactos en botones de acción de Mis ventas (/ventas/mias)', async ({ page }) => {
    await iniciarSesion(page, 'vendedor_demo', 'VendedorDemo2026!');
    await page.goto('/ventas/mias', { waitUntil: 'domcontentloaded' });

    const primerFilaMias = page.locator('table tbody tr').first();
    const btnVerMias = primerFilaMias.locator('a.ibtn', { hasText: '' }).filter({ has: page.locator('svg path[d*="M2 12"]') });
    const btnImprimirMias = primerFilaMias.locator('a.ibtn', { hasText: '' }).filter({ has: page.locator('svg path[d*="M6 18"]') });

    await expect(btnVerMias).toHaveAttribute('title', 'Ver comprobante');
    await expect(btnImprimirMias).toHaveAttribute('title', 'Imprimir comprobante');
  });

  test('d) Descuento cero muestra "—" en gris (ink-3) sin rojo ni S/ 0.00, y descuento > 0 muestra monto en rojo', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await asegurarCanastaLimpia(page);

    // 1. Crear una venta con descuento de S/ 1.00 para asegurar presencia de descuento positivo
    await page.locator('#buscar-caja').fill('para');
    const sugerencia = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await sugerencia.click();
    await expect(page.locator('#contenedor-tabla')).toBeVisible();

    // Abrir modal de descuento y aplicar descuento fijo de 1 sol
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await expect(btnEditarDesc).toBeVisible();
    await btnEditarDesc.click();

    await expect(page.locator('#modal-descuento-backdrop')).toBeVisible();
    await page.locator('#input-valor-descuento').fill('1.00');
    await page.locator('#btn-aplicar-descuento').click();

    // Confirmar venta con descuento
    const btnConfirmar = page.locator('#boton-confirmar');
    await expect(btnConfirmar).toBeEnabled();
    await btnConfirmar.click();
    await page.waitForURL((url) => /\/ventas\/\d+/.test(url.pathname), { timeout: 15000 });

    // 2. Ir a Historial (/ventas) y verificar ambas condiciones
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });

    // La venta recién creada debe mostrar S/ 1.00 en text-rose-600
    const celdaDescuentoRojo = page.locator('table tbody tr td span.text-rose-600').first();
    await expect(celdaDescuentoRojo).toBeVisible();
    await expect(celdaDescuentoRojo).toContainText('S/ 1.00');

    // Las ventas sin descuento deben mostrar "—" con text-ink-3 y no contener S/ 0.00
    const celdasGuion = page.locator('table tbody tr td span.text-ink-3', { hasText: '—' });
    expect(await celdasGuion.count()).toBeGreaterThan(0);

    // Ninguna celda de descuento debe contener "S/ 0.00"
    const todasLasCeldasDescuento = page.locator('table tbody tr td:nth-child(7)');
    const totalFilas = await todasLasCeldasDescuento.count();
    for (let i = 0; i < totalFilas; i++) {
      const texto = await todasLasCeldasDescuento.nth(i).textContent();
      expect(texto).not.toContain('S/ 0.00');
    }
  });

});
