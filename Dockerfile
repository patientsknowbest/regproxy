FROM maven:3.8.2-jdk-11 AS builder
COPY pom.xml pom.xml
RUN mvn dependency:go-offline
COPY . . 
RUN mvn package

FROM gcr.io/distroless/java-debian10:11
COPY --from=builder target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]