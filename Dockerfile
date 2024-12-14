FROM eclipse-temurin:21-jre-alpine AS jre-build
LABEL authors="skakumanu"

WORKDIR application

ARG PROJECT_NAME=video-service
ARG PROJECT_VERSION=1.0

ADD build/libs/${PROJECT_NAME}-${PROJECT_VERSION}.jar ./
RUN java -Djarmode=layertools -jar ${PROJECT_NAME}-${PROJECT_VERSION}.jar extract


FROM eclipse-temurin:21-jre-alpine

WORKDIR application
CMD apt-get update -y

COPY --from=jre-build application/dependencies/ ./
COPY --from=jre-build application/spring-boot-loader/ ./
COPY --from=jre-build application/snapshot-dependencies/ ./
COPY --from=jre-build application/application/ ./
ENTRYPOINT ["java", "-XX:+UseParallelGC", "-XX:GCTimeRatio=4", "-XX:AdaptiveSizePolicyWeight=90", "-XX:MinHeapFreeRatio=20", "-XX:MaxHeapFreeRatio=40", "-XX:+HeapDumpOnOutOfMemoryError", "-Xms512m", "-Xmx512m", "-Djava.security.egd=file:/dev/./urandom", "org.springframework.boot.loader.launch.JarLauncher"]