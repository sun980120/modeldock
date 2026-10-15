FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew bootJar --no-daemon
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 dock && adduser -D -u 10001 -G dock dock
WORKDIR /app
COPY --from=build --chown=dock:dock /src/build/libs/modeldock-api-0.1.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-XX:+ExitOnOutOfMemoryError","-jar","/app/app.jar"]
