# Stage 1: build
FROM maven:3.9.0-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package

# Stage 2: runtime
FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/yogabot-1.0-SNAPSHOT-jar-with-dependencies.jar ./yogabot.jar

# Переменные окружения
ENV BOT_USERNAME=${BOT_USERNAME}
ENV BOT_TOKEN=${BOT_TOKEN}
ENV ADMIN_ID=${ADMIN_ID}
ENV CHANNEL_ID=${CHANNEL_ID}
ENV DATABASE_URL=${DATABASE_URL}

# Запуск с правильным именем класса
CMD ["java", "-cp", "yogabot.jar", "org.example.Main"]

