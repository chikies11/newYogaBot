# Stage 1: сборка
FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /app

# Копируем pom.xml и исходники
COPY pom.xml .
COPY src ./src

# Сборка jar
RUN mvn clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Копируем собранный fat-jar (Spring Boot плагин создает его сам)
COPY --from=build /app/target/yogabot-1.0-SNAPSHOT.jar app.jar

# Прокидываем переменные окружения
ENV BOT_USERNAME=${BOT_USERNAME}
ENV BOT_TOKEN=${BOT_TOKEN}
ENV ADMIN_ID=${ADMIN_ID}
ENV CHANNEL_ID=${CHANNEL_ID}
ENV DATABASE_URL=${DATABASE_URL}
ENV BOT_PATH=/

# Команда запуска
ENTRYPOINT ["java","-jar","app.jar"]
