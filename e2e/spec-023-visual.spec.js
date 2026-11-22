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

  test('Punto A: Layout global - badge alertas, chip usuario en categorias y migas de pan exactas', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 1. Sidebar: globo rojo con alertas activas en el item "Alertas"
    await page.goto('/alertas', { waitUntil: 'domcontentloaded' });
    const badgeAlertas = page.locator('#sidebar-lateral a[href*="/alertas"] .bd');
    const badgeVisible = await badgeAlertas.isVisible();
    if (badgeVisible) {
      const numAlertas = await badgeAlertas.innerText();
      expect(Number(numAlertas)).toBeGreaterThan(0);
      const bg = await badgeAlertas.evaluate((el) => window.getComputedStyle(el).backgroundColor);
      expect(bg).toBe('rgb(225, 29, 72)'); // #e11d48
      const color = await badgeAlertas.evaluate((el) => window.getComputedStyle(el).color);
      expect(color).toBe('rgb(255, 255, 255)');
      const fontSize = await badgeAlertas.evaluate((el) => window.getComputedStyle(el).fontSize);
      expect(fontSize).toBe('11px');
    }

    // 2. Categorias: chip de usuario con admin_demo y Administrador
    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });
    const userChip = page.locator('header.inventario-cabecera .user');
    await expect(userChip).toBeVisible();
    await expect(userChip.locator('b')).toHaveText('admin_demo');
    await expect(userChip.locator('small')).toHaveText('Administrador');

    // 3. Migas de pan exactas:
    // Categorias: Catálogo / Categorías
    const crumbCat = page.locator('header.inventario-cabecera .crumb');
    await expect(crumbCat.locator('.pill')).toContainText('Catálogo');
    await expect(crumbCat.locator('b')).toHaveText('Categorías');

    // Historial: Ventas / Historial de ventas
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });
    const crumbHist = page.locator('header.inventario-cabecera .crumb');
    await expect(crumbHist.locator('.pill')).toContainText('Ventas');
    await expect(crumbHist.locator('b')).toHaveText('Historial de ventas');

    // Reportes: Reportes / Ventas por período
    await page.goto('/reportes/ventas', { waitUntil: 'domcontentloaded' });
    const crumbRep = page.locator('header.inventario-cabecera .crumb');
    await expect(crumbRep.locator('.pill')).toContainText('Reportes');
    await expect(crumbRep.locator('b')).toHaveText('Ventas por período');
  });

  test('Punto B: Patrones repetidos - filtros en una fila, sin doble borde en buscadores y contador en pildora inline', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 1. Buscador con lupa sin doble borde en /medicamentos
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });
    const inputBuscar = page.locator('#buscar');
    await expect(inputBuscar).toBeVisible();
    const inputBorderStyle = await inputBuscar.evaluate((el) => window.getComputedStyle(el).borderStyle);
    expect(inputBorderStyle).toBe('none');

    // 2. Filtros de medicamentos en una sola fila a 1600px
    const btnBuscarMed = page.locator('button:has-text("Buscar")').first();
    const btnLimpiarMed = page.locator('a:has-text("Limpiar")').first();
    const topBuscar = await btnBuscarMed.evaluate((el) => el.getBoundingClientRect().top);
    const topLimpiar = await btnLimpiarMed.evaluate((el) => el.getBoundingClientRect().top);
    expect(Math.abs(topBuscar - topLimpiar)).toBeLessThan(5);

    // 3. Proveedores: Limpiar en la misma fila que Buscar
    await page.goto('/proveedores', { waitUntil: 'domcontentloaded' });
    const btnBuscarProv = page.locator('button:has-text("Buscar")').first();
    const btnLimpiarProv = page.locator('a:has-text("Limpiar")').first();
    const topBuscarProv = await btnBuscarProv.evaluate((el) => el.getBoundingClientRect().top);
    const topLimpiarProv = await btnLimpiarProv.evaluate((el) => el.getBoundingClientRect().top);
    expect(Math.abs(topBuscarProv - topLimpiarProv)).toBeLessThan(5);

    // 4. Contadores de listado en pildora inline al lado del h2
    for (const ruta of ['/medicamentos', '/proveedores', '/movimientos', '/ventas', '/auditoria', '/usuarios']) {
      await page.goto(ruta, { waitUntil: 'domcontentloaded' });
      const secH2 = page.locator('.sec h2').first();
      await expect(secH2).toBeVisible();
      const secCnt = page.locator('.sec .cnt').first();
      await expect(secCnt).toBeVisible();
      const h2Top = await secH2.evaluate((el) => el.getBoundingClientRect().top);
      const cntTop = await secCnt.evaluate((el) => el.getBoundingClientRect().top);
      // Deben estar inline (diferencia de alineación vertical menor a 15px)
      expect(Math.abs(h2Top - cntTop)).toBeLessThan(15);
      const cntRadius = await secCnt.evaluate((el) => window.getComputedStyle(el).borderRadius);
      expect(cntRadius).toBe('999px');
    }
  });

  test('Punto C: Por pantalla - categorias, usuarios, historial, auditoria, reportes, ticket, alertas y modal nuevo cliente', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 7. Categorias: quitar #id visibles y subtitulo gris normal
    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });
    const idVisible = await page.locator('.tr .font-mono:has-text("#")').count();
    expect(idVisible).toBe(0);
    const lblSubtitulo = page.locator('.sec .lbl').first();
    const lblColor = await lblSubtitulo.evaluate((el) => window.getComputedStyle(el).color);
    // ink-3 es #5b6d66 -> rgb(91, 109, 102)
    expect(lblColor).toBe('rgb(91, 109, 102)');

    // 8. Usuarios: username mono verde pine-700 y boton reset clave neutro
    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });
    const userMono = page.locator('table tbody tr .who .mono').first();
    const userColor = await userMono.evaluate((el) => window.getComputedStyle(el).color);
    expect(userColor).toBe('rgb(22, 106, 85)'); // pine-700
    const btnClaveIcon = page.locator('table tbody tr button[title="Restablecer clave"] svg').first();
    const btnClaveClasses = await btnClaveIcon.getAttribute('class');
    expect(btnClaveClasses).not.toContain('text-blue-600');

    // 9. Historial: botones icono Ver e Imprimir con aria-label y target blank
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });
    const btnVer = page.locator('table tbody tr a[aria-label^="Ver comprobante"]').first();
    await expect(btnVer).toBeVisible();
    const btnImprimir = page.locator('table tbody tr a[aria-label^="Imprimir comprobante"]').first();
    await expect(btnImprimir).toBeVisible();
    expect(await btnImprimir.getAttribute('target')).toBe('_blank');

    // 10. Auditoria: entidad legible, IP text-left, fecha no mono
    await page.goto('/auditoria', { waitUntil: 'domcontentloaded' });
    const primerFila = page.locator('table tbody tr').first();
    if (await primerFila.isVisible()) {
      const celdaFecha = primerFila.locator('td').first();
      const fechaClase = await celdaFecha.getAttribute('class');
      expect(fechaClase).not.toContain('font-mono');
      const celdaIp = primerFila.locator('td').last();
      const ipClase = await celdaIp.getAttribute('class');
      expect(ipClase).not.toContain('r');
    }

    // 11. Reportes: leyenda "Ultimos N dias" y etiquetas dd/MM
    await page.goto('/reportes/ventas', { waitUntil: 'domcontentloaded' });
    const leyendaDias = page.locator('form[aria-label="Filtro de fechas"] .lbl:has-text("días")');
    await expect(leyendaDias).toBeVisible();

    // 12. Ticket: sin tarjeta atajos de mostrador, botones en barra fina sobre el ticket
    await page.goto('/ventas', { waitUntil: 'domcontentloaded' });
    const primerTicket = page.locator('table tbody tr a[aria-label^="Ver comprobante"]').first();
    await primerTicket.click();
    await page.waitForURL(/\/ventas\/\d+/, { timeout: 10000 });
    const atajos = page.locator('.ann:has-text("Atajos de mostrador")');
    expect(await atajos.count()).toBe(0);
    const barraTicket = page.locator('.barra-ticket-acciones, .stage .ticket-toolbar');
    await expect(barraTicket).toBeVisible();

    // 13. Alertas: sin borde superior en KPIs
    await page.goto('/alertas', { waitUntil: 'domcontentloaded' });
    const kpiCard = page.locator('.kpi').first();
    const borderTopWidth = await kpiCard.evaluate((el) => window.getComputedStyle(el).borderTopWidth);
    const borderTopColor = await kpiCard.evaluate((el) => window.getComputedStyle(el).borderTopColor);
    // Debe tener el borde normal de 1px con var(--line) rgb(223, 230, 227) o 0px, no el de 3px rojo/ambar
    expect(borderTopWidth).not.toBe('3px');

    // 14. Modal Nuevo cliente: toggle segmentado DNI / RUC con aria-pressed
    await page.goto('/clientes', { waitUntil: 'domcontentloaded' });
    const btnNuevoCliente = page.locator('a[data-modal-open="modal-nuevo-cliente"][data-modo="crear"]');
    await btnNuevoCliente.click();
    const modalCliente = page.locator('#modal-nuevo-cliente');
    await expect(modalCliente).toBeVisible();
    const selectTipoDoc = modalCliente.locator('select#modal-tipoDocumento');
    expect(await selectTipoDoc.count()).toBe(0);
    const btnToggleDni = modalCliente.locator('button.btn-toggle-doc[data-tipo="DNI"]');
    const btnToggleRuc = modalCliente.locator('button.btn-toggle-doc[data-tipo="RUC"]');
    await expect(btnToggleDni).toBeVisible();
    await expect(btnToggleRuc).toBeVisible();
    expect(await btnToggleDni.getAttribute('aria-pressed')).toBe('true');
    expect(await btnToggleRuc.getAttribute('aria-pressed')).toBe('false');
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

    // 21-paginacion.png (Movimientos, página 1, mostrando el control de paginación al pie)
    const navPaginacion = page.locator('nav[aria-label="Paginación"]');
    await navPaginacion.scrollIntoViewIfNeeded();
    await page.screenshot({ path: 'e2e/evidencias/visual/21-paginacion.png' });
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
    test.setTimeout(60000);
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // 14-modal-nuevo-cliente.png
    await page.goto('/clientes', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo cliente/i }).click();
    const modalCliente = page.locator('#modal-nuevo-cliente');
    await expect(modalCliente).toHaveClass(/activo|on/);
    await expect.poll(async () => await modalCliente.evaluate(el => window.getComputedStyle(el).opacity)).toBe('1');
    await page.waitForTimeout(300);
    await page.screenshot({ path: 'e2e/evidencias/visual/14-modal-nuevo-cliente.png' });

    // 15-modal-nuevo-proveedor.png (con estado de error en RUC del boceto 15)
    await page.goto('/proveedores', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo proveedor/i }).click();
    const modalProv = page.locator('#modal-nuevo-proveedor');
    await expect(modalProv).toHaveClass(/activo|on/);
    await expect.poll(async () => await modalProv.evaluate(el => window.getComputedStyle(el).opacity)).toBe('1');
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
    await page.waitForTimeout(300);
    await page.screenshot({ path: 'e2e/evidencias/visual/15-modal-nuevo-proveedor.png' });

    // 16-modal-nueva-categoria.png
    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nueva categoría/i }).click();
    const modalCat = page.locator('#modal-nueva-categoria');
    await expect(modalCat).toHaveClass(/activo|on/);
    await expect.poll(async () => await modalCat.evaluate(el => window.getComputedStyle(el).opacity)).toBe('1');
    await page.waitForTimeout(300);
    await page.screenshot({ path: 'e2e/evidencias/visual/16-modal-nueva-categoria.png' });

    // 17-modal-nuevo-usuario.png
    await page.goto('/usuarios', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo usuario/i }).click();
    const modalUser = page.locator('#modal-nuevo-usuario');
    await expect(modalUser).toHaveClass(/activo|on/);
    await expect.poll(async () => await modalUser.evaluate(el => window.getComputedStyle(el).opacity)).toBe('1');
    await page.waitForTimeout(300);
    await page.screenshot({ path: 'e2e/evidencias/visual/17-modal-nuevo-usuario.png' });

    // 18-modal-nuevo-medicamento.png
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });
    await page.getByRole('link', { name: /Nuevo medicamento/i }).click();
    const modalMed = page.locator('#modal-nuevo-medicamento');
    await expect(modalMed).toHaveClass(/activo|on/);
    await expect.poll(async () => await modalMed.evaluate(el => window.getComputedStyle(el).opacity)).toBe('1');
    await page.waitForTimeout(300);
    await page.screenshot({ path: 'e2e/evidencias/visual/18-modal-nuevo-medicamento.png' });

    // 19-modal-registrar-salida.png (abierto desde /movimientos)
    await page.goto('/movimientos', { waitUntil: 'domcontentloaded' });
    await page.locator('a[data-modal-open="modal-registrar-salida"]').first().click();
    const modalSalida = page.locator('#modal-registrar-salida');
    await expect(modalSalida).toHaveClass(/activo|on/);
    await expect.poll(async () => await modalSalida.evaluate(el => window.getComputedStyle(el).opacity)).toBe('1');
    await page.waitForTimeout(300);
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
      '21-paginacion.png',
    ];

    const dirVisual = path.resolve('e2e/evidencias/visual');
    expect(fs.existsSync(dirVisual)).toBeTruthy();
    for (const archivo of esperadas) {
      const ruta = path.join(dirVisual, archivo);
      expect(fs.existsSync(ruta), `Falta la captura: ${archivo}`).toBeTruthy();
    }
  });

  test('Ajuste de textos Categorías: sin jerga técnica (n-ario, taxonómica)', async ({ page }) => {
    await page.setViewportSize({ width: 1600, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/categorias', { waitUntil: 'domcontentloaded' });

    // 1. Subtítulo de la cabecera
    const subtitulo = page.locator('.head .sub');
    await expect(subtitulo).toHaveText('Organiza los medicamentos por categorías y subcategorías.');

    // 2. Título de la sección del árbol
    const tituloSeccion = page.locator('#titulo-arbol');
    await expect(tituloSeccion).toHaveText('Categorías');

    // 3. Texto de ayuda junto al título
    const ayudaSeccion = page.locator('.sec .lbl');
    await expect(ayudaSeccion).toHaveText('Usa + para añadir una subcategoría y el lápiz para editar.');

    // 4. No debe aparecer "n-ari" en ninguna parte de la página
    const contenidoPagina = await page.content();
    expect(contenidoPagina.toLowerCase()).not.toContain('n-ari');

    // Captura visual 08-categorias.png a 1600x900
    await page.screenshot({ path: 'e2e/evidencias/visual/08-categorias.png' });
  });

});
