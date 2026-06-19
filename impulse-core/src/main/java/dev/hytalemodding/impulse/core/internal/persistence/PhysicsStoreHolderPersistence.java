package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.BsonUtil;
import dev.hytalemodding.impulse.core.internal.modules.physicschunk.components.ChunkCollisionSourceComponent;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.components.BodyCommandComponent;
import dev.hytalemodding.impulse.core.plugin.components.BodyComponent;
import dev.hytalemodding.impulse.core.plugin.components.ColliderComponent;
import dev.hytalemodding.impulse.core.plugin.components.CollisionFilterComponent;
import dev.hytalemodding.impulse.core.plugin.components.JointComponent;
import dev.hytalemodding.impulse.core.plugin.components.MaterialComponent;
import dev.hytalemodding.impulse.core.plugin.components.ShapeComponent;
import dev.hytalemodding.impulse.core.plugin.components.SpaceComponent;
import dev.hytalemodding.impulse.core.plugin.components.TargetComponent;
import dev.hytalemodding.impulse.core.plugin.components.UuidComponent;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;

/**
 * ChunkStore-shaped holder serialization boundary for future PhysicsStore row storage.
 */
public final class PhysicsStoreHolderPersistence {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private PhysicsStoreHolderPersistence() {
    }

    @Nonnull
    public static List<Holder<PhysicsStore>> capturePersistentHolders(
        @Nonnull Store<PhysicsStore> store) {
        Capture capture = new Capture(store, snapshotBodiesByUuid(store));
        BiConsumer<ArchetypeChunk<PhysicsStore>, CommandBuffer<PhysicsStore>> collector =
            (chunk, _) -> capture.collectChunk(chunk);
        store.forEachChunk(UuidComponent.getComponentType(), collector);
        return capture.toHolders();
    }

    @Nonnull
    public static List<byte[]> capturePersistentHolderBlobs(
        @Nonnull Store<PhysicsStore> store) {
        return capturePersistentHolders(store).stream()
            .map(holder -> encodeHolder(store, holder))
            .toList();
    }

    @Nonnull
    public static byte[] encodeHolder(@Nonnull Store<PhysicsStore> store,
        @Nonnull Holder<PhysicsStore> holder) {
        BsonDocument document = store.getRegistry().serialize(
            Objects.requireNonNull(holder, "holder"));
        return BsonUtil.writeToBytes(document);
    }

    @Nonnull
    public static Holder<PhysicsStore> decodeHolder(
        @Nonnull ComponentRegistry<PhysicsStore> registry,
        @Nonnull byte[] bytes) {
        BsonDocument document = BsonUtil.readFromBytes(Objects.requireNonNull(bytes, "bytes"));
        Holder<PhysicsStore> holder = registry.deserialize(document);
        if (holder == null) {
            throw new IllegalArgumentException("PhysicsStore holder payload decoded to null");
        }
        return holder;
    }

    @Nonnull
    private static Map<UUID, PhysicsBodySnapshot> snapshotBodiesByUuid(
        @Nonnull Store<PhysicsStore> store) {
        PhysicsSnapshotResource snapshots = store.getResource(PhysicsSnapshotResource.getResourceType());
        Map<UUID, PhysicsBodySnapshot> bodies = new Object2ObjectOpenHashMap<>();
        for (PhysicsBodySnapshot body : snapshots.getLatestFrame().bodies()) {
            bodies.put(body.bodyUuid(), body);
        }
        return bodies;
    }

    private static final class Capture {

        @Nonnull
        private final Store<PhysicsStore> store;
        @Nonnull
        private final Map<UUID, PhysicsBodySnapshot> snapshotsByBodyUuid;
        @Nonnull
        private final List<Row> spaces = new ArrayList<>();
        @Nonnull
        private final List<Row> bodies = new ArrayList<>();
        @Nonnull
        private final List<Row> joints = new ArrayList<>();
        @Nonnull
        private final ObjectOpenHashSet<UUID> persistentBodyUuids = new ObjectOpenHashSet<>();

        private Capture(@Nonnull Store<PhysicsStore> store,
            @Nonnull Map<UUID, PhysicsBodySnapshot> snapshotsByBodyUuid) {
            this.store = store;
            this.snapshotsByBodyUuid = snapshotsByBodyUuid;
        }

