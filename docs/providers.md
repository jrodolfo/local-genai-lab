# Providers

Use this document when you want to control the backend default provider and understand how runtime provider switching works in the UI.

If you want a local template for provider configuration, start from [../.env.example](../.env.example) and copy it to `.env`.

## Overview

The frontend always talks to the Spring Boot backend.

- the backend still has a configured default provider
- the UI can now switch provider per request without restarting the backend
- the UI only shows providers that are actually configured in the current backend process
- the helper scripts below set the backend default provider for a local session
- the helper scripts auto-load the repo-local `.env` file when present, without overriding variables you already exported in the shell

Supported providers:

- `ollama`: default local provider
- `bedrock`: optional AWS-managed provider
- `huggingface`: optional hosted provider with a configured candidate list that the backend validates dynamically

## Ollama

Default local workflow:

```bash
cd scripts
./run-backend-ollama.sh
```

Defaults:

- provider: `ollama`
- `MCP_ENABLED=true`

Requirements:

- Ollama running locally at `http://localhost:11434`
- at least one local model installed, for example:

```bash
ollama pull llama3:8b
```

## Bedrock

Preferred local Bedrock workflow:

```bash
cd scripts
./run-backend-bedrock.sh
```

If `.env` contains Hugging Face config as well, the same backend process can expose both Bedrock and Hugging Face in the UI.

Defaults:

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
AWS_PROFILE=personal ./run-backend-bedrock.sh
```

Override the region or model when needed:

```bash
cd scripts
BEDROCK_REGION=us-east-1 BEDROCK_MODEL_ID=amazon.nova-lite-v1:0 ./run-backend-bedrock.sh
```

## Hugging Face

Preferred hosted Hugging Face workflow:

```bash
cd scripts
HUGGINGFACE_API_TOKEN=hf_xxx ./run-backend-huggingface.sh
```

If `.env` also contains Bedrock config, the same backend process can expose both providers in the UI while still starting with Hugging Face as the default provider.

Defaults:

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
HUGGINGFACE_API_TOKEN=hf_xxx \
HUGGINGFACE_DEFAULT_MODEL=Qwen/Qwen2.5-72B-Instruct \
HUGGINGFACE_MODELS=Qwen/Qwen2.5-72B-Instruct,meta-llama/Llama-3.1-8B-Instruct \
./run-backend-huggingface.sh
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
