# Stage 1: build
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
# Кэшируем зависимости сначала
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Устанавливаем времязону и базовые утилиты для отладки
RUN apk add --no-cache tzdata curl
ENV TZ=Europe/Moscow

# Создаем непривилегированного пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Health check для мониторинга
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Оптимизированные JVM параметры
CMD java -XX:+UseContainerSupport \
         -XX:MaxRAMPercentage=75.0 \
         -Xmx256m \
         -Xss512k \
         -XX:+UseG1GC \
         -XX:MaxGCPauseMillis=200 \
         -Dspring.profiles.active=prod \
         -Djava.security.egd=file:/dev/./urandom \
         -Dspring.datasource.hikari.leak-detection-threshold=60000 \
         -jar app.jar