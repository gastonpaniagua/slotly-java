# Imagen de producción de Slotly (Etapa 9): una sola app con la API y el front compilado adentro (D5: mismo origen,
# sin CORS). Se arma en tres etapas y la imagen final lleva solo el .jar y un JRE.
#   docker build -t slotly .

# ---- 1. Front: compila React con Vite (deja frontend/dist) ----
FROM node:24-alpine AS front
WORKDIR /front
# Primero solo las dependencias: si no cambian, Docker reusa esta capa y no las vuelve a bajar.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- 2. Back: arma el .jar con el front adentro (Spring lo sirve desde static/) ----
FROM eclipse-temurin:21-jdk AS back
WORKDIR /back
COPY backend/mvnw backend/pom.xml ./
COPY backend/.mvn .mvn
# Por si el checkout de Windows dejó finales de línea CRLF: el script no corre en Linux con ellos.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=front /front/dist src/main/resources/static
# Los tests corren en el CI (con Testcontainers); acá solo se empaqueta.
RUN ./mvnw -B -q package -DskipTests && cp target/slotly-*.jar app.jar

# ---- 3. Imagen final: JRE y el .jar, sin compiladores ni código fuente ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# Sin permisos de root: si alguien se mete en la app, no controla el contenedor.
RUN addgroup -S slotly && adduser -S slotly -G slotly && chown slotly:slotly /app
USER slotly
COPY --from=back --chown=slotly:slotly /back/app.jar app.jar

# Memoria: el límite lo pone la plataforma (512 MB en el plan gratis de Render); acá se le dice a la JVM que se
# acomode a él para que no la mate el "Out of memory":
# - MaxRAMPercentage=70: el heap usa hasta el 70 % del límite; el resto queda para lo que la JVM usa fuera del heap
#   (clases, hilos, código compilado).
# - UseSerialGC: el recolector más liviano, alcanza con 1 CPU y poca memoria.
# - Xss512k y ReservedCodeCacheSize=64m: menos memoria por hilo y para el código compilado.
# - TieredStopAtLevel=1: compila rápido y simple; con el poco CPU del plan gratis arranca bastante antes.
# - user.timezone=UTC: la JVM siempre en UTC (BUG-001); la hora de cada local la pone la app.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:ReservedCodeCacheSize=64m -XX:TieredStopAtLevel=1 -Duser.timezone=UTC"

# Arranque más rápido con CDS: un arranque de prueba al armar la imagen guarda las clases ya cargadas en app.jsa, y
# cada arranque real las lee de ahí. El arranque de prueba no tiene base: se apagan Flyway y la lectura del esquema,
# y se corta apenas el contexto queda listo (spring.context.exit=onRefresh). Sin los avisos de CDS por las clases de
# librerías que Slotly no usa (SAML, LDAP…): no se guardan y no pasa nada, pero llenaban el log del build.
RUN java -Djarmode=tools -jar app.jar extract --destination extraido && rm app.jar \
    && java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh -Xlog:cds=off -Xlog:cds+dynamic=off \
        -Dspring.flyway.enabled=false -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect \
        -Dslotly.trabajos.activos=false -jar extraido/app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-jar", "extraido/app.jar"]
