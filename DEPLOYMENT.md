# Local GenAI Lab - Deployment Guide

## Deploy Backend to Render

### Prerequisites

- A [Render](https://render.com) account (free tier works)
- A [Hugging Face](https://huggingface.co) account and API token (free at https://huggingface.co/settings/tokens)
- Code pushed to a GitHub repository

### Step-by-Step

#### 1. Push code to GitHub

Make sure `render.yaml` is committed and pushed.

#### 2. Create a Render Blueprint

1. Log in to [Render Dashboard](https://dashboard.render.com)
2. Click **New +** → **Blueprint**
3. Select your GitHub repository
4. Render detects `render.yaml` and shows the service to create
5. Click **Apply**

#### 3. Set the Hugging Face API token

1. In the Render dashboard, go to your service → **Environment** tab
2. Find `HUGGINGFACE_API_TOKEN` and click the edit icon
3. Paste your Hugging Face API token (starts with `hf_`)
4. Save

#### 4. Deploy

Render auto-deploys on push. Wait for the build to complete (~3-5 min).

Your backend is live at:
```
https://<your-service-name>.onrender.com
```

### Verify

| Check | URL |
|-------|-----|
| Health | `https://<name>.onrender.com/actuator/health` |
| Swagger UI | `https://<name>.onrender.com/swagger-ui/index.html` |
| API Docs | `https://<name>.onrender.com/v3/api-docs` |

### Environment Variables

| Variable | Value | Notes |
|----------|-------|-------|
| `APP_MODEL_PROVIDER` | `huggingface` | Default provider |
| `HUGGINGFACE_API_TOKEN` | `hf_xxx` | **Required** — set in Render dashboard |
| `HUGGINGFACE_DEFAULT_MODEL` | `meta-llama/Llama-3.1-8B-Instruct` | Default HF model |
| `MCP_ENABLED` | `false` | MCP tools disabled on cloud |
| `RAG_ENABLED` | `true` | RAG enabled with lexical mode |
| `RAG_VECTOR_STORE` | `in-memory` | No external DB needed |
| `APP_CORS_ALLOWED_ORIGINS` | `*` | Allow frontend from any origin |

### Connecting a Frontend

Point your frontend's backend URL to:
```
https://<your-service-name>.onrender.com
```

---

## Local Development

### Quick Start

```bash
cp .env.example .env
./scripts/start.sh
```

### With Docker

```bash
docker compose up --build -d
```

| Service | Port | URL |
|---------|------|-----|
| Frontend | 3000 | http://localhost:3000 |
| Backend | 8080 | http://localhost:8080 |
| Qdrant | 6333 | http://localhost:6333 |

### Without Docker

```bash
# Backend
cd backend
mvn spring-boot:run

# Frontend (separate terminal)
cd frontend
npm install
npm run dev
```

| Endpoint | URL |
|----------|-----|
| Frontend | http://localhost:5173 |
| Backend health | http://localhost:8080/actuator/health |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |

### Port Configuration

The backend port defaults to `8080`. Override with the `PORT` env var:

```bash
PORT=8081 mvn spring-boot:run
```

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Render build fails with "agents not found" | Ensure `dockerContext: .` is in `render.yaml` |
| Hugging Face 401 error | Check your API token in Render env vars |
| Render free tier spins down | First request after idle takes ~30s to wake up |
| Port conflict locally | Set `PORT=8081` before starting |
| Ollama connection refused locally | Run `ollama serve` first |
