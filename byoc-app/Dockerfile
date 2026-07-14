FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
# iproute2 (tc) is baked in at build time, when full image-build privileges are
# available, so fault injection at runtime only needs the NET_ADMIN capability
# to *use* tc -- not the broader capability set apt/dpkg needs to install
# packages inside an already-hardened (all-caps-dropped) running container.
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends iproute2 \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/byoc-app-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
