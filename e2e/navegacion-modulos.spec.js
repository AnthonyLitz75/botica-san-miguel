import { test, expect } from '@playwright/test';

test.describe('C. Navegación y Vistas de Negocio', () => {
  test.beforeEach(async ({ page }) => {
    // Autenticación previa como admin demo
    await page.goto('/acceso/login');
    const usuario = process.env.DEMO_ADMIN_USER || 'admin_demo';
    const clave = process.env.DEMO_ADMIN_PASSWORD || 'AdminDemo2026!';
    await page.getByLabel('Usuario', { exact: true }).fill(usuario);
    await page.getByLabel('Contraseña', { exact: true }).fill(clave);
    await page.getByRole('button', { name: /Iniciar sesión/i }).click();
    await page.waitForURL((url) => url.pathname.includes('/medicamentos'));
  });

  test('debe navegar al módulo de preparación de ventas (FEFO) y renderizar la interfaz', async ({ page }) => {
    await page.goto('/ventas/nueva');

    // Verificar presencia del buscador de medicamento y encabezado Caja
    const tituloCaja = page.getByRole('heading', { level: 1, name: 'Caja' });
    await expect(tituloCaja).toBeVisible();

    const inputBuscador = page.locator('#buscar-caja');
    await expect(inputBuscador).toBeVisible();

    // 1. Simular búsqueda de medicamento para desplegar autocompletado
    await inputBuscador.fill('para');

    const sugerencias = page.locator('#sugerencias-caja');
    await expect(sugerencias).toBeVisible();

    // 2. Tomar captura con el autocompletado desplegado (idéntico al diseño de referencia)
    await page.screenshot({ path: 'e2e/evidencias/03-preparacion-ventas.png', fullPage: true });

    // 3. Seleccionar el primer medicamento con stock (ej. Paracetamol 500 mg)
    const primeraOpcion = page.locator('#sugerencias-caja .o:not([style*="not-allowed"])').first();
    await expect(primeraOpcion).toBeVisible();
    await primeraOpcion.click();

    // 4. Verificar que se agregue a la canasta SIN error HTTP 415 (zona de alertas oculta)
    const zonaAlertas = page.locator('#zona-alertas');
    await expect(zonaAlertas).toBeHidden();

    // 5. Verificar que la tabla de la canasta sea visible y contenga la línea agregada
    const tablaCanasta = page.locator('#contenedor-tabla');
    await expect(tablaCanasta).toBeVisible();

    const contadorCanasta = page.locator('#contador-canasta');
    await expect(contadorCanasta).toContainText('1 producto');

    // 6. Verificar que el botón Deshacer esté habilitado y sin el texto (0 cambios)
    const botonDeshacer = page.locator('#boton-deshacer');
    await expect(botonDeshacer).toBeEnabled();
    await expect(botonDeshacer).toContainText('Deshacer último cambio');
    await expect(botonDeshacer).not.toContainText('(0 cambios)');

    // 7. Verificar que el botón Confirmar venta esté habilitado
    const botonConfirmar = page.locator('#boton-confirmar');
    await expect(botonConfirmar).toBeEnabled();

    // 8. Captura de la canasta con productos agregados
    await page.screenshot({ path: 'e2e/evidencias/03b-canasta-con-productos.png', fullPage: true });

    // 9. Verificar buscador de cliente y dropdown flotante
    const inputCliente = page.locator('#cliente-caja');
    await inputCliente.fill('alberto');
    const sugerenciasCliente = page.locator('#sugerencias-cliente');
    await expect(sugerenciasCliente).toBeVisible();
    await page.screenshot({ path: 'e2e/evidencias/03c-buscador-cliente.png', fullPage: true });

    // 10. Probar Modal de Descuentos (reemplazo de window.prompt)
    const btnEditarDesc = page.locator('.btn-editar-descuento').first();
    await expect(btnEditarDesc).toBeVisible();
    await btnEditarDesc.click();

    // 11. Verificar que el modal de descuentos se abra y muestre los componentes esperados
    const modalBackdrop = page.locator('#modal-descuento-backdrop');
    await expect(modalBackdrop).toBeVisible();

    const tituloModal = page.locator('#modal-descuento-titulo');
    await expect(tituloModal).toHaveText('Descuentos');

    const tabMonto = page.locator('#tab-descuento-monto');
    const tabPorcentaje = page.locator('#tab-descuento-porcentaje');
    await expect(tabMonto).toHaveClass(/tab-activa/);

    const inputDescuento = page.locator('#input-valor-descuento');
    await expect(inputDescuento).toBeVisible();

    // Captura del modal de descuentos abierto en modo Monto (idéntico al diseño objetivo)
    await page.screenshot({ path: 'e2e/evidencias/03d-modal-descuento-monto.png' });

    // 12. Cambiar a modalidad Porcentaje (%)
    await tabPorcentaje.click();
    await expect(tabPorcentaje).toHaveClass(/tab-activa/);
    await expect(page.locator('#label-input-descuento')).toHaveText('Porcentaje de descuento (%)');
    await expect(page.locator('#indicador-unidad-descuento')).toHaveText('%');

    // 13. Escribir 20% de descuento y verificar cálculo reactivo en vista previa
    await inputDescuento.fill('20');
    await expect(page.locator('#texto-previa-descuento')).toContainText('Se aplicará un descuento de S/ 1.70');
    await expect(page.locator('#texto-previa-nuevo-total')).toContainText('Nuevo total: S/ 6.80');

    // Captura del modal en modo Porcentaje
    await page.screenshot({ path: 'e2e/evidencias/03e-modal-descuento-porcentaje.png' });

    // 14. Aplicar descuento y verificar que el modal se cierre y la canasta se actualice
    const btnAplicar = page.locator('#btn-aplicar-descuento');
    await btnAplicar.click();
    await expect(modalBackdrop).toBeHidden();

    // 15. Verificar que la columna de descuento y total reflejen el nuevo valor
    await expect(btnEditarDesc).toContainText('- S/ 1.70');
    await page.screenshot({ path: 'e2e/evidencias/03f-canasta-con-descuento-aplicado.png', fullPage: true });
  });

  test('debe navegar al módulo de alertas de inventario y verificar renderizado de tablas', async ({ page }) => {
    await page.goto('/alertas');

    // Verificar encabezado accesible específico de la página de alertas
    const tituloAlertas = page.getByRole('heading', { level: 1, name: 'Alertas de inventario' });
    await expect(tituloAlertas).toBeVisible();

    const seccionMetricas = page.getByRole('region', { name: 'Resumen de alertas' });
    await expect(seccionMetricas).toBeVisible();

    // Captura de pantalla de alertas
    await page.screenshot({ path: 'e2e/evidencias/04-alertas-inventario.png', fullPage: true });
  });
});
