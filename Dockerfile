# Stage 1: build
FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /app

# Копируем pom.xml и исходники
COPY pom.xml .
COPY src ./src

# Сборка fat-jar (Spring Boot repackage)
RUN mvn clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Копируем любой JAR из target и называем его app.jar
COPY --from=build /app/target/*.jar app.jar

# Пробрасываем переменные окружения
ENV BOT_USERNAME=${BOT_USERNAME}
ENV BOT_TOKEN=${BOT_TOKEN}
ENV ADMIN_ID=${ADMIN_ID}
ENV CHANNEL_ID=${CHANNEL_ID}
ENV BOT_PATH=/

# Запуск
ENTRYPOINT ["java", "-jar", "app.jar"]
