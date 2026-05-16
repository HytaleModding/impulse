# Impulse

Physics library for Hytale. 

Currently wrapping [Libbulletjme](https://github.com/stephengold/Libbulletjme) as the default physics backend, with an experimental [Rapier](https://rapier.rs/) backend available.

## Modules

- **impulse-api** - backend-agnostic API layer and contracts.
- **impulse-bullet** - Libbulletjme backend implementation.
- **impulse-rapier** - experimental Rapier backend with a small Rust/JNI native shim.
- **impulse-core** - Hytale ECS integration.
- **impulse-examples** - example plugins to understand library usage.

## Getting started

You can start a debug server with all the example mods by running:

```bash
./gradlew runAllMods
```

### WIP workaround, set PhysicsBackend for the main PhysicsSpace

Bullet remains the default backend when it is present. To run the examples with Rapier instead, select it through a system property or environment variable:

```bash
./gradlew -Dimpulse.backend=impulse:rapier runAllMods
```

or:

```bash
./gradlew -Pimpulse.backend=impulse:rapier runAllMods
```

or:

```bash
IMPULSE_BACKEND=impulse:rapier ./gradlew runAllMods
```

The Rapier backend needs a Rust toolchain to build its native library. If `cargo` is available, `:impulse-rapier:processResources` builds and packages the Linux x86_64 native library automatically. You can also force native compilation with:

```bash
./gradlew :impulse-rapier:build -PbuildRapierNative=true
```

## Runtime commands

The core plugin registers `/impulse` commands for runtime physics controls:

- `/impulse backend list` - list discovered backends and active physics spaces.
- `/impulse backend swap --backend impulse:rapier` - migrate the main physics space to Rapier.
- `/impulse backend swap --backend impulse:bullet` - migrate the main physics space back to Bullet.
- `/impulse backend swap --backend <backend-id> --space <space-id>` - migrate a specific space.
- `/impulse clean --confirm` - remove all Impulse physics entities from the current world without touching unrelated world entities.
- `/impulse settings step-mode` - show the world physics step mode.
- `/impulse settings step-mode --mode progressive_refinement|fixed|ccd` - choose adaptive refinement, fixed substeps, or world-level CCD mode.
- `/impulse settings simulation-steps` - show the configured substep count.
- `/impulse settings simulation-steps --steps <count>` - set the minimum or fixed substep count, from 1 to 16.
- `/impulse settings max-step-dt` - show the adaptive substep dt threshold.
- `/impulse settings max-step-dt --dt <seconds>` - set the adaptive substep dt threshold used by `progressive_refinement`.

Bullet is the default backend when it is available. Rapier is experimental. Runtime backend
swapping migrates supported bodies and joints, but exact solver behavior can differ between
backends.

## Debug commands

Debug controls live under `/impulse debug`:

- `/impulse debug` - show debug command usage.
- `/impulse debug toggle` - toggle Impulse debug rendering globally.
- `/impulse debug shapes` - toggle collider shape overlays.
- `/impulse debug motion` - toggle linear and angular velocity arrows.
- `/impulse debug contacts` - toggle contact point and normal overlays.
- `/impulse debug joints` - toggle joint anchor, axis, and link overlays.

Debug overlays are intentionally separate from stress commands because rendering overlays can
dominate performance measurements.

## Example commands

Run the example mod with `./gradlew runAllMods`, then use `/impulse-examples`:

- `/impulse-examples drop` - spawn a falling box.
- `/impulse-examples shapes` - spawn box, sphere, capsule, cylinder, and cone bodies.
- `/impulse-examples materials` - compare restitution and friction settings.
- `/impulse-examples forces` - apply central impulse, off-center impulse, torque impulse, and force.
- `/impulse-examples joints` - spawn fixed, point, hinge, slider, and spring joint examples.
- `/impulse-examples raycast` - cast a physics ray from the player view and mark the closest hit.

### Stress commands

Stress commands are also part of the examples plugin:

- `/impulse-examples stress bodies [count]` - spawn visible physics block entities.
- `/impulse-examples stress raw-bodies [count]` - spawn physics bodies without Hytale entities.
- `/impulse-examples stress shapes [sets]` - spawn mixed box, sphere, capsule, cylinder, and cone bodies.
- `/impulse-examples stress joints [count]` - spawn separate fixed, point, hinge, slider, and spring rows.
- `/impulse-examples stress raycast [rays]` - run many raycasts and report timing.
- `/impulse-examples stress swap [cycles]` - repeatedly migrate the main space between Bullet and Rapier.

Use `raw-bodies` to isolate backend physics cost. Use `bodies` when you want the full gameplay
path, including Hytale entity storage, transform sync, networking, and rendering.

## Code of Conduct
This project and everyone participating in it is governed by HytaleModding's 
[Code of Conduct](https://github.com/HytaleModding/site/blob/main/CODE_OF_CONDUCT.md). 
By participating, you are expected to uphold this code. 
Please report unacceptable behavior to the project maintainers.

## Code style

The project uses Google Java Style with K&R braces and 4 spaces indentation. 
See [.editorconfig](.editorconfig) for the full formatting configuration.

## License

The Impulse project follows the [MIT](LICENSE) license. Third-party licenses are under [licenses/](licenses/).
