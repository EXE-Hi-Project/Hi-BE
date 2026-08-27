FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 hi
COPY --from=build /workspace/target/hi-api-1.0.0.jar /app/hi-api.jar
USER hi
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/hi-api.jar"]
