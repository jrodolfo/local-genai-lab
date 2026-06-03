# Ops

This directory contains local app runtime helpers.

Use the repository root for the public lifecycle commands:

```bash
./start.sh
./stop.sh
./restart.sh
./status.sh
```

`./stop.sh` stops PID-file-managed processes by default. Use `./stop.sh --all`
to also stop processes currently listening on the configured backend/frontend
ports. `./restart.sh` uses that broader stop behavior before starting the app.

or:

```bash
make start
make stop
make restart
make status
make check-app
make test-ops
```

What belongs in `ops/`:

- backend-only startup helpers
- local stack smoke checks
- shared shell runtime helpers used by the top-level lifecycle scripts
- tests for those operational helpers

Run operational helper tests from the repository root:

```bash
make test-ops
```

What does not belong in `ops/`:

- MCP tool scripts
- AWS audit/report generators
- MCP-facing report artifacts

Those stay under [`scripts/`](../scripts/README.md).
