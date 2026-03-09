# Build
FROM gradle:8.7-jdk17 AS build
WORKDIR /app

COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon || true

COPY src ./src
RUN gradle build -x test --no-daemon

# Runtime
FROM amazoncorretto:17-alpine
WORKDIR /app

# User
RUN addgroup -g 1001 appgroup && \
    adduser -u 1001 -G appgroup -D appuser

COPY --from=build /app/build/libs/assignment-*.jar app.jar

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

ENTRYPOINT ["java", \
"-XX:+UseContainerSupport", \
"-XX:MaxRAMPercentage=75.0", \
"-jar", "app.jar"]