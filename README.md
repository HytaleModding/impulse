# Impulse

Physics library for Hytale. 

## Modules

- **impulse-api** - backend-agnostic API layer and contracts.
- **impulse-bullet** - Libbulletjme backend implementation.
- **impulse-rapier** - Rapier backend with a small Rust/JNI native shim.
- **impulse-core** - Hytale ECS integration.
- **impulse-examples** - example plugins to understand library usage.

## Getting started

You can start a debug server with all the example mods and backend jars by running:

```bash
./gradlew runAllMods
```

Backend jars are Java service-provider jars, not Hytale plugins. Impulse discovers
`PhysicsBackend` providers from jars anywhere under the configured Hytale `mods` directories.

When multiple backend jars are installed, create spaces with an explicit backend:

```bash
/impulse space create --backend=impulse:rapier
```

The Rapier backend needs a Rust toolchain to build its native library. If `cargo` is available, `:impulse-rapier:processResources` builds and packages the current build platform native library automatically. You can also force native compilation with:

```bash
./gradlew :impulse-rapier:build -PbuildRapierNative=true
```

it also supports SIMD optimizations that can be enabled using:

```bash
./gradlew -PrapierNativeFeatures=rapier-simd-stable runAllMods
```

## Testing

Impulse has a dedicated headless/serverless test lane that does not boot the Hytale server or example runtime:

```bash
./gradlew headlessTest
```

Crucible in-game tests are also provided.

## Commands

- [`impulse-core`](impulse-core/README.md) documents `/impulse` runtime commands and local Crucible smoke.
- [`impulse-examples`](impulse-examples/README.md) documents example and stress commands.

## Code of Conduct
This project and everyone participating in it is governed by HytaleModding's 
[Code of Conduct](https://github.com/HytaleModding/site/blob/main/CODE_OF_CONDUCT.md). 
By participating, you are expected to uphold this code. 
Please report unacceptable behavior to the project maintainers.

## Code style

The project uses Google Java Style with K&R braces and 4 spaces indentation. 
See [.editorconfig](.editorconfig) for the full formatting configuration.

## License

The Impulse project follows the [Apache License 2.0](LICENSE) license. Third-party licenses are under [licenses/](licenses).
