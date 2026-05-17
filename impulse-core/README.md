# impulse-core

Hytale ECS integration for Impulse.

## Backend commands

- `/impulse backend list` - list discovered backends and active physics spaces.
- `/impulse backend swap --backend impulse:rapier` - migrate the main physics space to Rapier.
- `/impulse backend swap --backend impulse:bullet` - migrate the main physics space back to Bullet.
- `/impulse backend swap --backend <backend-id> --space <space-id>` - migrate a specific space.

Bullet is the default backend when it is available. Rapier is experimental. Runtime backend
swapping migrates supported bodies and joints, but exact solver behavior can differ between
backends.

## Cleanup commands

- `/impulse clean --confirm` - remove all Impulse physics entities from the current world without touching unrelated world entities.

## Profiling commands

- `/impulse perf toggle` - toggle Impulse runtime and world-collision profiling.
- `/impulse perf report` - print physics-step, sync, and world-collision profiling metrics.
- `/impulse perf stats` - show per-space body, awake, sleeping, joint, and contact counts.
- `/impulse perf reset` - reset profiling counters.

## Settings commands

- `/impulse settings step-mode` - show the world physics step mode.
- `/impulse settings step-mode --mode progressive_refinement|fixed|ccd` - choose adaptive refinement, fixed substeps, or world-level CCD mode.
- `/impulse settings simulation-steps` - show the configured substep count.
- `/impulse settings simulation-steps --steps <count>` - set the minimum or fixed substep count, from 1 to 16.
- `/impulse settings max-step-dt` - show the adaptive substep dt threshold.
- `/impulse settings max-step-dt --dt <seconds>` - set the adaptive substep dt threshold used by `progressive_refinement`.

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
