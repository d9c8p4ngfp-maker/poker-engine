# ── Stage 1: Build frontend ──
FROM node:20-alpine AS frontend-build
WORKDIR /app/web
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

# ── Stage 2: Build backend + bundle frontend ──
FROM eclipse-temurin:21-jdk AS backend-build
WORKDIR /app/server
COPY server/pom.xml server/mvnw server/mvnw.cmd server/.mvn/ ./
# Fix CRLF for mvnw on Linux
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY server/src/ src/
COPY --from=frontend-build /app/web/dist/ src/main/resources/static/
RUN ./mvnw package -DskipTests -B

# ── Stage 3: Run ──
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend-build /app/server/target/poker-server-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
