# Етап збирання: виконується на платформі збирання (швидко навіть для multi-arch образу)
FROM --platform=$BUILDPLATFORM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package

# Фінальний образ: лише JRE і jar контролера
FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.source="https://github.com/Arrow06/CI.CD"
WORKDIR /app
COPY --from=build /app/target/backup-controller.jar backup-controller.jar

# Контролер працює постійно (Deployment)
ENTRYPOINT ["java", "-jar", "backup-controller.jar"]
