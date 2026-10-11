# Сборка: docker build -t pulsar-tax-api .   Запуск с базой — docker compose up (docker-compose.yml).
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --create-home pulsar
USER pulsar
WORKDIR /home/pulsar
COPY --from=build /src/target/pulsar-tax.jar app.jar
ENV PULSAR_HOST=0.0.0.0 PULSAR_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
