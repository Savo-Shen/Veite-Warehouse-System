FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /build
COPY backend/pom.xml backend/pom.xml
COPY backend/ruoyi-common/pom.xml backend/ruoyi-common/pom.xml
COPY backend/ruoyi-modules/pom.xml backend/ruoyi-modules/pom.xml
COPY backend/ruoyi-admin-wms/pom.xml backend/ruoyi-admin-wms/pom.xml
COPY backend/ruoyi-common backend/ruoyi-common
COPY backend/ruoyi-modules backend/ruoyi-modules
COPY backend/ruoyi-admin-wms backend/ruoyi-admin-wms

RUN mvn -f backend/pom.xml -Pprod -DskipTests clean package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends default-mysql-client \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --home-dir /app wms
COPY --from=build /build/backend/ruoyi-admin-wms/target/ruoyi-admin-wms.jar /app/app.jar
RUN mkdir -p /app/logs /app/backups && chown -R wms:wms /app

USER wms
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
