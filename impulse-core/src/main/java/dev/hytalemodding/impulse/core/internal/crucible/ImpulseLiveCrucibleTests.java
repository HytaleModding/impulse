package dev.hytalemodding.impulse.core.internal.crucible;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.internal.physicsstore.PhysicsStoreSpaceMutations;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsSnapshotResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.BodyEntityDescriptor;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsBodyEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsEntities;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.snapshots.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components.BodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Crucible suites that exercise entity-backed bodies through Hytale's live ECS.
 */
final class ImpulseLiveCrucibleTests {

    private static final String DEFAULT_BLOCK_TYPE = "Rock_Stone";
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, DespawnComponent> DESPAWN_TYPE =
        DespawnComponent.getComponentType();

    private ImpulseLiveCrucibleTests() {
    }

    static void register(CrucibleBridge bridge, ClassLoader loader)
        throws ReflectiveOperationException {

        bridge.registerSuite(loader, ecsLiveSuite());
    }

    private static CrucibleSuite ecsLiveSuite() {
        return new CrucibleSuite(
            "impulse:ecs_live",
            "Impulse ECS Live",
            "Verifies entity-backed bodies move through the real Hytale ECS tick path",
            Set.of("live", "integration"),
            List.of(CrucibleTestCase.async("entity body falls",
                ImpulseLiveCrucibleTests::entityBodyFallsThroughEcs,
                "Entity-backed body did not fall through the live ECS tick path")));
    }

    private static CompletionStage<Boolean> entityBodyFallsThroughEcs(CrucibleContext context) {
        try {
            World world = context.world();
            Store<EntityStore> store = world.getEntityStore().getStore();
            Store<PhysicsStore> physicsStore = physicsStore(world);
            SpaceId spaceId = liveTestSpaceId(physicsStore, world);

            Vector3d visualPosition = new Vector3d(
                context.wx(0),
                context.wy(20),
                context.wz(0));
            PhysicsStoreSpaceMutations.putSpaceGravity(physicsStore,
                spaceId,
                new Vector3f(0.0f, -9.81f, 0.0f));
            UUID bodyUuid = UUID.randomUUID();
            submitLiveBody(physicsStore, spaceId, bodyUuid, visualPosition);

            Ref<EntityStore> ref = spawnLiveBlockBody(store, spaceId, bodyUuid, visualPosition);
            double startY = visualPosition.y;

            return context.waitApproxTicksOnWorld(40).thenApply(ignored -> bodyAndEntityMovedDown(
                store,
                ref,
                bodyUuid,
                startY));
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static boolean bodyAndEntityMovedDown(Store<EntityStore> store,
        Ref<EntityStore> ref,
        UUID bodyUuid,
        double startY) {

        if (!ref.isValid()) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TRANSFORM_TYPE);
        if (transform == null) {
            return false;
        }
        double transformY = transform.getPosition().y;
        PhysicsBodySnapshot snapshot = physicsStore(store.getExternalData().getWorld())
            .getResource(PhysicsSnapshotResource.getResourceType())
            .getBody(bodyUuid);
        if (snapshot == null) {
            return false;
        }
        float bodyY = snapshot.position().y;
        return transformY < startY - 0.05 && bodyY < startY - 0.05f;
    }

    private static SpaceId liveTestSpaceId(Store<PhysicsStore> store, World world) {
        SpaceId existingSpaceId = PhysicsSpaces.spaceIds(store)
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
        if (existingSpaceId != null) {
            return existingSpaceId;
        }
        return PhysicsSpaces.create(store,
            CrucibleBackends.requireBackendId(),
            PhysicsSpaceSettings.defaults());
    }

    private static void submitLiveBody(Store<PhysicsStore> store,
        SpaceId spaceId,
        UUID bodyUuid,
        Vector3d visualPosition) {
        PhysicsThreading.requireWorldThread(store, "add Crucible live PhysicsStore body entity");
        Ref<PhysicsStore> spaceRef = PhysicsSpaces.resolveRef(store, spaceId);
        if (spaceRef == null) {
            throw new IllegalStateException("No PhysicsStore space ref for id=" + spaceId.value());
        }
        BodyEntityDescriptor descriptor = PhysicsBodyEntities.dynamicBody(
            spaceRef,
            bodyUuid,
            new Vector3f((float) visualPosition.x,
                (float) visualPosition.y,
                (float) visualPosition.z),
            PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f),
            1.0f,
            RigidBodySpawnSettings.defaults(),
            null,
            PhysicsBodyPersistenceMode.PERSISTENT);
        store.addEntity(PhysicsEntities.bodyHolder(store,
            descriptor.bodyUuid(),
            descriptor.body(),
            descriptor.dynamics(),
            descriptor.target(),
            descriptor.collider(),
            descriptor.shape(),
            descriptor.material(),
            descriptor.filter()), AddReason.SPAWN);
    }

    private static Store<PhysicsStore> physicsStore(World world) {
        return PhysicsThreading.store(world);
    }

    private static Ref<EntityStore> spawnLiveBlockBody(Store<EntityStore> store,
        SpaceId spaceId,
        UUID bodyUuid,
        Vector3d visualPosition) {

        TimeResource time = store.getResource(TimeResource.getResourceType());
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            new Vector3d(visualPosition));
        holder.removeComponent(DESPAWN_TYPE);
        holder.addComponent(BodyAttachmentComponent.getComponentType(),
            new BodyAttachmentComponent(bodyUuid,
                TransformAuthority.BODY,
                AttachmentLifecycle.EXTERNAL_ENTITY));
        holder.addComponent(ImpulseControllableComponent.getComponentType(),
            new ImpulseControllableComponent());

        return store.addEntity(holder, AddReason.SPAWN);
    }
}
