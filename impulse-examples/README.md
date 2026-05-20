# impulse-examples

Example plugin for exercising Impulse from Hytale commands.

Run the example mod with:

```bash
./gradlew -Dimpulse.backend=impulse:rapier runAllMods
```

Fresh worlds need an explicit default physics space before example commands that spawn into the default space:

```bash
/impulse space create --default true
```

Use `/impulse space default --space <space-id>` instead when selecting an existing space. Missing-default errors are intentional and mean this setup step has not happened yet.

## Example commands

These commands are for gameplay/API demonstrations and may use full Hytale entities intentionally. Example entities attach to registered `PhysicsBodyId`s; removing an entity no longer destroys the backend body unless a command explicitly destroys that body id.

- `/impulse-examples drop` - spawn one falling entity-owned box.
- `/impulse-examples shapes` - spawn box, sphere, capsule, cylinder, and cone examples.
- `/impulse-examples materials` - compare restitution and friction settings.
- `/impulse-examples forces` - apply central impulse, off-center impulse, torque impulse, and force.
- `/impulse-examples joints` - spawn fixed, point, hinge, slider, and spring joint examples.
- `/impulse-examples raycast` - cast a physics ray from the player view and mark the closest hit.

## Stress commands

The scalable path is `detached-view`: registered runtime-only bodies with on-demand generated attachment proxies. Entity-backed stress is diagnostic only.

- `/impulse-examples stress bodies --count <n>` - spawn detached registry bodies with on-demand visual proxies. This defaults to `--mode=detached-view`.
- `/impulse-examples stress bodies --count <n> --mode=detached` - spawn backend bodies with no Hytale visuals.
- `/impulse-examples stress bodies --count <n> --mode=entity` - spawn full Hytale entity-backed bodies for adapter overhead diagnostics.
- `/impulse-examples stress raw-bodies --count <n>` - spawn backend bodies without Hytale entities or body-id registration diagnostics.
- `/impulse-examples stress benchmark --mode raw|entity --count <n>` - spawn a repeatable benchmark grid near the player.
- `/impulse-examples stress auto-benchmark --confirm true --mode raw|detached|detached-view|detached-view-chunks|entity --count <n> --sampleTicks <n>` - run a fixed-origin benchmark and report profiling after the sample window. This defaults to `--mode=detached-view`.
- `/impulse-examples stress shapes --sets <n>` - spawn mixed box, sphere, capsule, cylinder, and cone bodies.
- `/impulse-examples stress joints --count <n>` - spawn separate fixed, point, hinge, slider, and spring rows.
- `/impulse-examples stress raycast --rays <n>` - run many raycasts and report timing.
- `/impulse-examples stress swap --cycles <n>` - repeatedly migrate the default space between Bullet and Rapier.

`auto-benchmark` is destructive: it clears existing Impulse attachment entities, visual proxies, runtime spaces, registered bodies, and control sessions, then creates a fresh benchmark space and enables profiling. Use `detached-view` for the current 10k target path, `detached` or `raw-bodies` to isolate backend physics cost, `detached-view-chunks` to diagnose chunk-retained visual loading, and `entity` only when reviewing Hytale ECS, transform sync, networking, and rendering overhead.

## Persistence commands

- `/impulse-examples persistence save --name <name>` - reports that legacy entity-backed snapshot files are unsupported after schema v3.
- `/impulse-examples persistence load --name <name> --confirm true` - reports that legacy entity-backed snapshot files are unsupported after schema v3.

Hytale world persistence now stores persistent body state at world level by `PhysicsBodyId`. Old entity-backed example snapshot files are intentionally rejected rather than migrated implicitly.
