import { test, expect } from '@playwright/test';
import fs from 'fs';
import path from 'path';

test.describe('D. Verificación de LiveReload en Navegador Real', () => {
  const tailwindInputPath = path.resolve('src/main/resources/static/css/tailwind-input.css');
  const loginHtmlPath = path.resolve('src/main/resources/templates/acceso/login.html');

  let originalTailwindInput = '';
  let originalLoginHtml = '';

  const restoreOriginalFiles = () => {
    try {
      if (originalTailwindInput) {
        fs.writeFileSync(tailwindInputPath, originalTailwindInput, 'utf-8');
      }
      if (originalLoginHtml) {
        fs.writeFileSync(loginHtmlPath, originalLoginHtml, 'utf-8');
      }
    } catch (e) {
      // Ignorar errores en limpieza de proceso
    }
  };

  test.beforeAll(() => {
    originalTailwindInput = fs.readFileSync(tailwindInputPath, 'utf-8');
    originalLoginHtml = fs.readFileSync(loginHtmlPath, 'utf-8');

    // Registrar manejadores de proceso para evitar archivos modificados en caso de aborto
    process.once('exit', restoreOriginalFiles);
    process.once('SIGINT', restoreOriginalFiles);
    process.once('SIGTERM', restoreOriginalFiles);
  });

  test.afterEach(() => {
    restoreOriginalFiles();
  });

  test.afterAll(() => {
    restoreOriginalFiles();
  });

  test('debe conectar al WebSocket LiveReload y reflejar cambios CSS y HTML sin F5 manual', async ({ page }) => {
    const liveReloadLogs = [];
    page.on('console', (msg) => {
      const text = msg.text();
      if (text.includes('[LiveReload]')) {
        liveReloadLogs.push(text);
      }
    });

    // 1. Abrir la página en el navegador real
    await page.goto('/acceso/login');

    // 2. Esperar confirmación de conexión al WebSocket de LiveReload (puerto 35729)
    await expect.poll(() => liveReloadLogs.some(l => l.includes('Conectado al servidor LiveReload')), {
      message: 'Debe conectar al servidor LiveReload en puerto 35729',
      timeout: 8000,
      intervals: [500]
    }).toBe(true);

    const tituloLogin = page.locator('#titulo-login');
    await expect(tituloLogin).toBeVisible();

    // 3. Modificar temporalmente un estilo CSS (Hot CSS Reload sin F5)
    // Se aplica letter-spacing de 6px que no existe en el estilo base
    const testCssRule = '\n\n/* TEST_LIVERELOAD_HOT_CSS */\n#titulo-login { letter-spacing: 6px !important; }\n';
    fs.writeFileSync(tailwindInputPath, originalTailwindInput + testCssRule, 'utf-8');

    // 4. Verificación dura en el DOM: El navegador debe recibir y aplicar el nuevo estilo CSS en tiempo real
    await expect(tituloLogin).toHaveCSS('letter-spacing', '6px', { timeout: 20000 });

    // 5. Modificar temporalmente una plantilla HTML (LiveReload recarga la página automáticamente)
    const modifiedHtml = originalLoginHtml.replace(
      'Gestión de inventario y compras',
      'Gestión de inventario y compras (RECARGA_VIVA_EXITOSA)'
    );
    fs.writeFileSync(loginHtmlPath, modifiedHtml, 'utf-8');

    // 6. El navegador debe actualizarse solo y mostrar el nuevo texto en el DOM sin pulsar F5
    const nuevoTexto = page.getByText(/RECARGA_VIVA_EXITOSA/i);
    await expect(nuevoTexto).toBeVisible({ timeout: 10000 });

    // 7. Captura de pantalla de la recarga en vivo verificada
    await page.screenshot({ path: 'e2e/evidencias/05-livereload-verificado.png' });
  });
});