        private void collectChunk(@Nonnull ArchetypeChunk<PhysicsStore> chunk) {
            for (int index = 0; index < chunk.size(); index++) {
                UuidComponent uuidComponent = chunk.getComponent(index,
                    UuidComponent.getComponentType());
                if (uuidComponent == null || NIL_UUID.equals(uuidComponent.getUuid())) {
                    continue;
                }
                UUID uuid = uuidComponent.getUuid();
                Ref<PhysicsStore> ref = chunk.getReferenceTo(index);
                if (chunk.getComponent(index, SpaceComponent.getComponentType()) != null) {
                    spaces.add(new Row(uuid, ref));
                }

                BodyComponent body = chunk.getComponent(index, BodyComponent.getComponentType());
                if (body != null && shouldPersistBody(chunk, index, body)) {
                    bodies.add(new Row(uuid, ref));
                    persistentBodyUuids.add(uuid);
                }

                JointComponent joint = chunk.getComponent(index, JointComponent.getComponentType());
                if (joint != null) {
                    joints.add(new Row(uuid, ref, joint));
                }
            }
        }

        @Nonnull
        private List<Holder<PhysicsStore>> toHolders() {
            List<Row> rows = new ArrayList<>(spaces.size() + bodies.size() + joints.size());
            rows.addAll(spaces);
            rows.addAll(bodies);
            for (Row joint : joints) {
                if (persistentBodyUuids.contains(joint.requiredJoint().getBodyAUuid())
                    && persistentBodyUuids.contains(joint.requiredJoint().getBodyBUuid())) {
                    rows.add(joint);
                }
            }
            rows.sort(Comparator.comparing(Row::uuid));

            List<Holder<PhysicsStore>> holders = new ArrayList<>(rows.size());
            for (Row row : rows) {
                Holder<PhysicsStore> holder = store.copySerializableEntity(row.ref());
                sanitize(holder);
                patchBodyTarget(row.uuid(), holder);
                holders.add(holder);
            }
            return List.copyOf(holders);
        }

        private void sanitize(@Nonnull Holder<PhysicsStore> holder) {
            holder.tryRemoveComponent(BodyCommandComponent.getComponentType());
            holder.tryRemoveComponent(ChunkCollisionSourceComponent.getComponentType());
        }

        private void patchBodyTarget(@Nonnull UUID rowUuid,
            @Nonnull Holder<PhysicsStore> holder) {
            if (holder.getComponent(BodyComponent.getComponentType()) == null) {
                return;
            }

            TargetComponent target = holder.getComponent(TargetComponent.getComponentType());
            if (target == null) {
                target = new TargetComponent();
            }
            PhysicsBodySnapshot snapshot = snapshotsByBodyUuid.get(rowUuid);
            if (snapshot != null) {
                target.setPosition(snapshot.position());
                target.setRotation(snapshot.rotation());
                target.setLinearVelocity(snapshot.linearVelocity());
                target.setAngularVelocity(snapshot.angularVelocity());
                target.setActivate(!snapshot.sleeping());
            } else {
                target.setActivate(target.isActive() || target.isActivate());
            }
            target.setActive(false);
            target.setTransformEnabled(true);
            target.setVelocityEnabled(true);
            holder.putComponent(TargetComponent.getComponentType(), target);
        }

        private static boolean shouldPersistBody(@Nonnull ArchetypeChunk<PhysicsStore> chunk,
            int index,
            @Nonnull BodyComponent body) {
            return body.getPersistenceMode() == PhysicsBodyPersistenceMode.PERSISTENT
                && chunk.getComponent(index, ColliderComponent.getComponentType()) != null
                && chunk.getComponent(index, ShapeComponent.getComponentType()) != null
                && chunk.getComponent(index, MaterialComponent.getComponentType()) != null
                && chunk.getComponent(index, CollisionFilterComponent.getComponentType()) != null;
        }
    }

    private record Row(@Nonnull UUID uuid,
                       @Nonnull Ref<PhysicsStore> ref,
                       @Nullable JointComponent jointComponent) {

        private Row(@Nonnull UUID uuid, @Nonnull Ref<PhysicsStore> ref) {
            this(uuid, ref, null);
        }

        @Nonnull
        private JointComponent requiredJoint() {
            if (jointComponent == null) {
                throw new IllegalStateException("Row is not a joint");
            }
            return jointComponent;
        }
    }
}
