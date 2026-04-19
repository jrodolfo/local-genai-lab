# Scripts

[![ci](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/jrodolfo/local-genai-lab/actions/workflows/ci.yml)
![shell](https://img.shields.io/badge/shell-bash-89e051)
![license](https://img.shields.io/badge/license-MIT-blue)
![aws](https://img.shields.io/badge/aws-cloud-a1662f)

Shell-based AWS audit and S3 CloudWatch helpers, plus local smoke-check utilities used by the main application and the MCP server.

The directory includes:

- a regional AWS audit report generator
- a focused S3 CloudWatch report generator for one bucket
- local shell tests
- a smoke-check script for the local frontend/backend/model-discovery/Ollama stack
- helper scripts for starting the backend with `ollama`, `bedrock`, or `huggingface`

It is designed for a practical cleanup workflow:
- compare resources across one or more regions
- spot likely billable resources first
- keep raw command output for later inspection
- continue running even when some AWS services, permissions, or endpoints fail

## What It Produces

Each regional audit run writes a timestamped folder under `reports/audit/`, for example:

```text
reports/audit/aws-audit-2026-04-06_16-34-25/
```

Each S3 CloudWatch run writes a timestamped folder under `reports/s3-cloudwatch/`, for example:

```text
reports/s3-cloudwatch/s3-cloudwatch-2026-04-06_20-35-38/
```

That folder contains:
- `report.txt`: human-readable summary and detailed results
- `summary.json`: machine-readable run summary with counts and failed/skipped commands
- `json/`: raw JSON outputs for successful JSON commands
- `text/`: raw text outputs for text-based commands
- `stderr/`: stderr captured from failed commands
- `meta/status.tsv`: machine-readable command status metadata

The `reports/` directory is ignored by Git so generated output does not get committed.

## Project structure

```text
.
├── .github/workflows/
│   └── ci.yml
├── reports/
│   ├── audit/
│   └── s3-cloudwatch/
├── tests/
│   ├── mock-s3-cloudwatch-aws.sh
│   ├── test-s3-cloudwatch.sh
│   └── test.sh
├── aws-region-audit-report.sh
├── aws-s3-cloudwatch-report.sh
├── LICENSE
├── Makefile
└── README.md
```

Key files:
- `aws-region-audit-report.sh`: regional AWS audit report generator
- `aws-s3-cloudwatch-report.sh`: focused S3 CloudWatch report generator for one bucket
- `check-app.sh`: local stack smoke-check script
- `run-backend-ollama.sh`: start the backend with the Ollama provider
- `run-backend-bedrock.sh`: start the backend with Bedrock defaults
- `run-backend-huggingface.sh`: start the backend with Hugging Face defaults
- `.env.example`: sample multi-provider environment file for local startup
- `LICENSE`: MIT license for the repository
- `tests/`: mock-based shell tests
- `.github/workflows/ci.yml`: GitHub Actions CI workflow

## Requirements

- macOS or another Bash-compatible environment
- AWS CLI v2
- `jq`
- valid AWS credentials

## Usage

Run the audit:

```bash
make audit
```

Run the audit for specific regions through `make`:

```bash
make audit REGIONS="us-east-2"
```

Or:

```bash
make audit REGIONS="us-east-1 us-east-2"
```

Limit the audit to specific service groups:

```bash
make audit SERVICES="sagemaker ec2"
```

Run the script directly with the default regions:

```bash
./aws-region-audit-report.sh
```

Override the regions:

```bash
./aws-region-audit-report.sh --regions us-east-1 us-east-2
```

Or:

```bash
./aws-region-audit-report.sh --regions us-east-1,us-east-2
```

Filter by service groups:

```bash
./aws-region-audit-report.sh --services sagemaker,ec2
```

Service filter keys:
- `sts`
- `aws-config`
- `s3`
- `ec2`
- `elbv2`
- `rds`
- `lambda`
- `ecs`
- `eks`
- `sagemaker`
- `opensearch`
- `secretsmanager`
- `logs`
- `tagging`

Run local tests:

```bash
make test
```

Check script syntax:

```bash
make lint
```

## CI

GitHub Actions runs CI for pushes to `main` and for pull requests.

The default CI workflow runs:
- `make lint`
- `make test`

These checks are local and mock-based, so they do not require AWS credentials.

Show available targets:

```bash
make help
```

Check whether the local app stack is up:

```bash
make check-app
```

Optional overrides:

```bash
BACKEND_URL=http://localhost:8080 FRONTEND_URL=http://localhost:3000 CHECK_OLLAMA=false make check-app
```

The smoke check uses these defaults:

- `BACKEND_URL=http://localhost:8080`
- `FRONTEND_URL=http://localhost:5173`
- `OLLAMA_URL=http://localhost:11434`
- `CHECK_OLLAMA=true`
- `CHECK_MODELS=true`

`check-app.sh` now checks `/api/models` as well, so it can distinguish “backend process is up” from “the UI can actually load usable models”.

For common local runtime problems, see [../docs/troubleshooting.md](../docs/troubleshooting.md).

Start the backend in Ollama mode:

```bash
make run-backend-ollama
```

All three backend helper scripts auto-load the repo-local `.env` file when present, without overriding variables you already exported in the shell. A good local starting point is:

```bash
cp .env.example .env
```

Start the backend in Bedrock mode:

```bash
make run-backend-bedrock
```

Bedrock helper defaults:

- `BEDROCK_REGION=us-east-2`
- `BEDROCK_MODEL_ID=us.amazon.nova-pro-v1:0`
- `MCP_ENABLED=true`

Start the backend in Hugging Face mode:

```bash
HUGGINGFACE_API_TOKEN=hf_xxx make run-backend-huggingface
```

Hugging Face helper defaults:

- `HUGGINGFACE_BASE_URL=https://router.huggingface.co/v1/chat/completions`
- `HUGGINGFACE_DEFAULT_MODEL=meta-llama/Llama-3.1-8B-Instruct`
- `HUGGINGFACE_MODELS` defaults to the configured default model
- `MCP_ENABLED=true`

If the same `.env` file also contains Bedrock config, the backend process can expose both Bedrock and Hugging Face in the runtime selector while still starting with one default provider.

For Nova Pro, use the inference profile id rather than the base model id.

Override them when needed:

```bash
BEDROCK_REGION=us-east-1 BEDROCK_MODEL_ID=amazon.nova-lite-v1:0 make run-backend-bedrock
```

For the full provider workflow and verification steps, see [../docs/providers.md](../docs/providers.md).

Run the S3 CloudWatch bucket report:

```bash
make s3-cloudwatch BUCKET=example.com
```

Override the request-metrics region:

```bash
make s3-cloudwatch BUCKET=example.com REGION=us-east-2
```

Override the queried time window:

```bash
make s3-cloudwatch BUCKET=example.com DAYS=30
```

## AWS Services Covered

The script currently checks:
- STS
- S3
- EC2 instances
- EBS volumes
- Elastic IPs
- VPCs
- subnets
- security groups
- ELBv2
- RDS
- Lambda
- ECS
- EKS
- SageMaker domains
- SageMaker notebook instances
- OpenSearch
- Secrets Manager
- CloudWatch Logs
- Resource Groups Tagging API

## S3 CloudWatch Script

Use [`aws-s3-cloudwatch-report.sh`](./aws-s3-cloudwatch-report.sh) when you want a CloudWatch-focused report for one bucket.

Example:

```bash
./aws-s3-cloudwatch-report.sh --bucket example.com
```

Query a longer time window:

```bash
./aws-s3-cloudwatch-report.sh --bucket example.com --days 30
```

The script:
- detects the bucket region
- queries S3 storage metrics from CloudWatch in `us-east-1`
- queries bucket request metrics from the bucket region
- writes a readable `report.txt`
- writes a machine-readable `summary.json`
- saves raw JSON and stderr details under `reports/s3-cloudwatch/`

Notes for S3 metrics:
- storage metrics such as `BucketSizeBytes` and `NumberOfObjects` are daily metrics
- request metrics may not exist unless S3 request metrics are enabled for the bucket
- a static website bucket is a good fit for checking request counts, errors, bytes downloaded, and object counts

## Notes

- Regional commands use explicit `--region` values.
- The default regions are `us-east-1` and `us-east-2`, but you can override them with `--regions`.
- `make audit` also accepts `REGIONS="..."` and `SERVICES="..."` and passes them through to the script.
- `make s3-cloudwatch` accepts `BUCKET=...` and optional `REGION=...` and `DAYS=...`.
- Skipped commands are recorded explicitly when you use `--services`.
- The script is intentionally defensive and continues after individual command failures.
- If AWS permissions are missing or a service is unavailable, the failure is recorded in the report and under `stderr/`.
