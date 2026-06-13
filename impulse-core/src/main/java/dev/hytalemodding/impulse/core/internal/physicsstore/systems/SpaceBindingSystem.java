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
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRestoreStatusResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsRuntimeResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.components.SpaceComponent;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Binds SpaceComponent rows to backend runtime spaces.
 */
public final class SpaceBindingSystem extends TickingSystem<PhysicsStore>
    implements QuerySystem<PhysicsStore> {

    private static final Set<Dependency<PhysicsStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, IdentityIndexSystem.class),
        new SystemDependency<>(Order.BEFORE, BodyBindingSystem.class)
    );

    @Override
    public void tick(float dt, int systemIndex, @Nonnull Store<PhysicsStore> store) {
        PhysicsRestoreStatusResource restore = store.getResource(
            PhysicsRestoreStatusResource.getResourceType());
        if (restore.isFailed()) {
            return;
        }
        PhysicsRuntimeResource runtime = store.getResource(PhysicsRuntimeResource.getResourceType());
        PhysicsSpaceCompatibilityIndexResource compatibility = store.getResource(
            PhysicsSpaceCompatibilityIndexResource.getResourceType());
        PhysicsIdentityIndexResource identity = store.getResource(
            PhysicsIdentityIndexResource.getResourceType());
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> bindChunk(runtime, compatibility, identity, chunk);
        store.forEachChunk(systemIndex, collector);
    }

    private static void bindChunk(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull ArchetypeChunk<PhysicsStore> chunk) {
        for (int index = 0; index < chunk.size(); index++) {
            SpaceComponent space = chunk.getComponent(index, SpaceComponent.getComponentType());
            if (space == null) {
                continue;
            }
            UUID spaceUuid = PhysicsStoreSystemSupport.rowUuid(chunk, index);
            if (PhysicsStoreSystemSupport.isNil(spaceUuid)
                || runtime.getSpaceHandle(spaceUuid) != null) {
                continue;
            }
            bindSpace(runtime,
                compatibility,
                identity,
                chunk.getReferenceTo(index),
                spaceUuid,
                space);
        }
    }

    private static void bindSpace(@Nonnull PhysicsRuntimeResource runtime,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        @Nonnull PhysicsIdentityIndexResource identity,
        @Nonnull Ref<PhysicsStore> ref,
        @Nonnull UUID spaceUuid,
        @Nonnull SpaceComponent space) {
        BackendId backendId = space.getBackendId();
        if (backendId.value().isBlank()) {
            return;
        }
        PhysicsBackendRuntime backendRuntime = runtime.getRuntime(backendId);
        if (backendRuntime == null) {
            backendRuntime = Impulse.createRuntime(backendId);
            runtime.putRuntime(backendId, backendRuntime);
        }
        SpaceId compatibilitySpaceId = compatibility.getSpaceId(spaceUuid);
        if (compatibilitySpaceId == null) {
            compatibilitySpaceId = SpaceId.next();
        }
        SpaceId.reserveAtLeast(compatibilitySpaceId.value());
        BackendSpaceHandle handle = new BackendSpaceHandle(
            backendRuntime.createSpace(compatibilitySpaceId));
        Vector3f gravity = space.getGravity();
        backendRuntime.setGravity(handle.value(), gravity.x, gravity.y, gravity.z);
        runtime.putSpaceBinding(spaceUuid, backendId, handle);
        compatibility.putSpace(compatibilitySpaceId, spaceUuid);
        identity.putSpaceHandle(handle, ref);
    }

    @Nonnull
    @Override
    public Query<PhysicsStore> getQuery() {
        return PhysicsStoreSystemSupport.UUID_QUERY;
    }

    @Nonnull
    @Override
    public Set<Dependency<PhysicsStore>> getDependencies() {
        return DEPENDENCIES;
    }
}
