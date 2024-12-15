FROM maven:3.8.5-openjdk-17 AS build

WORKDIR /app

COPY pom.xml .
COPY /src /app/src

RUN mvn clean package -DskipTests

FROM openjdk:17-jdk-slim

WORKDIR /app

COPY --from=build /app/target/telegramBot-0.0.1-SNAPSHOT.jar /app/telegramBot-0.0.1-SNAPSHOT.jar

CMD ["java", "-jar", "/app/telegramBot-0.0.1-SNAPSHOT.jar"]