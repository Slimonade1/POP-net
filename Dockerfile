FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends iputils-ping && rm -rf /var/lib/apt/lists/*
COPY app.jar app.jar
COPY scripts ./scripts
ENTRYPOINT ["java", "-Xmx256m", "-jar", "app.jar"]