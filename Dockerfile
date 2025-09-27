# Stage 1: build
FROM maven:3.9.0-eclipse-temurin-17 AS build

WORKDIR /app
COPY pom.xml .
COPY src ./src

# собираем jar (Spring Boot plugin сам сделает runnable jar с Main-Class)
RUN mvn clean package -DskipTests

# Stage 2: runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# копируем итоговый jar (без -with-dependencies, spring-boot уже упакует)
COPY --from=build /app/target/yogabot-1.0-SNAPSHOT.jar app.jar

ENV BOT_USERNAME=${BOT_USERNAME}
ENV BOT_TOKEN=${BOT_TOKEN}
ENV ADMIN_ID=${ADMIN_ID}
ENV CHANNEL_ID=${CHANNEL_ID}
ENV DATABASE_URL=${DATABASE_URL}
ENV BOT_PATH=/

CMD ["java", "-jar", "app.jar"]

