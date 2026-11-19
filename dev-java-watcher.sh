#!/usr/bin/env bash
# ==============================================================================
# dev-java-watcher.sh: Vigilante Incremental Resiliente de Código Java
# Proyecto: Botica San Miguel - Desarrollo Local Asistido por IA
# ==============================================================================
set -u

WATCH_DIR="src/main/java"
LOCK_FILE="/tmp/mvn-compile.lock"
POLL_INTERVAL=1
DEBOUNCE_SECONDS=1

echo "[JAVA-WATCHER] 👁️  Iniciando vigilancia incremental en $WATCH_DIR..."

# Limpieza inicial defensiva de bloqueos obsoletos de ejecuciones previas
rm -f "$LOCK_FILE" 2>/dev/null || true

# Limpieza al salir o recibir señales
cleanup_lock() {
    rm -f "$LOCK_FILE" 2>/dev/null || true
    exit 0
}
trap cleanup_lock EXIT INT TERM

get_latest_mtime() {
    # Busca la fecha epoch más reciente entre los archivos .java de forma segura
    local mtime
    mtime=$(find "$WATCH_DIR" -type f -name "*.java" -exec stat -c %Y {} + 2>/dev/null | sort -nr | head -n1 2>/dev/null)
    if [[ "$mtime" =~ ^[0-9]+$ ]]; then
        echo "$mtime"
    else
        echo 0
    fi
}

is_compilation_active() {
    if [ -f "$LOCK_FILE" ]; then
        local pid
        pid=$(cat "$LOCK_FILE" 2>/dev/null || echo "")
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            return 0 # Proceso de compilación activo
        else
            # Lock huérfano de un proceso ya terminado
            echo "[JAVA-WATCHER] 🧹 Lock huérfano detectado (PID inactivo: $pid). Limpiando bloqueo..."
            rm -f "$LOCK_FILE" 2>/dev/null || true
            return 1
        fi
    fi
    return 1
}

LAST_MTIME=$(get_latest_mtime)
LAST_MTIME="${LAST_MTIME:-0}"
echo "[JAVA-WATCHER] 📌 Estado base registrado (timestamp: $LAST_MTIME). Esperando modificaciones..."

while true; do
    sleep "$POLL_INTERVAL"
    CURRENT_MTIME=$(get_latest_mtime)
    CURRENT_MTIME="${CURRENT_MTIME:-0}"

    if [ "$CURRENT_MTIME" -gt "$LAST_MTIME" ]; then
        echo "[JAVA-WATCHER] 🔔 Cambio detectado en $WATCH_DIR. Aplicando debounce ($DEBOUNCE_SECONDS s)..."
        sleep "$DEBOUNCE_SECONDS"

        # Actualizar mtime post-debounce para absorber guardados concurrentes
        CURRENT_MTIME=$(get_latest_mtime)
        LAST_MTIME="${CURRENT_MTIME:-0}"

        if is_compilation_active; then
            echo "[JAVA-WATCHER] ⏳ Compilación ya en progreso. Saltando evento redundante."
            continue
        fi

        # Registrar PID actual en el lockfile
        echo $$ > "$LOCK_FILE"
        echo "[JAVA-WATCHER] 🔨 Ejecutando compilación incremental Maven (./mvnw compile -Dexec.skip=true)..."
        START_TIME=$(date +%s)

        # Compilar solo cambios incrementales sin tests ni empaquetado
        if ./mvnw compile -Dexec.skip=true; then
            END_TIME=$(date +%s)
            DURATION=$((END_TIME - START_TIME))
            # Disparar reinicio limpio de Spring Boot DevTools mediante el trigger file
            mkdir -p target/classes
            touch target/classes/.reloadtrigger
            echo "[JAVA-WATCHER] ✅ Compilación exitosa en ${DURATION}s. Classes actualizadas y trigger activado para DevTools."
        else
            EXIT_CODE=$?
            echo "[JAVA-WATCHER] ❌ Error de compilación Java detectado (código $EXIT_CODE)."
            echo "[JAVA-WATCHER] 💡 Revisa la sintaxis en el archivo modificado y guarda de nuevo para reintentar."
        fi

        rm -f "$LOCK_FILE" 2>/dev/null || true
    fi
done
