package dev.hytalemodding.impulse.core.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.control.PhysicsControlSessions;
import dev.hytalemodding.impulse.core.plugin.joint.JointKey;
import dev.hytalemodding.impulse.core.plugin.simulation.JointCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandBatch;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandHandle;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandMetadata;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandRecipe;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsCommandResult;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsOwnerTransaction;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastHitView;
import dev.hytalemodding.impulse.core.plugin.simulation.RaycastSegment;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodyCommandRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnBatchRecorder;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnTemplateRecorder;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class PluginApiBoundaryTest {
    private static final Pattern INTERNAL_REFERENCE =
            Pattern.compile("\\bdev\\.hytalemodding\\.impulse\\.core\\.internal\\.");
    private static final Pattern BACKEND_CAPABILITY_API_REFERENCE =
        Pattern.compile("\\bdev\\.hytalemodding\\.impulse\\.api\\.capability\\.");

    @Test
    void pluginApiPublicSurfaceDoesNotExposeInternalImplementationPackages() throws IOException {
        Path pluginSourceRoot = pluginSourceRoot();
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(pluginSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> collectPublicSurfaceViolations(pluginSourceRoot,
                    path,
                    violations));
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Plugin API public surface must not expose core.internal packages:\n"
                    + String.join("\n", violations));
    }

    @Test
    void pluginSourceDoesNotImportInternalImplementationPackagesWithoutExplicitBridge()
        throws IOException {
        Path pluginSourceRoot = pluginSourceRoot();
        Set<String> allowedInternalBridges = Set.of("control/PhysicsControlSessions.java");
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(pluginSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> collectInternalImportViolations(pluginSourceRoot,
                    path,
                    allowedInternalBridges,
                    violations));
        }

        assertTrue(violations.isEmpty(),
            () -> "Plugin source should not import core.internal without an explicit bridge exception:\n"
                + String.join("\n", violations));
    }

    @Test
    void pluginBodyPackageDoesNotExposeLiveRegistrationRecords() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("body/PhysicsBodyRegistration.java")),
            "Live backend body registrations belong behind owner-scoped/internal APIs");
    }

    @Test
    void pluginComponentsDoNotExposeControlSessionRuntimeState() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("components/PhysicsControlSessionComponent.java")),
            "Kinematic control session state is runtime implementation state, not plugin ABI");
    }

    @Test
    void pluginPersistenceDoesNotExposeCodecDtoHelpers() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("persistence/PersistentQuaternion.java")),
            "Codec DTO helpers should not become plugin persistence ABI");
        assertFalse(Files.exists(pluginSourceRoot().resolve("components/PersistentQuaternion.java")),
            "Component codec DTO helpers should not be duplicated as plugin ABI");
    }

    @Test
    void pluginApiDoesNotExposeBackendCapabilityTypes() throws IOException {
        Path pluginSourceRoot = pluginSourceRoot();
        List<String> violations = new ArrayList<>();

        try (var paths = Files.walk(pluginSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        if (BACKEND_CAPABILITY_API_REFERENCE.matcher(source).find()) {
                            violations.add(pluginSourceRoot.relativize(path).toString());
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to inspect " + path, exception);
                    }
                });
        }

        assertTrue(violations.isEmpty(),
            () -> "Plugin API must not expose backend capability API imports:\n"
                + String.join("\n", violations));
    }

    @Test
    void javaSourceTreeDoesNotCarryDeprecationCleanupMarkers() throws IOException {
        String deprecatedAnnotation = "@" + "Deprecated";
        String suppressDeprecation = "@SuppressWarnings(\"" + "deprecation" + "\")";
        List<String> violations = new ArrayList<>();

        for (Path root : javaSourceRoots()) {
            try (var paths = Files.walk(root)) {
                paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectDeprecationCleanupMarkers(root,
                        path,
                        deprecatedAnnotation,
                        suppressDeprecation,
                        violations));
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Remove deprecated API markers and warning suppressions instead of preserving old surfaces:\n"
                + String.join("\n", violations));
    }

    @Test
    void pluginSimulationApiDoesNotExposeLiveBackendOrHytaleTypes() {
        List<Class<?>> types = List.of(PhysicsQuery.class);

        for (Class<?> type : types) {
            for (Method method : type.getDeclaredMethods()) {
                if (!isExposed(method)) {
                    continue;
                }
                assertFalse(usesForbiddenSimulationType(method.getGenericReturnType()),
                    type.getName() + " exposes forbidden return type on " + method);
                for (Type parameterType : method.getGenericParameterTypes()) {
                    assertFalse(usesForbiddenSimulationType(parameterType),
                        type.getName() + " exposes forbidden parameter type on " + method);
                }
            }
        }
    }

    @Test
    void pluginSimulationApiDoesNotExposePackedCommandEncoding() throws ClassNotFoundException {
        assertFalse(isPublicPluginClass("simulation.PhysicsCommandOperations"),
            "Packed command operations are executor encoding, not plugin ABI");
        assertFalse(isPublicPluginClass("simulation.RigidBodySpawnBatch"),
            "Packed spawn batches are executor encoding, not plugin ABI");
        for (Method method : PhysicsCommandBatch.class.getDeclaredMethods()) {
            if (!isExposed(method)) {
                continue;
            }
            assertNotEquals("operations", method.getName(),
                "PhysicsCommandBatch must not expose packed operation storage");
            assertFalse(method.getGenericReturnType().getTypeName().contains("PhysicsCommandOperations"),
                "PhysicsCommandBatch must not expose packed operation storage");
        }
    }

    @Test
    void pluginSimulationApiDoesNotExposePublicDispatcherExecution()
        throws IOException {
        assertFalse(isPublicPluginClass("simulation.PhysicsCommandDispatcher"),
            "PhysicsCommandDispatcher is an executor bridge and must not be public plugin API");
        for (Method method : PhysicsCommandBatch.class.getDeclaredMethods()) {
            if (isExposed(method)) {
                assertNotEquals("dispatchTo", method.getName(),
                    "PhysicsCommandBatch dispatch belongs inside core.internal.simulation");
                assertFalse(method.getName().toLowerCase().contains("execute"),
                    "PhysicsCommandBatch execution belongs inside core.internal.simulation");
            }
        }
        assertFalse(Files.readString(pluginSourceRoot().resolve("simulation/PhysicsCommandBatch.java"))
                .contains("core.internal.simulation"),
            "PhysicsCommandBatch must not import internal executor types");
        assertFalse(Files.readString(coreReadme()).contains("PhysicsCommandDispatcher"),
            "Public docs should point plugin authors at submitCommands and recorders");

        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(examplesSourceRoot())) {
            paths.filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".md"))
                .forEach(path -> {
                    try {
                        if (Files.readString(path).contains("PhysicsCommandDispatcher")) {
                            violations.add(examplesSourceRoot().relativize(path).toString());
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to inspect " + path, exception);
                    }
                });
        }
        assertTrue(violations.isEmpty(),
            () -> "Examples should not expose PhysicsCommandDispatcher as plugin authoring API:\n"
                + String.join("\n", violations));
    }

    @Test
    void pluginSimulationApiDoesNotExposeMechanicalValueCommandRecords() {
        assertFalse(isPublicPluginClass("simulation.PhysicsCommand"),
            "Plugins should author commands through submitCommands recipes, not value-command records");
        assertFalse(declaresMethod(PhysicsCommandRecorder.class, "add"),
            "PhysicsCommandRecorder should not accept mechanical value-command records");

        List<String> forbiddenFiles = List.of(
            "ActivateRigidBodyCommand.java",
            "ApplyRigidBodyForceCommand.java",
            "ApplyRigidBodyImpulseCommand.java",
            "CreateJointCommand.java",
            "DestroyJointBetweenBodiesCommand.java",
            "DestroyJointCommand.java",
            "DestroyRigidBodyCommand.java",
            "OwnerTransactionCommand.java",
            "RigidBodySpawnSpec.java",
            "SetRigidBodyTransformCommand.java",
            "SetRigidBodyTypeCommand.java",
            "SetRigidBodyVelocityCommand.java",
            "SpawnRigidBodiesCommand.java",
            "SpawnRigidBodyCommand.java"
        );
        for (String fileName : forbiddenFiles) {
            assertFalse(Files.exists(pluginSourceRoot().resolve("simulation").resolve(fileName)),
                fileName + " should not be part of the plugin command-buffer authoring API");
        }
    }

    @Test
    void commandResultsDoNotExposeLegacyStepCompletionAlias() {
        assertFalse(declaresMethod(PhysicsCommandResult.class, "appliedStepSequence"),
            "Command completion is not physics-step completion; use batch/snapshot correlation metadata");
    }

    @Test
    void commandMetadataDoesNotExposeInternalWorldEpoch() {
        assertFalse(declaresMethod(PhysicsCommandMetadata.class, "worldEpoch"),
            "Command-world epoch is an internal stale-batch guard, not public plugin metadata");
    }

    @Test
    void commandCompletionSummaryDoesNotExposeResultViewMarker() throws NoSuchMethodException {
        assertFalse(isPublicPluginClass("simulation.PhysicsCommandResultsView"),
            "Result-list optimization markers should not be public plugin ABI");
        assertTrue(isPublicPluginClass("simulation.PhysicsCommandCompletion"),
            "Command handles should expose a semantic completion summary instead of result-list markers");

        Method completionSummary = PhysicsCommandHandle.class.getMethod("completionSummary");

        assertEquals("java.util.concurrent.CompletionStage",
            completionSummary.getReturnType().getName());
    }

    @Test
    void commandSubmitUsesTypedOwnerCompletionWithoutMutationHandleBridge() throws IOException {
        String runtimeResource = Files.readString(coreSourceRoot()
            .resolve("internal/resources/PhysicsWorldRuntimeResource.java"));

        assertTrue(runtimeResource.contains("ownerGateway.enqueueCall(\"execute physics command batch\""),
            "Command submit should route the real completion value through owner routing directly");
        assertFalse(runtimeResource.contains("CompletableFuture<PhysicsCommandCompletion> completion = new CompletableFuture<>()"),
            "Command submit should not allocate a separate completion future around a mutation handle");
        assertFalse(runtimeResource.contains("completion.complete(executeCommandBatch(batch))"),
            "Command submit should not bridge command completion through a void owner mutation");
    }

    @Test
    void simulationQueriesUseTypedOwnerCompletionWithoutMutationHandleBridge() throws IOException {
        String runtimeResource = Files.readString(coreSourceRoot()
            .resolve("internal/resources/PhysicsWorldRuntimeResource.java"));

        assertTrue(runtimeResource.contains("ownerGateway.enqueueCall(\"execute physics query\""),
            "Public simulation queries should route copied results through typed owner completion");
        assertTrue(runtimeResource.contains("ownerGateway.enqueueCall(\"execute internal physics query\""),
            "Internal simulation queries should route copied results through typed owner completion");
        assertFalse(runtimeResource.contains("completion.complete(simulationExecutor.query(query))"),
            "Public queries should not bridge copied results through a void owner mutation");
        assertFalse(runtimeResource.contains("completion.complete(simulationExecutor.queryInternal(query))"),
            "Internal queries should not bridge copied results through a void owner mutation");
    }

    @Test
    void liveOwnerTransactionsAreTopLevelCommandContextEscapeHatchOnly()
        throws ClassNotFoundException, NoSuchMethodException {
        Class<?> commandContext = pluginClass("simulation.PhysicsCommandContext");

        assertFalse(declaresMethod(PhysicsCommandRecorder.class, "ownerTransaction"),
            "Generic command recorders should describe copied simulation intent only");
        assertFalse(declaresMethod(commandContext, "ownerTransaction"),
            "The live-owner escape hatch should be named explicitly");

        Method liveOwnerTransaction = commandContext.getMethod("liveOwnerTransaction",
            String.class,
            PhysicsOwnerTransaction.class);

        assertEquals(commandContext,
            liveOwnerTransaction.getReturnType(),
            "The live-owner escape hatch should stay on the top-level command authoring context");
    }

    @Test
    void liveOwnerTransactionTypesBelongToSimulationPackage() throws IOException {
        assertTrue(isPublicPluginClass("simulation.PhysicsOwnerAccess"),
            "Owner access is only public as part of the simulation command-buffer escape hatch");
        assertTrue(isPublicPluginClass("simulation.PhysicsOwnerTransaction"),
            "The live-owner escape hatch transaction type should live in the simulation package");
        assertFalse(isPublicPluginClass("execution.PhysicsOwnerAccess"),
            "Owner access should not make core.plugin.execution look like a general callback package");
        assertFalse(isPublicPluginClass("execution.PhysicsOwnerTransaction"),
            "Owner transactions should be framed by PhysicsCommandContext.liveOwnerTransaction");
        assertFalse(isPublicPluginClass("execution.PhysicsOwnerScopedMutation"),
            "Old scoped-mutation terminology should not remain as public plugin ABI");

        String commandContext = Files.readString(pluginSourceRoot()
            .resolve("simulation/PhysicsCommandContext.java"));
        assertFalse(commandContext.contains("core.plugin.execution.PhysicsOwner"),
            "PhysicsCommandContext should not import live-owner types from the execution package");
        assertTrue(commandContext.contains("PhysicsOwnerTransaction"),
            "The live-owner escape hatch should use transaction terminology");
    }

    @Test
    void asyncMutationHandleBelongsToResourcePackage() throws IOException {
        assertTrue(isPublicPluginClass("resources.PhysicsMutationHandle"),
            "Async resource mutation handles should live with PhysicsWorldResource");
        assertFalse(isPublicPluginClass("execution.PhysicsMutationHandle"),
            "core.plugin.execution should not remain as a one-type public package");

        String worldResource = Files.readString(pluginSourceRoot()
            .resolve("resources/PhysicsWorldResource.java"));
        assertFalse(worldResource.contains("core.plugin.execution.PhysicsMutationHandle"),
            "PhysicsWorldResource async methods should not expose a separate execution package");
    }

    @Test
    void physicsWorldResourceDoesNotExposeDirectOwnerCallbackSurface() {
        assertFalse(declaresMethod(PhysicsWorldResource.class, "runOnPhysicsOwner"),
            "Plugins should use copied commands/queries or the explicit command-buffer liveOwnerTransaction escape hatch");
        assertFalse(declaresMethod(PhysicsWorldResource.class, "callOnPhysicsOwner"),
            "Plugins should use copied queries instead of arbitrary live owner reads");
        assertFalse(declaresMethod(PhysicsWorldResource.class, "enqueuePhysicsMutation"),
            "Plugins should use typed async resource methods or command buffers instead of arbitrary live owner mutations");
        assertFalse(isPublicPluginClass("execution.PhysicsOwnerCallable"),
            "Unscoped live-owner callback types should not be public plugin ABI");
        assertFalse(isPublicPluginClass("execution.PhysicsOwnerMutation"),
            "Unscoped live-owner callback types should not be public plugin ABI");
        assertFalse(isPublicPluginClass("execution.PhysicsOwnerScopedCallable"),
            "Scoped live-owner reads should not be public plugin ABI");
    }

    @Test
    void runtimeResourceUsesInternalOwnerRoutingNames() throws ClassNotFoundException {
        Class<?> runtimeResource = Class.forName(
            "dev.hytalemodding.impulse.core.internal.resources.PhysicsWorldRuntimeResource");

        assertFalse(declaresMethod(runtimeResource, "runOnPhysicsOwner"),
            "Internal owner routing should no longer carry the old public-callback naming");
        assertFalse(declaresMethod(runtimeResource, "callOnPhysicsOwner"),
            "Internal owner routing should no longer carry the old public-callback naming");
        assertFalse(declaresMethod(runtimeResource, "enqueuePhysicsMutation"),
            "Internal async owner routing should be named as owner routing, not generic plugin mutation");
        assertTrue(declaresMethod(runtimeResource, "runOwnerMutation"),
            "Internal immediate mutations should use owner-routing terminology");
        assertTrue(declaresMethod(runtimeResource, "enqueueOwnerMutation"),
            "Internal queued mutations should use owner-routing terminology");
        assertTrue(declaresMethod(runtimeResource, "callOwner"),
            "Internal reads should use owner-routing terminology");
    }

    @Test
    void examplesDoNotUseInternalOrLiveOwnerEscapeHatches() throws IOException {
        Path examplesSourceRoot = examplesSourceRoot();
        List<String> violations = new ArrayList<>();
        Pattern forbidden = Pattern.compile(
            "core\\.internal|core\\.plugin\\.execution|runOnPhysicsOwner|callOnPhysicsOwner"
                + "|enqueuePhysicsMutation|liveOwnerTransaction");

        try (var paths = Files.walk(examplesSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        if (forbidden.matcher(source).find()) {
                            violations.add(examplesSourceRoot.relativize(path).toString());
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to read " + path, exception);
                    }
                });
        }

        assertTrue(violations.isEmpty(),
            () -> "Examples should stay on copied commands/queries and plugin facades:\n"
                + String.join("\n", violations));
    }

    @Test
    void examplesAuthorSimulationThroughSubmitCommandRecipes() throws IOException {
        Path examplesSourceRoot = examplesSourceRoot();
        List<String> violations = new ArrayList<>();
        Pattern manualBufferSurface =
            Pattern.compile("\\bPhysicsCommand(?:Buffer|Context)\\b|createCommandBuffer\\(");

        try (var paths = Files.walk(examplesSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        if (manualBufferSurface.matcher(source).find()) {
                            violations.add(examplesSourceRoot.relativize(path).toString());
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to read " + path, exception);
                    }
                });
        }

        assertTrue(violations.isEmpty(),
            () -> "Examples should teach lambda-first submitCommands recipes, not manual buffers:\n"
                + String.join("\n", violations));
    }

    @Test
    void positionOnlyBodyMovementIsAFirstClassRecorderOperation() throws NoSuchMethodException {
        assertEquals(PhysicsCommandRecorder.class,
            PhysicsCommandRecorder.class.getMethod("setBodyPosition",
                RigidBodyKey.class,
                float.class,
                float.class,
                float.class,
                boolean.class).getReturnType(),
            "Position-only body movement should not require a transform command that overwrites rotation");
        assertEquals(RigidBodyCommandRecorder.class,
            RigidBodyCommandRecorder.class.getMethod("setPosition",
                float.class,
                float.class,
                float.class,
                boolean.class).getReturnType(),
            "Single-body recorders should expose position-only movement");
    }

    @Test
    void spaceGravityIsAFirstClassCommandBufferOperation() throws NoSuchMethodException {
        assertEquals(PhysicsCommandRecorder.class,
            PhysicsCommandRecorder.class.getMethod("setSpaceGravity",
                dev.hytalemodding.impulse.api.SpaceId.class,
                float.class,
                float.class,
                float.class).getReturnType(),
            "Space gravity should be copied command-buffer intent, not a live owner callback");
    }

    @Test
    void templatedBulkSpawnCanRecordGeneratedKeyBitsWithoutAllocatingKeyWrappers()
        throws NoSuchMethodException {
        assertEquals(RigidBodySpawnTemplateRecorder.class,
            RigidBodySpawnTemplateRecorder.class.getMethod("body",
                long.class,
                long.class,
                float.class,
                float.class,
                float.class).getReturnType(),
            "Generated-key stress loops should not allocate a RigidBodyKey wrapper before recording");
    }

    @Test
    void genericBulkSpawnCanRecordGeneratedKeyBitsWithoutAllocatingKeyWrappers()
        throws NoSuchMethodException {
        assertEquals(RigidBodySpawnBatchRecorder.class,
            RigidBodySpawnBatchRecorder.class.getMethod("body",
                long.class,
                long.class,
                dev.hytalemodding.impulse.api.SpaceId.class,
                PhysicsShapeSpec.class,
                float.class,
                dev.hytalemodding.impulse.api.PhysicsBodyType.class,
                float.class,
                float.class,
                float.class,
                RigidBodySpawnSettings.class,
                dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind.class,
                dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode.class).getReturnType(),
            "Generated-key heterogeneous spawn loops should not allocate a RigidBodyKey wrapper before recording");
    }

    @Test
    void physicsCommandRecipesUseContextAsAuthoringSurface()
        throws ClassNotFoundException, NoSuchMethodException {
        Class<?> commandContext = pluginClass("simulation.PhysicsCommandContext");
        Method recipeMethod = PhysicsCommandRecipe.class.getMethod("record", commandContext);

        assertEquals(commandContext,
            recipeMethod.getParameterTypes()[0],
            "Recipes should expose a command authoring context, not a storage buffer");
        assertFalse(isPublicPluginClass("simulation.PhysicsCommandBuffer"),
            "Mutable command-buffer storage should stay internal");
        assertFalse(declaresMethod(PhysicsCommandRecorder.class, "compose"),
            "Recipe composition belongs on the top-level command context, not generic recorders");
        assertTrue(declaresMethod(commandContext, "compose"),
            "PhysicsCommandContext should be the named composition context");
        assertFalse(declaresMethod(commandContext, "isFrozen"),
            "Frozen storage state should not be part of the plugin authoring context");
        assertFalse(declaresMethod(commandContext, "isEmpty"),
            "Mutable-buffer storage inspection should stay internal");
    }

    @Test
    void publicCommandDocsUseContextTerminology() throws IOException {
        String readme = Files.readString(coreReadme());

        assertFalse(readme.contains("command-buffer lambda DSL"),
            "Public docs should frame plugin authoring as command-context recipes");
        assertFalse(readme.contains("## Simulation command buffers"),
            "Public docs should not present mutable-buffer terminology as the plugin model");
        assertTrue(readme.contains("## Simulation command context"),
            "Public docs should name the plugin-facing authoring context");
    }

    @Test
    void physicsWorldResourceExposesOnlyRecipeCommandSubmission() {
        assertFalse(declaresMethod(PhysicsWorldResource.class, "createCommandBuffer"),
            "Manual buffer creation is an internal recording detail; plugins should call submitCommands");
        assertTrue(declaresMethod(PhysicsWorldResource.class, "submitCommands"),
            "PhysicsWorldResource should expose recipe-based command submission");

        List<String> manualBufferMethods = new ArrayList<>();
        for (Method method : PhysicsWorldResource.class.getDeclaredMethods()) {
            if (!isExposed(method)) {
                continue;
            }
            if (method.getGenericReturnType().getTypeName().contains("PhysicsCommandBuffer")
                || method.getGenericReturnType().getTypeName().contains("PhysicsCommandContext")) {
                manualBufferMethods.add(method.toString());
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (parameterType.getName().contains("PhysicsCommandBuffer")
                    || parameterType.getName().contains("PhysicsCommandContext")) {
                    manualBufferMethods.add(method.toString());
                }
            }
        }

        assertTrue(manualBufferMethods.isEmpty(),
            () -> "PhysicsWorldResource should not expose manual command-buffer handles:\n"
                + String.join("\n", manualBufferMethods));
    }

    @Test
    void pluginSimulationSettingsExposePrimitivePresenceAccessors() throws NoSuchMethodException {
        assertEquals(boolean.class, RigidBodySpawnSettings.class.getMethod("hasFriction").getReturnType());
        assertEquals(float.class, RigidBodySpawnSettings.class.getMethod("friction").getReturnType());
        assertEquals(boolean.class, RigidBodySpawnSettings.class.getMethod("hasRestitution").getReturnType());
        assertEquals(float.class, RigidBodySpawnSettings.class.getMethod("restitution").getReturnType());
        assertEquals(boolean.class, RigidBodySpawnSettings.class.getMethod("hasLinearDamping").getReturnType());
        assertEquals(float.class, RigidBodySpawnSettings.class.getMethod("linearDamping").getReturnType());
        assertEquals(boolean.class, RigidBodySpawnSettings.class.getMethod("hasAngularDamping").getReturnType());
        assertEquals(float.class, RigidBodySpawnSettings.class.getMethod("angularDamping").getReturnType());
        assertEquals(boolean.class, RigidBodySpawnSettings.class.getMethod("hasCollisionFilter").getReturnType());
        assertEquals(int.class, RigidBodySpawnSettings.class.getMethod("collisionGroup").getReturnType());
        assertEquals(int.class, RigidBodySpawnSettings.class.getMethod("collisionMask").getReturnType());
        assertEquals(boolean.class, RigidBodySpawnSettings.class.getMethod("hasSensor").getReturnType());
        assertEquals(boolean.class, RigidBodySpawnSettings.class.getMethod("sensor").getReturnType());
    }

    @Test
    void liveBodyFactorySpawnApiIsRemovedFromPluginSurface() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("body/PhysicsBodies.java")),
            "Live body factory helpers were compatibility scaffolding; copied simulation commands are the spawn API");
        assertFalse(Files.exists(pluginSourceRoot().resolve("body/PhysicsBodyFactory.java")),
            "Plugin APIs should not expose live backend body factories");
        assertFalse(Files.exists(pluginSourceRoot().resolve("body/PhysicsBodySpawnSpec.java")),
            "Plugin APIs should not expose live backend body spawn specs");
        assertFalse(Files.exists(pluginSourceRoot().resolve("body/PhysicsBodySpawnResult.java")),
            "Body spawn result DTO only supported the removed live factory helper");
    }

    @Test
    void pluginPhysicsKeysUsePrimitiveUuidStorage() {
        assertPrimitiveUuidStorage(RigidBodyKey.class);
        assertPrimitiveUuidStorage(JointKey.class);
    }

    @Test
    void jointCommandRecorderStoresJointGeometryAsPrimitiveFields() {
        for (Field field : JointCommandRecorder.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertFalse(field.getType().getName().equals("org.joml.Vector3f"),
                "JointCommandRecorder should not retain Vector3f wrapper fields on command-recording paths");
        }
    }

    @Test
    void jointCommandExecutionUsesScalarSpaceFactories()
        throws IOException, NoSuchMethodException {
        assertScalarJointFactory("createFixedJoint", 6);
        assertScalarJointFactory("createPointJoint", 6);
        assertScalarJointFactory("createHingeJoint", 9);
        assertScalarJointFactory("createSliderJoint", 9);
        assertScalarJointFactory("createSpringJoint", 9);

        String executor = Files.readString(coreSourceRoot()
            .resolve("internal/simulation/PhysicsSimulationExecutor.java"));
        String createJoint = sourceSection(executor,
            "public void createJoint(",
            "public void destroyJoint(");

        assertFalse(createJoint.contains("vector("),
            "Command execution should pass scalar joint geometry to PhysicsSpace");
        assertFalse(executor.contains("private static Vector3f vector("),
            "Command execution should not rebuild Vector3f wrappers for joint creation");
    }

    @Test
    void physicsShapeSpecsStoreShapeGeometryAsPrimitiveFields() {
        for (Field field : PhysicsShapeSpec.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertFalse(field.getType() == Vector3f.class,
                "PhysicsShapeSpec should not retain Vector3f wrappers on command-recording paths");
        }

        PhysicsShapeSpec box = PhysicsShapeSpec.box(0.5f, 0.75f, 1.0f);
        assertEquals(0.5f, box.halfExtentX());
        assertEquals(0.75f, box.halfExtentY());
        assertEquals(1.0f, box.halfExtentZ());
    }

    @Test
    void raycastBatchSegmentsStoreGeometryAsPrimitiveFields() {
        assertPrimitiveGeometryStorage(RaycastSegment.class);
    }

    @Test
    void raycastHitViewsStoreGeometryAsPrimitiveFields() {
        assertPrimitiveGeometryStorage(RaycastHitView.class);
    }

    @Test
    void raycastBatchQueryReturnsCompactResultView() throws IOException {
        String batchQuery = Files.readString(pluginSourceRoot()
            .resolve("simulation/RaycastClosestBatchQuery.java"));

        assertTrue(isPublicPluginClass("simulation.RaycastClosestBatchResult"),
            "Batched raycasts should return a compact result view instead of one Optional per ray");
        assertTrue(batchQuery.contains("implements PhysicsQuery<RaycastClosestBatchResult>"),
            "Batched raycast queries should expose a compact copied result view");
        assertFalse(batchQuery.contains("List<Optional<RaycastHitView>>"),
            "Batched raycast queries should not require Optional allocation per ray");
    }

    @Test
    void raycastBatchExecutorDoesNotMapBackendHitOptionals() throws IOException {
        String executor = Files.readString(coreSourceRoot()
            .resolve("internal/simulation/PhysicsSimulationExecutor.java"));

        assertFalse(executor.contains(".map(this::toView)"),
            "Copied raycast execution should avoid Optional/stream mapper allocation on hit conversion");
        assertFalse(executor.contains(".map(this::toView)\n                .orElse(null)"),
            "Batch raycast execution should not allocate a second Optional wrapper for hit rays");
    }

    @Test
    void examplesPreferSimulationCommandsOverLegacyBodyFactorySpawn() throws IOException {
        Path examplesSourceRoot = examplesSourceRoot();
        List<String> violations = new ArrayList<>();
        try (var paths = Files.walk(examplesSourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        String source = Files.readString(path);
                        if (source.contains("PhysicsBodyFactory")
                            || source.contains("PhysicsBodySpawnSpec")
                            || source.contains("PhysicsBodies.spawn(")) {
                            violations.add(examplesSourceRoot.relativize(path).toString());
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException("Failed to inspect " + path, exception);
                    }
                });
        }

        assertTrue(violations.isEmpty(),
            () -> "Examples should use simulation command recipes for copied body spawns:\n"
                + String.join("\n", violations));
    }

    @Test
    void detachedStreamingBenchmarkUsesSimulationCommandsForBulkSpawn() throws IOException {
        Path source = coreSourceRoot()
            .resolve("internal/crucible/ImpulseDetachedStreamingBenchmarkCrucibleTests.java");
        String benchmark = Files.readString(source);

        assertFalse(benchmark.contains("runOnPhysicsOwner(\"spawn detached streaming benchmark bodies\""),
            "Detached streaming benchmark body creation should use copied simulation commands");
        assertTrue(benchmark.contains("spawnBodies(count,"),
            "Detached streaming benchmark should use a bulk spawn command buffer");
        assertFalse(benchmark.contains("Vector3d position = layout.position(i);"),
            "Detached streaming benchmark command-buffer spawn prep should compute primitive coordinates");
    }

    @Test
    void apiCrucibleResourceLifecycleUsesSimulationCommandsForSpawns() throws IOException {
        Path source = coreSourceRoot()
            .resolve("internal/crucible/ImpulseApiCrucibleTests.java");
        String crucible = Files.readString(source);

        assertFalse(crucible.contains("runOnPhysicsOwner(\"populate crucible physics space\""),
            "Resource lifecycle Crucible body population should use copied simulation commands");
        assertFalse(crucible.contains("callOnPhysicsOwner(\"register crucible detached body\""),
            "Resource lifecycle Crucible detached registration should use copied simulation commands");
        assertTrue(crucible.contains("submitCommands("),
            "Resource lifecycle Crucible tests should submit copied simulation commands");
    }

    @Test
    void liveCrucibleEntityBodyUsesSimulationCommandsForSpawn() throws IOException {
        Path source = coreSourceRoot()
            .resolve("internal/crucible/ImpulseLiveCrucibleTests.java");
        String crucible = Files.readString(source);

        assertFalse(crucible.contains("callOnPhysicsOwner(\"create live crucible physics body\""),
            "Live ECS Crucible body creation should use copied simulation commands");
        assertFalse(crucible.contains("callOnPhysicsOwner(\"register live crucible body\""),
            "Live ECS Crucible body registration should use copied simulation commands");
        assertTrue(crucible.contains("RigidBodyStateQuery"),
            "Live ECS Crucible should read body movement through copied state queries");
    }

    @Test
    void stressHighCountPathsUseTemplatedBulkSpawnWithoutPerBodyVectorLayout() throws IOException {
        Path stressRoot = examplesSourceRoot()
            .resolve("dev/hytalemodding/impulse/examples/commands/stress");
        String bodies = Files.readString(stressRoot.resolve("StressBodiesCommand.java"));
        String benchmark = Files.readString(stressRoot.resolve("StressBenchmarkCommand.java"));
        String raw = Files.readString(stressRoot.resolve("StressRawBodiesCommand.java"));
        Pattern templatedBulkSpawn = Pattern.compile("spawnBodies\\(count,\\s+spaceId,");

        assertTrue(templatedBulkSpawn.matcher(bodies).find(),
            "Detached stress bodies should use the templated bulk spawn overload");
        assertFalse(bodies.contains("Vector3d position = layout.position(i);"),
            "Detached stress bodies should compute primitive coordinates in the spawn loop");
        assertTrue(templatedBulkSpawn.matcher(benchmark).find(),
            "Raw stress benchmark should use the templated bulk spawn overload");
        assertFalse(benchmark.contains("Vector3d position = layout.position(i);"),
            "Raw stress benchmark should compute primitive coordinates in the spawn loop");
        assertTrue(templatedBulkSpawn.matcher(raw).find(),
            "Raw stress bodies should use the templated bulk spawn overload");
    }

    @Test
    void stressJointRowsRecordJointGeometryWithScalars() throws IOException {
        String joints = Files.readString(examplesSourceRoot()
            .resolve("dev/hytalemodding/impulse/examples/commands/stress/StressJointsCommand.java"));

        assertFalse(joints.contains("new Vector3f("),
            "Stress joint rows should use scalar joint recorder overloads instead of per-joint vectors");
        assertFalse(joints.contains("org.joml.Vector3f"),
            "Stress joint rows should not import Vector3f after switching to scalar joint recording");
    }

    @Test
    void entityBackedStressPathsBatchPhysicsSpawnsBeforeAttachingEntities() throws IOException {
        Path examplesRoot = examplesSourceRoot()
            .resolve("dev/hytalemodding/impulse/examples/commands");
        Path stressRoot = examplesRoot.resolve("stress");
        String utils = Files.readString(examplesRoot.resolve("ExamplePhysicsUtils.java"));
        String bodies = Files.readString(stressRoot.resolve("StressBodiesCommand.java"));
        String benchmark = Files.readString(stressRoot.resolve("StressBenchmarkCommand.java"));
        String joints = Files.readString(stressRoot.resolve("StressJointsCommand.java"));

        assertTrue(utils.contains("spawnBlockBodies("),
            "Examples should expose a batch helper for entity-backed body visuals");
        assertFalse(bodies.contains("ExamplePhysicsUtils.spawnBlockBody("),
            "Entity stress bodies should not submit one physics command buffer per body");
        assertFalse(benchmark.contains("ExamplePhysicsUtils.spawnBlockBody("),
            "Entity benchmark bodies should not submit one physics command buffer per body");
        assertFalse(joints.contains("ExamplePhysicsUtils.spawnBlockBody("),
            "Stress joint rows should batch body creation with joint creation");
    }

    @Test
    void demoCommandsBatchPhysicsSpawnsBeforeAttachingEntities() throws IOException {
        Path examplesRoot = examplesSourceRoot()
            .resolve("dev/hytalemodding/impulse/examples/commands");
        String utils = Files.readString(examplesRoot.resolve("ExamplePhysicsUtils.java"));
        String forces = Files.readString(examplesRoot.resolve("ForcesCommand.java"));
        String joints = Files.readString(examplesRoot.resolve("JointsCommand.java"));

        assertTrue(utils.contains("recordBlockBodySpawn("),
            "Examples should expose a helper that records attached body spawns into caller command buffers");
        assertTrue(forces.contains("recordBlockBodySpawn("),
            "Force demos should batch physics body creation with force commands");
        assertTrue(joints.contains("recordBlockBodySpawn("),
            "Joint demos should batch physics body creation with joint commands");
        assertFalse(forces.contains("ExamplePhysicsUtils.spawnBlockBody("),
            "Force demos should not submit one physics command buffer per body before applying forces");
        assertFalse(joints.contains("ExamplePhysicsUtils.spawnBlockBody("),
            "Joint demos should not submit one physics command buffer per body before creating joints");
    }

    @Test
    void grabCommandUsesCommandHandleSummaryInsteadOfResultListScan() throws IOException {
        Path examplesRoot = examplesSourceRoot()
            .resolve("dev/hytalemodding/impulse/examples/commands");
        String grab = Files.readString(examplesRoot.resolve("GrabCommand.java"));

        assertTrue(grab.contains(".firstRejected()"),
            "Grab control setup should use command-handle summary helpers");
        assertFalse(grab.contains("List<PhysicsCommandResult>"),
            "Grab control setup should not materialize command result lists just to test success");
        assertFalse(grab.contains("private static boolean allApplied("),
            "Grab control setup should not maintain a manual result-list scanner");
    }

    @Test
    void crucibleBenchmarkSpawnLoopsDoNotGenerateRandomKeysPerBody() throws IOException {
        Path crucibleRoot = coreSourceRoot().resolve("internal/crucible");
        List<String> benchmarkFiles = List.of(
            "ImpulseDetachedStreamingBenchmarkCrucibleTests.java",
            "ImpulseRapierBodyBenchmarkCrucibleTests.java");
        List<String> violations = new ArrayList<>();

        for (String benchmarkFile : benchmarkFiles) {
            String source = Files.readString(crucibleRoot.resolve(benchmarkFile));
            if (source.contains("spawns.body(RigidBodyKey.random()")) {
                violations.add(benchmarkFile);
            }
        }

        assertTrue(violations.isEmpty(),
            () -> "Benchmark spawn loops should use run-scoped generated key bits:\n"
                + String.join("\n", violations));
    }

    @Test
    void controlSessionCleanupUsesSimulationCommandsForRelease() throws IOException {
        String cleanup = Files.readString(coreSourceRoot()
            .resolve("internal/systems/step/PhysicsControlSessionCleanup.java"));

        assertFalse(cleanup.contains("runOnPhysicsOwner("),
            "Control cleanup should release stable joint/body keys through simulation commands");
        assertFalse(cleanup.contains("createCommandBuffer("),
            "Control cleanup should submit release recipes without exposing manual command buffers");
        assertTrue(cleanup.contains("submitCommands("),
            "Control cleanup should record release operations through the recipe command surface");
        assertTrue(cleanup.contains("destroyJointBetween("),
            "Control cleanup should use the command-buffer body-pair joint fallback");
    }

    @Test
    void kinematicControlAnchorUpdatesUseSimulationCommands() throws IOException {
        String controlSystem = Files.readString(coreSourceRoot()
            .resolve("internal/systems/step/PhysicsKinematicControlSystem.java"));

        assertFalse(controlSystem.contains("runOnPhysicsOwner(\"apply kinematic control anchor\""),
            "Kinematic control anchor movement should be copied into simulation commands");
        assertTrue(controlSystem.contains("setBodyPosition("),
            "Kinematic control should move anchors without overwriting their rotation");
    }

    @Test
    void visualOcclusionUsesCopiedRaycastQueries() throws IOException {
        String visualSystem = Files.readString(coreSourceRoot()
            .resolve("internal/systems/visual/PhysicsDetachedVisualMaterializationSystem.java"))
            + Files.readString(coreSourceRoot()
                .resolve("internal/systems/visual/DetachedVisualOcclusion.java"));

        assertFalse(visualSystem.contains("callOnPhysicsOwner(\"raycast visual occlusion\""),
            "Visual occlusion should use copied raycast query results, not live owner callbacks");
        assertTrue(visualSystem.contains("new RaycastClosestQuery("),
            "Visual occlusion should use the copied raycast query API");
        assertTrue(visualSystem.contains(".bodyKey()"),
            "Visual occlusion should compare copied raycast body keys");
    }

    @Test
    void debugOverlayCapturesContactsAndJointsThroughCopiedQueries() throws IOException, ClassNotFoundException {
        String debugSystem = Files.readString(coreSourceRoot()
            .resolve("internal/systems/debug/PhysicsDebugSystem.java"));

        assertFalse(debugSystem.contains("callOnPhysicsOwner(\"capture physics debug contacts\""),
            "Debug contact capture should use copied simulation query results");
        assertFalse(debugSystem.contains("callOnPhysicsOwner(\"capture physics debug joints\""),
            "Debug joint capture should use copied simulation query results");
        assertTrue(debugSystem.contains("new PhysicsDebugContactsQuery("),
            "Debug contact capture should request copied contact debug views");
        assertTrue(debugSystem.contains("new PhysicsDebugJointsQuery("),
            "Debug joint capture should request copied joint debug views");
        assertFalse(isPublicPluginClass("simulation.PhysicsDebugContactsQuery"),
            "Renderer-oriented debug queries should not become public plugin ABI");
        assertFalse(isPublicPluginClass("simulation.PhysicsDebugJointsQuery"),
            "Renderer-oriented debug queries should not become public plugin ABI");
        assertPrimitiveGeometryStorage(Class.forName(
            "dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugContactView"));
        assertPrimitiveGeometryStorage(Class.forName(
            "dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugJointView"));
    }

    @Test
    void liveCrucibleConfiguresGravityThroughSimulationCommands() throws IOException {
        String liveCrucible = Files.readString(coreSourceRoot()
            .resolve("internal/crucible/ImpulseLiveCrucibleTests.java"));

        assertFalse(liveCrucible.contains("runOnPhysicsOwner(\"configure live crucible gravity\""),
            "Live Crucible gravity setup should use copied simulation commands");
        assertTrue(liveCrucible.contains("setSpaceGravity("),
            "Live Crucible gravity setup should use the space-gravity command");
    }

    @Test
    void rapierBodyBenchmarkPreparesBodiesThroughSimulationCommands() throws IOException {
        String benchmark = Files.readString(coreSourceRoot()
            .resolve("internal/crucible/ImpulseRapierBodyBenchmarkCrucibleTests.java"));

        assertFalse(benchmark.contains("runOnPhysicsOwner(\"prepare rapier body benchmark space\""),
            "Rapier body benchmark setup should use copied simulation commands");
        assertTrue(benchmark.contains("submitCommands("),
            "Rapier body benchmark setup should submit copied command-buffer work");
        assertTrue(benchmark.contains("spawnBodies(matrixCase.count(),"),
            "Rapier body benchmark should use templated bulk spawn for benchmark bodies");
    }

    @Test
    void rapierBodyBenchmarkCollectsStatsThroughAggregateQuery() throws IOException {
        String benchmark = Files.readString(coreSourceRoot()
            .resolve("internal/crucible/ImpulseRapierBodyBenchmarkCrucibleTests.java"));

        assertFalse(benchmark.contains("callOnPhysicsOwner(\"collect rapier body benchmark space stats\""),
            "Rapier body benchmark stats should use one copied aggregate query");
        assertTrue(benchmark.contains("new BenchmarkSpaceStatsQuery("),
            "Rapier body benchmark stats should use the copied aggregate stats query");
        assertFalse(isPublicPluginClass("simulation.BenchmarkSpaceStatsQuery"),
            "Benchmark-only aggregate queries should not become public plugin ABI");
    }

    @Test
    void detachedStreamingBenchmarkUsesAggregateQueriesForPrewarmAndStats()
        throws IOException, ClassNotFoundException {
        String benchmark = Files.readString(coreSourceRoot()
            .resolve("internal/crucible/ImpulseDetachedStreamingBenchmarkCrucibleTests.java"));

        assertFalse(benchmark.contains("callOnPhysicsOwner(\"prewarm detached streaming benchmark collision\""),
            "Detached benchmark collision prewarm should use one copied aggregate operation");
        assertFalse(benchmark.contains("callOnPhysicsOwner(\"collect detached streaming benchmark space stats\""),
            "Detached benchmark stats should use one copied aggregate query");
        assertTrue(benchmark.contains("new WorldCollisionPrewarmEnvelopeQuery("),
            "Detached benchmark prewarm should use the copied scalar-envelope prewarm query");
        assertTrue(benchmark.contains("new BenchmarkSpaceStatsQuery("),
            "Detached benchmark stats should use the copied aggregate stats query");
        assertFalse(benchmark.contains("RigidBodyStateQuery"),
            "Benchmark stats must not fan out into per-body state queries");
        assertFalse(isPublicPluginClass("simulation.WorldCollisionPrewarmEnvelopeQuery"),
            "Benchmark-only prewarm queries should not become public plugin ABI");
        assertPrimitiveGeometryStorage(Class.forName(
            "dev.hytalemodding.impulse.core.internal.simulation.BenchmarkSpaceStatsView"));
        assertPrimitiveGeometryStorage(Class.forName(
            "dev.hytalemodding.impulse.core.internal.simulation.WorldCollisionPrewarmEnvelopeQuery"));
    }

    @Test
    void pluginControlDoesNotExposeServiceLayer() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("control/PhysicsControlSessionService.java")),
            "Control sessions should use the public helper directly");
    }

    @Test
    void pluginControlDoesNotExposeDedicatedResource() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("control/PhysicsControlResource.java")),
            "Control sessions do not need a Hytale resource when the helper has no resource state");
    }

    @Test
    void pluginControlExposesStatelessHelper() {
        assertTrue(Files.exists(pluginSourceRoot().resolve("control/PhysicsControlSessions.java")),
            "Control sessions should be managed by a stateless public helper");
    }

    @Test
    void pluginControlSessionsDoNotExposeRawJointHandles() {
        for (Method method : PhysicsControlSessions.class.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertFalse(parameterType == PhysicsJoint.class,
                    "Control-session API should use JointKey, not raw PhysicsJoint handles");
            }
        }
    }

    @Test
    void pluginControlDoesNotExposeStaticConvenienceFacade() {
        assertFalse(Files.exists(pluginSourceRoot().resolve("control/PhysicsControls.java")),
            "Use the scoped PhysicsControlSessions helper instead of a vague control facade");
    }

    @Test
    void physicsWorldResourceDoesNotOwnControlSessionLifecycle() throws IOException {
        String worldResource =
            Files.readString(pluginSourceRoot().resolve("resources/PhysicsWorldResource.java"));
        assertFalse(worldResource.contains("ControlSession"),
            "Control-session lifecycle belongs to the control helper, not PhysicsWorldResource");
    }

    @Test
    void physicsWorldResourceDoesNotExposeControlledBodyMutation() throws IOException {
        String worldResource =
            Files.readString(pluginSourceRoot().resolve("resources/PhysicsWorldResource.java"));
        assertFalse(worldResource.contains("markBodyControlled"),
            "Controlled-body flags are internal control runtime state, not plugin ABI");
        assertFalse(worldResource.contains("clearControlledBody"),
            "Controlled-body flags are internal control runtime state, not plugin ABI");
    }

    private static Path pluginSourceRoot() {
        Path moduleRelative = Path.of("src/main/java/dev/hytalemodding/impulse/core/plugin");
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("impulse-core/src/main/java/dev/hytalemodding/impulse/core/plugin");
    }

    private static Path coreSourceRoot() {
        Path moduleRelative = Path.of("src/main/java/dev/hytalemodding/impulse/core");
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("impulse-core/src/main/java/dev/hytalemodding/impulse/core");
    }

    private static Path examplesSourceRoot() {
        Path rootRelative = Path.of("impulse-examples/src/main/java");
        if (Files.isDirectory(rootRelative)) {
            return rootRelative;
        }
        Path siblingRelative = Path.of("../impulse-examples/src/main/java");
        if (Files.isDirectory(siblingRelative)) {
            return siblingRelative;
        }
        throw new IllegalStateException("Cannot resolve impulse-examples source root");
    }

    private static Path coreReadme() {
        Path moduleRelative = Path.of("README.md");
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("impulse-core/README.md");
    }

    private static List<Path> javaSourceRoots() {
        List<Path> roots = new ArrayList<>();
        addIfDirectory(roots, Path.of("src/main/java"));
        addIfDirectory(roots, Path.of("src/test/java"));
        addIfDirectory(roots, Path.of("impulse-api/src/main/java"));
        addIfDirectory(roots, Path.of("impulse-api/src/test/java"));
        addIfDirectory(roots, Path.of("impulse-bullet/src/main/java"));
        addIfDirectory(roots, Path.of("impulse-core/src/main/java"));
        addIfDirectory(roots, Path.of("impulse-core/src/test/java"));
        addIfDirectory(roots, Path.of("impulse-examples/src/main/java"));
        addIfDirectory(roots, Path.of("impulse-rapier/src/main/java"));
        addIfDirectory(roots, Path.of("../impulse-api/src/main/java"));
        addIfDirectory(roots, Path.of("../impulse-api/src/test/java"));
        addIfDirectory(roots, Path.of("../impulse-bullet/src/main/java"));
        addIfDirectory(roots, Path.of("../impulse-examples/src/main/java"));
        addIfDirectory(roots, Path.of("../impulse-rapier/src/main/java"));
        return roots;
    }

    private static void addIfDirectory(List<Path> roots, Path path) {
        if (Files.isDirectory(path)) {
            roots.add(path);
        }
    }

    private static boolean usesForbiddenSimulationType(Type type) {
        if (type instanceof Class<?> clazz) {
            String name = clazz.getName();
            return PhysicsBody.class.isAssignableFrom(clazz)
                || PhysicsSpace.class.isAssignableFrom(clazz)
                || PhysicsJoint.class.isAssignableFrom(clazz)
                || name.equals("com.hypixel.hytale.component.Store")
                || name.equals("com.hypixel.hytale.component.CommandBuffer")
                || name.equals("com.hypixel.hytale.server.core.universe.world.storage.EntityStore");
        }
        return false;
    }

    private static void assertPrimitiveUuidStorage(Class<?> type) {
        int longFields = 0;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertFalse(field.getType() == java.util.UUID.class,
                type.getSimpleName() + " should not retain UUID wrapper fields on hot identity paths");
            if (field.getType() == long.class) {
                longFields++;
            }
        }
        assertEquals(2,
            longFields,
            type.getSimpleName() + " should store UUID identity as two primitive long fields");
    }

    private static void assertPrimitiveGeometryStorage(Class<?> type) {
        int floatFields = 0;
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            assertFalse(field.getType() == Vector3f.class,
                type.getSimpleName() + " should not retain Vector3f wrappers on copied query paths");
            assertFalse(field.getType().getName().equals("org.joml.Vector3d"),
                type.getSimpleName() + " should not retain Vector3d wrappers on copied query paths");
            if (field.getType() == float.class) {
                floatFields++;
            }
        }
        assertTrue(floatFields > 0,
            type.getSimpleName() + " should store copied geometry as primitive float fields");
    }

    private static void assertScalarJointFactory(String methodName, int floatCount)
        throws NoSuchMethodException {
        Class<?>[] parameterTypes = new Class<?>[2 + floatCount];
        parameterTypes[0] = PhysicsBody.class;
        parameterTypes[1] = PhysicsBody.class;
        for (int index = 2; index < parameterTypes.length; index++) {
            parameterTypes[index] = float.class;
        }
        assertEquals(PhysicsJoint.class,
            PhysicsSpace.class.getMethod(methodName, parameterTypes).getReturnType(),
            methodName + " should expose a scalar overload for command execution");
    }

    private static String sourceSection(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0, "Missing source start marker: " + startMarker);
        assertTrue(end > start, "Missing source end marker: " + endMarker);
        return source.substring(start, end);
    }

    private static void collectDeprecationCleanupMarkers(Path root,
        Path source,
        String deprecatedAnnotation,
        String suppressDeprecation,
        List<String> violations) {
        try {
            String content = Files.readString(source);
            if (content.contains(deprecatedAnnotation)) {
                violations.add(root.relativize(source) + " contains " + deprecatedAnnotation);
            }
            if (content.contains(suppressDeprecation)) {
                violations.add(root.relativize(source) + " suppresses deprecation warnings");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect " + source, exception);
        }
    }

    private static void collectPublicSurfaceViolations(Path root,
        Path source,
        List<String> violations) {
        String fileName = source.getFileName().toString();
        if ("package-info.java".equals(fileName) || "module-info.java".equals(fileName)) {
            return;
        }

        Class<?> type = loadPluginClass(root, source);
        for (Method method : type.getDeclaredMethods()) {
            if (isExposed(method)) {
                collectTypeViolation(type, method, "return", method.getGenericReturnType(), violations);
                for (Type parameterType : method.getGenericParameterTypes()) {
                    collectTypeViolation(type, method, "parameter", parameterType, violations);
                }
            }
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (isExposed(constructor)) {
                for (Type parameterType : constructor.getGenericParameterTypes()) {
                    collectTypeViolation(type, constructor, "constructor parameter", parameterType, violations);
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (isExposed(field)) {
                collectTypeViolation(type, field, "field", field.getGenericType(), violations);
            }
        }
    }

    private static Class<?> loadPluginClass(Path root, Path source) {
        String relativeName = root.relativize(source).toString()
            .replace('/', '.')
            .replace('\\', '.')
            .replaceAll("\\.java$", "");
        String className = "dev.hytalemodding.impulse.core.plugin." + relativeName;
        try {
            return Class.forName(className, false, PluginApiBoundaryTest.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to load " + className, exception);
        }
    }

    private static void collectInternalImportViolations(Path root,
        Path source,
        Set<String> allowedInternalBridges,
        List<String> violations) {
        try {
            String relative = root.relativize(source).toString().replace('\\', '/');
            if (allowedInternalBridges.contains(relative)) {
                return;
            }
            String content = Files.readString(source);
            if (content.contains("import dev.hytalemodding.impulse.core.internal.")) {
                violations.add(relative);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to inspect " + source, exception);
        }
    }

    private static boolean isPublicPluginClass(String relativeClassName) {
        try {
            Class<?> type = pluginClass(relativeClassName);
            return Modifier.isPublic(type.getModifiers());
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private static Class<?> pluginClass(String relativeClassName) throws ClassNotFoundException {
        return Class.forName("dev.hytalemodding.impulse.core.plugin." + relativeClassName,
            false,
            PluginApiBoundaryTest.class.getClassLoader());
    }

    private static boolean declaresMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExposed(Member member) {
        int modifiers = member.getModifiers();
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void collectTypeViolation(Class<?> owner,
        Member member,
        String role,
        Type type,
        List<String> violations) {
        String typeName = type.getTypeName();
        if (INTERNAL_REFERENCE.matcher(typeName).find()) {
            violations.add(owner.getName() + "#" + member.getName() + " " + role + ": " + typeName);
        }
    }
}
