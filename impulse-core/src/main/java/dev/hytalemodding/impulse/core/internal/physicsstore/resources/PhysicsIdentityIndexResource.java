package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.resources.BackendBodyHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendJointHandle;
import dev.hytalemodding.impulse.core.internal.resources.BackendSpaceHandle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity indexes for UUID boundaries and backend handle hot paths.
 */
public final class PhysicsIdentityIndexResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Map<UUID, Ref<PhysicsStore>> refsByUuid = new Object2ObjectOpenHashMap<>();
    @Nonnull
    private final Int2ObjectOpenHashMap<Ref<PhysicsStore>> spaceRefsByHandle =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Long2ObjectOpenHashMap<Ref<PhysicsStore>> bodyRefsByHandle =
        new Long2ObjectOpenHashMap<>();
    @Nonnull
    private final Long2ObjectOpenHashMap<Ref<PhysicsStore>> jointRefsByHandle =
        new Long2ObjectOpenHashMap<>();

    public PhysicsIdentityIndexResource() {
    }

    public void putUuid(@Nonnull UUID uuid, @Nonnull Ref<PhysicsStore> ref) {
        refsByUuid.put(uuid, ref);
    }

    @Nullable
    public Ref<PhysicsStore> getByUuid(@Nonnull UUID uuid) {
        return refsByUuid.get(uuid);
    }

    public void removeUuid(@Nonnull UUID uuid, @Nonnull Ref<PhysicsStore> ref) {
        refsByUuid.remove(uuid, ref);
    }

    public void clearUuidRefs() {
        refsByUuid.clear();
    }

    public void putSpaceHandle(@Nonnull BackendSpaceHandle handle, @Nonnull Ref<PhysicsStore> ref) {
        spaceRefsByHandle.put(handle.value(), ref);
    }

    @Nullable
    public Ref<PhysicsStore> getBySpaceHandle(@Nonnull BackendSpaceHandle handle) {
        return spaceRefsByHandle.get(handle.value());
    }

    public void putBodyHandle(@Nonnull BackendBodyHandle handle, @Nonnull Ref<PhysicsStore> ref) {
        bodyRefsByHandle.put(handle.value(), ref);
    }

    @Nullable
    public Ref<PhysicsStore> getByBodyHandle(@Nonnull BackendBodyHandle handle) {
        return bodyRefsByHandle.get(handle.value());
    }

    public void putJointHandle(@Nonnull BackendJointHandle handle, @Nonnull Ref<PhysicsStore> ref) {
        jointRefsByHandle.put(handle.value(), ref);
    }

    @Nullable
    public Ref<PhysicsStore> getByJointHandle(@Nonnull BackendJointHandle handle) {
        return jointRefsByHandle.get(handle.value());
    }

    public void clear() {
        refsByUuid.clear();
        spaceRefsByHandle.clear();
        bodyRefsByHandle.clear();
        jointRefsByHandle.clear();
    }

    @Nonnull
    @Override
    public PhysicsIdentityIndexResource clone() {
        PhysicsIdentityIndexResource copy = new PhysicsIdentityIndexResource();
        copy.refsByUuid.putAll(refsByUuid);
        copy.spaceRefsByHandle.putAll(spaceRefsByHandle);
        copy.bodyRefsByHandle.putAll(bodyRefsByHandle);
        copy.jointRefsByHandle.putAll(jointRefsByHandle);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsIdentityIndexResource> getResourceType() {
        return PhysicsStoreTypes.identityIndexResourceType();
    }
}
