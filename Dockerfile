# Stage 1: build
FROM maven:3.9.0-eclipse-temurin-17 AS build

WORKDIR /app

# Копируем pom и исходники
COPY pom.xml .
COPY src ./src

# Сборка jar с зависимостями
RUN mvn clean package

# Stage 2: runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

# Копируем собранный jar
COPY --from=build /app/target/yogabot-1.0-SNAPSHOT-jar-with-dependencies.jar ./yogabot.jar

# Используем переменные окружения, которые будут задаваться в Render
ENV BOT_USERNAME=${BOT_USERNAME}
ENV BOT_TOKEN=${BOT_TOKEN}
ENV ADMIN_ID=${ADMIN_ID}
ENV CHANNEL_ID=${CHANNEL_ID}
ENV DATABASE_URL=${DATABASE_URL}

# Команда запуска бота
CMD ["java", "-jar", "yogabot.jar"]
