# Етап збирання проєкту
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Етап створення фінального образу
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Відкриваємо порт сервісу
EXPOSE 8080

# Запуск сервісу
ENTRYPOINT ["java", "-jar", "app.jar"]