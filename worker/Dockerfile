FROM python:3.13-slim

ARG JAVA_VERSION=25

RUN apt-get update && \
    apt-get install -y --no-install-recommends wget ca-certificates && \
    mkdir -p /opt/java && \
    wget -qO /tmp/jdk.tar.gz \
      "https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/linux/x64/jre/hotspot/normal/eclipse?project=jdk" && \
    tar -xzf /tmp/jdk.tar.gz -C /opt/java --strip-components=1 && \
    rm /tmp/jdk.tar.gz && \
    apt-get purge -y wget && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

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
