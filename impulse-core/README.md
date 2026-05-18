# impulse-core

Hytale ECS integration for Impulse.

Impulse is currently unreleased. The primary runtime model is backend-owned physics bodies registered in `PhysicsWorldResource`; full Hytale entities are adapter objects for interaction, selection, examples, and visual proxies.

## Space commands

Spaces are explicit. Commands should not create hidden default spaces.

First run in a fresh world should create the default space explicitly before commands that target the default space:

```bash
/impulse space create --default true
```

- `/impulse space create --default true` - create a physics space using the configured backend and streaming world collision, then make it default.
- `/impulse space create` - create a physics space without relying on hidden default-space creation.
- `/impulse space create --backend impulse:rapier --worldCollision streaming --default true` - create a Rapier space and make it default.
- `/impulse space list` - list spaces, body counts, joint counts, backend ids, and world-collision mode.
- `/impulse space default` - show the default physics space.
- `/impulse space default --space <space-id>` - set the default physics space.
- `/impulse space delete --space <space-id> --confirm` - delete an empty physics space and close its backend state.

## Backend commands

- `/impulse backend list` - list discovered backends and active physics spaces.
- `/impulse backend swap --backend impulse:rapier` - migrate the default physics space to Rapier.
- `/impulse backend swap --backend impulse:bullet` - migrate the default physics space back to Bullet.
- `/impulse backend swap --backend <backend-id> --space <space-id>` - migrate a specific space.

Bullet is the default backend when it is available. Rapier currently has the strongest 10k-body direction because of native parallel stepping, but backend-facing APIs should stay common where practical.

## Cleanup commands

- `/impulse clean --confirm` - remove Impulse body entities, visual proxies, runtime bodies, joints, spaces, and control sessions from the current world.

Cleanup does not create a replacement/default space implicitly. Run `/impulse space create --default true` after cleaning when the next workflow needs a default space.

## Settings commands

- `/impulse settings step-mode` - show the world physics step mode.
- `/impulse settings step-mode --mode progressive_refinement|fixed|ccd` - choose adaptive refinement, fixed substeps, or world-level CCD mode.
- `/impulse settings simulation-steps` - show the configured substep count.
- `/impulse settings simulation-steps --steps <count>` - set the minimum or fixed substep count, from 1 to 16.
- `/impulse settings max-step-dt` - show the adaptive substep dt threshold.
- `/impulse settings max-step-dt --dt <seconds>` - set the adaptive substep dt threshold used by `progressive_refinement`.
- `/impulse settings solver` - show solver tuning for the default space.
- `/impulse settings solver --solverIterations <n> --pgsIterations <n> --stabilizationIterations <n> --minIslandSize <n>` - tune compatible backends.
- `/impulse settings visual-sync` - show visual LOD and occlusion settings.
- `/impulse settings visual-sync --fullRadius <blocks> --maxRadius <blocks> --farMode cutoff|lod` - tune visual sync range behavior.
- `/impulse settings visual-sync --midInterval <ticks> --farInterval <ticks>` - tune lower-frequency visual updates.
- `/impulse settings visual-sync --occlusion off|priority|cull --occlusionRaycasts <n> --occlusionCache <ticks>` - tune optional raycast-backed visual prioritization.
- `/impulse settings world-collision` - show world-collision settings for the default space.

## Profiling commands

- `/impulse perf enable` - enable Impulse runtime and world-collision profiling.
- `/impulse perf disable` - disable profiling.
- `/impulse perf toggle` - toggle profiling.
- `/impulse perf report` - print physics-step, sync, entity, detached, and world-collision profiling metrics.
- `/impulse perf stats` - show per-space body, awake, sleeping, joint, contact, and ownership counts.
- `/impulse perf reset` - reset profiling counters.

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
