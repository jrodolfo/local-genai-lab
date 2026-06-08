# Ops

This directory contains local app runtime helpers.

Use the repository root for the public lifecycle commands:

```bash
./start.sh
./stop.sh
./restart.sh
./status.sh
./build.sh
```

`./stop.sh` stops PID-file-managed processes by default. Use `./stop.sh --all`
to also stop processes currently listening on the configured backend/frontend
ports. `./restart.sh` uses that broader stop behavior before starting the app.
`./build.sh` builds backend, frontend, and MCP artifacts without starting
or stopping the app.

`./build.sh` runs tests unless explicitly skipped, so normal output includes
Maven and npm test/build progress. JVM/native-access warnings from Java
dependencies may still appear during backend tests. Application/controller stack
traces from expected negative-path tests should not be treated as normal build
noise.

or:

```bash
make start
make stop
make restart
make status
make build
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
