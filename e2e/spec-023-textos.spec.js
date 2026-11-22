import { test, expect } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

/**
 * e2e/spec-023-textos.spec.js
 * AJUSTE 023 (paso 6): Pulido de Auditoría, textos sin códigos internos y comprobante
 * Botica San Miguel (AyE)
 */

async function iniciarSesion(page, usuario = 'admin_demo', clave = 'AdminDemo2026!') {
  await page.goto('/acceso/login', { waitUntil: 'domcontentloaded' });
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => !url.pathname.includes('/acceso/login'), { timeout: 15000 });
}

async function guardarCaptura(page, nombreArchivo) {
  const dirBocetos = path.join(process.cwd(), 'docs', 'bocetos');
  const dirEvidencias = path.join(process.cwd(), 'e2e', 'evidencias', 'visual');
  if (!fs.existsSync(dirBocetos)) fs.mkdirSync(dirBocetos, { recursive: true });
  if (!fs.existsSync(dirEvidencias)) fs.mkdirSync(dirEvidencias, { recursive: true });

  const rutaBoceto = path.join(dirBocetos, nombreArchivo);
  const rutaEvidencia = path.join(dirEvidencias, nombreArchivo);

  await page.screenshot({ path: rutaBoceto, fullPage: true });
  fs.copyFileSync(rutaBoceto, rutaEvidencia);
}

