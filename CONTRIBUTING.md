# Contributing To Impulse

Impulse is still in its early phase and contributions are always welcomed!

## Prerequisites

- Java 25.
- Hytale Server version specified in the gradle properties.
- Rust and Cargo when building or testing the Rapier native backend.

## Contribution Scope

Good first contributions are documentation fixes, focused bug fixes, examples and small tests.

Open an issue or design discussion before changing architecture, persistence, lifecycle behavior,
backend runtime contracts, public API packages or native artifact packaging.

For bug reports, include a clear reproduction path and logs. When the bug depends on Hytale server
runtime behavior, also try to add or propose a failing Crucible test so that it can be reproduced in-game. 

## AI-Assisted Contributions

AI-assisted contributions are allowed when a human contributor reviews, understands, tests, and takes
responsibility for the work. Meaningful AI assistance must be disclosed in the pull request or commit
message.

See [AI Coding Assistants](doc/ai-coding-assistants.md) for the full policy.

## Code Style

- Follow the existing Java style: 4 spaces, K&R braces, Google Java Style conventions, and
  `.editorconfig`.
- Keep `impulse-api` backend-neutral and free of Hytale, Bullet, Rapier, and native details.
- Treat `dev.hytalemodding.impulse.core.plugin.*` as the supported Hytale plugin API surface.
- Treat `dev.hytalemodding.impulse.core.internal.*` as unsupported implementation detail.
- Prefer behavior-focused tests to structural tests that lock in private registration lists.

## Commit And PR Policy

Use Conventional Commit subjects when preparing commits, for example:

```text
fix(core): clean control sessions on module shutdown
docs: add backend provider guide
```

Pull requests should be narrow. Include the reason for the change, user-visible behavior, and the
validation that was run.

## Validation

For most code changes, run:

```bash
./gradlew headlessTest
```

For focused core changes, also run the relevant module tests:

```bash
./gradlew :impulse-core:test
```

Runtime/server behavior should be checked with:

```bash
./gradlew runAllMods
```

When a runtime bug is fixed, prefer adding a focused Crucible test that reproduces the failing
server behavior.

Backend-specific changes should name the backend used for validation, such as `impulse:bullet` or
`impulse:rapier`.