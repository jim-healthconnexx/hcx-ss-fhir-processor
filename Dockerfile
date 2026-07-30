# HDC-175: Single-stage runtime image.
# Build the JAR first with: ./mvnw -DskipTests package
# Then build the image with: docker build -t hcx-ss-fhir-processor:latest .
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
