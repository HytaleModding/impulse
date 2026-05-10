# Impulse

Physics library for Hytale. 

Currently wrapping [Libbulletjme](https://github.com/stephengold/Libbulletjme) as physics backend.

## Modules

- **impulse-api** - backend-agnostic API layer and contracts.
- **impulse-bullet** - Libbulletjme backend implementation.
- **impulse-core** - Hytale ECS integration.
- **impulse-examples** - example plugins to understand library usage.

## Getting started

You can start a debug server with all the example mods by running:

```bash
./gradlew runAllMods
```

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
