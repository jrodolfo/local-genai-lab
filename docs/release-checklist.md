# Release Checklist

Use this checklist before larger pushes, demos, or dependency maintenance
branches. It is intentionally local-first and report-oriented.

## Functional Checks

Run the normal verification that matches the size of the change:

```bash
make test
make verify
```

For Docker-specific changes, also run:

```bash
./scripts/docker-full-check.sh
```

## Dependency Freshness

Run the dependency freshness report when preparing maintenance work:

```bash
make dependency-freshness
```

Use it as an early-warning system, not as an instruction to upgrade everything.
The report surfaces:

- Maven parent, dependency, property, and plugin update signals
- npm outdated packages for `frontend/` and `mcp/`
- Docker image references from Dockerfiles and Compose files
- moving Docker tags such as `latest`
- suggested follow-up branch names for small modernization slices

The script is report-only and does not modify dependency files. Treat suggested
branch names as triage hints, not as automatic upgrade instructions. Optional
Docker Hub metadata is disabled by default to keep the normal command
local-friendly:

```bash
DEPENDENCY_FRESHNESS_REGISTRY=true ./agents/dependency-freshness.sh
```

Use the output to choose small, targeted dependency upgrades instead of waiting
until CI, build compatibility, or security scanning forces urgent work.
