# Release Checklist

Use this checklist before larger pushes, demos, or dependency maintenance
branches. It is intentionally local-first and report-oriented.

## Functional Checks

Run the normal verification that matches the size of the change:

```bash
make test
make verify
```

For final release preparation, prefer the guided wrapper:

```bash
make prepare-release VERSION=v0.2.0
```

This runs `git status`, `git pull`, the local and Docker-inclusive release
checks with output written to versioned files under `/tmp`, `git diff --check`,
and a final `git status`. It then prints the GitHub Release fields and
post-publish tag sync commands. The version argument is required because it is
used in the `/tmp` file names and release reminder text. It does not create tags
or publish the release.

Before opening a release PR or merging a larger release-preparation branch, run
the local release gate:

```bash
make release-check
```

For Docker-specific changes or final local release confidence, also run the
Docker-inclusive release gate:

```bash
make release-check-docker
```

`make release-check-docker` is the lower-level Docker-inclusive gate. It does
not need a version and does not print release-publishing reminders.

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

Qdrant image triage:

- The Compose stack uses the pinned external image `qdrant/qdrant:v1.18.2`.
- On 2026-07-01, Docker Hub metadata showed `v1.18.2` as the current stable
  release tag, with `latest`, `v1`, and `v1.18` as moving aliases and `dev` as
  an unstable development tag.
- Keep Qdrant pinned to a stable release tag. Do not switch this project to
  `latest`, broad version aliases, or `dev` only to make the freshness report
  look quieter.
- Treat Qdrant scan findings as external vendor-image findings unless the
  repository starts building its own Qdrant image or changes Qdrant runtime
  configuration.
