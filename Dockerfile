# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy only what dependency resolution needs first, so the dependency download
# lands in its own layer and is reused when just sources change.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN ./gradlew --no-daemon dependencies --configuration runtimeClasspath

COPY src ./src
RUN ./gradlew --no-daemon stage

# ---- runtime stage ----
# Ubuntu-based (glibc) rather than Alpine: Netty's native epoll transport is
# glibc-linked and would silently fall back to slower NIO on musl.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home --shell /usr/sbin/nologin app
COPY --from=build /app/build/libs/SteplerStalzoneServer-all.jar app.jar
USER app

# The listen port comes from $PORT at runtime; this is the local default.
EXPOSE 8080

# Exec form so the JVM is PID 1 and receives SIGTERM directly, letting Ktor
# shut down gracefully instead of being killed.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
