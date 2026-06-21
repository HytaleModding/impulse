# Impulse Jolt Backend

`impulse-jolt` is the Jolt backend provider module for Impulse.

The backend id is `impulse:jolt`. The module is discovered through the same
`PhysicsBackendRuntimeProvider` service mechanism used by the other backend provider jars.

## Native Strategy

The integration strategy is to build and package a C++ `impulse_jolt` native resource through
Gradle using CMake and the standard Impulse native resource layout:

```text
native/<os>/<arch>/<library>
```

Resource names:

- Linux: `native/linux/x86_64/libimpulse_jolt.so`
- Linux ARM64: `native/linux/arm64/libimpulse_jolt.so`
- macOS ARM64: `native/osx/arm64/libimpulse_jolt.dylib`
- Windows x86_64: `native/windows/x86_64/impulse_jolt.dll`

Gradle properties:

- `buildJoltNative`
- `joltCxx`
- `joltCmake`
- `joltCmakeGenerator`
- `joltPhysicsGitTag`
- `impulse.joltNativeResourceRoot`

The default local native build uses CMake FetchContent to build upstream Jolt Physics from the
pinned tag `v5.5.0`. `joltPhysicsGitTag` can override that tag, and
`impulse.joltNativeResourceRoot` can still supply prebuilt native resources instead of building
locally.

The Java boundary uses Java 25 Project Panama/FFM downcalls rather than JNI. The packaged native
library is expected to export these C ABI symbols:

- `impulse_jolt_create_space`
- `impulse_jolt_destroy_space`
- `impulse_jolt_step`
- `impulse_jolt_set_gravity`
- `impulse_jolt_get_gravity`
- `impulse_jolt_create_body`
- `impulse_jolt_remove_body`
- `impulse_jolt_contains_body`
- `impulse_jolt_body_snapshot`
- `impulse_jolt_set_body_transform`
- `impulse_jolt_set_body_position`
- `impulse_jolt_set_body_velocity`
- `impulse_jolt_set_body_type`
- `impulse_jolt_set_body_damping`
- `impulse_jolt_set_body_friction`
- `impulse_jolt_set_body_restitution`
- `impulse_jolt_set_body_collision_filter`
- `impulse_jolt_set_body_sensor`
- `impulse_jolt_set_body_continuous_collision`
- `impulse_jolt_is_body_continuous_collision_enabled`
- `impulse_jolt_activate_body`
- `impulse_jolt_sleep_body`
- `impulse_jolt_apply_body_impulse`
- `impulse_jolt_apply_body_force`
- `impulse_jolt_create_joint`
- `impulse_jolt_remove_joint`
- `impulse_jolt_raycast_closest`
- `impulse_jolt_raycast_all`
- `impulse_jolt_contacts`
- `impulse_jolt_contact_count`
- `impulse_jolt_body_count`
- `impulse_jolt_joint_count`

Space lifecycle, gravity, stepping, body lifecycle/mutation/snapshots, joint lifecycle, raycasts,
contact queries, body count, joint count, and runtime stats are wired through that ABI.
Java-assigned body and joint ids are stable within the runtime and map to opaque native handles
that wrap Jolt `BodyID` and constraint values. Query results map native body handles back to those
Java-assigned ids before calling Impulse sinks.
The current native implementation uses Jolt `PhysicsSystem`/`BodyInterface` for real rigid body
simulation, including broadphase, narrow phase, contact solving, gravity, forces, impulses,
activation, sensor state, motion quality, friction, restitution, raycasts, and dynamic bodies
resting on static collision. Contact queries are backed by a native Jolt `ContactListener` active
contact registry.

Contact-event, voxel terrain, and advanced capability operations still fail or return explicit
unsupported results until their native paths are implemented and tested. Jolt is staged as a backend
provider jar for explicit runtime selection, but it is not production-complete until those paths and
server runtime validation pass.

`impulse_jolt_body_snapshot` writes two output buffers:

- float buffer, 24 entries: position xyz, rotation xyzw, linear velocity xyz, angular velocity xyz,
  mass, friction, restitution, linear damping, angular damping, center-of-mass Y offset, box half
  extents xyz, radius, half height
- int buffer, 9 entries: shape type code, body type code, sleeping flag, sensor flag, collision
  group, collision mask, continuous-collision flag, has-box-half-extents flag, axis code

Raycast buffers:

- body-handle buffer: one native body handle per hit
- float buffer, 8 entries per hit: point xyz, normal xyz, fraction, distance

Contact buffers:

- body-handle buffer, 2 entries per contact: body A handle, body B handle
- float buffer, 11 entries per contact: point A xyz, point B xyz, normal B xyz, distance, impulse

The contact impulse field is currently reported as `0.0` because Jolt's contact listener does not
provide the solved impulse on the query path used here.
