package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime identity index for durable UUID boundaries.
 */
public final class PhysicsIdentityIndexResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Map<UUID, Ref<PhysicsStore>> refsByUuid = new Object2ObjectOpenHashMap<>();

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

    public void clear() {
        refsByUuid.clear();
    }

    @Nonnull
    @Override
    public PhysicsIdentityIndexResource clone() {
        PhysicsIdentityIndexResource copy = new PhysicsIdentityIndexResource();
        copy.refsByUuid.putAll(refsByUuid);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsIdentityIndexResource> getResourceType() {
        return PhysicsResourceTypes.identityIndexResourceType();
    }
}
