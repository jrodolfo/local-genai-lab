# Providers

Use this document when you want to control the backend default provider and understand how runtime provider switching works in the UI.

If you want a local template for provider configuration, start from [../.env.example](../.env.example) and copy it to `.env`.

## Overview

The frontend always talks to the Spring Boot backend.

- the backend still has a configured default provider
- the UI can now switch provider per request without restarting the backend
- the UI only shows providers that are actually configured in the current backend process
- the helper script below sets the backend default provider for a local session
- the helper script auto-loads the repo-local `.env` file when present, without overriding variables you already exported in the shell

Supported providers:

- `ollama`: default local provider
- `bedrock`: optional AWS-managed provider
- `huggingface`: optional hosted provider with a configured candidate list that the backend validates dynamically

## Unified startup

Default local workflow:

```bash
cd scripts
./run-backend.sh
```

The unified startup script uses:

- `APP_MODEL_PROVIDER` to choose the default provider at startup
- the current shell and optional `.env` file for all provider configuration

Examples:

```bash
cd scripts
APP_MODEL_PROVIDER=ollama ./run-backend.sh
```

```bash
cd scripts
APP_MODEL_PROVIDER=bedrock AWS_PROFILE=personal ./run-backend.sh
```

```bash
cd scripts
APP_MODEL_PROVIDER=huggingface HUGGINGFACE_API_TOKEN=hf_xxx ./run-backend.sh
```

## Ollama

Defaults when `APP_MODEL_PROVIDER=ollama`:

- provider: `ollama`
- `MCP_ENABLED=true`

Requirements:

- Ollama running locally at `http://localhost:11434`
- at least one local model installed, for example:

```bash
ollama pull llama3:8b
```

## Bedrock

Defaults when `APP_MODEL_PROVIDER=bedrock`:

- provider: `bedrock`
- region: `us-east-2`
- model: `us.amazon.nova-pro-v1:0`
- `MCP_ENABLED=true`

Requirements:

- valid AWS credentials available to the backend process
- Bedrock model access enabled in your AWS account
- the selected region supports the configured model
- for Nova Pro, use an inference profile id such as `us.amazon.nova-pro-v1:0` rather than the base model id `amazon.nova-pro-v1:0`

Common credential paths:

- default AWS SDK resolution from `~/.aws/credentials`
- `AWS_PROFILE`
- environment credentials

Example with an explicit AWS profile:

```bash
cd scripts
APP_MODEL_PROVIDER=bedrock AWS_PROFILE=personal ./run-backend.sh
```

Override the region or model when needed:

```bash
cd scripts
APP_MODEL_PROVIDER=bedrock BEDROCK_REGION=us-east-1 BEDROCK_MODEL_ID=amazon.nova-lite-v1:0 ./run-backend.sh
```

## Hugging Face

Defaults when `APP_MODEL_PROVIDER=huggingface`:

- provider: `huggingface`
- base URL: `https://router.huggingface.co/v1/chat/completions`
- default model: `meta-llama/Llama-3.1-8B-Instruct`
- configured candidate list: defaults to the configured default model unless overridden
- `MCP_ENABLED=true`

Requirements:

- a valid Hugging Face API token available to the backend process
- a configured default model and candidate list that you want the backend to validate for the UI

Override the configured candidate list when needed:

```bash
cd scripts
APP_MODEL_PROVIDER=huggingface \
HUGGINGFACE_API_TOKEN=hf_xxx \
HUGGINGFACE_DEFAULT_MODEL=Qwen/Qwen2.5-72B-Instruct \
HUGGINGFACE_MODELS=Qwen/Qwen2.5-72B-Instruct,meta-llama/Llama-3.1-8B-Instruct \
./run-backend.sh
```

## Verification

After the backend starts, verify the default provider:

- health: `http://localhost:8080/actuator/health`
- info: `http://localhost:8080/actuator/info`

You can also run the local smoke check:

```bash
cd scripts
./check-app.sh
```

## Notes

- the frontend model selector is provider-aware
- the frontend provider selector can switch between providers configured in the current backend process without restarting the backend
- for `ollama`, the UI only offers models installed locally
- for `bedrock`, the backend tries to list available inference profiles in the configured region and falls back to the configured model id if discovery is unavailable
- for `huggingface`, the backend starts from a configured candidate list and validates which models are currently usable before returning them to the UI
- successful MCP/tool execution still enriches prompts before the backend calls the active provider
