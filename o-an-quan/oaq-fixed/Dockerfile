FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV OANQUAN_DATA_DIR=/app/data
RUN mkdir -p /app/data
COPY --from=build /app/target/o-an-quan-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 10000
ENTRYPOINT ["sh", "-c", "exec java -jar app.jar --server.address=0.0.0.0 --server.port=${PORT:-10000}"]
