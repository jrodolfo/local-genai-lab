FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src ./src
RUN mvn -q -DskipTests clean package

FROM node:20-bookworm-slim AS mcp-build
WORKDIR /app/mcp
COPY mcp/package.json mcp/package-lock.json ./
RUN npm ci
COPY mcp ./
RUN npm run build && npm prune --omit=dev

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends awscli jq \
    && rm -rf /var/lib/apt/lists/*
COPY --from=mcp-build /usr/local/bin/node /usr/local/bin/node
COPY --from=backend-build /app/target/local-genai-lab-backend-0.0.1-SNAPSHOT.jar app.jar
COPY --from=mcp-build /app/mcp/package.json /app/mcp/package-lock.json ./mcp/
COPY --from=mcp-build /app/mcp/dist ./mcp/dist
COPY --from=mcp-build /app/mcp/node_modules ./mcp/node_modules
COPY docs ./docs
COPY agents ./agents
RUN rm -f /usr/bin/pebble \
    && mkdir -p /app/data/sessions /app/agents/reports/audit /app/agents/reports/s3-cloudwatch
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
