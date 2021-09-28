FROM gcr.io/distroless/java-debian10:11
ADD target/app.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]