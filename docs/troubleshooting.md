# Troubleshooting

This document covers the most common local runtime issues for `Local GenAI Lab`.

## Backend Is Down

Symptoms:
- UI shows `Failed to load sessions.`
- model dropdown is empty
- prompt input is disabled

Check:

```bash
cd scripts
./check-app.sh
```

Fix:
- start the backend again
- confirm `http://localhost:8080/actuator/health` is reachable

## No Ollama Models Available

Symptoms:
- UI says no Ollama models are installed locally
- send button stays disabled in Ollama mode

Check:

```bash
ollama list
```

Fix:

```bash
ollama pull llama3:8b
```

Then refresh the UI.

## Bedrock Model Validation Error

Symptoms:
- Bedrock returns a validation error about on-demand throughput not being supported

Cause:
- the configured model id is a base model id instead of an inference profile id

Example:
- use `us.amazon.nova-pro-v1:0`
- not `amazon.nova-pro-v1:0`

Fix:
- start the backend with the Bedrock helper script
- or set `BEDROCK_MODEL_ID` to the correct inference profile

See [providers.md](./providers.md) for the Bedrock startup flow.

## AWS Credentials or Access Problems

Symptoms:
- Bedrock requests fail
- MCP-backed AWS audit scripts fail
- backend or script output shows AWS auth/permission errors

Check:

```bash
aws sts get-caller-identity
```

Fix:
- verify your AWS credentials or `AWS_PROFILE`
- confirm the selected Bedrock region and model/profile are enabled for your account

## Slow Local Models

Symptoms:
- long waits before any answer arrives
- UI shows `Working...` and an elapsed wait timer for a long time

Cause:
- larger local models such as `codellama:70b` are much slower on a local developer machine

Fix:
- switch to a lighter Ollama model for normal use
- keep the heavy model only for deliberate comparisons

## Java Version Warnings

Symptoms:
- backend startup shows warnings about restricted native access or Tomcat JNI loading

Cause:
- running the backend on a newer JDK than the project baseline

Recommended fix:
- use Java 21 for this repo

Check:

```bash
java -version
```

## `check-app.sh` Fails on Models

Symptoms:
- `backend: ok`
- `models: failed`

Cause:
- backend is up, but `/api/models` cannot return usable models for the active provider

Typical reasons:
- no Ollama models installed
- Bedrock discovery failing
- Bedrock credentials/access not available

This is an application-level failure, not just a process-level one. The UI will usually be degraded until model discovery works.
