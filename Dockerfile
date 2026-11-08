# syntax=docker/dockerfile:1.7
# Imagem de qualquer serviço a partir do monorepo:
#   docker build --build-arg SERVICE=bank-service -t hub/bank-service .
#
# A etapa "build" não depende de SERVICE: é idêntica para os 11 serviços, então o Docker a executa
# uma única vez (compila todos os módulos) e cada imagem só copia o próprio jar. Compilar cada serviço
# separadamente rodaria 11 Maven em paralelo e esgotaria a memória do Docker Desktop.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-Xmx1g"
COPY pom.xml ./
COPY libs libs
COPY services services
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre AS extract
ARG SERVICE
WORKDIR /extract
COPY --from=build /workspace/services/${SERVICE}/target/${SERVICE}.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system hub && useradd --system --gid hub --uid 10001 hub
WORKDIR /app
# Camadas da menos para a mais volátil: só a camada "application" muda a cada commit.
COPY --from=extract /extracted/dependencies/ ./
COPY --from=extract /extracted/spring-boot-loader/ ./
COPY --from=extract /extracted/snapshot-dependencies/ ./
COPY --from=extract /extracted/application/ ./
USER hub
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
