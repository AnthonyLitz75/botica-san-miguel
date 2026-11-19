#!/usr/bin/env bash
# ==============================================================================
# docker-dev.sh: Script de gestión para Linux / macOS / WSL2
# ==============================================================================
set -e

ACTION="${1:-up}"
SERVICE="${2:-}"
COMPOSE_FILE="docker-compose.dev.yml"

case "$ACTION" in
    up)
        echo "Iniciando entorno de desarrollo con Docker Compose..."
        docker compose -f "$COMPOSE_FILE" up -d --build
        echo ""
        echo "Servicios iniciados. Verificando estado:"
        docker compose -f "$COMPOSE_FILE" ps
        echo ""
        echo "URLs disponibles:"
        echo "  - Aplicacion Web:  http://localhost:8080/acceso/login"
        echo "  - PostgreSQL Host: localhost:5433 (BD: farmacia_aed, User: postgres)"
        echo "  - Depurador JVM:   localhost:5005"
        echo "  - LiveReload:      localhost:35729"
        ;;
    down)
        echo "Deteniendo contenedores (conservando volumenes)..."
        docker compose -f "$COMPOSE_FILE" down
        ;;
    stop)
        echo "Pausando contenedores..."
        docker compose -f "$COMPOSE_FILE" stop
        ;;
    restart)
        if [ -n "$SERVICE" ]; then
            echo "Reiniciando servicio '$SERVICE'..."
            docker compose -f "$COMPOSE_FILE" restart "$SERVICE"
        else
            echo "Reiniciando todos los servicios..."
            docker compose -f "$COMPOSE_FILE" restart
        fi
        ;;
    logs)
        if [ -n "$SERVICE" ]; then
            docker compose -f "$COMPOSE_FILE" logs -f "$SERVICE"
        else
            docker compose -f "$COMPOSE_FILE" logs -f
        fi
        ;;
    status)
        docker compose -f "$COMPOSE_FILE" ps
        ;;
    build)
        echo "Reconstruyendo imagenes de desarrollo..."
        docker compose -f "$COMPOSE_FILE" build
        ;;
    shell)
        TARGET="${SERVICE:-backend}"
        echo "Abriendo shell interactivo en '$TARGET'..."
        docker compose -f "$COMPOSE_FILE" exec "$TARGET" sh
        ;;
    test)
        echo "Ejecutando suite de pruebas End-to-End con Microsoft Playwright..."
        npx playwright test
        ;;
    *)
        echo "Uso: $0 {up|down|stop|restart|logs|status|build|shell|test} [servicio]"
        exit 1
        ;;
esac
