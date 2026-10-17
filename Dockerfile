# Build the jar with the wrapper, so the image needs no Maven of its own.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Copy the wrapper and the POM first: these layers cache, so a source-only
# change does not re-download every dependency.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
# Skip the tests here: they need a real PostgreSQL, which CI provides and the
# image build does not.
RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/clinic-*.jar app.jar

# Render's free instance has 512 MB. Let the JVM size its heap from the
# container limit rather than the host's memory.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
