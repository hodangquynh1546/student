FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
RUN groupadd --system edutrack && useradd --system --gid edutrack --home-dir /app edutrack
WORKDIR /app
COPY --from=build /build/target/*.jar /app/edutrack.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod 755 /app/docker-entrypoint.sh && mkdir -p /app/uploads && chown -R edutrack:edutrack /app
USER edutrack
EXPOSE 8080
ENTRYPOINT ["/app/docker-entrypoint.sh"]
