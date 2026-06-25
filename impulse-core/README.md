# impulse-core

Hytale ECS integration and plugin API for Impulse.

## Software architecture

Impulse core is divided in two categories:

- `internal`: core logic and backend ABI.
- `plugin`: impulse api exposed to hytale plugins

## Backend

- `/impulse backend list` - list discovered backends and active physics spaces.

Backend jars are Java service-provider jars. Impulse discovers `PhysicsBackendRuntimeProvider`
services from jars anywhere under the configured Hytale `mods` directories.

## Event frames

`PhysicsWorlds.latestEventFrame(physicsStore)` exposes the latest value-only physics event frame
for diagnostics. When collection is enabled, backends emit bounded post-step event
batches; core translates them to stable UUID-primary `PhysicsFrameEvent` values and copied
PhysicsStore refs where available, then publishes one `PhysicsEventFramePublishedEvent` Hytale
world event for the completed frame.

Backend event collection is opt-in through `PhysicsWorldSettings.setEventCollectionMode(...)`.
Worlds default to `PhysicsEventCollectionMode.DISABLED`; use
`PhysicsEventCollectionMode.CONTACTS` when a plugin intentionally consumes backend contact events.
At runtime, use `/impulse settings simulation events contacts` to enable collection for the current
world and `/impulse settings simulation events disabled` to return to the default hot path.

## Cleanup commands

- `/impulse clean --confirm` - remove Impulse attachment entities, visual proxies, runtime bodies, joints, and control sessions from the current world while keeping explicit spaces.

## Profiling

Spark plugin is advised to profile threaded physics benchmarks. By using the following command,
the exported profile includes Hytale world/store tick threads and PhysicsStore completion work:

```bash
/spark profiler start --timeout 60 --save-to-file --regex --not-combined --ignore-sleeping --thread WorldThread.* --thread Impulse.*PhysicsStore.* --thread ChunkLighting.* --thread WorldMap.*
```

Avoid contact debug rendering during benchmark captures; it calls backend contact enumeration and
will distort the hot path.

## Runtime validation

Use ordinary Gradle tests for backend physics, PhysicsStore topology, settings round trips, and
module lifecycle predicates. When a failure depends on live Hytale server behavior, reproduce it
with `./gradlew runAllMods` and document the world setup and commands used.
