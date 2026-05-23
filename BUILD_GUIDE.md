# Build Guide

This document describes how to build the Impulse project and its native components.

## Project Structure

The Impulse project is organized into several modules:

- **impulse-api**: Core public API for physics backends
- **impulse-core**: Hytale mod implementation using the physics backends
- **impulse-bullet**: Physics backend implementation using Bullet physics
- **impulse-rapier**: Physics backend implementation using Rapier physics (with Rust natives)
- **impulse-examples**: Example mod demonstrating Impulse usage

## Building Locally

### Prerequisites

- Java 25 or later
- Gradle (included via wrapper)
- For Rapier natives: Rust toolchain (install via [rustup](https://rustup.rs/))
- For cross-platform Rapier builds: `cross` tool (install with `cargo install cross`)

### Build All Modules (without native libraries)

```bash
./gradlew build
```

### Build Core Only

```bash
./gradlew :impulse-core:build
```

### Build API Only

```bash
./gradlew :impulse-api:build
```

### Build Bullet Backend

```bash
./gradlew :impulse-bullet:build
```

This automatically includes platform-specific natives from LibBulletJME.

### Build Rapier Backend

#### Default (Linux x86_64)

```bash
./gradlew :impulse-rapier:build
```

This requires Rust and Cargo to be installed.

#### Specific Platform

To build for a specific platform, use the `rapierBuildPlatform` property:

```bash
# Linux x86_64
./gradlew :impulse-rapier:build -PbuildRapierNative=true -PrapierBuildPlatform=linux-x86_64

# Linux ARM64
./gradlew :impulse-rapier:build -PbuildRapierNative=true -PrapierBuildPlatform=linux-arm64

# Windows x86_64
./gradlew :impulse-rapier:build -PbuildRapierNative=true -PrapierBuildPlatform=windows-x86_64

# macOS x86_64
./gradlew :impulse-rapier:build -PbuildRapierNative=true -PrapierBuildPlatform=osx-x86_64

# macOS ARM64 (Apple Silicon)
./gradlew :impulse-rapier:build -PbuildRapierNative=true -PrapierBuildPlatform=osx-arm64
```

#### Cross-Compilation

For cross-compilation (e.g., building Linux ARM64 on a Linux x86_64 system), install `cross`:

```bash
cargo install cross
```

Then build with:

```bash
./gradlew :impulse-rapier:build -PbuildRapierNative=true -PrapierBuildPlatform=linux-arm64
```

### Build Debug Version of Rapier

```bash
./gradlew :impulse-rapier:build -PbuildRapierNative=true -PrapierNativeProfile=debug
```

### Run Tests

```bash
# Run all headless tests
./gradlew headlessTest

# Run tests for a specific module
./gradlew :impulse-api:test
./gradlew :impulse-bullet:test
./gradlew :impulse-rapier:test
./gradlew :impulse-core:test
```

## GitHub Workflows

The project uses several GitHub Actions workflows for automated building and releasing.

### Workflow Files

- **`headless-tests.yml`**: Runs on every push and PR, executes all tests
- **`build-core.yml`**: Builds impulse-api and impulse-core on Linux
- **`build-rapier-natives.yml`**: Builds Rapier natives for all platforms in a matrix
- **`build-bullet.yml`**: Builds Bullet backend on multiple platforms
- **`release.yml`**: Comprehensive release workflow (manual trigger or on tag)

### Build Matrix

The workflows build for the following platforms:

**Linux:**
- x86_64 (Intel/AMD)
- ARM64 (including SIMD support if available)

**Windows:**
- x86_64 (Intel/AMD)

**macOS:**
- x86_64 (Intel)
- ARM64 (Apple Silicon)

### Running Release Workflow

The release workflow can be triggered manually or automatically when pushing a tag:

**Manual trigger:**
1. Go to GitHub -> Actions -> Release Build
2. Click "Run workflow"

**Automatic trigger:**
```bash
git tag v0.1.0
git push origin v0.1.0
```

This will:
1. Build impulse-api and impulse-core on Ubuntu
2. Build Rapier natives for all 5 platform combinations
3. Build Bullet backend on Ubuntu, Windows, and macOS
4. Combine all artifacts into a release package
5. Create a GitHub Release with the package archive

### Artifact Organization

The release package is organized as:

```
Impulse-0.1.0/
  mods/
    impulse-api-*.jar
    impulse-core-*.jar
    rapier-natives/          (contains native libs for all platforms)
    bullet-builds/           (contains bullet jars from all OS builds)
  docs/
    README.md
    LICENSE
```

## Rapier Native Library Details

The Rapier backend is written in Rust and requires native compilation. The build process:

1. Downloads/patches the Rapier physics engine source
2. Compiles the JNI bindings using Cargo
3. Produces platform-specific native libraries:
   - Linux x86_64: `libimpulse_rapier.so`
   - Linux ARM64: `libimpulse_rapier.so`
   - Windows x86_64: `impulse_rapier.dll`
   - macOS x86_64: `libimpulse_rapier.dylib`
   - macOS ARM64: `libimpulse_rapier.dylib`

These are bundled into the `impulse-rapier-*.jar` and loaded at runtime via Snaploader.

## Troubleshooting

### Cargo not found

If you get "command not found: cargo", install Rust:

```bash
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source $HOME/.cargo/env
```

### Cross-compilation fails

For Linux ARM64 cross-compilation, install `cross`:

```bash
cargo install cross
```

### Out of memory during build

Increase Gradle JVM memory in `gradle.properties`:

```
org.gradle.jvmargs=-Xmx4G
```

### Snaploader extraction issues

Clear the Snaploader cache:

```bash
rm -rf ~/.snaploader
```

Then rebuild.

