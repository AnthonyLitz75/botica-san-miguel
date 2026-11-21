import { test, expect } from '@playwright/test';

/**
 * e2e/spec-023-lotes.spec.js
 * Especificación 023 (Tercera Pasada): Pantalla "Lotes de <medicamento>" e ícono de desactivar
 * Botica San Miguel (AyE)
 */
test.describe('Spec 023 (Tercera Pasada): Botón desactivar y Pantalla de Lotes', () => {

  async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => !url.pathname.includes('/acceso/login') && !url.pathname.includes('/login'), { timeout: 15000 });
  }

  test('PARTE 1: Botón de desactivar y de editar en Medicamentos tienen idéntico tamaño (34x34), radio y alineación vertical', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.setViewportSize({ width: 1600, height: 900 });
    await page.goto('/medicamentos');
    await expect(page.getByRole('heading', { level: 1, name: 'Medicamentos' })).toBeVisible();

    const primeraFila = page.locator('table tbody tr').first();
    await expect(primeraFila).toBeVisible();

    const btnEditar = primeraFila.locator('a.ibtn').first();
    const btnDesactivar = primeraFila.locator('button.ibtn.dng').first();

    await expect(btnEditar).toBeVisible();
    await expect(btnDesactivar).toBeVisible();

    const boxEditar = await btnEditar.boundingBox();
    const boxDesactivar = await btnDesactivar.boundingBox();

    expect(boxEditar).not.toBeNull();
    expect(boxDesactivar).not.toBeNull();

    // 1. Ambos botones deben tener exactamente el mismo tamaño (34x34 px en el boceto)
    expect(Math.abs(boxDesactivar.width - boxEditar.width)).toBeLessThanOrEqual(0.5);
    expect(Math.abs(boxDesactivar.height - boxEditar.height)).toBeLessThanOrEqual(0.5);
    expect(boxDesactivar.width).toBeCloseTo(34, 0);
    expect(boxDesactivar.height).toBeCloseTo(34, 0);

    // 2. Ambos botones deben estar alineados verticalmente (mismo centro Y)
    const centroYEditar = boxEditar.y + boxEditar.height / 2;
    const centroYDesactivar = boxDesactivar.y + boxDesactivar.height / 2;
    expect(Math.abs(centroYDesactivar - centroYEditar)).toBeLessThanOrEqual(1.0);

    // 3. Estilos de peligro del botón desactivar (.ibtn.dng)
    const estilosDesactivar = await btnDesactivar.evaluate((el) => {
      const win = el.ownerDocument.defaultView;
      const comp = win.getComputedStyle(el);
      return {
        color: comp.color,
        borderColor: comp.borderColor,
        borderRadius: comp.borderRadius,
      };
    });

    // Color del ícono rojo del sistema (var(--rose): rgb(190, 18, 60))
    expect(estilosDesactivar.color).toBe('rgb(190, 18, 60)');
    // Borde rosado suave (#fecdd3: rgb(254, 205, 211))
    expect(estilosDesactivar.borderColor).toBe('rgb(254, 205, 211)');
    // Radio de 9px (según boceto)
    expect(estilosDesactivar.borderRadius).toBe('9px');

    // 4. Hover con fondo rosado muy claro (var(--rose-bg): rgb(255, 228, 230))
    await btnDesactivar.hover();
    await expect(btnDesactivar).toHaveCSS('background-color', 'rgb(255, 228, 230)');

    // 5. Botón nativo con aria-label
    await expect(btnDesactivar).toHaveAttribute('aria-label', /^Desactivar /);

    // Captura visual actualizada 04-medicamentos.png a 1600x900
    await page.screenshot({ path: 'e2e/evidencias/visual/04-medicamentos.png' });
  });

  test('PARTE 2: Pantalla Lotes - eyebrow pine-700, volver a la derecha, tarjeta de datos 3 cols, banner FEFO y botón registrar salida', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.setViewportSize({ width: 1600, height: 900 });

    // Navegar a Lotes desde Medicamentos
    await page.goto('/medicamentos');
    const enlaceLotes = page.locator('table tbody tr a.enlace-lotes').first();
    await expect(enlaceLotes).toBeVisible();
    await enlaceLotes.click();

    await page.waitForURL((url) => /\/medicamentos\/\d+\/lotes/.test(url.pathname), { timeout: 15000 });
    const heading = page.getByRole('heading', { level: 1, name: /Lotes de /i });
    await expect(heading).toBeVisible();

    // a) Eyebrow: verde pine-700, 11px, uppercase, letter-spacing, semibold
    const eyebrow = page.locator('.eyebrow');
    await expect(eyebrow).toBeVisible();
    const estilosEyebrow = await eyebrow.evaluate((el) => {
      const comp = el.ownerDocument.defaultView.getComputedStyle(el);
      return {
        color: comp.color,
        fontSize: comp.fontSize,
        textTransform: comp.textTransform,
      };
    });
    expect(estilosEyebrow.color).toBe('rgb(22, 106, 85)'); // pine-700
    expect(estilosEyebrow.fontSize).toBe('11px');
    expect(estilosEyebrow.textTransform).toBe('uppercase');

    // b) Botón "Volver a medicamentos": a la derecha de la cabecera en escritorio (x > heading.x)
    const btnVolver = page.getByRole('link', { name: /Volver a medicamentos/i });
    await expect(btnVolver).toBeVisible();
    const boxHeading = await heading.boundingBox();
    const boxVolver = await btnVolver.boundingBox();
    expect(boxVolver.x).toBeGreaterThan(boxHeading.x);

    // c) Tarjeta resumen de datos: dl como tarjeta con 3 columnas en escritorio
    const dlTarjeta = page.locator('dl.resumen-registro');
    await expect(dlTarjeta).toBeVisible();
    const estilosDl = await dlTarjeta.evaluate((el) => {
      const comp = el.ownerDocument.defaultView.getComputedStyle(el);
      return {
        backgroundColor: comp.backgroundColor,
        borderRadius: comp.borderRadius,
        display: comp.display,
        gridTemplateColumns: comp.gridTemplateColumns,
      };
    });
    expect(estilosDl.backgroundColor).toBe('rgb(255, 255, 255)');
    expect(estilosDl.borderRadius).toBe('16px');
    expect(estilosDl.display).toBe('grid');
    // En escritorio debe tener 3 columnas
    const columnas = estilosDl.gridTemplateColumns.trim().split(/\s+/);
    expect(columnas.length).toBe(3);

    // Código en mono y pine-700
    const codigoMed = dlTarjeta.locator('.codigo-medicamento');
    const colorCodigo = await codigoMed.evaluate((el) => el.ownerDocument.defaultView.getComputedStyle(el).color);
    expect(colorCodigo).toBe('rgb(22, 106, 85)'); // pine-700

    // d) Banner FEFO: fondo pine-50, borde pine-100, radio 12, cursiva Cola de Prioridad
    const bannerFefo = page.locator('.banner-fefo');
    await expect(bannerFefo).toBeVisible();
    const estilosBanner = await bannerFefo.evaluate((el) => {
      const comp = el.ownerDocument.defaultView.getComputedStyle(el);
      return {
        backgroundColor: comp.backgroundColor,
        borderColor: comp.borderColor,
        borderRadius: comp.borderRadius,
      };
    });
    expect(estilosBanner.backgroundColor).toBe('rgb(233, 244, 240)'); // pine-50
    expect(estilosBanner.borderColor).toBe('rgb(211, 235, 227)'); // pine-100
    expect(estilosBanner.borderRadius).toBe('12px');
    await expect(bannerFefo.locator('em')).toHaveText(/Cola de Prioridad/i);

    // e) Acción "Registrar salida": botón pequeño tipo chip (fondo pine-50, texto pine-700, radio 8 o 9)
    const btnSalida = page.locator('table tbody tr a[data-modal-open="modal-registrar-salida"]').first();
    if (await btnSalida.isVisible()) {
      const estilosSalida = await btnSalida.evaluate((el) => {
        const comp = el.ownerDocument.defaultView.getComputedStyle(el);
        return {
          backgroundColor: comp.backgroundColor,
          color: comp.color,
          borderRadius: comp.borderRadius,
        };
      });
      expect(estilosSalida.backgroundColor).toBe('rgb(233, 244, 240)'); // pine-50
      expect(estilosSalida.color).toBe('rgb(22, 106, 85)'); // pine-700
      expect(parseInt(estilosSalida.borderRadius, 10)).toBeGreaterThanOrEqual(8);
      await expect(btnSalida).toHaveAttribute('aria-label', /^Registrar salida del lote /);
    }

    // f) Contador inline "Lotes registrados [n]"
    const secContador = page.locator('.sec .cnt');
    await expect(secContador).toBeVisible();
  });

  test('PARTE 2 (Responsivo): Pantalla Lotes no causa scroll horizontal de página a 320 px', async ({ page }) => {
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/medicamentos/1/lotes');
    await expect(page.getByRole('heading', { level: 1, name: /Lotes de /i })).toBeVisible();

    const noHorizontalScroll = await page.evaluate(() => {
      return document.documentElement.scrollWidth <= window.innerWidth;
    });
    expect(noHorizontalScroll).toBe(true);

    // El botón "Volver a medicamentos" se reubica debajo del título en móvil (boxVolver.y > boxHeading.y)
    const heading = page.getByRole('heading', { level: 1, name: /Lotes de /i });
    const btnVolver = page.getByRole('link', { name: /Volver a medicamentos/i });
    const boxHeading = await heading.boundingBox();
    const boxVolver = await btnVolver.boundingBox();
    expect(boxVolver.y).toBeGreaterThan(boxHeading.y);
  });
});
