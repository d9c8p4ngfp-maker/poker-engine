# Dockerfile for Render — Spring Boot on Java 21
FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY server/ server/

WORKDIR /app/server
# Fix Windows CRLF line endings before running mvnw
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw package -DskipTests

EXPOSE 8080
CMD ["java", "-jar", "target/poker-server-0.1.0-SNAPSHOT.jar"]
