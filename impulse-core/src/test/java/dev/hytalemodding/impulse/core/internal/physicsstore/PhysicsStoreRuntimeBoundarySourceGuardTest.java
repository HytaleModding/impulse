package dev.hytalemodding.impulse.core.internal.physicsstore;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PhysicsStoreRuntimeBoundarySourceGuardTest {

    @Test
    void spaceMutationRuntimeCleanupDoesNotResolveRuntimeBindingsByUuid() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/physicsstore/PhysicsStoreSpaceMutations.java"));

        assertFalse(source.contains("runtime.getSpaceHandle(spaceUuid)"));
        assertFalse(source.contains("runtime.getSpaceBackendId(spaceUuid)"));
    }

    @Test
    void staleBodyCleanupDoesNotFallbackToRuntimeUuidLookups() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/systems/StaleBodyRemovalSystem.java"));

        assertFalse(source.contains("runtime.getJointHandle(joint.jointUuid())"));
        assertFalse(source.contains("runtime.getJointSpaceHandle(joint.jointUuid())"));
        assertFalse(source.contains("runtime.getSpaceHandle(joint.spaceUuid())"));
        assertFalse(source.contains("runtime.removeBodyHandle(body.bodyUuid())"));
    }

    @Test
    void backendAccessDoesNotResolveRuntimeSpacesByUuid() throws IOException {
        String backendAccess = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/plugin/physicsstore/PhysicsBackendAccess.java"));
        String diagnostics = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/plugin/physicsstore/PhysicsDiagnostics.java"));

        assertFalse(backendAccess.contains("runtime.getSpaceHandle(spaceUuid)"));
        assertFalse(backendAccess.contains("runtime.getSpaceBackendId(spaceUuid)"));
        assertFalse(diagnostics.contains("PhysicsBackendAccess.space(runtime, spaceUuid)"));
    }

    @Test
    void completedStepPublicationIteratesRuntimeSpacesByRef() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/systems/CompletedStepPublicationSystem.java"));

        assertFalse(source.contains("runtime.forEachSpaceBinding"));
    }

    @Test
    void chunkCollisionVoxelStitchingDoesNotResolveRuntimeBindingsByUuid() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/systems/ChunkCollisionVoxelStitchingSystem.java"));

        assertFalse(source.contains("runtime.getBodyHandle(neighborUuid)"));
        assertFalse(source.contains("runtime.getBodySpaceHandle(neighborUuid)"));
    }

    @Test
    void debugQueriesDoNotResolveRuntimeSpacesByUuid() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/systems/debug/PhysicsStoreDebugQueries.java"));

        assertFalse(source.contains("runtime.getSpaceHandle(spaceUuid)"));
        assertFalse(source.contains("runtime.getSpaceBackendId(spaceUuid)"));
    }

    @Test
    void legacyWorldResourceFacadeDoesNotIterateRuntimeSpacesByUuid() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/resources/PhysicsWorldRuntimeResource.java"));

        assertFalse(source.contains("forEachSpaceBinding"));
    }

    @Test
    void legacyAuthoritativeAsyncMutationsWaitForBackendIdle() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/resources/PhysicsWorldRuntimeResource.java"));

        assertTrue(source.contains("PhysicsThreading.callWhenBackendIdleOnWorldThread(world,"
            + System.lineSeparator() + "                operation,"));
        assertFalse(source.contains("PhysicsThreading.executeOnWorldThread(world, operation, mutation)"));
    }

    @Test
    void publicWorldSettingsAsyncWaitsForBackendIdle() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/plugin/physicsstore/PhysicsWorlds.java"));

        assertTrue(source.contains("PhysicsThreading.callWhenBackendIdleOnWorldThread(world,"
            + System.lineSeparator() + "            \"queue PhysicsStore world settings update\""));
        assertFalse(source.contains("PhysicsThreading.executeOnWorldThread(world,"
            + System.lineSeparator() + "            \"queue PhysicsStore world settings update\""));
    }

    @Test
    void runtimeResourceDoesNotExposeUuidRuntimeReadApis() throws IOException {
        String source = Files.readString(Path.of(
            "src/main/java/dev/hytalemodding/impulse/core/internal/resources/PhysicsRuntimeResource.java"));

        assertFalse(source.contains("getSpaceHandle(@Nonnull UUID"));
        assertFalse(source.contains("getSpaceBackendId(@Nonnull UUID"));
        assertFalse(source.contains("getBodyHandle(@Nonnull UUID"));
        assertFalse(source.contains("getBodySpaceHandle(@Nonnull UUID"));
        assertFalse(source.contains("getJointHandle(@Nonnull UUID"));
        assertFalse(source.contains("getJointSpaceHandle(@Nonnull UUID"));
        assertFalse(source.contains("bodyUuidsForSpaceHandle"));
        assertFalse(source.contains("jointUuidsForSpaceHandle"));
        assertFalse(source.contains("forEachSpaceBinding"));
        assertFalse(source.contains("@Nullable Ref<PhysicsStore> spaceRef"));
        assertFalse(source.contains("@Nullable Ref<PhysicsStore> jointRef"));
    }
}
