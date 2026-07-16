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

## Ollama Warning in Docker on Linux or EC2

Symptoms:
- UI says `Ollama is not available. Start the Ollama service or select another provider such as Amazon Bedrock or Hugging Face.`
- provider or model selectors are empty in Docker mode
- the host can run `ollama`, but the backend container cannot reach it

Verify that Docker resolves the Linux host alias:

```bash
docker exec llm-backend getent hosts host.docker.internal
```

Verify that the backend container can reach the Ollama API:

```bash
docker exec llm-backend curl -sS http://host.docker.internal:11434/api/tags
```

The response should include the locally installed Ollama models.

Verify that the expected provider settings reached the backend container
without printing secret token values:

```bash
docker exec llm-backend sh -c '
  printf "APP_MODEL_PROVIDER=%s\n" "${APP_MODEL_PROVIDER:-missing}"
  printf "OLLAMA_BASE_URL=%s\n" "${OLLAMA_BASE_URL:-missing}"
  printf "OLLAMA_DEFAULT_MODEL=%s\n" "${OLLAMA_DEFAULT_MODEL:-missing}"
  printf "BEDROCK_REGION=%s\n" "${BEDROCK_REGION:-missing}"

  if [ -n "${HUGGINGFACE_API_TOKEN:-}" ]; then
    echo "HUGGINGFACE_API_TOKEN=set"
  else
    echo "HUGGINGFACE_API_TOKEN=missing"
  fi
'
```

Do not print secret token values when troubleshooting.

If the hostname does not resolve, confirm your local `docker-compose.yml`
includes:

```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

If the hostname resolves but the connection is refused, Ollama is probably
listening only on the host loopback interface. On a Linux host or EC2 instance,
configure the Ollama systemd service to listen on host interfaces:

```bash
sudo systemctl edit ollama
```

Add:

```ini
[Service]
Environment="OLLAMA_HOST=0.0.0.0:11434"
```

Then restart Ollama:

```bash
sudo systemctl daemon-reload
sudo systemctl restart ollama
sudo ss -lntp | grep 11434
```

Keep port `11434` closed in the EC2 security group or any Internet-facing
firewall. Docker needs host-local access to Ollama; the public Internet does
not.

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
- Docker backend returns an Agent tool failure about missing `aws` or `jq`
- Docker backend returns an Agent tool failure about a missing complete report bundle

Check:

```bash
aws sts get-caller-identity
jq --version
```

Fix:
- verify your AWS credentials or `AWS_PROFILE`
- confirm the selected Bedrock region and model/profile are enabled for your account
- for Docker-based MCP AWS tools, copy `.env.docker-aws-tools.example` to
  `.env.docker-aws-tools`, set `LOCAL_GENAI_LAB_ENABLE_AWS_TOOLS=true`, and
  confirm `LOCAL_GENAI_LAB_AWS_DIR` points to your local AWS config directory
- the Docker backend image includes AWS CLI and `jq`, but it does not mount host
  AWS credentials unless the local AWS tools override is enabled
- if an audit created `report.txt` but not `summary.json`, the MCP server treats
  the report as incomplete; common causes are missing `jq`, missing `aws`, or an
  interrupted script run

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

This project targets Java 21 for the Spring Boot backend. The backend Maven
build enforces Java 21 and Maven 3.9+ before compile/test work starts.

Symptoms:
- backend startup shows warnings about restricted native access or Tomcat JNI loading
- `mvn validate` or `mvn test` fails with a Maven Enforcer message

Cause:
- running the backend on a newer JDK than the Java 21 project baseline
- running backend Maven commands with Maven older than 3.9

Recommended fix:
- use Java 21 for this repo
- use Maven 3.9 or newer

Check:

```bash
java -version
mvn -version
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
