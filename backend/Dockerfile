# Stage 1: Build
FROM maven:3.8.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies to cache them in docker layers
RUN mvn dependency:go-offline -B
COPY src ./src
# Compile and package, skipping unit test compile to bypass legacy tests
RUN mvn package -Dmaven.test.skip=true -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
