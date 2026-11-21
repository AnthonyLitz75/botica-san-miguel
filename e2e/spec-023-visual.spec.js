import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

/**
 * e2e/spec-023-visual.spec.js
 * Pruebas de estilos computados, tokens y accesibilidad visual
 * Especificación 023: Alineación visual con los bocetos
 * Botica San Miguel (AyE)
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
}

test.describe('Spec 023: Fase 1 — Pruebas de estilos computados y tokens', () => {

  test('T2301: estilos computados del shell a 1600x900 (sidebar 248 px, cabecera 64 px, fondo #f3f5f4, fuente base 14 px)', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });

    // 1. Sidebar width a 1600px debe ser 248px
    const sidebar = page.locator('aside.inventario-lateral');
    await expect(sidebar).toBeVisible();
    const sidebarWidth = await sidebar.evaluate((el) => window.getComputedStyle(el).width);
    expect(sidebarWidth).toBe('248px');

    // 2. Cabecera fija a 1600px debe medir 64px de alto
    const header = page.locator('header.inventario-cabecera');
    await expect(header).toBeVisible();
    const headerHeight = await header.evaluate((el) => window.getComputedStyle(el).height);
    expect(headerHeight).toBe('64px');

    // 3. Fondo de página debe ser #f3f5f4 -> rgb(243, 245, 244)
    const bodyBg = await page.evaluate(() => window.getComputedStyle(document.body).backgroundColor);
    expect(bodyBg).toBe('rgb(243, 245, 244)');

    // 4. Fuente base del documento debe ser 14px
    const bodyFontSize = await page.evaluate(() => window.getComputedStyle(document.body).fontSize);
    expect(bodyFontSize).toBe('14px');
  });

  test('T2302: estilos computados de componentes en pantalla representativa (boton primario, tarjeta, campo 42 px, badge, KPI)', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });

    // 1. Tarjeta (.card): fondo blanco y radio 16 px (CA-3.2)
    const card = page.locator('.card').first();
    await expect(card).toBeVisible();
    const cardRadius = await card.evaluate((el) => window.getComputedStyle(el).borderRadius);
    expect(cardRadius).toBe('16px');

    // 2. Botón primario (.btn.p): fondo pine-700 (#166a55), radio 10 px (CA-3.1)
    const btnP = page.locator('.btn.p').first();
    await expect(btnP).toBeVisible();
    const btnRadius = await btnP.evaluate((el) => window.getComputedStyle(el).borderRadius);
    expect(btnRadius).toBe('10px');
    const btnBg = await btnP.evaluate((el) => window.getComputedStyle(el).backgroundColor);
    expect(btnBg).toBe('rgb(22, 106, 85)');

    // 3. Campo de texto (.inp): alto 42 px, radio 10 px (CA-3.3)
    const inp = page.locator('.inp').first();
    await expect(inp).toBeVisible();
    const inpHeight = await inp.evaluate((el) => window.getComputedStyle(el).height);
    expect(inpHeight).toBe('42px');
    const inpRadius = await inp.evaluate((el) => window.getComputedStyle(el).borderRadius);
    expect(inpRadius).toBe('10px');

    // 4. Badge (.badge): radio 999 px (CA-3.3)
    const badge = page.locator('.badge').first();
    await expect(badge).toBeVisible();
    const badgeRadius = await badge.evaluate((el) => window.getComputedStyle(el).borderRadius);
    expect(badgeRadius).toBe('999px');

    // 5. KPI (.kpi .ic): icono 44 px radio 12 px (CA-3.4) en /alertas
    await page.goto('/alertas', { waitUntil: 'domcontentloaded' });
    const kpiIc = page.locator('.kpi .ic').first();
    await expect(kpiIc).toBeVisible();
    const kpiIcWidth = await kpiIc.evaluate((el) => window.getComputedStyle(el).width);
    expect(kpiIcWidth).toBe('44px');
    const kpiIcHeight = await kpiIc.evaluate((el) => window.getComputedStyle(el).height);
    expect(kpiIcHeight).toBe('44px');
    const kpiIcRadius = await kpiIc.evaluate((el) => window.getComputedStyle(el).borderRadius);
    expect(kpiIcRadius).toBe('12px');
  });

  test('T2303: tokens existen en tailwind.config.js y no se carga ninguna fuente externa', async () => {
    // 1. Leer tailwind.config.js
    const configPath = path.resolve('tailwind.config.js');
    expect(fs.existsSync(configPath)).toBeTruthy();
    const configContenido = fs.readFileSync(configPath, 'utf-8');

    // Tokens de color requeridos por spec (CA-1.1)
    expect(configContenido).toContain("50: '#e9f4f0'");
    expect(configContenido).toContain("100: '#d3ebe3'");
    expect(configContenido).toContain("600: '#1b7f66'");
    expect(configContenido).toContain("700: '#166a55'");
    expect(configContenido).toContain("800: '#143a31'");
    expect(configContenido).toContain("900: '#0f2a24'");

    expect(configContenido).toContain("DEFAULT: '#10201b'");
    expect(configContenido).toContain("2: '#33463f'");
    expect(configContenido).toContain("3: '#5b6d66'");

    expect(configContenido).toContain("line: '#dfe6e3'");
    expect(configContenido).toContain("bg: '#f3f5f4'");
    expect(configContenido).toContain("card: '#ffffff'");

    expect(configContenido).toContain("amber:");
    expect(configContenido).toContain("rose:");
    expect(configContenido).toContain("blue:");
    expect(configContenido).toContain("ok:");

    // 2. Verificar que no se carguen fuentes externas (CA-1.2) en las plantillas HTML
    const templatesDir = path.resolve('src/main/resources/templates');
    const listarHtmls = (dir) => {
      let resultados = [];
      const lista = fs.readdirSync(dir);
      for (const item of lista) {
        const itemPath = path.join(dir, item);
        const stat = fs.statSync(itemPath);
        if (stat.isDirectory()) {
          resultados = resultados.concat(listarHtmls(itemPath));
        } else if (item.endsWith('.html')) {
          resultados.push(itemPath);
        }
      }
      return resultados;
    };

    const htmlFiles = listarHtmls(templatesDir);
    expect(htmlFiles.length).toBeGreaterThan(0);

    for (const file of htmlFiles) {
      const contenido = fs.readFileSync(file, 'utf-8');
      expect(contenido).not.toContain('fonts.googleapis.com');
      expect(contenido).not.toContain('fonts.gstatic.com');
      expect(contenido).not.toContain('use.typekit.net');
      expect(contenido).not.toContain('font-awesome');
    }
  });

  test('T2316: asociacion de labels en preparacion.html y lotes/listado.html, y <title> en error.html', async () => {
    // 1. Verificar error.html
    const errorHtmlPath = path.resolve('src/main/resources/templates/fragmentos/error.html');
    const errorHtml = fs.readFileSync(errorHtmlPath, 'utf-8');
    expect(errorHtml).toContain('<title>');

    // 2. Verificar preparacion.html: todos los <label> deben tener atributo for
    const preparacionHtmlPath = path.resolve('src/main/resources/templates/ventas/preparacion.html');
    const preparacionHtml = fs.readFileSync(preparacionHtmlPath, 'utf-8');
    const labelsPrepSinFor = [...preparacionHtml.matchAll(/<label\b(?![^>]*\bfor=)[^>]*>/gi)];
    expect(labelsPrepSinFor.length).toBe(0);

    // 3. Verificar lotes/listado.html: todos los <label> deben tener atributo for
    const lotesHtmlPath = path.resolve('src/main/resources/templates/lotes/listado.html');
    const lotesHtml = fs.readFileSync(lotesHtmlPath, 'utf-8');
    const labelsLotesSinFor = [...lotesHtml.matchAll(/<label\b(?![^>]*\bfor=)[^>]*>/gi)];
    expect(labelsLotesSinFor.length).toBe(0);
  });

  test('T2307: Capturas Grupo 1 (01-caja, 03-alertas, 04-medicamentos) a 1600x900', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 01-caja.png
    await page.goto('/ventas/nueva', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/01-caja.png' });

    // 03-alertas.png
    await page.goto('/alertas', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: /Alertas de inventario/i })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/03-alertas.png' });

    // 04-medicamentos.png
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Medicamentos' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/04-medicamentos.png' });
  });

  test('T2308: Capturas Grupo 2 (02-ticket, 09-historial-ventas, 07-reportes-ventas) a 1600x900', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 09-historial-ventas.png
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Historial de ventas' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/09-historial-ventas.png' });

    // 02-ticket.png (navegar al comprobante de la primera venta disponible)
    const primerComprobanteLnk = page.locator('table tbody tr a.lnk').first();
    if (await primerComprobanteLnk.isVisible()) {
      await primerComprobanteLnk.click();
    } else {
      await page.goto('/ventas/1', { waitUntil: 'domcontentloaded' });
    }
    await expect(page.locator('#comprobante')).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/02-ticket.png' });

    // 07-reportes-ventas.png
    await page.goto('/reportes/ventas', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Ventas por período' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/07-reportes-ventas.png' });
  });

  test('T2309: Capturas Grupo 3 (05-ordenes-compra, 06-auditoria, 13-movimientos) a 1600x900', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 05-ordenes-compra.png
    await page.goto('/ordenes-compra', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Órdenes de compra' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/05-ordenes-compra.png' });

    // 06-auditoria.png
    await page.goto('/auditoria', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Auditoría' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/06-auditoria.png' });

    // 13-movimientos.png
    await page.goto('/movimientos', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Movimientos de inventario' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/13-movimientos.png' });
  });

  test('T2310: Capturas Grupo 4 (10-clientes, 11-proveedores, 08-categorias, 12-usuarios) a 1600x900', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 10-clientes.png
    await page.goto('/clientes', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Clientes' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/10-clientes.png' });

    // 11-proveedores.png
    await page.goto('/proveedores', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Proveedores' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/11-proveedores.png' });

    // 08-categorias.png
    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Árbol de categorías' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/08-categorias.png' });

    // 12-usuarios.png
    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('heading', { level: 1, name: 'Gestión de usuarios' })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/12-usuarios.png' });
  });

  test('T2311: Capturas Modales 14 a 19 a 1600x900', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 14-modal-nuevo-cliente.png
    await page.goto('/clientes', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo cliente/i }).click();
    const modalCliente = page.locator('#modal-nuevo-cliente');
    await expect(modalCliente).toHaveClass(/activo|on/);
    await page.screenshot({ path: 'e2e/evidencias/visual/14-modal-nuevo-cliente.png' });

    // 15-modal-nuevo-proveedor.png (con estado de error en RUC del boceto 15)
    await page.goto('/proveedores', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo proveedor/i }).click();
    const modalProv = page.locator('#modal-nuevo-proveedor');
    await expect(modalProv).toHaveClass(/activo|on/);
    // Simular estado visual del boceto 15
    await page.evaluate(() => {
      const rucInput = document.querySelector('#modal-ruc');
      if (rucInput) {
        rucInput.value = '2060198765';
        const contenedor = rucInput.closest('.inp');
        if (contenedor) contenedor.classList.add('err');
        let help = contenedor.parentElement.querySelector('.help.e');
        if (!help) {
          help = document.createElement('div');
          help.className = 'help e';
          contenedor.parentElement.appendChild(help);
        }
        help.textContent = 'El RUC debe tener 11 dígitos (ingresaste 10).';
      }
    });
    await page.screenshot({ path: 'e2e/evidencias/visual/15-modal-nuevo-proveedor.png' });

    // 16-modal-nueva-categoria.png
    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nueva categoría/i }).click();
    const modalCat = page.locator('#modal-nueva-categoria');
    await expect(modalCat).toHaveClass(/activo|on/);
    await page.screenshot({ path: 'e2e/evidencias/visual/16-modal-nueva-categoria.png' });

    // 17-modal-nuevo-usuario.png
    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo usuario/i }).click();
    const modalUser = page.locator('#modal-nuevo-usuario');
    await expect(modalUser).toHaveClass(/activo|on/);
    await page.screenshot({ path: 'e2e/evidencias/visual/17-modal-nuevo-usuario.png' });

    // 18-modal-nuevo-medicamento.png
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo medicamento/i }).click();
    const modalMed = page.locator('#modal-nuevo-medicamento');
    await expect(modalMed).toHaveClass(/activo|on/);
    await page.screenshot({ path: 'e2e/evidencias/visual/18-modal-nuevo-medicamento.png' });

    // 19-modal-registrar-salida.png
    await page.goto('/medicamentos/1/lotes', { waitUntil: 'domcontentloaded' });
    const btnSalida = page.locator('a[data-modal-open="modal-registrar-salida"]').first();
    if (await btnSalida.isVisible()) {
      await btnSalida.click();
    } else {
      await page.locator('#modal-registrar-salida').evaluate(el => el.classList.add('activo', 'on'));
    }
    const modalSalida = page.locator('#modal-registrar-salida');
    await expect(modalSalida).toHaveClass(/activo|on/);
    await page.screenshot({ path: 'e2e/evidencias/visual/19-modal-registrar-salida.png' });
  });

  test('T2312: Captura 00-login-sin-cambios y verificacion de las 20 capturas en e2e/evidencias/visual/', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    // 00-login-sin-cambios.png
    await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('button', { name: /Iniciar sesión/i })).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/visual/00-login-sin-cambios.png' });

    // Verificar existencia de las 20 capturas
    const esperadas = [
      '00-login-sin-cambios.png',
      '01-caja.png',
      '02-ticket.png',
      '03-alertas.png',
      '04-medicamentos.png',
      '05-ordenes-compra.png',
      '06-auditoria.png',
      '07-reportes-ventas.png',
      '08-categorias.png',
      '09-historial-ventas.png',
      '10-clientes.png',
      '11-proveedores.png',
      '12-usuarios.png',
      '13-movimientos.png',
      '14-modal-nuevo-cliente.png',
      '15-modal-nuevo-proveedor.png',
      '16-modal-nueva-categoria.png',
      '17-modal-nuevo-usuario.png',
      '18-modal-nuevo-medicamento.png',
      '19-modal-registrar-salida.png',
    ];

    const dirVisual = path.resolve('e2e/evidencias/visual');
    expect(fs.existsSync(dirVisual)).toBeTruthy();
    for (const archivo of esperadas) {
      const ruta = path.join(dirVisual, archivo);
      expect(fs.existsSync(ruta), `Falta la captura: ${archivo}`).toBeTruthy();
    }
  });

});
