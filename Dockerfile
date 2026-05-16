FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./

RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src

RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:25-jre

WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=build /workspace/target/chat-socket-0.0.1-SNAPSHOT.jar app.jar

USER 10001:10001

EXPOSE 10000

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT:-10000} -jar /app/app.jar"]
