# Ops

This directory contains local app runtime helpers.

Use the repository root for the public lifecycle commands:

```bash
./start.sh
./stop.sh
./restart.sh
./status.sh
```

or:

```bash
make start
make stop
make restart
make status
make check-app
```

What belongs in `ops/`:

- backend-only startup helpers
- local stack smoke checks
- shared shell runtime helpers used by the top-level lifecycle scripts
- tests for those operational helpers

What does not belong in `ops/`:

- MCP tool scripts
- AWS audit/report generators
- MCP-facing report artifacts

Those stay under [`scripts/`](../scripts/README.md).
