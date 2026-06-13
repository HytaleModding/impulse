package dev.hytalemodding.impulse.core.plugin.physicsstore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Runtime-resolved PhysicsStore reference paired with its durable UUID.
 */
public final class PhysicsRef {

    @Nonnull
    private final UUID uuid;
    @Nullable
    private final Ref<PhysicsStore> ref;

    private PhysicsRef(@Nonnull UUID uuid, @Nullable Ref<PhysicsStore> ref) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.ref = ref;
    }

    @Nonnull
    public static PhysicsRef persistent(@Nonnull UUID uuid) {
        return new PhysicsRef(uuid, null);
    }

    @Nonnull
    public static PhysicsRef resolved(@Nonnull UUID uuid, @Nonnull Ref<PhysicsStore> ref) {
        return new PhysicsRef(uuid, Objects.requireNonNull(ref, "ref"));
    }

    @Nonnull
    public UUID getUuid() {
        return uuid;
    }

    @Nonnull
    public PhysicsPersistentRef toPersistentRef() {
        return new PhysicsPersistentRef(uuid);
    }

    public boolean isResolved() {
        return ref != null && ref.isValid();
    }

    @Nullable
    public Ref<PhysicsStore> getRef() {
        return ref;
    }

    @Nonnull
    public Ref<PhysicsStore> requireRef() {
        Ref<PhysicsStore> current = ref;
        if (current == null || !current.isValid()) {
            throw new IllegalStateException("PhysicsStore reference " + uuid + " is not resolved");
        }
        return current;
    }
}
