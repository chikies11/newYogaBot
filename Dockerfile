# Stage 1: build
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app

# Копируем pom.xml и исходники
COPY pom.xml .
COPY src ./src

# Скачиваем зависимости
RUN mvn dependency:go-offline

# Сборка приложения
RUN mvn clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Создаем пользователя для безопасности
RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

# Копируем JAR из стадии сборки
COPY --from=builder /app/target/*.jar app.jar

# Открываем порт
EXPOSE 8080

# Запуск приложения
ENTRYPOINT ["java", "-jar", "app.jar"]