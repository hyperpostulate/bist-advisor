FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY --from=build /app/target/bist-advisor-0.1.0.jar app.jar
RUN mkdir -p /app/cache
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
