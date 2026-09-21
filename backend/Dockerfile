# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY core ./core
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f core/pom.xml install -DskipTests -Dcheckstyle.skip=true
COPY backend/pom.xml ./backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f backend/pom.xml dependency:go-offline
COPY backend/src ./backend/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -f backend/pom.xml package -DskipTests && cp backend/target/funbuild-*.jar app.jar

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/app.jar ./app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
