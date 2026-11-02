# syntax=docker/dockerfile:1.7
# Uma imagem por serviço a partir do monorepo:
#   docker build --build-arg SERVICE=bank-service -t hub/bank-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY libs libs
COPY services services
ARG SERVICE
# O cache do ~/.m2 é compartilhado entre os builds dos serviços.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/${SERVICE} -am package -DskipTests
RUN java -Djarmode=tools -jar services/${SERVICE}/target/${SERVICE}.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system hub && useradd --system --gid hub --uid 10001 hub
WORKDIR /app
# Camadas da menos para a mais volátil: só a camada "application" muda a cada commit.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
USER hub
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
