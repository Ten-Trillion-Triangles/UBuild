# Development and verification image, not a production runtime image.
FROM eclipse-temurin:21-jdk@sha256:1f79c73404fb0cccf9a3459eda22892f368d994b1028d6fb1ae871c1f49749a6

ARG UBUILD_UID=1000
ARG UBUILD_GID=1000

ENV HOME=/home/ubuild \
    GRADLE_USER_HOME=/opt/gradle

WORKDIR /workspace
COPY . .

RUN ./gradlew --no-daemon --dependency-verification=strict clean test :server:buildFatJar \
    && test -s /workspace/server/build/libs/server-all.jar \
    && mkdir -p /home/ubuild/.ubuild \
    && chown -R ${UBUILD_UID}:${UBUILD_GID} /home/ubuild /opt/gradle /workspace \
    && chmod -R u+rwX /home/ubuild /opt/gradle /workspace

USER ${UBUILD_UID}:${UBUILD_GID}

ENTRYPOINT ["java", "-Duser.home=/home/ubuild", "-jar", "/workspace/server/build/libs/server-all.jar"]
