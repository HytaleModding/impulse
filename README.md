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

## Documentation

- [API contract and backend expectations](docs/api-contract.md)
- [Rapier native bridge notes](docs/rapier-native.md)

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
