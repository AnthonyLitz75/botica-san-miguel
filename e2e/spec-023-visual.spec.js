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
    await page.goto('/alertas', { waitUntil: 'domcontentloaded' });

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
    // Ir a /medicamentos para encontrar un .inp
    await page.goto('/medicamentos', { waitUntil: 'domcontentloaded' });
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

});
