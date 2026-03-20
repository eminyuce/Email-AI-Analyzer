FROM amazoncorretto:24
WORKDIR /app
COPY target/email-analyser-ai-tool.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
