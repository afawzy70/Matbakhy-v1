# Runtime image for the Spring Boot API. Build the jar first: ./mvnw -B -ntp -DskipTests package
FROM eclipse-temurin:21.0.12.1_1-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
RUN addgroup -S matbakhy && adduser -S -G matbakhy matbakhy
WORKDIR /app
COPY app/target/matbakhy-app-0.1.0-SNAPSHOT.jar /app/matbakhy-api.jar
USER matbakhy
EXPOSE 8080
# The heap share is an environment setting, not a command-line flag: a flag on the java command line would beat any
# JAVA_TOOL_OPTIONS a deployment sets (the 4 GB staging profile lowers it to leave room beside the heap).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/matbakhy-api.jar"]
