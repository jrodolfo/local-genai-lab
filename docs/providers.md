# Providers

Use this document when you want to switch the backend between `ollama` and `bedrock` without bloating the main README.

## Overview

The frontend always talks to the Spring Boot backend. The backend decides which model provider is active.

Supported providers:

- `ollama`: default local provider
- `bedrock`: optional AWS-managed provider

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

## Verification

After the backend starts, verify the active provider:

- health: `http://localhost:8080/actuator/health`
- info: `http://localhost:8080/actuator/info`

You can also run the local smoke check:

```bash
cd scripts
./check-app.sh
```

## Notes

- the frontend model selector is provider-aware
- for `ollama`, the UI only offers models installed locally
- for `bedrock`, the backend tries to list available inference profiles in the configured region and falls back to the configured model id if discovery is unavailable
- successful MCP/tool execution still enriches prompts before the backend calls the active provider
