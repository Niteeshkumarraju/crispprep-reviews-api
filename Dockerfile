# 1) Build the jar with Maven
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q -B -DskipTests package

# 2) Run it on a small Java runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/reviews-0.0.1-SNAPSHOT.jar app.jar
# free servers have ~512 MB of memory
# TieredStopAtLevel=1: much faster start on the tiny free CPU (wake-up after sleep); plenty fast for this traffic
ENV JAVA_OPTS="-Xmx320m -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1 -XX:CICompilerCount=1"
EXPOSE 8080
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
