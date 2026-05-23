# impulse-core

Hytale ECS integration for Impulse.

Impulse is currently unreleased. The primary runtime model is backend bodies registered in `PhysicsWorldResource` by stable `PhysicsBodyId`. Full Hytale entities are gameplay adapters or generated visual views; they do not own backend body destruction.

## Body lifecycle

Impulse runtime bodies fall into three categories:

- Entity-attached bodies: gameplay objects such as players, NPCs, mobs, vehicles, props, and examples keep Hytale entities for identity and interaction. Those entities hold a `PhysicsBodyAttachmentComponent` with a body id. Removing the entity clears the attachment only.
- Detached bodies with optional views: backend bodies can exist without any Hytale entity. `PhysicsDetachedVisualMaterializationSystem` may create generated visual proxy entities near viewers; deleting a proxy does not delete the body.
- Runtime-only helper bodies: temporary anchors, world-collision helpers, and stress/runtime support bodies are registered as runtime-only resources and are never persisted.

Backend body deletion is explicit through `PhysicsWorldResource.destroyBody(bodyId)`. Entity removal, view dematerialization, chunk migration, and sync cleanup must not implicitly remove backend bodies.

## Space commands

Spaces are explicit. Commands should not create hidden default spaces.

First run in a fresh world should create the default space explicitly before commands that target the default space:

```bash
/impulse space create --default=true
```

- `/impulse space create --default=true` - create a physics space using the configured backend and streaming world collision, then make it default.
- `/impulse space create` - create a physics space without relying on hidden default-space creation.
- `/impulse space create --backend=impulse:rapier --worldCollision=streaming --default=true` - create a Rapier space and make it default.
- `/impulse space list` - list spaces, body counts, joint counts, backend ids, and world-collision mode.
- `/impulse space default` - show the default physics space.
- `/impulse space default --space=<space-id>` - set the default physics space.
- `/impulse space delete --space=<space-id> --confirm` - delete an empty physics space and close its backend state.

## Backend commands

- `/impulse backend list` - list discovered backends and active physics spaces.

Backend swap is not registered as a public command. Create explicit spaces with the desired backend instead.

Bullet is the default backend when it is available. Rapier currently has the strongest 10k-body direction, but backend-facing APIs should stay common where practical.

## Cleanup commands

- `/impulse clean --confirm` - remove Impulse attachment entities, visual proxies, runtime bodies, joints, and control sessions from the current world while keeping explicit spaces.

Cleanup does not create a replacement/default space implicitly. Run `/impulse space create --default=true` after cleaning when the next workflow needs a default space.

## Settings commands

- `/impulse settings step-mode` - show the world physics step mode.
- `/impulse settings step-mode --mode=progressive_refinement|adaptive|fixed|ccd` - choose dt-limited adaptive substeps, body-aware adaptive refinement, fixed substeps, or world-level CCD mode.
- `/impulse settings simulation-steps` - show the configured substep count.
- `/impulse settings simulation-steps --steps=<count>` - set the minimum or fixed substep count, from 1 to 16.
- `/impulse settings max-step-dt` - show the adaptive substep dt threshold.
- `/impulse settings max-step-dt --dt=<seconds>` - set the adaptive substep dt threshold used by `progressive_refinement` and `adaptive`.
- `/impulse settings solver` - show solver tuning for the default space.
- `/impulse settings solver --solverIterations=<n> --pgsIterations=<n> --stabilizationIterations=<n> --minIslandSize=<n>` - tune compatible backends.
- `/impulse settings visual-sync` - show visual LOD and occlusion settings.
- `/impulse settings visual-sync --fullRadius=<blocks> --maxRadius=<blocks> --farMode=cutoff|lod` - tune visual sync range behavior.
- `/impulse settings visual-sync --midInterval=<ticks> --farInterval=<ticks>` - tune lower-frequency visual updates.
- `/impulse settings visual-sync --occlusion=off|priority|cull --occlusionRaycasts=<n> --occlusionCache=<ticks>` - tune optional raycast-backed visual prioritization.
- `/impulse settings visual-sync --smoothing=true --smoothingRate=14` - smooth near dynamic visuals toward published snapshots without extrapolating.
- `/impulse settings visual-sync --prediction=true --predictionMaxSeconds=0.10` - allow near dynamic visuals to dead-reckon briefly between published physics snapshots; keep this off when it causes visible correction jitter.
- `/impulse settings visual-sync --materializationInterestInterval=<ticks> --materializationCandidateInterval=<ticks> --materializationVisibilityInterval=<ticks> --materializationSpawnRate=<n> --materializationCap=<n>` - tune detached visual materialization cadence and proxy budgets.
- `/impulse settings collision-lod --enabled=true --nearRadius=<blocks> --midRadius=<blocks> --hysteresis=<blocks> --interval=<ticks> --farSleep=true` - opt into distance collision LOD for default dynamic-body filters.
- `/impulse settings world-collision` - show world-collision settings for the default space.

## Profiling commands

- `/impulse perf enable` - enable Impulse runtime and world-collision profiling.
- `/impulse perf disable` - disable profiling.
- `/impulse perf toggle` - toggle profiling.
- `/impulse perf report` - print physics-step, sync, entity, detached, and world-collision profiling metrics.
- `/impulse perf stats` - show per-space body, awake, sleeping, joint, contact, attachment, detached, and world-collision counts.
- `/impulse perf reset` - reset profiling counters.

Spark plugin is advised to profile threaded physics benchmarks. By using the following command,
the exported profile includes both Hytale world threads and Impulse's per-world physics worker:

```bash
/spark profiler start --timeout 60 --save-to-file --regex --not-combined --ignore-sleeping --thread WorldThread.* --thread Impulse.*physics.*worker.* --thread ChunkLighting.* --thread WorldMap.*
```

Avoid contact debug rendering during benchmark captures; it calls backend contact enumeration and
will distort the hot path.

## Debug commands

- `/impulse debug` - show debug command usage.
- `/impulse debug toggle` - toggle Impulse debug rendering globally.
- `/impulse debug shapes` - toggle collider shape overlays.
- `/impulse debug motion` - toggle linear and angular velocity arrows.
- `/impulse debug contacts` - toggle contact point and normal overlays.
- `/impulse debug joints` - toggle joint anchor, axis, and link overlays.

## Crucible smoke

Install the patched Crucible runtime jar:

```bash
./scripts/ci/install-crucible-runtime.sh
```

Run the smoke-tagged runtime suite:

```bash
JAVA_TOOL_OPTIONS="-Dcrucible.autorun=true -Dcrucible.tags=smoke" \
  ./gradlew -Dimpulse.backend=impulse:rapier runAllMods
```

Run the live-tagged runtime suite:

```bash
JAVA_TOOL_OPTIONS="-Dcrucible.autorun=true -Dcrucible.tags=live" \
  ./gradlew -Dimpulse.backend=impulse:rapier runAllMods
```

Run the detached full-collision streaming benchmark scenario:

```bash
JAVA_TOOL_OPTIONS="-Dcrucible.autorun=true -Dcrucible.tags=benchmark" \
  ./gradlew -Dimpulse.backend=impulse:rapier runAllMods
```
