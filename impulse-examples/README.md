# impulse-examples

Example plugin for exercising Impulse from Hytale commands.

Run the example mod with:

```bash
./gradlew -Dimpulse.backend=impulse:rapier runAllMods
```

Fresh worlds need an explicit default physics space before example commands that spawn into the default space:

```bash
/impulse space create --default=true
```

Use `/impulse space default --space=<space-id>` instead when selecting an existing space. Missing-default errors are intentional and mean this setup step has not happened yet.

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

- `/impulse-examples stress bodies --count=<n>` - spawn detached registry bodies with on-demand visual proxies. This defaults to `--mode=detached-view`.
- `/impulse-examples stress bodies --count=1000 --visibility=cone` - require detached-view proxies to pass range and view-cone interest checks.
- `/impulse-examples stress bodies --count=1000 --collisions=body` - allow detached bodies to collide with terrain and other dynamic bodies. The default `world` policy collides with terrain only.
- `/impulse-examples stress bodies --count=1000 --visualRadius=64 --visualDematerializeRadius=80` - tune proxy materialization and hysteresis radii.
- `/impulse-examples stress bodies --count=1000 --visualSpawnRate=64 --visualCap=500` - tune progressive proxy spawn budget and materialized proxy cap.
- `/impulse-examples stress bodies --count=1000 --visualPrediction=true --visualSmoothing=false` - tune visual snapshot prediction and smoothing.
- `/impulse-examples stress bodies --count=1000 --collisionLod=true` - enable distance collision LOD for default dynamic-body filters during the stress run.
- `/impulse-examples stress bodies --count=<n> --mode=detached` - spawn backend bodies with no Hytale visuals.
- `/impulse-examples stress bodies --count=<n> --mode=entity` - spawn full Hytale entity-backed bodies for adapter overhead diagnostics.
- `/impulse-examples stress raw-bodies --count=<n>` - spawn backend bodies without Hytale entities or body-id registration diagnostics.
- `/impulse-examples stress benchmark --mode=raw|entity --count=<n>` - spawn a repeatable benchmark grid near the player.
- `/impulse-examples stress shapes --sets=<n>` - spawn mixed box, sphere, capsule, cylinder, and cone bodies.
- `/impulse-examples stress joints --count=<n>` - spawn separate fixed, point, hinge, slider, and spring rows.
- `/impulse-examples stress raycast --rays=<n>` - run many raycasts and report timing.

## Persistence commands

- `/impulse-examples persistence save` - force-sync current runtime spaces, persistent bodies, and persistent-body joints into Hytale world persistence.
- `/impulse-examples persistence load --confirm=true` - queue a runtime restore from Hytale world persistence; hydration systems rebuild spaces, bodies, and joints on the next tick.
- `/impulse-examples persistence status` - compare runtime counts with stored schema-v4 world persistence counts and show restore state.

Hytale world persistence stores persistent body state at world level by `PhysicsBodyId`. Example gameplay bodies spawned through the regular commands are persistent; stress detached-view bodies remain runtime-only benchmark state.
