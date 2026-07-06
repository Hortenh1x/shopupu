# --- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# cache dependencies separately from sources
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Runtime stage ------------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system shopupu && useradd --system --gid shopupu shopupu
USER shopupu:shopupu

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
