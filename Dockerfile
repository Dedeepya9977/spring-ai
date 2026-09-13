FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /source
COPY pom.xml .
COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 app && useradd --uid 10001 --gid 10001 --no-create-home app
WORKDIR /app
COPY --from=build --chown=10001:10001 /source/target/agent-foundations-1.0.0.jar /app/app.jar
USER 10001:10001
ENV MCP_SERVER_JAR=/app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70.0","-jar","/app/app.jar"]
