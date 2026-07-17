# Local GenAI Lab - Setup & Commands Guide

## Prerequisites

### Required Software

| Tool | Version | Purpose |
|------|---------|---------|
| **Java** | 21 (exact, not 22+) | Spring Boot backend |
| **Maven** | 3.9+ | Build backend, dependency management |
| **Node.js** | 20.19+ | Frontend (React/Vite) and MCP server |
| **npm** | (bundled with Node) | Package manager for frontend/MCP |
| **Ollama** | Latest | Default local LLM provider |

### Optional Software

| Tool | Purpose |
|------|---------|
| **Docker & Docker Compose** | Containerized deployment, optional Qdrant |
| **AWS CLI + credentials** | AWS tool flows (S3 reports, region audits) |
| **jq** | JSON processing in shell scripts |
| **Trivy** | Docker image vulnerability scanning |

---

## Installation

### 1. Install Java 21

```bash
# macOS (Homebrew)
brew install openjdk@21

# Ubuntu/Debian
sudo apt install openjdk-21-jdk

# Windows (Scoop)
scoop install openjdk21

# Verify
java -version
```

### 2. Install Maven 3.9+

```bash
# macOS (Homebrew)
brew install maven

# Ubuntu/Debian
sudo apt install maven

# Windows (Scoop)
scoop install maven

# Verify
mvn -version
```

### 3. Install Node.js 20.19+

```bash
# macOS (Homebrew)
brew install node@20

# Ubuntu/Debian (via nvm)
nvm install 20.19
nvm use 20.19

# Windows (Scoop)
scoop install nodejs20

# Verify
node -v   # should be >= 20.19.0
npm -v
```

### 4. Install Ollama

```bash
# macOS / Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows: download from https://ollama.com/download

# Pull the default model
ollama pull llama3:8b

# Verify Ollama is running
curl http://localhost:11434
```

### 5. Install Docker (optional)

```bash
# macOS: install Docker Desktop from https://docker.com/products/docker-desktop
# Ubuntu/Debian
sudo apt install docker.io docker-compose-plugin

# Verify
docker --version
docker compose version
```

---

## Quick Start (Fastest Path)

```bash
# 1. Copy environment config
cp .env.example .env

# 2. Start everything (backend + frontend)
./scripts/start.sh
```

Then open:
- **Frontend:** http://localhost:5173
- **Backend health:** http://localhost:8080/actuator/health
- **Swagger UI:** http://localhost:8080/swagger-ui/index.html

---

## All Commands

### Make Commands (Primary Interface)

```bash
make help                  # Show all available targets
```

#### Lifecycle

```bash
make start                 # Start backend and frontend in background
make stop                  # Stop background processes
make restart               # Restart backend and frontend
make status                # Show process, URL, and log status
```

#### Build

```bash
make build                 # Build backend, frontend, and MCP artifacts
make build-frontend        # Build only the frontend
make build-mcp             # Build only the MCP server
```

#### Testing

```bash
make test                  # Run ops, backend, and frontend tests
make verify                # Run broader project verification (test + build + mcp)
make test-ops              # Run operational shell helper tests
make test-backend          # Run backend tests (mvn test)
make test-frontend         # Run frontend tests (vitest)
make test-mcp              # Run MCP tests
make test-scripts          # Run MCP tool script lint/tests
make test-rag-qdrant-smoke # Run optional live RAG + Qdrant smoke test
```

#### Validation & Release

```bash
make check-app             # Run local stack smoke check
make release-check         # Run local pre-release validation gate
make release-check-docker  # Run release check with Docker verification/scan
make dependency-freshness  # Report Maven, npm, and Docker dependency freshness
make prepare-release VERSION=vX.Y.Z  # Run guided release preparation
```

#### Docker

```bash
make docker-sanity-check   # Check Docker daemon and Compose availability
make docker-start          # Start backend, frontend, and Qdrant with Docker
make docker-stop           # Stop the Docker Compose stack
make docker-restart        # Restart the Docker Compose stack
make docker-status         # Show Docker Compose service status
make docker-logs           # Follow Docker Compose service logs
make docker-tunnel-info    # Print SSH tunnel commands for remote Docker access
make docker-check          # Smoke-check the running Docker Compose stack
make docker-verify         # Restart, inspect, and smoke-check Docker mode
make docker-scan           # Scan Docker images for vulnerabilities (Trivy)
make docker-full-check     # Run Docker verification and image scan
```

#### Utility

```bash
make clean-ds-store        # Remove macOS .DS_Store files from the repo tree
```

---

### Direct Script Commands

```bash
# Lifecycle
./scripts/start.sh          # Start the app (backend + frontend)
./scripts/stop.sh           # Stop the app
./scripts/restart.sh        # Restart the app
./scripts/status.sh         # Show status
./scripts/build.sh          # Build all artifacts
./scripts/build.sh --skip-tests       # Build without running backend tests
./scripts/build.sh --clean-frontend   # Clean frontend dist before building

# Docker
./scripts/docker-start.sh   # Start Docker Compose stack
./scripts/docker-stop.sh    # Stop Docker Compose stack
./scripts/docker-restart.sh # Restart Docker Compose stack
./scripts/docker-status.sh  # Docker Compose status
./scripts/docker-logs.sh    # Follow Docker logs
./scripts/docker-check.sh   # Smoke-check Docker stack
./scripts/docker-verify.sh  # Full Docker verification
./scripts/docker-scan.sh    # Vulnerability scan with Trivy
./scripts/docker-full-check.sh  # Verify + scan combined
./scripts/docker-sanity-check.sh  # Check Docker daemon availability
./scripts/docker-tunnel-info.sh   # SSH tunnel commands for remote access

# Release
./scripts/release-check.sh          # Pre-release validation
./scripts/prepare-release.sh vX.Y.Z # Guided release preparation
```

