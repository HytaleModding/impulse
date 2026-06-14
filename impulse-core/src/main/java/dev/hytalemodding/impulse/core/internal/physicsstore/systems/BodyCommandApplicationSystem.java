package dev.hytalemodding.impulse.core.internal.physicsstore.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.QuerySystem;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.runtime.BackendRuntimeCodes;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource.PendingBodyOperation;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.DynamicsComponent;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Drains one-tick body command components into runtime/backend operations.
 */
public final class BodyCommandApplicationSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, BodyBindingSystem.class),
        new SystemDependency<>(Order.AFTER, TerrainColliderBindingSystem.class)
    );
    private static final Query<PhysicsStore> QUERY = BodyCommandComponent.getComponentType();

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, commandBuffer) -> applyCommands(store, runtime, restore, chunk, commandBuffer);
        store.forEachChunk(systemIndex, collector);
    }

    private static void applyCommands(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk,
        @Nonnull CommandBuffer<PhysicsStore> commandBuffer) {
        for (int index = 0; index < chunk.size(); index++) {
            BodyCommandComponent commands = chunk.getComponent(index,
                BodyCommandComponent.getComponentType());
            if (commands == null) {
                continue;
            }
            UUID bodyUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            Ref<PhysicsStore> ref = chunk.getReferenceTo(index);
            if (PhysicsStoreSystemSupport.isNil(bodyUuid)) {
                restore.recordSoftSkip("Body command row has nil UUID");
                commandBuffer.removeComponent(ref, BodyCommandComponent.getComponentType());
                continue;
            }
            for (BodyCommandComponent.Entry command : commands.entries()) {
                applyCommand(store, runtime, restore, ref, bodyUuid, command);
            }
            commandBuffer.removeComponent(ref, BodyCommandComponent.getComponentType());
        }
    }

    private static void applyCommand(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyCommandComponent.Entry command) {
        switch (command.getKind()) {
            case WAKE -> runtime.enqueuePendingBodyOperation(PendingBodyOperation.wake(bodyUuid,
                null,
                null));
            case SLEEP -> runtime.enqueuePendingBodyOperation(PendingBodyOperation.sleep(bodyUuid,
                null,
                null));
            case IMPULSE -> enqueueVector(runtime, bodyUuid, command, PendingBodyOperation.Kind.IMPULSE);
            case TORQUE_IMPULSE -> enqueueVector(runtime,
                bodyUuid,
                command,
                PendingBodyOperation.Kind.TORQUE_IMPULSE);
            case FORCE -> enqueueVector(runtime, bodyUuid, command, PendingBodyOperation.Kind.FORCE);
            case TORQUE -> enqueueVector(runtime, bodyUuid, command, PendingBodyOperation.Kind.TORQUE);
            case SET_TYPE -> applyBodyType(store, runtime, restore, ref, bodyUuid, command);
        }
    }

    private static void applyBodyType(@Nonnull Store<PhysicsStore> store,
        @Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsRestoreStatusResource restore,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyCommandComponent.Entry command) {
        DynamicsComponent dynamics = PhysicsStoreSystemSupport.component(store,
            ref,
            DynamicsComponent.getComponentType());
        DynamicsComponent updated = dynamics != null ? dynamics.clone() : new DynamicsComponent();
        updated.setBodyType(command.getBodyType());
        store.putComponent(ref, DynamicsComponent.getComponentType(), updated);

        RuntimeBodyBinding binding = runtimeBodyBinding(runtime, bodyUuid, restore, false);
        if (binding == null) {
            if (command.isActivate()) {
                runtime.enqueuePendingBodyOperation(PendingBodyOperation.wake(bodyUuid, null, null));
            }
            return;
        }
        binding.backendRuntime().setBodyType(binding.spaceHandle().value(),
            binding.bodyHandle().value(),
            BackendRuntimeCodes.bodyTypeCode(command.getBodyType()));
        updateBodyHitMetadata(runtime, binding.bodyHandle(), command.getBodyType());
        if (command.isActivate()) {
            runtime.enqueuePendingBodyOperation(PendingBodyOperation.wake(bodyUuid,
                binding.spaceHandle(),
                binding.bodyHandle()));
        }
    }

    private static void enqueueVector(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID bodyUuid,
        @Nonnull BodyCommandComponent.Entry command,
        @Nonnull PendingBodyOperation.Kind kind) {
        runtime.enqueuePendingBodyOperation(PendingBodyOperation.vector(kind,
            bodyUuid,
            null,
            null,
            command.getX(),
            command.getY(),
            command.getZ(),
            command.hasOffset(),
            command.getOffsetX(),
            command.getOffsetY(),
            command.getOffsetZ()));
    }

    @Nullable
    private static RuntimeBodyBinding runtimeBodyBinding(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull UUID bodyUuid,
        @Nonnull PhysicsRestoreStatusResource restore,
        boolean requireBound) {
        BackendBodyHandle bodyHandle = runtime.getBodyHandle(bodyUuid);
        BackendSpaceHandle spaceHandle = runtime.getBodySpaceHandle(bodyUuid);
        if (bodyHandle == null || spaceHandle == null) {
            if (requireBound) {
                restore.recordSoftSkip("Body command target is unbound: " + bodyUuid);
            }
            return null;
        }
        PhysicsBackendRuntime backendRuntime = runtimeForSpace(runtime, spaceHandle);
        if (backendRuntime == null) {
            restore.recordSoftSkip("Body command backend runtime is missing: " + bodyUuid);
            return null;
        }
        return new RuntimeBodyBinding(spaceHandle, bodyHandle, backendRuntime);
    }

    private static PhysicsBackendRuntime runtimeForSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull BackendSpaceHandle spaceHandle) {
        final PhysicsBackendRuntime[] resolved = new PhysicsBackendRuntime[1];
        runtime.forEachSpaceBinding((_, _, handle, backendRuntime) -> {
            if (handle.value() == spaceHandle.value()) {
                resolved[0] = backendRuntime;
            }
        });
        return resolved[0];
    }

    private static void updateBodyHitMetadata(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull BackendBodyHandle bodyHandle,
        @Nonnull PhysicsBodyType bodyType) {
        PhysicsRuntimeResource.BodyHitMetadata metadata = runtime.getBodyHitMetadata(bodyHandle);
        if (metadata != null) {
            runtime.putBodyHitMetadata(bodyHandle,
                metadata.bodyKey(),
                bodyType,
                metadata.shapeType());
        }
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }

    private record RuntimeBodyBinding(@Nonnull BackendSpaceHandle spaceHandle,
                                      @Nonnull BackendBodyHandle bodyHandle,
                                      @Nonnull PhysicsBackendRuntime backendRuntime) {
    }
}
