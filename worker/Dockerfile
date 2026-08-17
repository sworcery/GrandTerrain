# Pinned JRE image instead of a "latest" Adoptium API download: the API URL
# resolves to whatever build is current, so two builds a week apart could
# ship different Java runtimes (or fail outright when the endpoint is down).
FROM eclipse-temurin:25-jre AS java

FROM python:3.13-slim

COPY --from=java /opt/java/openjdk /opt/java
ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN mkdir -p /app/data/jobs /app/data/worlds

EXPOSE 8080

# Bootstrap downloads the runtime jars (Fabric server, Fabric API, Chunky,
# GrandTerrain) into DATA_DIR on first boot, then hands off to the API.
CMD ["sh", "-c", "python -m src.bootstrap && exec uvicorn src.main:app --host 0.0.0.0 --port 8080"]
