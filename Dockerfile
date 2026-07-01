# syntax=docker/dockerfile:1

# --- build stage ---
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy wrapper + build files first so the Gradle distribution layer can be cached.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --version --no-daemon

# Now copy sources and build the boot jar.
COPY src ./src
RUN ./gradlew clean bootJar --no-daemon

# --- runtime stage ---
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN useradd -r -u 1001 appuser
COPY --from=build /workspace/build/libs/app.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
