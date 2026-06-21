# Dockerfile for Render — Spring Boot on Java 21
FROM eclipse-temurin:21-jdk

WORKDIR /app
COPY server/pom.xml server/mvnw server/mvnw.cmd server/.mvn/ server/
COPY server/src server/src/

WORKDIR /app/server
RUN chmod +x mvnw && ./mvnw package -DskipTests

EXPOSE 8080
CMD ["java", "-jar", "target/poker-server-0.1.0-SNAPSHOT.jar"]
