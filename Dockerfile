FROM amazoncorretto:24-alpine

WORKDIR /app

# Copy exact jar (avoid wildcard issues)
COPY target/app.jar app.jar

# Run as non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

EXPOSE 8080

# JVM tuned for containers
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]