test.describe('Ajuste 023: Paso 6 — Textos, comprobante y pulido de interfaces', () => {

  test('a. Auditoría: contador y pie con coma, aviso azul, guion en celdas vacías y nota de CSV', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/auditoria', { waitUntil: 'networkidle' });

    // Subtítulo
    const sub = page.locator('.head .sub');
    await expect(sub).toHaveText('Quién hizo qué y cuándo en el sistema.');

    // Placeholder usuario
    const inputUsuario = page.locator('#usuario');
    await expect(inputUsuario).toHaveAttribute('placeholder', 'Nombre de usuario');

    // Aviso azul de solo lectura (sin trigger ni Bitácora)
    const avisoAzul = page.locator('.note');
    await expect(avisoAzul).toBeVisible();
    await expect(avisoAzul).toContainText('Registro de solo lectura: ningún evento puede modificarse ni eliminarse.');
    await expect(avisoAzul).not.toContainText('trigger');
    await expect(avisoAzul).not.toContainText('Bitácora');

    // Nota del CSV
    const notaCsv = page.locator('span.text-ink-3:has-text("El CSV incluye hasta")');
    await expect(notaCsv).toBeVisible();
    await expect(notaCsv).toContainText(/El CSV incluye hasta \d{1,3}(,\d{3})+ filas; acota las fechas si necesitas más\./);

    // Contador de eventos con separador de miles
    const contador = page.locator('section[aria-labelledby="titulo-auditoria"] .cnt');
    if (await contador.isVisible()) {
      const textoContador = (await contador.textContent() || '').trim();
      expect(textoContador).toMatch(/^\d{1,3}(,\d{3})*$/);
    }

    // Pie de paginación con separador de miles
    const piePaginacion = page.locator('.lbl.resultados-pie');
    if (await piePaginacion.isVisible()) {
      const textoPie = (await piePaginacion.textContent() || '').trim();
      expect(textoPie).toMatch(/Mostrando \d+–\d+ de \d{1,3}(,\d{3})*/);
    }

    // Captura 37: Auditoría
    await guardarCaptura(page, '37-auditoria.png');
  });

  test('b. Modal Nuevo usuario: textos limpios, ayudas fieles y sin jerga técnica', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/usuarios', { waitUntil: 'networkidle' });

    // Abrir modal nuevo usuario
    const btnNuevoUsuario = page.locator('[data-modal-open="modal-nuevo-usuario"][data-modo="crear"]');
    await expect(btnNuevoUsuario).toBeVisible();
    await btnNuevoUsuario.click();

    const modal = page.locator('#modal-nuevo-usuario');
    await expect(modal).toHaveClass(/activo/);

    // Subtítulo
    await expect(modal.locator('.mh p')).toHaveText('Crea la cuenta de un trabajador y elige qué puede hacer en el sistema.');

    // Labels
    await expect(modal.locator('label[for="modal-nombre-user"]')).toContainText('Nombre completo');
    await expect(modal.locator('label[for="modal-nombre-user"]')).not.toContainText('del trabajador');

    await expect(modal.locator('label[for="modal-nombreUsuario"]')).toContainText('Nombre de usuario');
    await expect(modal.locator('label[for="modal-nombreUsuario"]')).not.toContainText('(login)');

    // Ayuda de usuario fiel a la regla real
    await expect(modal.locator('.help:has-text("De 4 a 50 caracteres")')).toBeVisible();

    // Rol y opciones
    await expect(modal.locator('label[for="modal-rol-user"]')).toContainText('Rol');
    await expect(modal.locator('label[for="modal-rol-user"]')).not.toContainText('RBAC');

    const opcionesRol = await modal.locator('#modal-rol-user option').allInnerTexts();
    expect(opcionesRol).toContain('Vendedor: atención y caja');
    expect(opcionesRol).toContain('Almacenero: inventario y movimientos');
    expect(opcionesRol).toContain('Compras: proveedores y órdenes de compra');
    expect(opcionesRol).toContain('Administrador: acceso total');

    // Contraseña
    await expect(modal.locator('.help:has-text("Se guarda cifrada: nadie podrá verla después.")')).toBeVisible();
    await expect(modal.locator('#modal-contenedor-clave')).not.toContainText('BCrypt');
    await expect(modal.locator('#modal-contenedor-clave')).not.toContainText('RNF-01');

    // Captura 40: Modal Nuevo Usuario
    await guardarCaptura(page, '40-usuario-modal.png');
    await modal.locator('.btn-cerrar-modal').first().click();
  });

  test('c. Modal Nuevo medicamento: textos claros, categoría opcional y ayuda sin D-6', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/medicamentos', { waitUntil: 'networkidle' });

    const btnNuevoMed = page.locator('[data-modal-open="modal-nuevo-medicamento"][data-modo="crear"]');
    await expect(btnNuevoMed).toBeVisible();
    await btnNuevoMed.click();

    const modal = page.locator('#modal-nuevo-medicamento');
    await expect(modal).toHaveClass(/activo/);

    // Subtítulo
    await expect(modal.locator('.mh p')).toHaveText('Registra un medicamento para poder venderlo y controlar su stock.');

    // Labels y ayudas
    await expect(modal.locator('label[for="modal-codigo-med"]')).toContainText('Código');
    await expect(modal.locator('label[for="modal-codigo-med"]')).not.toContainText('del fármaco');
    await expect(modal.locator('.help:has-text("Es único y no se puede cambiar después.")')).toBeVisible();

    await expect(modal.locator('label[for="modal-concentracion-med"]')).toContainText('Concentración (opcional)');
    await expect(modal.locator('label[for="modal-unidad-med"]')).toContainText('Unidad de venta');
    await expect(modal.locator('label[for="modal-categoria-med"]')).toContainText('Categoría (opcional)');
    await expect(modal.locator('#modal-categoria-med option[value=""]')).toHaveText('Sin categoría');

    // Precio y stock mínimo sin códigos internos
    await expect(modal.locator('.help:has-text("Puedes poner 0.00 si es una donación.")')).toBeVisible();
    await expect(modal.locator('.mb')).not.toContainText('D-6');

    await expect(modal.locator('label[for="modal-stockMinimo-med"]')).toContainText('Stock mínimo');
    await expect(modal.locator('label[for="modal-stockMinimo-med"]')).not.toContainText('de alerta');
    await expect(modal.locator('.help:has-text("Te avisamos en Alertas cuando el stock baje a este número.")')).toBeVisible();

    // Captura 41: Modal Nuevo Medicamento
    await guardarCaptura(page, '41-medicamento-modal.png');
    await modal.locator('.btn-cerrar-modal').first().click();
  });

  test('d. Modal Registrar salida: textos claros, textarea con fuente sans y aviso amarillo', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');
    await page.goto('/medicamentos', { waitUntil: 'networkidle' });

    // Navegar a los lotes del primer medicamento
    const linkLotes = page.locator('a.enlace-lotes').first();
    await expect(linkLotes).toBeVisible();
    await linkLotes.click();
    await page.waitForURL(/\/medicamentos\/\d+\/lotes/, { timeout: 10000 });

    // Abrir modal registrar salida
    const btnSalida = page.locator('table tbody tr a[data-modal-open="modal-registrar-salida"]').first();
    await expect(btnSalida).toBeVisible();
    await btnSalida.click();

    const modal = page.locator('#modal-registrar-salida');
    await expect(modal).toHaveClass(/activo/);

    // Subtítulo
    await expect(modal.locator('.mh p')).toHaveText('Retira unidades de este lote por merma, vencimiento o ajuste.');

    // Label Lote
    await expect(modal.locator('.lbl:has-text("Lote")')).toBeVisible();

    // Ayuda de cantidad
    const ayudaCant = modal.locator('#modal-salida-max-help');
    await expect(ayudaCant).toContainText(/Puedes retirar hasta \d+ unidades\./);

    // Placeholder y ayuda de motivo
    const textareaMotivo = modal.locator('#modal-salida-motivo');
    await expect(textareaMotivo).toHaveAttribute('placeholder', 'Ej. Frasco quebrado, lote vencido o merma por humedad');
    await expect(modal.locator('.help:has-text("Queda registrado en el historial junto con tu usuario.")')).toBeVisible();
    await expect(modal).not.toContainText('Decisión D-7');

    // Tipografía sans en textarea
    const fontFamily = await textareaMotivo.evaluate((el) => window.getComputedStyle(el).fontFamily);
    expect(fontFamily.toLowerCase()).not.toContain('monospace');
    expect(fontFamily.toLowerCase()).not.toContain('consolas');

    // Aviso amarillo
    const avisoAmarillo = modal.locator('.note');
    await expect(avisoAmarillo).toContainText('Esto descuenta el stock y queda registrado en Auditoría con tu usuario.');
    await expect(avisoAmarillo).not.toContainText('inmutable');
    await expect(avisoAmarillo).not.toContainText('D-13');

    // Captura 38: Modal Registrar Salida
    await guardarCaptura(page, '38-registrar-salida-modal.png');
    await modal.locator('.btn-cerrar-modal').first().click();
  });

  test('e. Comprobante de venta: ticket "TICKET DE VENTA", botón "Nueva venta" y panel "Después de cobrar"', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    // Navegar al historial y abrir el comprobante de la primera venta disponible
    await page.goto('/ventas', { waitUntil: 'networkidle' });
    const enlaceComprobante = page.locator('table.tabla-inventario tbody tr a.lnk').first();
    if (await enlaceComprobante.isVisible()) {
      await enlaceComprobante.click();
    } else {
      await page.goto('/ventas/1', { waitUntil: 'networkidle' });
    }

    await page.waitForURL(/\/ventas\/\d+/, { timeout: 15000 });

    // Subtítulo
    await expect(page.locator('.head .sub')).toHaveText('La venta se registró. Imprime el ticket o inicia la siguiente venta.');

    // Botón "Nueva venta" enlazando a /ventas/preparacion
    const btnNuevaVenta = page.locator('a.btn:has-text("Nueva venta")');
    await expect(btnNuevaVenta).toBeVisible();
    await expect(btnNuevaVenta).toHaveAttribute('href', '/ventas/preparacion');

    // Ticket título: TICKET DE VENTA (sin ELECTRÓNICA)
    const ticket = page.locator('article#comprobante');
    await expect(ticket).toContainText('TICKET DE VENTA');
    await expect(ticket).not.toContainText('BOLETA DE VENTA ELECTRÓNICA');

    // Panel derecho: "Después de cobrar"
    const panelLateral = page.locator('.ann');
    await expect(panelLateral.locator('h3')).toHaveText('Después de cobrar');
    await expect(panelLateral).toContainText('Imprime el ticket si el cliente lo pide (está formateado a 80 mm).');
    await expect(panelLateral).toContainText(/El número de boleta es B001-\d{8}; búscalo en Ventas si necesitas reimprimir\./);
    await expect(panelLateral).toContainText('Los lotes y vencimientos se descontaron del inventario.');
    await expect(panelLateral).not.toContainText('ESC/POS');
    await expect(panelLateral).not.toContainText('RNF-04');

    // Captura 39: Comprobante
    await guardarCaptura(page, '39-comprobante.png');
  });

  test('f. Barrido: verificación de ausencia de códigos internos en las vistas principales', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await iniciarSesion(page, 'admin_demo', 'AdminDemo2026!');

    const rutas = ['/medicamentos', '/alertas', '/usuarios', '/auditoria', '/reportes/ventas', '/ventas/preparacion'];
    const regexProhibido = /\b(RNF-\d+|RF-\d+|CU-\d+|RBAC|BCrypt)\b|Decisión D-|\bD-\d+\b/i;

    for (const ruta of rutas) {
      await page.goto(ruta, { waitUntil: 'domcontentloaded' });
      const textoVisible = await page.locator('body').innerText();
      const match = textoVisible.match(regexProhibido);
      expect(match, `Código interno encontrado en ${ruta}: ${match ? match[0] : ''}`).toBeNull();
    }
  });

});
