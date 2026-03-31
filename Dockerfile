FROM amazoncorretto:24-alpine

WORKDIR /app

# Copy exact jar (avoid wildcard issues)
COPY target/*.jar app.jar

EXPOSE 8080

# JVM tuned for containers
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]