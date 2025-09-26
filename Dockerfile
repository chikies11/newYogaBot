# Используем официальное изображение Java 17 с Maven
FROM maven:3.9.3-eclipse-temurin-17 AS build

# Копируем проект
WORKDIR /app
COPY pom.xml .
COPY src ./src

# Сборка fat-jar
RUN mvn clean package

# Новый контейнер для запуска
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY --from=build /app/target/yogabot-1.0-SNAPSHOT-jar-with-dependencies.jar ./yogabot.jar

# Переменные окружения (Render сможет их передавать)
ENV BOT_USERNAME="katysyoga_bot"
ENV BOT_TOKEN="7970982996:AAFeH9IMDHqyTTmqhshuxdhRibxz7fVP_I0"
ENV ADMIN_ID="639619404"
ENV CHANNEL_ID="@yoga_yollayo11"
ENV DATABASE_URL="postgresql://yogabot_user:NZ8XT9dWuccinu31ke6qcy7KcnwY5cpC@dpg-d3bbrbu3jp1c73atqikg-a/yogabot_db"

# Запуск бота
CMD ["java", "-jar", "yogabot.jar"]
