# builder
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B --no-transfer-progress -DskipTests package

# runtime
FROM eclipse-temurin:21-jre
ARG JAVA_OPTS=""
WORKDIR /app
COPY --from=builder /workspace/target/*.jar /app/app.jar

# non-root user
RUN addgroup --system app && adduser --system --ingroup app app || true && chown app:app /app/app.jar || true
USER app
EXPOSE 8080
ENV JAVA_OPTS="${JAVA_OPTS}"
# ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]

#  -Dserver.port=$PORT
ENTRYPOINT ["sh","-c","exec java -XX:MaxRAMPercentage=75 $JAVA_OPTS -Dserver.port=$PORT -jar /app/app.jar"]