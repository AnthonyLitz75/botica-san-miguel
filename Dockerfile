# ==========================================
# Etapa 1: Compilación (Build Stage)
# ==========================================
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copiar archivos de Maven Wrapper y pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Normalizar terminaciones de línea (CRLF a LF) y otorgar permisos de ejecución
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw

# Descargar dependencias para aprovechar el almacenamiento en caché de Docker
RUN ./mvnw dependency:go-offline -B || true

# Copiar el código fuente y empaquetar el JAR ejecutable (omitiendo tests para optimizar tiempos de despliegue)
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# Etapa 2: Imagen de Ejecución Ligera (Runtime)
# ==========================================
FROM eclipse-temurin:25-jre AS runner
WORKDIR /app

# Crear usuario y grupo de sistema sin privilegios para ejecución segura
RUN addgroup --system spring && adduser --system --ingroup spring spring
USER spring:spring

# Copiar el JAR generado desde la etapa de compilación
COPY --from=builder /app/target/farmacia-aed-0.0.1-SNAPSHOT.jar app.jar

# Render expone y asigna el puerto en la variable de entorno $PORT (por defecto 8080)
ENV PORT=8080
EXPOSE 8080

# Optimización de memoria JVM para el plan gratuito de Render (512 MB de RAM)
# Limita el heap a 75% de la RAM disponible para evitar que el OOM Killer del sistema detenga el proceso
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Iniciar la aplicación Spring Boot
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
