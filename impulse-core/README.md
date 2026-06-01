# impulse-core

Hytale ECS integration and plugin API for Impulse.

## Software architecture

Impulse core is divided in two categories:

- `internal`: core logic and backend ABI.
- `plugin`: impulse api exposed to hytale plugins

## Backend

- `/impulse backend list` - list discovered backends and active physics spaces.

Backend jars are Java service-provider jars. Impulse discovers `PhysicsBackend` providers from jars 
anywhere under the configured Hytale `mods` directories.

## Event frames

`PhysicsWorldResource.getLatestEventFrame()` exposes the latest value-only physics event frame for
diagnostics. Backends emit bounded post-step `PhysicsBackendEvent` batches; core translates them to
stable `PhysicsFrameEvent` values keyed by `RigidBodyKey` and `JointKey`, then publishes one
`PhysicsEventFramePublishedEvent` Hytale world event for the completed frame.

## Cleanup commands

- `/impulse clean --confirm` - remove Impulse attachment entities, visual proxies, runtime bodies, joints, and control sessions from the current world while keeping explicit spaces.

## Profiling

Spark plugin is advised to profile threaded physics benchmarks. By using the following command,
the exported profile includes both Hytale world threads and Impulse's physics owner-lane executor threads:

```bash
/spark profiler start --timeout 60 --save-to-file --regex --not-combined --ignore-sleeping --thread WorldThread.* --thread Impulse.*physics.*owner.* --thread ChunkLighting.* --thread WorldMap.*
```

Avoid contact debug rendering during benchmark captures; it calls backend contact enumeration and
will distort the hot path.

## Crucible tests

Install the patched Crucible runtime jar:

```bash
./scripts/ci/install-crucible-runtime.sh
```

Run the smoke-tagged runtime suite:

```bash
JAVA_TOOL_OPTIONS="-Dcrucible.autorun=true -Dcrucible.tags=smoke" \
  ./gradlew runAllMods
```

Run the live-tagged runtime suite:

```bash
JAVA_TOOL_OPTIONS="-Dcrucible.autorun=true -Dcrucible.tags=live" \
  ./gradlew runAllMods
```

Run the detached full-collision streaming benchmark scenario:

```bash
JAVA_TOOL_OPTIONS="-Dcrucible.autorun=true -Dcrucible.tags=benchmark" \
  ./gradlew runAllMods
```

Crucible selects `impulse:rapier` when it is installed. Override that only for backend-specific
debugging with `-Dimpulse.crucible.backend=<id>`.
