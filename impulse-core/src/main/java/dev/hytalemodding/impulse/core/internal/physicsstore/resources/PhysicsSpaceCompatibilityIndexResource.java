package dev.hytalemodding.impulse.core.internal.physicsstore.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-only compatibility map for legacy SpaceId query and command boundaries.
 */
public final class PhysicsSpaceCompatibilityIndexResource implements Resource<PhysicsStore> {

    @Nonnull
    private final Int2ObjectOpenHashMap<UUID> spaceUuidsByCompatId =
        new Int2ObjectOpenHashMap<>();
    @Nonnull
    private final Object2IntOpenHashMap<UUID> compatIdsBySpaceUuid =
        new Object2IntOpenHashMap<>();

    public PhysicsSpaceCompatibilityIndexResource() {
        compatIdsBySpaceUuid.defaultReturnValue(Integer.MIN_VALUE);
    }

    public void putSpace(@Nonnull SpaceId spaceId, @Nonnull UUID spaceUuid) {
        int compatibilityId = spaceId.value();
        UUID previousUuid = spaceUuidsByCompatId.put(compatibilityId,
            Objects.requireNonNull(spaceUuid, "spaceUuid"));
        if (previousUuid != null && !previousUuid.equals(spaceUuid)) {
            compatIdsBySpaceUuid.removeInt(previousUuid);
        }
        int previousId = compatIdsBySpaceUuid.put(spaceUuid, compatibilityId);
        if (previousId != Integer.MIN_VALUE && previousId != compatibilityId) {
            spaceUuidsByCompatId.remove(previousId);
        }
    }

    public boolean hasSpace(@Nonnull SpaceId spaceId) {
        return spaceUuidsByCompatId.containsKey(spaceId.value());
    }

    @Nullable
    public UUID getSpaceUuid(@Nonnull SpaceId spaceId) {
        return spaceUuidsByCompatId.get(spaceId.value());
    }

    @Nullable
    public SpaceId getSpaceId(@Nonnull UUID spaceUuid) {
        int value = compatIdsBySpaceUuid.getInt(spaceUuid);
        return value != Integer.MIN_VALUE ? new SpaceId(value) : null;
    }

    public void removeBySpaceUuid(@Nonnull UUID spaceUuid) {
        int value = compatIdsBySpaceUuid.removeInt(spaceUuid);
        if (value != Integer.MIN_VALUE) {
            spaceUuidsByCompatId.remove(value);
        }
    }

    @Nonnull
    public Collection<SpaceId> spaceIds() {
        ArrayList<SpaceId> ids = new ArrayList<>(spaceUuidsByCompatId.size());
        spaceUuidsByCompatId.keySet().forEach((int value) -> ids.add(new SpaceId(value)));
        return ids;
    }

    public int size() {
        return spaceUuidsByCompatId.size();
    }

    public void clear() {
        spaceUuidsByCompatId.clear();
        compatIdsBySpaceUuid.clear();
    }

    @Nonnull
    @Override
    public PhysicsSpaceCompatibilityIndexResource clone() {
        PhysicsSpaceCompatibilityIndexResource copy = new PhysicsSpaceCompatibilityIndexResource();
        copy.spaceUuidsByCompatId.putAll(spaceUuidsByCompatId);
        copy.compatIdsBySpaceUuid.putAll(compatIdsBySpaceUuid);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PhysicsSpaceCompatibilityIndexResource>
        getResourceType() {
        return PhysicsStoreTypes.spaceCompatibilityIndexResourceType();
    }
}
