# ==========================================
# Etapa 1: Estilos (Tailwind CSS con Node.js)
# ==========================================
FROM node:22-alpine AS estilos
WORKDIR /app

# Instalar dependencias de Node primero para aprovechar el almacenamiento en caché de Docker
COPY package.json ./
RUN npm install --no-audit --no-fund

# Copiar la configuración de Tailwind y los archivos que escanea (plantillas y JS)
COPY tailwind.config.js ./
COPY src/main/resources/templates ./src/main/resources/templates
COPY src/main/resources/static/js ./src/main/resources/static/js
COPY src/main/resources/static/css/tailwind-input.css ./src/main/resources/static/css/tailwind-input.css

# Generar tailwind.css minificado
RUN npm run build:css

# ==========================================
# Etapa 2: Compilación (Build Stage)
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

# Copiar el código fuente y reemplazar tailwind.css por el generado en la etapa de estilos
COPY src ./src
COPY --from=estilos /app/src/main/resources/static/css/tailwind.css ./src/main/resources/static/css/tailwind.css

# Empaquetar el JAR ejecutable (omitiendo tests para optimizar tiempos de despliegue).
# -Dexec.skip=true evita que Maven ejecute npm: esta imagen no incluye Node y los estilos ya se generaron arriba
RUN ./mvnw clean package -DskipTests -B -Dexec.skip=true

# ==========================================
# Etapa 3: Imagen de Ejecución Ligera (Runtime)
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
