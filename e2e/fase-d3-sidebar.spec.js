import { test, expect } from '@playwright/test';

/**
 * e2e/fase-d3-sidebar.spec.js — Especificación RED para H-05
 * Botica San Miguel (AyE)
 *
 * Cobertura de pruebas E2E para:
 * A. Responsive y dimensiones de pantalla (1440, 1280, 1024, 1023, 820, 768, 430, 390, 375, 320 px).
 * B. Comportamiento interactivo del drawer (apertura, cierre por X, backdrop, Escape, navegación, unicidad).
 * C. Accesibilidad y gestión de foco (role dialog, aria-modal, focus trap, foco inicial, restauración, inert/scroll-lock).
 * D. Convivencia con modales y atajos de Caja POS (F2, F4, F9, Escape jerárquico).
 * E. Verificación representativa en vistas operativas y vistas sin navegación.
 */

// Helper de autenticación reutilizable
async function iniciarSesionAdmin(page) {
  await page.goto('/acceso/login');
  const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
  const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';
  await page.getByLabel('Usuario', { exact: true }).fill(usuario);
  await page.getByLabel('Contraseña', { exact: true }).fill(clave);
  await page.getByRole('button', { name: /Iniciar sesión/i }).click();
  await page.waitForURL((url) => url.pathname.includes('/medicamentos'));
}

