# impulse-examples

Example plugin for exercising Impulse from Hytale commands.

Run the example mod with:

```bash
./gradlew runAllMods
```

## Example commands

- `/impulse-examples drop` - spawn a falling box.
- `/impulse-examples shapes` - spawn box, sphere, capsule, cylinder, and cone bodies.
- `/impulse-examples materials` - compare restitution and friction settings.
- `/impulse-examples forces` - apply central impulse, off-center impulse, torque impulse, and force.
- `/impulse-examples joints` - spawn fixed, point, hinge, slider, and spring joint examples.
- `/impulse-examples raycast` - cast a physics ray from the player view and mark the closest hit.

## Stress commands

- `/impulse-examples stress bodies [count]` - spawn visible physics block entities.
- `/impulse-examples stress raw-bodies [count]` - spawn physics bodies without Hytale entities.
- `/impulse-examples stress shapes [sets]` - spawn mixed box, sphere, capsule, cylinder, and cone bodies.
- `/impulse-examples stress joints [count]` - spawn separate fixed, point, hinge, slider, and spring rows.
- `/impulse-examples stress raycast [rays]` - run many raycasts and report timing.
- `/impulse-examples stress swap [cycles]` - repeatedly migrate the main space between Bullet and Rapier.

Use `raw-bodies` to isolate backend physics cost. Use `bodies` for the gameplay path with Hytale
entity storage, transform sync, networking, and rendering.
