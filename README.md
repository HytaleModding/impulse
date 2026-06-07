# Impulse

Impulse is a physics framework for Hytale that connects Hytale ECS worlds to pluggable physics engines.

## Modules

Impulse codebase is divided as follows:

- **impulse-core** - Hytale ECS integration and backend communication.
- **impulse-api** - backend-agnostic API layer and contracts.
- **impulse-native-loader** - legacy loader for PhysicsBackend
- **impulse-examples** - example plugins to understand the framework usage.
  
Official physics backend implementations:
- **impulse-bullet** - Libbulletjme backend implementation.
- **impulse-rapier** - Rapier backend with a small Rust/JNI native shim.

### Architecture

<details>
<summary>High level architecture diagram</summary>

```mermaid id="impulse-overview-cycle"
flowchart TB
    Worlds["Hytale ECS worlds\nWorld A / World B / ... / World N"]

    subgraph Examples["impulse-examples / plugin usage"]
        direction TB

        Intents["Physics Intent Components\n(WIP)"]
        ECSSystem["IntentToCommandRecorderSystem\n(WIP)"]
        Direct["Direct command calls"]
        Commands["commands / queries"]

        Intents --> ECSSystem --> Commands
        Direct --> Commands
    end

    subgraph CoreWorld["impulse-core: per-world runtime"]
        direction TB

        Plugin["Plugin API package"]

        Modules["Internal modules\n- Hytale modules substitution (WIP)\n- World collision module\n- Control session module"]

        Requests["physics requests"]
        Ordering["mutations + step request ordering"]
        LaneBuild["owner-lane work units\ncomputed per world"]

        Plugin --> Requests
        Modules --> Requests
        Requests --> Ordering
        Ordering --> LaneBuild
    end

    subgraph OwnerQueues["owner-lane queues"]
        direction LR

        QueueA["World A\nowner-lane queue"]
        QueueB["World B\nowner-lane queue"]
        QueueN["World N\nowner-lane queue"]
    end

    subgraph SharedCore["impulse-core: shared execution layer"]
        direction TB

        Scheduler["thread pool scheduler\nselects ready owner lanes"]
        Workers["worker threads\nexecute owner lanes"]
        Dispatch["backend dispatch"]

        Scheduler --> Workers --> Dispatch
    end

    subgraph API["impulse-api"]
        direction TB

        Current["PhysicsBackend"]
        WIP["Runtime / Provider"]

        Current ~~~ WIP
    end

    subgraph Backends["backends"]
        direction TB

        Java["Java engines"]
        Native["native engines"]
        Bridge["impulse-native-bridge\nFFM (WIP) or JNI"]
        Active["active backend instances\nper physics space"]

        Native --> Bridge
        Java --> Active
        Bridge --> Active
    end

    Step["simulation / mutation\nper physics space"]

    subgraph Publication["impulse-core: publication layer"]
        direction TB
        Router["snapshot + event publication"]
    end

    Worlds --> Intents
    Worlds --> Direct

    Commands ----> Plugin

    LaneBuild --> QueueA
    LaneBuild --> QueueB
    LaneBuild --> QueueN

    QueueA ----> Scheduler
    QueueB ----> Scheduler
    QueueN ----> Scheduler

    Dispatch ----> Current
    Dispatch -.-> WIP

    Current ----> Active
    WIP -.-> Active

    Active ----> Step
    Step --> Router
    Router --> Worlds
```

</details>

<details>
<summary>Snapshot and Events publication</summary>

```mermaid
flowchart LR
    subgraph ActiveBackends["active backend space instances"]
        direction TB
        BackendSpaceA["Backend space A"]
        BackendSpaceB["Backend space B"]
        BackendSpaceN["Backend space N"]
    end

    BackendRequests --> BackendSpaceA
    BackendRequests --> BackendSpaceB
    BackendRequests --> BackendSpaceN

    BackendSpaceA --> StepA["step / mutate"]
    BackendSpaceB --> StepB["step / mutate"]
    BackendSpaceN --> StepN["step / mutate"]

    StepA --> PublishedState["published body / joint state snapshots"]
    StepB --> PublishedState
    StepN --> PublishedState

    StepA --> PublishedEvents["published physics events"]
    StepB --> PublishedEvents
    StepN --> PublishedEvents

    PublishedState --> CorePublication["impulse-core publication layer"]
    PublishedEvents --> CorePublication

    CorePublication --> ECSUpdate["Hytale ECS update systems"]
    ECSUpdate --> TransformComponents["TransformComponent updates"]
    ECSUpdate --> GameplaySystems["gameplay / collision / control systems"]
```

</details>

For detailed explainations read each module's README.md and code documentation

## Getting started

You can start a debug server with all the example mods and backend jars by running:

```bash
./gradlew runAllMods
```

### Backend Provider Jars

Backend jars are Java service-provider jars. Impulse discovers `PhysicsBackend` providers from jars anywhere under the configured Hytale `mods` directories.

When multiple backend jars are installed, create spaces with an explicit backend:

```bash
/impulse space create --backend=impulse:rapier
```

The Rapier backend needs a Rust toolchain to build its native library. If `cargo` is available, `:impulse-rapier:processResources` builds and packages the current build platform native library automatically. You can also force native compilation with:

```bash
./gradlew :impulse-rapier:build -PbuildRapierNative=true
```

It also supports SIMD optimizations that can be enabled using:

```bash
./gradlew -PrapierNativeFeatures=rapier-simd-stable runAllMods
```

## Testing

Impulse has a dedicated headless/serverless test lane that does not boot the Hytale server or example runtime:

```bash
./gradlew headlessTest
```

Crucible in-game tests are also provided. Run them in game with:

```
/crucible run
```

## Native Binary Notice

Backend provider artifacts may include third-party native binaries so Impulse can load the
backend at runtime. These artifacts are convenience packages for Impulse plugins; they are not
the official upstream distribution channel for those native libraries. Download standalone
Bullet/Libbulletjme or Rapier binaries from their upstream projects instead.

## Code style

The project uses Google Java Style with K&R braces and 4 spaces indentation. 
See [.editorconfig](.editorconfig) for the full formatting configuration.

## License

The Impulse project follows the [Apache License 2.0](LICENSE) license. Third-party licenses are under [licenses/](licenses).
