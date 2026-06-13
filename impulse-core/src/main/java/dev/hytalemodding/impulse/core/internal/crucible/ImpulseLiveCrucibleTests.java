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
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.modules.control.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.physicsstore.projection.BodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.simulation.PhysicsShapeSpec;
import dev.hytalemodding.impulse.core.plugin.simulation.RigidBodySpawnSettings;
import dev.hytalemodding.impulse.core.plugin.simulation.query.RigidBodyStateQuery;
import dev.hytalemodding.impulse.core.plugin.simulation.view.RigidBodyStateView;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.joml.Vector3d;

/**
 * Crucible suites that exercise entity-backed bodies through Hytale's live ECS.
 */
final class ImpulseLiveCrucibleTests {

    private static final String DEFAULT_BLOCK_TYPE = "Rock_Stone";
    private static final ComponentType<EntityStore, TransformComponent> TRANSFORM_TYPE =
        TransformComponent.getComponentType();
    private static final ComponentType<EntityStore, DespawnComponent> DESPAWN_TYPE =
        DespawnComponent.getComponentType();
    private static final ComponentType<EntityStore, BodyAttachmentComponent> ATTACHMENT_TYPE =
        BodyAttachmentComponent.getComponentType();

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
            PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
            SpaceId spaceId = liveTestSpaceId(resource, world);

            Vector3d visualPosition = new Vector3d(
                context.wx(0),
                context.wy(20),
                context.wz(0));
            resource.submitCommands(0L,
                    commands -> commands.setSpaceGravity(spaceId, 0f, -9.81f, 0f))
                .completionSummary()
                .toCompletableFuture()
                .join();
            RigidBodyKey bodyKey = RigidBodyKey.random();
            submitLiveBody(resource, spaceId, bodyKey, visualPosition);

            Ref<EntityStore> ref = spawnLiveBlockBody(store, spaceId, bodyKey, visualPosition);
            double startY = visualPosition.y;

            return context.waitApproxTicksOnWorld(40).thenApply(ignored -> bodyAndEntityMovedDown(
                store,
                resource,
                ref,
                bodyKey,
                startY));
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static boolean bodyAndEntityMovedDown(Store<EntityStore> store,
        PhysicsWorldResource resource,
        Ref<EntityStore> ref,
        RigidBodyKey bodyKey,
        double startY) {

        if (!ref.isValid()) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TRANSFORM_TYPE);
        if (transform == null) {
            return false;
        }
        double transformY = transform.getPosition().y;
        Optional<RigidBodyStateView> state = resource.query(new RigidBodyStateQuery(bodyKey))
            .completion()
            .toCompletableFuture()
            .join();
        if (state.isEmpty()) {
            return false;
        }
        float bodyY = state.get().pose().position().y;
        return transformY < startY - 0.05 && bodyY < startY - 0.05f;
    }

    private static SpaceId liveTestSpaceId(PhysicsWorldResource resource, World world) {
        SpaceId existingSpaceId = resource.getSpaceIds()
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
        if (existingSpaceId != null) {
            return existingSpaceId;
        }
        return resource.createSpace(CrucibleBackends.requireBackendId(),
            world.getName(),
            PhysicsSpaceSettings.defaults());
    }

    private static void submitLiveBody(PhysicsWorldResource resource,
        SpaceId spaceId,
        RigidBodyKey bodyKey,
        Vector3d visualPosition) {
        resource.submitCommands(0L,
                1,
                commands -> commands.spawnBody(bodyKey, spawn -> spawn
                    .space(spaceId)
                    .shape(PhysicsShapeSpec.box(0.5f, 0.5f, 0.5f))
                    .mass(1.0f)
                    .type(PhysicsBodyType.DYNAMIC)
                    .position((float) visualPosition.x,
                        (float) visualPosition.y,
                        (float) visualPosition.z)
                    .settings(RigidBodySpawnSettings.defaults())
                    .kind(PhysicsBodyKind.BODY)
                    .persistence(PhysicsBodyPersistenceMode.PERSISTENT)))
            .firstRejected()
            .toCompletableFuture()
            .join()
            .ifPresent(result -> {
                throw new IllegalStateException("spawn live crucible body command "
                    + result.commandSequence() + " rejected: " + result.message());
            });
    }

    private static Ref<EntityStore> spawnLiveBlockBody(Store<EntityStore> store,
        SpaceId spaceId,
        RigidBodyKey bodyKey,
        Vector3d visualPosition) {

        TimeResource time = store.getResource(TimeResource.getResourceType());
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            new Vector3d(visualPosition));
        holder.removeComponent(DESPAWN_TYPE);
        holder.addComponent(ATTACHMENT_TYPE,
            new BodyAttachmentComponent(bodyKey.value(),
                spaceId,
                TransformAuthority.BODY,
                AttachmentLifecycle.EXTERNAL_ENTITY));
        holder.addComponent(ImpulseControllableComponent.getComponentType(),
            new ImpulseControllableComponent());

        return store.addEntity(holder, AddReason.SPAWN);
    }
}
