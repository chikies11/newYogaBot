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

# Копируем любой jar из target (Spring Boot кладет только один fat-jar)
COPY --from=build /app/target/*.jar app.jar

# Запуск
ENTRYPOINT ["java", "-jar", "app.jar"]
