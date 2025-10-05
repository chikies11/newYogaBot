# Stage 1: build
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
# Кэшируем зависимости сначала
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: runtime (используем более легкий образ)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Устанавливаем времязону
RUN apk add --no-cache tzdata
ENV TZ=Europe/Moscow

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Оптимизированные JVM параметры
ENTRYPOINT ["java", "-XX:+UseG1GC", "-Xmx256m", "-Xss512k", \
           "-Dspring.profiles.active=prod", \
           "-Djava.security.egd=file:/dev/./urandom", \
           "-jar", "app.jar"]