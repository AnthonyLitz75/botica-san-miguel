#!/usr/bin/env bash
# ==============================================================================
# entrypoint-dev.sh: Supervisor de Procesos de Desarrollo en Contenedor Docker
# Inicia de forma supervisada el Java Watcher y Spring Boot DevTools
# Maneja señales SIGTERM y SIGINT para apagado limpio y sin procesos huérfanos
# ==============================================================================
set -u

WATCHER_PID=""
SPRING_PID=""
SHUTTING_DOWN=0

# Normalizar saltos de línea y permisos ejecutables
sed -i 's/\r$//' ./dev-java-watcher.sh 2>/dev/null || true
chmod +x ./dev-java-watcher.sh 2>/dev/null || true

cleanup() {
    if [ "$SHUTTING_DOWN" -eq 1 ]; then
        return
    fi
    SHUTTING_DOWN=1
    echo "[ENTRYPOINT] 🛑 Señal recibida. Deteniendo servicios de desarrollo ordenadamente..."

    if [ -n "$WATCHER_PID" ] && kill -0 "$WATCHER_PID" 2>/dev/null; then
        echo "[ENTRYPOINT] ⏳ Enviando SIGTERM a Java Watcher (PID: $WATCHER_PID)..."
        kill -TERM "$WATCHER_PID" 2>/dev/null || true
    fi

    if [ -n "$SPRING_PID" ] && kill -0 "$SPRING_PID" 2>/dev/null; then
        echo "[ENTRYPOINT] ⏳ Enviando SIGTERM a Spring Boot (PID: $SPRING_PID)..."
        kill -TERM "$SPRING_PID" 2>/dev/null || true
    fi

    # Esperar terminación de procesos hijos
    if [ -n "$SPRING_PID" ]; then
        wait "$SPRING_PID" 2>/dev/null || true
    fi
    if [ -n "$WATCHER_PID" ]; then
        wait "$WATCHER_PID" 2>/dev/null || true
    fi

    echo "[ENTRYPOINT] ✅ Procesos de desarrollo finalizados limpiamente."
    exit 0
}

# Registrar traps de apagado limpio
trap cleanup SIGINT SIGTERM SIGHUP

# 1. Iniciar Java Watcher en segundo plano
./dev-java-watcher.sh &
WATCHER_PID=$!
echo "[ENTRYPOINT] 🚀 Java Watcher iniciado en background (PID: $WATCHER_PID)"

# 2. Iniciar Spring Boot en segundo plano (sin exec para preservar el supervisor)
./mvnw spring-boot:run \
    -Dspring-boot.run.profiles=dev \
    -Dexec.skip=true \
    -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005" &
SPRING_PID=$!
echo "[ENTRYPOINT] 🚀 Spring Boot iniciado en background (PID: $SPRING_PID)"

# Esperar a que el proceso principal (Spring Boot) termine o reciba una señal
wait "$SPRING_PID" 2>/dev/null || true
EXIT_CODE=$?

cleanup
exit $EXIT_CODE
