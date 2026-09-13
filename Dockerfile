FROM eclipse-temurin:21-jdk

WORKDIR /workspace
COPY . .

RUN ./gradlew --no-daemon clean build
