import { test, expect } from '@playwright/test';

async function capturarPantallaSegura(page, ruta, reintentos = 3) {
  for (let i = 0; i < reintentos; i++) {
    try {
      await page.screenshot({ path: ruta });
      return;
    } catch (e) {
      if (i === reintentos - 1) throw e;
      await new Promise(r => setTimeout(r, 250));
    }
  }
}

test.describe('Spec 021: Gráfico de ventas netas por día', () => {

  test.beforeEach(async ({ page }) => {
    test.setTimeout(45000);
    await page.setViewportSize({ width: 1600, height: 900 });

    // Iniciar sesión como administrador (único rol con acceso a reportes)
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
    const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
  });

  test('T2107 / CA-1.1, CA-1.2, CA-1.3, CA-1.6: Rango de 7 días dibuja 7 barras continuas con accesibilidad y titles', async ({ page }) => {
    // Rango de 7 días exactos: del 14/11/2026 al 20/11/2026
    await page.goto('/reportes/ventas?desde=2026-11-14&hasta=2026-11-20', { waitUntil: 'networkidle' });

    await expect(page.getByRole('heading', { level: 1, name: /Ventas por período/i })).toBeVisible();

    // 1. Verificar gráfico presente con role="img" y aria-label accesible (CA-1.6)
    const grafico = page.locator('.bars[role="img"]');
    await expect(grafico).toBeVisible();
    const ariaLabel = await grafico.getAttribute('aria-label');
    expect(ariaLabel).toBeTruthy();
    expect(ariaLabel).toContain('14/11/2026');
    expect(ariaLabel).toContain('20/11/2026');
    expect(ariaLabel).toMatch(/Total neto:\s*S\/\s*[\d,.]+/);
    expect(ariaLabel).toContain('Mayor venta: 19/11/2026');

    // 2. Verificar que se dibujan exactamente 7 barras (CA-1.1)
    const barras = grafico.locator('.barra-item');
    await expect(barras).toHaveCount(7);

    // 3. Verificar que cada barra tiene su title con dd/MM y total neto (CA-1.2)
    const fechasEsperadas = ['14/11', '15/11', '16/11', '17/11', '18/11', '19/11', '20/11'];
    for (let i = 0; i < 7; i++) {
      const barra = barras.nth(i);
      const title = await barra.getAttribute('title');
      expect(title).toContain(fechasEsperadas[i]);
      expect(title).toMatch(/S\/\s*[\d,.]+/);

      const etiquetaSmall = barra.locator('small');
      if (i === 0 || i === 5 || i === 6) {
        await expect(etiquetaSmall).toHaveText(fechasEsperadas[i]);
      } else {
        await expect(etiquetaSmall).toHaveCount(0);
      }
    }

    // 4. Los días sin ventas (16, 17, 18, 20) tienen altura 0% y clase barra-cero (CA-1.3)
    const diasSinVentas = [2, 3, 4, 6]; // índices 16/11, 17/11, 18/11, 20/11
    for (const idx of diasSinVentas) {
      const barraSin = barras.nth(idx);
      await expect(barraSin).toHaveClass(/barra-cero/);
      const style = await barraSin.getAttribute('style');
      expect(style).toContain('height: 0%');
    }

    // 5. El día con mayor venta (19/11, índice 5) tiene altura 100%
    const barraTope = barras.nth(5);
    const styleTope = await barraTope.getAttribute('style');
    expect(styleTope).toContain('height: 100%');

    // Captura de evidencia
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-021-grafico-7dias.png');
  });

  test('T2107 / CA-1.5: Rango superior a 31 días no dibuja el gráfico y muestra el aviso acotador', async ({ page }) => {
    // Rango de 51 días: del 01/10/2026 al 20/11/2026
    await page.goto('/reportes/ventas?desde=2026-10-01&hasta=2026-11-20', { waitUntil: 'networkidle' });

    // 1. Gráfico no debe existir
    await expect(page.locator('.bars[role="img"]')).toHaveCount(0);

    // 2. Aviso debe estar visible con el texto exacto de la spec
    const aviso = page.getByText('Acota el rango a 31 días para ver el gráfico');
    await expect(aviso).toBeVisible();

    // 3. La tabla sigue visible con sus datos
    const tabla = page.locator('table');
    await expect(tabla).toBeVisible();
    await expect(tabla.locator('tbody tr')).not.toHaveCount(0);

    // Captura de evidencia
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-021-aviso-rango-largo.png');
  });

  test('T2107 / CA-1.4: Rango sin ventas muestra estado vacío y ningún gráfico', async ({ page }) => {
    // Rango en el pasado sin ventas registradas
    await page.goto('/reportes/ventas?desde=2025-01-01&hasta=2025-01-07', { waitUntil: 'networkidle' });

    // 1. Gráfico no se dibuja
    await expect(page.locator('.bars[role="img"]')).toHaveCount(0);

    // 2. Estado vacío existente visible en la tabla
    await expect(page.getByText('No se registraron ventas en el rango de fechas seleccionado.')).toBeVisible();

    // Captura de evidencia
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-021-rango-sin-ventas.png');
  });

  test('Regla 12 / Responsivo 320 px: Gráfico no causa scroll horizontal de página en móvil', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/reportes/ventas?desde=2026-11-14&hasta=2026-11-20', { waitUntil: 'networkidle' });

    // 1. Verificar ausencia de scroll horizontal en la página
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    const innerWidth = await page.evaluate(() => window.innerWidth);
    expect(scrollWidth).toBeLessThanOrEqual(innerWidth);

    // 2. El gráfico y su contenedor scrollable existen
    const contenedorScroll = page.locator('.grafico-scroll-contenedor');
    await expect(contenedorScroll).toBeVisible();

    // Captura de evidencia en 320 px
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-021-grafico-320px.png');
  });

  test('T2109 / CA-V.1: Captura de pantalla completa a 1600x900 para contraste visual contra Boceto 07', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto('/reportes/ventas?desde=2026-11-14&hasta=2026-11-20', { waitUntil: 'networkidle' });

    await expect(page.getByRole('heading', { level: 1, name: /Ventas por período/i })).toBeVisible();
    await expect(page.locator('.bars[role="img"]')).toBeVisible();

    // Captura a 1600x900 según protocolo de Spec 023 (CA-V.1)
    await capturarPantallaSegura(page, 'e2e/evidencias/spec-021-reporte-ventas-1600x900.png');
  });

});
