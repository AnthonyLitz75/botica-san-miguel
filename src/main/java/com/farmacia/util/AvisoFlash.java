package com.farmacia.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.RequestContextUtils;

public final class AvisoFlash {

    private AvisoFlash() {
        // Clase utilitaria estática
    }

    public static void guardar(HttpServletRequest request, HttpServletResponse response, String targetPath, String mensaje) {
        if (request == null || mensaje == null || mensaje.isBlank()) {
            return;
        }

        FlashMap flashMap = RequestContextUtils.getOutputFlashMap(request);
        if (flashMap == null) {
            flashMap = new FlashMap();
            request.setAttribute(DispatcherServlet.OUTPUT_FLASH_MAP_ATTRIBUTE, flashMap);
        }

        flashMap.put("mensaje", mensaje);
        flashMap.put("mensajeExito", mensaje);

        if (targetPath != null && !targetPath.isBlank()) {
            String path = targetPath.contains("?") ? targetPath.substring(0, targetPath.indexOf("?")) : targetPath;
            flashMap.setTargetRequestPath(path);
        }

        FlashMapManager manager = RequestContextUtils.getFlashMapManager(request);
        if (manager != null && response != null) {
            manager.saveOutputFlashMap(flashMap, request, response);
        }
    }
}
