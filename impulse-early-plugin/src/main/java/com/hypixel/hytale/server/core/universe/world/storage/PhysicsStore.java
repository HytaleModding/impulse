package com.hypixel.hytale.server.core.universe.world.storage;

import com.hypixel.hytale.codec.store.CodecKey;
import com.hypixel.hytale.codec.store.CodecStore;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.IResourceStorage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PhysicsStore {

    @Nonnull
    public static final ComponentRegistry<PhysicsStore> REGISTRY = new ComponentRegistry<>();

    @Nonnull
    public static final CodecKey<Holder<PhysicsStore>> HOLDER_CODEC_KEY =
        new CodecKey<>("PhysicsHolder");

    static {
        CodecStore.STATIC.putCodecSupplier(HOLDER_CODEC_KEY, REGISTRY::getEntityCodec);
    }

    @Nonnull
    private final World world;
    @Nonnull
    private final Map<UUID, Ref<PhysicsStore>> refsByUuid = new ConcurrentHashMap<>();
    @Nullable
    private Store<PhysicsStore> store;

    public PhysicsStore(@Nonnull World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    public synchronized void start(@Nonnull IResourceStorage resourceStorage) {
        Objects.requireNonNull(resourceStorage, "resourceStorage");
        Store<PhysicsStore> current = store;
        if (current != null && !current.isShutdown()) {
            throw new IllegalStateException("PhysicsStore is already started");
        }
        store = REGISTRY.addStore(this, resourceStorage, addedStore -> store = addedStore);
    }

    public synchronized void shutdown() {
        Store<PhysicsStore> current = requireStarted();
        if (!current.isShutdown()) {
            current.shutdown();
        }
        store = null;
        refsByUuid.clear();
    }

    @Nonnull
    public Store<PhysicsStore> getStore() {
        return requireStarted();
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    @Nullable
    public Ref<PhysicsStore> getRefFromUUID(@Nonnull UUID uuid) {
        return refsByUuid.get(Objects.requireNonNull(uuid, "uuid"));
    }

    public void putRefForUUID(@Nonnull UUID uuid, @Nonnull Ref<PhysicsStore> ref) {
        refsByUuid.put(Objects.requireNonNull(uuid, "uuid"), Objects.requireNonNull(ref, "ref"));
    }

    public void removeRefForUUID(@Nonnull UUID uuid, @Nonnull Ref<PhysicsStore> ref) {
        refsByUuid.remove(Objects.requireNonNull(uuid, "uuid"), Objects.requireNonNull(ref, "ref"));
    }

    public void clearUuidIndex() {
        refsByUuid.clear();
    }

    @Nonnull
    private synchronized Store<PhysicsStore> requireStarted() {
        Store<PhysicsStore> current = store;
        if (current == null || current.isShutdown()) {
            throw new IllegalStateException("PhysicsStore is not started; the Impulse early plugin "
                + "must transform World before core physics-store systems run");
        }
        return current;
    }
}