---

### Component-Level Commands

#### Backend (Spring Boot)

```bash
cd backend

# Run
mvn spring-boot:run

# Run with specific provider
APP_MODEL_PROVIDER=ollama mvn spring-boot:run
APP_MODEL_PROVIDER=bedrock mvn spring-boot:run
APP_MODEL_PROVIDER=huggingface mvn spring-boot:run

# Build
mvn clean package          # Build with tests
mvn clean package -DskipTests  # Build without tests

# Test
mvn test

# API endpoints (when running)
# Health:  http://localhost:8080/actuator/health
# Swagger: http://localhost:8080/swagger-ui/index.html
# OpenAPI: http://localhost:8080/v3/api-docs
```

#### Frontend (React/Vite)

```bash
cd frontend

# Install dependencies
npm install

# Run dev server
npm run dev               # Starts on port 5173

# Build for production
npm run build

# Run tests
npm test -- --run

# Preview production build
npm run preview
```

#### MCP Server

```bash
cd mcp

# Install dependencies
npm install

# Build
npm run build

# Run tests
npm test

# Run in dev mode
npm run dev

# Start built server
npm start
```

---

## Docker Mode

### Standard Docker Compose

```bash
# Start all services (backend + frontend + Qdrant)
docker compose up -d

# View logs
docker compose logs -f

# Stop
docker compose down

# Check status
docker compose ps
```

### Docker with AWS Tools

```bash
# 1. Configure AWS tool env
cp .env.docker-aws-tools.example .env.docker-aws-tools
# Edit .env.docker-aws-tools with your AWS config path

# 2. Start with AWS tools overlay
docker compose -f docker-compose.yml -f docker-compose.aws-tools.yml up -d
```

### Docker Services

| Service | Port | Purpose |
|---------|------|---------|
| backend | 8080 | Spring Boot API + MCP server |
| frontend | 3000 (mapped to 80) | React UI via nginx |
| qdrant | 6333 | Optional vector store for RAG |

---

## Environment Configuration

### .env File

```bash
cp .env.example .env
```

Key settings:

```bash
# Provider selection: ollama | bedrock | huggingface
APP_MODEL_PROVIDER=ollama

# Ollama settings
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_DEFAULT_MODEL=llama3:8b

# MCP (tool execution)
MCP_ENABLED=true

# RAG
RAG_ENABLED=true
RAG_RETRIEVAL_MODE=lexical          # lexical | vector
RAG_VECTOR_STORE=in-memory          # in-memory | qdrant
RAG_QDRANT_URL=http://localhost:6333
RAG_EMBEDDING_PROVIDER=ollama
RAG_EMBEDDING_MODEL=nomic-embed-text
```

### Port Overrides

```bash
SERVER_PORT=8080           # Backend port
FRONTEND_PORT=5173         # Frontend dev server port
```

---

## RAG Setup (Optional)

### Vector Retrieval with In-Memory Store

```bash
# No extra setup needed; just change config
# Set in .env:
RAG_RETRIEVAL_MODE=vector
RAG_VECTOR_STORE=in-memory
```

### Vector Retrieval with Qdrant

```bash
# 1. Start Qdrant (standalone)
docker run -d -p 6333:6333 qdrant/qdrant:v1.18.2

# Or it starts automatically with docker-compose.yml

# 2. Configure .env
RAG_RETRIEVAL_MODE=vector
RAG_VECTOR_STORE=qdrant
RAG_QDRANT_URL=http://localhost:6333

# 3. Pull embedding model into Ollama
ollama pull nomic-embed-text
```

---

## AWS Tool Setup (Optional)

```bash
# 1. Install AWS CLI
# macOS: brew install awscli
# Ubuntu: sudo apt install awscli
# Windows: scoop install aws

# 2. Configure AWS credentials
aws configure

# 3. Verify
aws sts get-caller-identity
```

---

## Common Workflows

### First-Time Setup

```bash
cp .env.example .env
ollama pull llama3:8b
./scripts/start.sh
```

### Development Cycle

```bash
make stop        # Stop any running instance
make start       # Start fresh
make status      # Check everything is running
```

### Full Verification Before Release

```bash
make test              # Run all tests
make build             # Build all components
make verify            # Test + build + MCP checks
make release-check     # Full pre-release validation
```

### Troubleshooting

```bash
# Check if ports are in use
lsof -i :8080
lsof -i :5173

# Check backend logs
cat .run/backend.log

# Check frontend logs
cat .run/frontend.log

# Verify Ollama is running
curl http://localhost:11434

# Verify backend health
curl http://localhost:8080/actuator/health

# Full stack check
make check-app
```

---

## Useful Endpoints

| Endpoint | Description |
|----------|-------------|
| `http://localhost:5173` | Frontend UI |
| `http://localhost:8080/actuator/health` | Backend health check |
| `http://localhost:8080/actuator/info` | Backend runtime info |
| `http://localhost:8080/swagger-ui/index.html` | API documentation |
| `http://localhost:8080/v3/api-docs` | OpenAPI spec |
| `http://localhost:6333` | Qdrant dashboard (if running) |