test.describe('Fase D.3: Menú lateral responsive y accesibilidad (H-05)', () => {

  // ==========================================================================
  // BLOQUE A: Responsive y Dimensiones del Layout Global
  // ==========================================================================
  test.describe('A. Responsive y Dimensiones de Pantalla', () => {

    test('D3-A01-desk-1440: en 1440px la sidebar debe permanecer fija a la izquierda y el botón hamburguesa oculto', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 1440, height: 900 });

      const aside = page.locator('aside.inventario-lateral');
      await expect(aside).toBeVisible();

      const asideBox = await aside.boundingBox();
      expect(asideBox).not.toBeNull();
      expect(asideBox.width).toBe(248);
      expect(asideBox.x).toBe(0);

      const area = page.locator('.inventario-area');
      const areaMargin = await area.evaluate((el) => window.getComputedStyle(el).marginLeft);
      expect(areaMargin).toBe('248px');

      // En 1440px los elementos de menú móvil deben estar completamente ocultos sin colisión de selectores
      const menuMovilDetails = page.locator('details.menu-movil');
      if (await menuMovilDetails.count() > 0) {
        await expect(menuMovilDetails).toBeHidden();
      }

      const disparadorSummary = page.locator('header.inventario-cabecera summary');
      if (await disparadorSummary.count() > 0) {
        await expect(disparadorSummary).toBeHidden();
      }

      const btnNuevoSidebar = page.locator('#btn-abrir-sidebar');
      if (await btnNuevoSidebar.count() > 0) {
        await expect(btnNuevoSidebar).toBeHidden();
      }
    });

    test('D3-A02-desk-1280: en 1280px la sidebar debe permanecer fija y el layout no desbordar', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 1280, height: 800 });

      const aside = page.locator('aside.inventario-lateral');
      await expect(aside).toBeVisible();
      const asideBox = await aside.boundingBox();
      expect(asideBox.width).toBe(248);

      const hasHorizontalScroll = await page.evaluate(() => document.body.scrollWidth > window.innerWidth);
      expect(hasHorizontalScroll).toBe(false);
    });

    test('D3-A03-desk-1024: en exactamente 1024px la sidebar no debe colapsar a bloque estático vertical gigante', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 1024, height: 768 });

      // En 1024px el contenido y la cabecera no deben quedar desplazados hacia abajo por una sidebar estática de 871px
      const cabecera = page.locator('header.inventario-cabecera');
      await expect(cabecera).toBeVisible();
      const cabeceraBox = await cabecera.boundingBox();
      expect(cabeceraBox).not.toBeNull();
      // En producción actual, cabeceraBox.y es 871px debido a @media (max-width: 1024px)
      expect(cabeceraBox.y).toBeLessThan(100);

      // La sidebar no debe tener ancho del 100% (1024px) apilada arriba
      const aside = page.locator('aside.inventario-lateral');
      const asideBox = await aside.boundingBox();
      expect(asideBox.width).toBeLessThanOrEqual(280);
    });

    test('D3-A04-tab-1023: en tablet landscape (1023px) la cabecera debe iniciar arriba y la sidebar no apilarse estática', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 1023, height: 768 });

      const cabecera = page.locator('header.inventario-cabecera');
      const cabeceraBox = await cabecera.boundingBox();
      expect(cabeceraBox.y).toBeLessThan(100);

      // La sidebar no debe ocupar espacio estático vertical
      const aside = page.locator('aside.inventario-lateral');
      const isStaticInFlow = await aside.evaluate((el) => {
        const style = window.getComputedStyle(el);
        return style.position === 'static';
      });
      expect(isStaticInFlow).toBe(false);
    });

    test('D3-A05-tab-820: en tablet portrait (820px) la sidebar no debe bloquear el contenido con 871px de altura estática', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 820, height: 1180 });

      const cabecera = page.locator('header.inventario-cabecera');
      const cabeceraBox = await cabecera.boundingBox();
      expect(cabeceraBox.y).toBeLessThan(100);

      const mainContent = page.locator('#contenido');
      const mainBox = await mainContent.boundingBox();
      expect(mainBox.y).toBeLessThan(200);
    });

    test('D3-A06-tab-768: en iPad portrait estándar (768px) el contenido debe iniciar en el primer pliegue', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 768, height: 1024 });

      const cabecera = page.locator('header.inventario-cabecera');
      const cabeceraBox = await cabecera.boundingBox();
      expect(cabeceraBox.y).toBeLessThan(100);

      const aside = page.locator('aside.inventario-lateral');
      const asideBox = await aside.boundingBox();
      // La sidebar cerrada no debe estar empujando el contenido con height > 800px en el flujo
      const asideHeightInFlow = await aside.evaluate((el) => {
        const style = window.getComputedStyle(el);
        return (style.position === 'static' || style.position === 'relative') ? el.offsetHeight : 0;
      });
      expect(asideHeightInFlow).toBe(0);
    });

    test('D3-A07-mov-430: en móvil grande (430px) no debe haber desbordamiento horizontal en el layout base', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 430, height: 932 });

      const hasHorizontalScroll = await page.evaluate(() => document.body.scrollWidth > window.innerWidth);
      expect(hasHorizontalScroll).toBe(false);
    });

    test('D3-A08-mov-390: en móvil estándar (390px) no debe haber desbordamiento horizontal', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 390, height: 844 });

      const hasHorizontalScroll = await page.evaluate(() => document.body.scrollWidth > window.innerWidth);
      expect(hasHorizontalScroll).toBe(false);
    });

    test('D3-A09-mov-375: en móvil compacto (375px) en Caja POS, el buscador y canasta deben iniciar en top < 250px', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.goto('/ventas/nueva');
      await page.setViewportSize({ width: 375, height: 667 });

      const buscadorCaja = page.locator('#buscar-caja');
      await expect(buscadorCaja).toBeVisible();
      const buscadorBox = await buscadorCaja.boundingBox();
      // En producción actual, el buscador está en top: ~1100px debido a la sidebar apilada de 871px
      expect(buscadorBox.y).toBeLessThan(250);

      const hasHorizontalScroll = await page.evaluate(() => document.body.scrollWidth > window.innerWidth);
      expect(hasHorizontalScroll).toBe(false);
    });

    test('D3-A10-mov-320: en móvil mínimo (320px) el layout base debe ser fluido sin scroll horizontal', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 320, height: 568 });

      const cabecera = page.locator('header.inventario-cabecera');
      await expect(cabecera).toBeVisible();
      const hasHorizontalScroll = await page.evaluate(() => document.body.scrollWidth > window.innerWidth);
      expect(hasHorizontalScroll).toBe(false);
    });

  });

  // ==========================================================================
  // BLOQUE B: Comportamiento del Menú Móvil y Unicidad en DOM
  // ==========================================================================
  test.describe('B. Comportamiento del Drawer Móvil', () => {

    test('D3-B01-tamano-tactil-disparador: el disparador móvil debe cumplir WCAG 2.2 AA (>= 24x24 px) y objetivo ergonómico (>= 44x44 px)', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera button[aria-label*="navegación" i], header.inventario-cabecera [aria-controls="sidebar-lateral"]').first();
      await expect(disparador).toBeVisible();

      const box = await disparador.boundingBox();
      expect(box).not.toBeNull();

      // 1. Comprobación normativa WCAG 2.2 SC 2.5.8 (Nivel AA obligatorio: mínimo 24x24 px)
      // En producción actual mide 20x25 px (20px < 24px, fallo normativo AA)
      expect(box.width, 'El ancho del disparador debe ser al menos 24px según WCAG 2.2 AA SC 2.5.8').toBeGreaterThanOrEqual(24);
      expect(box.height, 'El alto del disparador debe ser al menos 24px según WCAG 2.2 AA SC 2.5.8').toBeGreaterThanOrEqual(24);

      // 2. Comprobación de objetivo ergonómico del proyecto (WCAG 2.1 SC 2.5.5 AAA / pautas táctiles: 44x44 px)
      expect(box.width, 'El ancho del disparador debe alcanzar 44px según objetivo ergonómico').toBeGreaterThanOrEqual(44);
      expect(box.height, 'El alto del disparador debe alcanzar 44px según objetivo ergonómico').toBeGreaterThanOrEqual(44);
    });

    test('D3-B02-drawer-inicialmente-cerrado: en tablet y móvil el drawer debe comenzar cerrado y fuera de pantalla', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const sidebar = page.locator('#sidebar-lateral, aside.inventario-lateral');
      const estaOcultoFueraPantalla = await sidebar.evaluate((el) => {
        const rect = el.getBoundingClientRect();
        const style = window.getComputedStyle(el);
        return rect.right <= 0 || style.display === 'none' || style.visibility === 'hidden' || style.transform.includes('matrix') && rect.x < 0;
      });
      // En producción actual, el sidebar está completamente visible en pantalla en posición estática
      expect(estaOcultoFueraPantalla).toBe(true);
    });

    test('D3-B03-apertura-boton: hacer clic en el disparador debe abrir el drawer y mostrar el backdrop', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      const sidebar = page.locator('#sidebar-lateral, aside.inventario-lateral');
      const rectSidebar = await sidebar.boundingBox();
      // El drawer abierto debe tener su borde izquierdo visible en pantalla (x >= 0)
      expect(rectSidebar.x).toBeGreaterThanOrEqual(0);

      // Debe existir un backdrop visible
      const backdrop = page.locator('#sidebar-backdrop, .sidebar-backdrop');
      await expect(backdrop).toBeVisible();
    });

    test('D3-B04-cierre-boton-x: hacer clic en el botón de cierre dentro del drawer debe cerrarlo', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      // Botón de cierre dedicado dentro del drawer
      const btnCerrar = page.locator('#btn-cerrar-sidebar, aside.inventario-lateral button[aria-label*="cerrar" i]').first();
      await expect(btnCerrar).toBeVisible();
      await btnCerrar.click();

      const sidebar = page.locator('#sidebar-lateral, aside.inventario-lateral');
      const estaOculto = await sidebar.evaluate((el) => {
        const rect = el.getBoundingClientRect();
        return rect.right <= 0 || el.classList.contains('hidden') || !el.classList.contains('sidebar-abierto');
      });
      expect(estaOculto).toBe(true);
    });

    test('D3-B05-cierre-backdrop: hacer clic en el backdrop fuera del panel debe cerrar el drawer', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      const backdrop = page.locator('#sidebar-backdrop, .sidebar-backdrop');
      await expect(backdrop).toBeVisible();
      // Clic en la zona exterior del backdrop (ej. borde derecho x: 350, y: 300)
      await page.mouse.click(350, 300);

      await expect(backdrop).toBeHidden();
    });

    test('D3-B06-cierre-escape: con el drawer realmente abierto, presionar Escape debe cerrarlo y ocultar el backdrop', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      // PRECONDICIÓN: Demostrar que el drawer está realmente abierto y el backdrop activo
      const backdrop = page.locator('#sidebar-backdrop, .sidebar-backdrop');
      await expect(backdrop, 'Precondición: el backdrop del drawer debe estar visible antes de evaluar Escape').toBeVisible({ timeout: 3000 });

      const sidebar = page.locator('#sidebar-lateral, aside.inventario-lateral');
      const boxAbierto = await sidebar.boundingBox();
      expect(boxAbierto.x, 'El borde izquierdo del drawer debe estar visible en pantalla (x >= 0)').toBeGreaterThanOrEqual(0);

      // ACCIÓN: Presionar Escape
      await page.keyboard.press('Escape');

      // POSTCONDICIÓN: Verificar que el drawer se cierra y el backdrop desaparece
      await expect(backdrop, 'El backdrop debe ocultarse tras presionar Escape').toBeHidden();
      const estaOculto = await sidebar.evaluate((el) => {
        const rect = el.getBoundingClientRect();
        return rect.right <= 0 || el.classList.contains('hidden') || !el.classList.contains('sidebar-abierto');
      });
      expect(estaOculto, 'La barra lateral debe estar oculta tras presionar Escape').toBe(true);
    });

    test('D3-B07-cierre-navegacion: con drawer abierto en móvil, hacer clic en un enlace de navegación debe navegar y cerrar el drawer', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      // PRECONDICIÓN: Demostrar que el drawer y backdrop están realmente activos
      const backdrop = page.locator('#sidebar-backdrop, .sidebar-backdrop');
      await expect(backdrop, 'Precondición: el backdrop del drawer debe estar visible antes de navegar').toBeVisible({ timeout: 3000 });

      // Clic en enlace a Categorías dentro del drawer desplegado
      const enlaceCategorias = page.locator('aside.inventario-lateral a[href*="/categorias"], #sidebar-lateral a[href*="/categorias"]').first();
      await expect(enlaceCategorias).toBeVisible();
      await enlaceCategorias.click();

      await page.waitForURL((url) => url.pathname.includes('/categorias'));

      // POSTCONDICIÓN: En la nueva vista no debe quedar ningún backdrop ni drawer bloqueando la pantalla
      await expect(backdrop).toBeHidden();
      const sidebarEnNuevaVista = page.locator('#sidebar-lateral, aside.inventario-lateral');
      const bloqueando = await sidebarEnNuevaVista.evaluate((el) => el.classList.contains('sidebar-abierto'));
      expect(bloqueando, 'El drawer no debe quedar en estado abierto tras la navegación').toBe(false);
    });

    test('D3-B08-unicidad-menu-dom: no debe existir duplicación del menú en el DOM en viewports reducidos', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      // En producción actual hay 2 nav.inventario-nav simultáneos (uno en aside y otro en header details)
      const navs = page.locator('nav.inventario-nav');
      const count = await navs.count();
      expect(count).toBe(1);
    });

  });

  // ==========================================================================
  // BLOQUE C: Accesibilidad y Foco (WCAG 2.1 AA / 2.2 AA)
  // ==========================================================================
  test.describe('C. Accesibilidad y Foco', () => {

    test('D3-C01-semantica-dialog-modal: mientras está abierto en móvil/tablet debe exponer role="dialog" y aria-modal="true"', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      const sidebar = page.locator('#sidebar-lateral, aside.inventario-lateral');
      const role = await sidebar.getAttribute('role');
      const ariaModal = await sidebar.getAttribute('aria-modal');

      expect(role).toBe('dialog');
      expect(ariaModal).toBe('true');
    });

    test('D3-C02-nombre-accesible: el elemento de navegación estructural cuenta con nombre accesible (aria-label)', async ({ page }) => {
      // Nota de auditoría: verifica la presencia de aria-label en el elemento estático actual
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const sidebar = page.locator('#sidebar-lateral, aside.inventario-lateral');
      const ariaLabel = await sidebar.getAttribute('aria-label');
      const ariaLabelledby = await sidebar.getAttribute('aria-labelledby');

      expect(ariaLabel || ariaLabelledby).toBeTruthy();
    });

    test('D3-C03-foco-inicial: al abrir el drawer el foco debe transferirse inmediatamente a un control interior', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      const focoEstaDentro = await page.evaluate(() => {
        const sidebar = document.querySelector('#sidebar-lateral, aside.inventario-lateral');
        return sidebar ? sidebar.contains(document.activeElement) : false;
      });
      expect(focoEstaDentro).toBe(true);
    });

    test('D3-C04-focus-trap: Tab y Shift+Tab deben quedar confinados dentro del drawer abierto', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      // Tabular varias veces y comprobar que el activeElement siga dentro del drawer
      for (let i = 0; i < 10; i++) {
        await page.keyboard.press('Tab');
        const focoDentro = await page.evaluate(() => {
          const sidebar = document.querySelector('#sidebar-lateral, aside.inventario-lateral');
          return sidebar ? sidebar.contains(document.activeElement) : false;
        });
        expect(focoDentro).toBe(true);
      }
    });

    test('D3-C05-restauracion-foco: con foco dentro del drawer abierto, presionar Escape debe cerrar el drawer y restaurar el foco al disparador', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      // PRECONDICIÓN ESTRICTA: El foco debe encontrarse dentro del drawer antes de evaluar la restauración
      const focoEnDrawer = await page.evaluate(() => {
        const sidebar = document.querySelector('#sidebar-lateral, aside.inventario-lateral');
        return sidebar ? sidebar.contains(document.activeElement) : false;
      });
      expect(focoEnDrawer, 'Precondición estricta: el foco debe estar dentro del drawer antes de evaluar la restauración').toBe(true);

      // ACCIÓN: Cerrar mediante Escape
      await page.keyboard.press('Escape');

      // POSTCONDICIÓN: Verificar que el foco regresa deterministamente al disparador que lo abrió
      await expect(disparador).toBeFocused();
    });

    test('D3-C06-aislamiento-fondo: mientras el drawer esté abierto, el contenedor de fondo debe estar aislado con aria-hidden o inert', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      const areaFondo = page.locator('.inventario-area');
      const isAriaHidden = await areaFondo.getAttribute('aria-hidden');
      const isInert = await areaFondo.evaluate((el) => el.hasAttribute('inert') || el.inert === true);

      expect(isAriaHidden === 'true' || isInert).toBe(true);
    });

    test('D3-C07-bloqueo-scroll: mientras el drawer esté abierto, el body debe bloquear el desplazamiento', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      const bodyOverflow = await page.evaluate(() => window.getComputedStyle(document.body).overflow);
      expect(bodyOverflow).toBe('hidden');
    });

  });

  // ==========================================================================
  // BLOQUE D: Interacción con Otros Modales y Caja POS
  // ==========================================================================
  test.describe('D. Convivencia con Modales y Caja POS', () => {

    test('D3-D01-no-abrir-si-hay-modal-activo: el drawer no debe poder abrirse mientras otro modal de negocio esté activo', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 375, height: 667 });

      // Abrir modal de nuevo medicamento en /medicamentos
      const btnNuevoMed = page.locator('a[data-modal-open="modal-nuevo-medicamento"]').first();
      await expect(btnNuevoMed).toBeVisible();
      await btnNuevoMed.click();

      const modalNuevoMed = page.locator('#modal-nuevo-medicamento');
      await expect(modalNuevoMed).toBeVisible();

      // El disparador del sidebar debe estar deshabilitado o no interactivo
      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary').first();
      const esClickable = await disparador.evaluate((el) => {
        const style = window.getComputedStyle(el);
        return !el.hasAttribute('disabled') && style.pointerEvents !== 'none';
      });
      expect(esClickable).toBe(false);

      // Cerrar modal de nuevo medicamento
      await page.keyboard.press('Escape');
      await expect(modalNuevoMed).toBeHidden();
    });

    test('D3-D02-convivencia-drawer-modal-escape: convivencia y precedencia de modales en Caja POS (no doble diálogo y Escape jerárquico)', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.goto('/ventas/nueva');
      await page.setViewportSize({ width: 375, height: 667 });

      // 0. Preparar una línea con medicamento para habilitar el botón de cancelación
      const inputBuscador = page.locator('#buscar-caja');
      await inputBuscador.fill('para');
      const opcion = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
      await expect(opcion).toBeVisible();
      await opcion.click();

      const btnCancelar = page.locator('#btn-cancelar-venta');
      await expect(btnCancelar).toBeEnabled();

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      const sidebarBackdrop = page.locator('#sidebar-backdrop, .sidebar-backdrop');
      const modalCancelar = page.locator('#modal-cancelar-venta-backdrop');

      // ======================================================================
      // 1. Con drawer abierto:
      //    - El modal de cancelación no debe poder abrirse.
      //    - Escape cierra el drawer.
      //    - El foco retorna al disparador del drawer.
      // ======================================================================
      await disparador.click();

      // Precondición estricta: el drawer y su backdrop deben estar visibles
      await expect(sidebarBackdrop, 'Precondición: el backdrop del drawer debe estar visible').toBeVisible({ timeout: 3000 });

      // El modal de cancelación no debe poder abrirse (botón inactivo / no interactivo)
      const btnCancelarClickeable = await btnCancelar.evaluate((el) => {
        const style = window.getComputedStyle(el);
        const isDisabled = el.hasAttribute('disabled') || el.disabled === true;
        const isPointerEventsNone = style.pointerEvents === 'none';
        const hasInertAncestor = el.closest('[inert]') !== null || el.inert === true;
        const hasAriaHiddenAncestor = el.closest('[aria-hidden="true"]') !== null;
        return !isDisabled && !isPointerEventsNone && !hasInertAncestor && !hasAriaHiddenAncestor;
      });
      expect(btnCancelarClickeable, 'El botón de cancelación no debe ser interactivo mientras el drawer esté abierto').toBe(false);

      // Escape cierra el drawer
      await page.keyboard.press('Escape');
      await expect(sidebarBackdrop, 'El backdrop del drawer debe ocultarse tras Escape').toBeHidden();
      await expect(modalCancelar, 'El modal de cancelación no debe abrirse al presionar Escape en el drawer').toBeHidden();

      // El foco retorna al disparador del drawer
      await expect(disparador, 'El foco debe retornar al disparador tras cerrar el drawer').toBeFocused();

      // ======================================================================
      // 2. Con modal de cancelación abierto:
      //    - El drawer no debe poder abrirse.
      //    - Escape cierra el modal de cancelación.
      //    - El foco retorna a #btn-cancelar-venta.
      // ======================================================================
      await btnCancelar.click();
      await expect(modalCancelar, 'Precondición: el modal de cancelación debe estar visible').toBeVisible();

      // El drawer no debe poder abrirse (disparador inactivo / no interactivo)
      const disparadorClickeableConModal = await disparador.evaluate((el) => {
        const style = window.getComputedStyle(el);
        const isDisabled = el.hasAttribute('disabled') || el.disabled === true;
        const isPointerEventsNone = style.pointerEvents === 'none';
        const hasInertAncestor = el.closest('[inert]') !== null || el.inert === true;
        const hasAriaHiddenAncestor = el.closest('[aria-hidden="true"]') !== null;
        return !isDisabled && !isPointerEventsNone && !hasInertAncestor && !hasAriaHiddenAncestor;
      });
      expect(disparadorClickeableConModal, 'El disparador del drawer debe estar inactivo mientras el modal de cancelación esté abierto').toBe(false);

      // Escape cierra el modal de cancelación
      await page.keyboard.press('Escape');
      await expect(modalCancelar, 'El modal de cancelación debe cerrarse tras presionar Escape').toBeHidden();
      await expect(sidebarBackdrop, 'El drawer no debe abrirse al cerrar el modal con Escape').toBeHidden();

      // El foco retorna a #btn-cancelar-venta
      await expect(btnCancelar, 'El foco debe retornar a #btn-cancelar-venta tras cerrar el modal con Escape').toBeFocused();

      // ======================================================================
      // 3. Después de cerrar el modal:
      //    - El drawer debe poder abrirse nuevamente.
      //    - Escape debe cerrarlo.
      //    - El foco debe retornar al disparador original.
      // ======================================================================
      await disparador.click();
      await expect(sidebarBackdrop, 'El drawer debe poder abrirse nuevamente tras cerrar el modal').toBeVisible({ timeout: 3000 });

      // Escape debe cerrarlo
      await page.keyboard.press('Escape');
      await expect(sidebarBackdrop, 'El drawer reabierto debe cerrarse tras presionar Escape').toBeHidden();

      // El foco debe retornar al disparador original
      await expect(disparador, 'El foco debe retornar al disparador tras cerrar el drawer reabierto').toBeFocused();
    });

    test('D3-D03-no-desvio-por-atajos-pos: presionar F2 en Caja POS con drawer abierto no debe desviar el foco al buscador de fondo', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.goto('/ventas/nueva');
      await page.setViewportSize({ width: 375, height: 667 });

      const disparador = page.locator('#btn-abrir-sidebar, header.inventario-cabecera .menu-movil summary, header.inventario-cabecera [aria-label*="navegación" i]').first();
      await disparador.click();

      // Presionar F2 (atajo habitual de Caja POS para enfocar buscador de medicamentos)
      await page.keyboard.press('F2');

      const focoSigueDentro = await page.evaluate(() => {
        const sidebar = document.querySelector('#sidebar-lateral, aside.inventario-lateral');
        return sidebar ? sidebar.contains(document.activeElement) : false;
      });
      expect(focoSigueDentro).toBe(true);
    });

    test('D3-D04-regresion-flujos-caja-desktop: [NO-REGRESIÓN] la canasta y buscador en Caja POS en escritorio conservan sus datos e interacción íntegros', async ({ page }) => {
      // Nota de auditoría: Clasificada expresamente como salvaguarda de no-regresión general de Caja POS (1440px),
      // no como prueba de integración del drawer responsive.
      await iniciarSesionAdmin(page);
      await page.goto('/ventas/nueva');
      await page.setViewportSize({ width: 1440, height: 900 });

      // Verificar que el título de Caja y el buscador estén operativos
      await expect(page.getByRole('heading', { level: 1, name: 'Caja' })).toBeVisible();
      const inputBuscar = page.locator('#buscar-caja');
      await expect(inputBuscar).toBeVisible();
      await inputBuscar.fill('para');

      const primeraOpcion = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
      await expect(primeraOpcion).toBeVisible();
      await primeraOpcion.click();

      await expect(page.locator('#contenedor-tabla')).toBeVisible();
      await expect(page.locator('#contador-canasta')).toContainText('1 producto');
    });

  });

  // ==========================================================================
  // BLOQUE E: Alcance de Vistas Representativas
  // ==========================================================================
  test.describe('E. Alcance de Vistas Representativas', () => {

    test('D3-E01-vistas-operativas-compartidas: la navegación compartida debe renderizarse de forma homogénea en vistas clave', async ({ page }) => {
      await iniciarSesionAdmin(page);
      await page.setViewportSize({ width: 1280, height: 800 });

      const vistas = [
        { url: '/medicamentos', nombre: 'Medicamentos' },
        { url: '/alertas', nombre: 'Alertas' },
        { url: '/ordenes-compra', nombre: 'Órdenes de compra' },
        { url: '/ventas/nueva', nombre: 'Caja' }
      ];

      for (const v of vistas) {
        await page.goto(v.url);
        const aside = page.locator('aside.inventario-lateral');
        await expect(aside).toBeVisible();

        const cabecera = page.locator('header.inventario-cabecera');
        await expect(cabecera).toBeVisible();

        // Enlace activo con aria-current="page" debe coincidir con la vista
        const itemActivo = page.locator('aside.inventario-lateral a[aria-current="page"]');
        await expect(itemActivo).toBeVisible();
      }
    });

    test('D3-E02-vistas-sin-navegacion: /acceso/login no debe arrojar errores de script ni contener fragmentos de navegación', async ({ page }) => {
      const consoleErrors = [];
      page.on('console', (msg) => {
        if (msg.type() === 'error') {
          consoleErrors.push(msg.text());
        }
      });

      await page.goto('/acceso/login');
      const aside = page.locator('aside.inventario-lateral');
      await expect(aside).toBeHidden();

      const cabecera = page.locator('header.inventario-cabecera');
      await expect(cabecera).toBeHidden();

      expect(consoleErrors).toEqual([]);
    });

  });

});
