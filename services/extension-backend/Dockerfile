# syntax=docker/dockerfile:1.9

ARG JDK_IMAGE=eclipse-temurin:25-jdk@sha256:97014c4b396021f9ddb7d592a7dbedb0c4e4215c29e03dc01c393558aefb71c2
ARG JRE_IMAGE=eclipse-temurin:25-jre-alpine@sha256:2ca9adf44f5c29d28ecd26cf92d75cc0c66b7f32bfd839a4439e363a8b428af8

# hadolint ignore=DL3006
FROM ${JDK_IMAGE} AS builder
WORKDIR /app

COPY services/extension-backend/gradlew ./
COPY services/extension-backend/gradle ./gradle
COPY services/extension-backend/build.gradle.kts services/extension-backend/settings.gradle.kts ./
RUN chmod +x ./gradlew

# Resolve dependencies before copying source, so an unrelated source change
# doesn't invalidate the (much slower) dependency-download layer.
RUN --mount=type=cache,id=gradle,target=/root/.gradle \
    ./gradlew --no-daemon dependencies

COPY services/extension-backend/src ./src

RUN --mount=type=cache,id=gradle,target=/root/.gradle \
    ./gradlew bootJar -x test --no-daemon

# hadolint ignore=DL3006
FROM ${JRE_IMAGE} AS runtime

WORKDIR /app

LABEL org.opencontainers.image.title="ApollonCASE Extension Backend" \
      org.opencontainers.image.description="Spring Boot gateway for ApollonCASE: identity, projects, permissions, and the diagrams-backend proxy." \
      org.opencontainers.image.licenses="MIT"

RUN addgroup -S app && adduser -S -G app app

COPY --from=builder --chown=app:app /app/build/libs/*.jar ./app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/actuator/health || exit 1

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
