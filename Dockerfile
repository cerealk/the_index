# Build stage: compile the server and lay out a runnable distribution.
FROM eclipse-temurin:24-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle.kts gradle.properties ./
COPY gradle gradle
COPY buildSrc buildSrc
COPY app app
COPY utils utils
RUN ./gradlew --no-daemon :app:installDist

# Runtime stage: just the JRE and the distribution.
FROM eclipse-temurin:24-jre
WORKDIR /opt/the_index
COPY --from=build /src/app/build/install/app ./
VOLUME ["/data/storage", "/data/inbox", "/data/cache"]
EXPOSE 8080
ENTRYPOINT ["/opt/the_index/bin/app"]
