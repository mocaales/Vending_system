# syntax=docker/dockerfile:1

# ---- build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Dependency layer: cached until pom.xml or the wrapper changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI, not in the image build.
RUN ./mvnw -B -q -DskipTests package \
    && mkdir -p /out \
    && cp target/*.jar /out/app.jar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /app
COPY --from=build --chown=app:app /out/app.jar app.jar

USER app
EXPOSE 8000

# Sized for a 512 MB container limit.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
