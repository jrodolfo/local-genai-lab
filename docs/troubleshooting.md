# Troubleshooting

This document covers the most common local runtime issues for `Local GenAI Lab`.

## Backend Is Down

Symptoms:
- UI shows `Failed to load sessions.`
- model dropdown is empty
- prompt input is disabled

Check:

```bash
./ops/check-app.sh
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
- start only the backend helper with `APP_MODEL_PROVIDER=bedrock ./ops/start-backend-helper.sh`
- or set `BEDROCK_MODEL_ID` to the correct inference profile

See [providers.md](./providers.md) for the Bedrock startup flow.

## Hugging Face Token or Model Problems

Symptoms:
- Hugging Face is missing from the provider selector
- startup in Hugging Face mode fails immediately
- Hugging Face requests fail with auth or model errors

Check:

```bash
echo "${HUGGINGFACE_API_TOKEN:-}"
```

Fix:
- set `HUGGINGFACE_API_TOKEN` before starting the backend
- confirm `HUGGINGFACE_DEFAULT_MODEL` is one of the configured candidate models
- if needed, override `HUGGINGFACE_MODELS` so the backend can validate the models you actually want to use
- if the UI shows no usable Hugging Face models, verify that the configured model ids are valid for your account and token

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

This project targets Java 21 for the Spring Boot backend.

Symptoms:
- backend startup shows warnings about restricted native access or Tomcat JNI loading

Cause:
- running the backend on a newer JDK than the Java 21 project baseline

Recommended fix:
- use Java 21 for this repo

Check:

```bash
java -version
```

## `ops/check-app.sh` Fails on Models

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

## Provider Status Looks Stale

Symptoms:
- the provider status banner still shows an older state
- `Last checked` is not recent
- you changed local/provider setup and the UI did not reflect it immediately

Cause:
- provider status is cached briefly on the backend to avoid excessive live discovery checks

What `Last checked` means:
- it is the timestamp of the last backend status evaluation returned to the UI
- it is informational, not a guarantee that the provider has not changed since then

Fix:
- use `Refresh status` in the UI to re-fetch the current provider status
- if a real chat request still fails, trust the request-time error over the cached banner state

Important distinction:
- the status banner is a lightweight readiness/troubleshooting surface
- actual `/api/chat` and `/api/chat/stream` failures are still the source of truth for request-time provider errors

## Artifact Inspector Looks Empty

Symptoms:
- the artifact inspector shows an empty-state message
- you opened a tool result but do not see preview content yet

What this usually means:
- no summary, report, or file list has been selected yet
- the selected run directory has no files to show
- the artifact preview endpoint returned no previewable content for that path

Expected behavior:
- before any artifact action, the panel shows `Select a summary, report, or file list to inspect artifacts.`
- empty file lists show `No files were found in this run directory.`
- missing preview content shows `No preview content is available for this artifact.`

Fix:
- click `Open summary`, `Open report`, or `Show files` from a completed tool result card
- if the panel still stays empty, inspect the tool result card and backend logs to confirm the run directory and artifact paths were produced as expected

## Correlating A Failed Chat Request

Chat responses now include an `X-Request-Id` header. The backend logs use the same request id for:

- request start
- request completion
- stream aborts
- tool execution start / completion / failure

If a request fails or behaves unexpectedly, capture the request id and grep the backend logs for it.
