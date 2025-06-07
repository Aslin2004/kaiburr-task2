# Base image
FROM openjdk:17-jdk-slim

# Set working directory
WORKDIR /app

# Copy project JAR
COPY target/taskmanager-0.0.1-SNAPSHOT.jar app.jar

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar"]
