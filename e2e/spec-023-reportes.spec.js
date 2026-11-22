import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

/**
 * e2e/spec-023-reportes.spec.js
 * AJUSTE 023 (paso 5): Unificación visual de Reportes y corrección de Inventario
 * Botica San Miguel (AyE)
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
}

async function asegurarDirectorioEvidencias() {
  const dir = path.join(process.cwd(), 'e2e', 'evidencias', 'visual');
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true });
  }
  return dir;
}

const PANTALLAS_REPORTES = [
  { ruta: '/reportes/ventas', nombre: 'Ventas por período', tieneCsv: true },
  { ruta: '/reportes/mas-vendidos', nombre: 'Medicamentos más vendidos', tieneCsv: true },
  { ruta: '/reportes/mermas', nombre: 'Mermas y bajas', tieneCsv: true },
  { ruta: '/reportes/inventario', nombre: 'Inventario', tieneCsv: true },
  { ruta: '/reportes/compras', nombre: 'Estado de compras', tieneCsv: true },
  { ruta: '/reportes/panel', nombre: 'Panel resumen', tieneCsv: false }
];

test.describe('AJUSTE 023 (paso 5): Unificación de Reportes y corrección de Inventario', () => {

  test.beforeEach(async ({ page }) => {
    test.setTimeout(60000);
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
  });

  test('a) Las 6 pantallas cumplen barra de ruta "Reportes / <nombre>", botón "Exportar CSV" a la derecha con icono válido, y pestañas con borde', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });

    for (const p of PANTALLAS_REPORTES) {
      await page.goto(p.ruta, { waitUntil: 'domcontentloaded' });

      // 1. Barra de ruta: chip "Reportes" a la izquierda y nombre de la página a la derecha
      const crumb = page.locator('.crumb');
      await expect(crumb).toBeVisible();
      await expect(crumb.locator('.pill')).toContainText('Reportes');
      await expect(crumb.locator('b')).toHaveText(p.nombre);

      // 2. Botón "Exportar CSV" (salvo en Panel resumen)
      if (p.tieneCsv) {
        const btnCsv = page.getByRole('link', { name: /Exportar CSV/i });
        await expect(btnCsv).toBeVisible();
        const box = await btnCsv.boundingBox();
        expect(box).not.toBeNull();
        // Mitad derecha del viewport (viewport width = 1600 px -> x > 800)
        expect(box.x).toBeGreaterThan(800);
      } else {
        await expect(page.getByRole('link', { name: /Exportar CSV/i })).toHaveCount(0);
      }

      // 3. Comprobar que en todos los elementos con clase `icono` su máscara no es "none"
      const iconos = page.locator('.icono');
      const countIconos = await iconos.count();
      for (let i = 0; i < countIconos; i++) {
        const mask = await iconos.nth(i).evaluate((el) => {
          const style = window.getComputedStyle(el);
          return style.webkitMaskImage || style.maskImage;
        });
        expect(mask).not.toBe('none');
      }

      // 4. Pestañas inactivas tienen borde (border-width > 0)
      const pestanasInactivas = page.locator('.btn.s').filter({ hasText: /Ventas por período|Más vendidos|Mermas y bajas|Inventario|Estado de compras|Panel resumen/ });
      const countInactivas = await pestanasInactivas.count();
      expect(countInactivas).toBeGreaterThan(0);
      for (let i = 0; i < countInactivas; i++) {
        const borderWidth = await pestanasInactivas.nth(i).evaluate((el) => {
          return parseFloat(window.getComputedStyle(el).borderTopWidth || window.getComputedStyle(el).borderWidth);
        });
        expect(borderWidth).toBeGreaterThan(0);
      }
    }
  });

  test('b) Más vendidos: el valor de recaudación ocupa una sola línea', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto('/reportes/mas-vendidos', { waitUntil: 'domcontentloaded' });

    const valorRecaudacion = page.locator('.kpi .n').filter({ hasText: 'S/' }).first();
    await expect(valorRecaudacion).toBeVisible();

    const box = await valorRecaudacion.boundingBox();
    expect(box).not.toBeNull();

    const lineHeight = await valorRecaudacion.evaluate((el) => {
      const lh = window.getComputedStyle(el).lineHeight;
      const fs = window.getComputedStyle(el).fontSize;
      return lh === 'normal' ? parseFloat(fs) * 1.2 : parseFloat(lh);
    });

    // Altura menor a 1.5 veces el alto de línea (una sola línea)
    expect(box.height).toBeLessThan(1.5 * lineHeight);
  });

  test('c) Inventario: lotes cerrados por defecto, tabla interna cabe dentro de la tarjeta a 1600 y 1280 px, y código no se parte', async ({ page }) => {
    for (const ancho of [1600, 1280]) {
      await page.setViewportSize({ width: ancho, height: 900 });
      await page.goto('/reportes/inventario', { waitUntil: 'domcontentloaded' });

      // 1. Ningún <details> abierto al cargar
      const detailsAbiertos = await page.locator('details[open]').count();
      expect(detailsAbiertos).toBe(0);

      // 2. Columna código no se parte en dos líneas
      const primerCodigo = page.locator('table tbody tr td.font-mono').first();
      await expect(primerCodigo).toBeVisible();
      const boxCodigo = await primerCodigo.boundingBox();
      expect(boxCodigo).not.toBeNull();
      expect(boxCodigo.height).toBeLessThan(55);
      const ws = await primerCodigo.evaluate((el) => window.getComputedStyle(el).whiteSpace);
      expect(ws).toBe('nowrap');

      // 3. Al abrir el primer lote, la tabla interna queda dentro de la tarjeta
      const primerSummary = page.locator('details summary').first();
      await expect(primerSummary).toBeVisible();
      await primerSummary.click();

      const tablaInterna = page.locator('details table').first();
      await expect(tablaInterna).toBeVisible();
      const boxTabla = await tablaInterna.boundingBox();
      const tarjeta = page.locator('.card.overflow-hidden').first();
      const boxTarjeta = await tarjeta.boundingBox();

      expect(boxTabla).not.toBeNull();
      expect(boxTarjeta).not.toBeNull();
      expect(boxTabla.x).toBeGreaterThanOrEqual(boxTarjeta.x - 2);
      expect(boxTabla.x + boxTabla.width).toBeLessThanOrEqual(boxTarjeta.x + boxTarjeta.width + 5);
    }
  });

  test('d) Estado de compras: orden cancelada muestra "—" en Pendientes y tarjeta Total órdenes tiene "Incluye canceladas"', async ({ page }) => {
    test.setTimeout(90000);
    await page.setViewportSize({ width: 1600, height: 900 });

    // 1. Crear una orden propia con datos únicos para no alterar datos existentes
    const observacionUnica = 'Orden e2e ajuste 023 ' + Date.now();
    await page.goto('/ordenes-compra/nuevo', { waitUntil: 'domcontentloaded' });
    await page.locator('#idProveedor').selectOption({ index: 1 });
    await page.locator('#medicamento').selectOption({ index: 1 });
    await page.locator('#cantidad').fill('15');
    await page.locator('#costo').fill('8.50');
    await page.locator('#observaciones').fill(observacionUnica);

    const btnEmitir = page.getByRole('button', { name: /Emitir orden/i }).filter({ visible: true });
    await expect(btnEmitir).toBeVisible();
    await Promise.all([
      page.waitForURL((url) => url.pathname.startsWith('/ordenes-compra/') && !url.pathname.endsWith('/nuevo')),
      btnEmitir.click()
    ]);

    // 2. Cancelar la orden recién creada con el modal
    const btnCancelar = page.getByRole('button', { name: /Cancelar orden/i });
    await expect(btnCancelar).toBeVisible();
    await btnCancelar.click();

    const modalConfirmar = page.locator('#modal-confirmar');
    await expect(modalConfirmar).toBeVisible();
    await Promise.all([
      page.waitForLoadState('domcontentloaded'),
      modalConfirmar.locator('#modal-confirmar-accion').click()
    ]);
    await expect(page.locator('.badge.estado-registro')).toContainText('CANCELADA');

    // 3. Ir al reporte de estado de compras
    await page.goto('/reportes/compras', { waitUntil: 'domcontentloaded' });

    // Verificar que la tarjeta "Total órdenes" muestra "Incluye canceladas"
    await expect(page.getByText('Incluye canceladas')).toBeVisible();

    // Buscar la fila de la orden cancelada
    const filaCancelada = page.locator('table tbody tr').filter({ hasText: 'CANCELADA' }).first();
    await expect(filaCancelada).toBeVisible();
    // La columna Pendientes (última celda td) muestra "—"
    const celdaPendientes = filaCancelada.locator('td').nth(7);
    await expect(celdaPendientes).toContainText('—');
  });

  test('e) Ventas por período: descuento 0 muestra "—" no rojo, gráfico "Mayor venta del día" y barras con title', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto('/reportes/ventas', { waitUntil: 'domcontentloaded' });

    // 1. Gráfico muestra "Mayor venta del día"
    await expect(page.getByText(/Mayor venta del día:/i)).toBeVisible();

    // 2. Cada barra tiene title con fecha y monto
    const barras = page.locator('.barra-item');
    const countBarras = await barras.count();
    expect(countBarras).toBeGreaterThan(0);
    for (let i = 0; i < countBarras; i++) {
      const title = await barras.nth(i).getAttribute('title');
      expect(title).toBeTruthy();
      expect(title).toMatch(/\d{2}\/\d{2}/);
      expect(title).toMatch(/S\/\s*[\d,.]+/);
    }

    // 3. Descuento 0 muestra "—" en ink-3 y sin clase text-rose-600
    const filasDescuentoCero = page.locator('table tbody tr').filter({ hasText: '—' });
    const countCero = await filasDescuentoCero.count();
    if (countCero > 0) {
      const celdaDescuento = filasDescuentoCero.first().locator('td').nth(3);
      await expect(celdaDescuento).toContainText('—');
      await expect(celdaDescuento.locator('span')).not.toHaveClass(/text-rose-600/);
    }
  });

  test('f) Panel: textos nuevos sin "compatible con hojas de cálculo" y botones con nombres de pestañas', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto('/reportes/panel', { waitUntil: 'domcontentloaded' });

    // 1. Texto nuevo visible
    await expect(page.getByText('Descarga los reportes en CSV para abrirlos en Excel.')).toBeVisible();
    await expect(page.locator('body')).not.toContainText('compatible con hojas de cálculo y herramientas analíticas');

    // 2. Los 3 botones de la tarjeta verde tienen los nombres exactos de las pestañas
    const cardVerde = page.locator('.card.pad').last();
    await expect(cardVerde.getByRole('link', { name: 'Ventas por período' })).toBeVisible();
    await expect(cardVerde.getByRole('link', { name: 'Más vendidos' })).toBeVisible();
    await expect(cardVerde.getByRole('link', { name: 'Mermas y bajas' })).toBeVisible();
  });

  test('g) A 320 px ninguna de las 6 pantallas genera scroll horizontal del documento', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 640 });

    for (const p of PANTALLAS_REPORTES) {
      await page.goto(p.ruta, { waitUntil: 'domcontentloaded' });

      const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
      const clientWidth = await page.evaluate(() => document.documentElement.clientWidth);
      expect(scrollWidth).toBeLessThanOrEqual(clientWidth + 1);
    }
  });

  test('Captura de evidencias visuales 31 a 36 a 1600x900', async ({ page }) => {
    const evidenciasDir = await asegurarDirectorioEvidencias();
    await page.setViewportSize({ width: 1600, height: 900 });

    // 31-reporte-ventas.png
    await page.goto('/reportes/ventas', { waitUntil: 'domcontentloaded' });
    await page.screenshot({ path: path.join(evidenciasDir, '31-reporte-ventas.png') });

    // 32-reporte-mas-vendidos.png
    await page.goto('/reportes/mas-vendidos', { waitUntil: 'domcontentloaded' });
    await page.screenshot({ path: path.join(evidenciasDir, '32-reporte-mas-vendidos.png') });

    // 33-reporte-mermas.png
    await page.goto('/reportes/mermas', { waitUntil: 'domcontentloaded' });
    await page.screenshot({ path: path.join(evidenciasDir, '33-reporte-mermas.png') });

    // 34-reporte-inventario.png (con el primer lote desplegado)
    await page.goto('/reportes/inventario', { waitUntil: 'domcontentloaded' });
    const primerDetails = page.locator('details summary').first();
    if (await primerDetails.count() > 0) {
      await primerDetails.click();
    }
    await page.screenshot({ path: path.join(evidenciasDir, '34-reporte-inventario.png') });

    // 35-reporte-estado-compras.png
    await page.goto('/reportes/compras', { waitUntil: 'domcontentloaded' });
    await page.screenshot({ path: path.join(evidenciasDir, '35-reporte-estado-compras.png') });

    // 36-reporte-panel.png
    await page.goto('/reportes/panel', { waitUntil: 'domcontentloaded' });
    await page.screenshot({ path: path.join(evidenciasDir, '36-reporte-panel.png') });
  });

});
