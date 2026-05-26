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
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.plugin.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.AttachmentLifecycle;
import dev.hytalemodding.impulse.core.plugin.components.PhysicsBodyAttachmentComponent.TransformAuthority;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyKind;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyPersistenceMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.List;
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
    private static final ComponentType<EntityStore, PhysicsBodyAttachmentComponent> ATTACHMENT_TYPE =
        PhysicsBodyAttachmentComponent.getComponentType();
    private static final ComponentType<EntityStore, ImpulseControllableComponent> IMPULSE_CONTROLLABLE_TYPE =
        ImpulseControllableComponent.getComponentType();

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
            PhysicsSpace space = liveTestSpace(resource, world);

            Vector3d visualPosition = new Vector3d(
                context.wx(0),
                context.wy(20),
                context.wz(0));
            PhysicsBody body = resource.callOnPhysicsOwner("create live crucible physics body", () -> {
                space.setGravity(0f, -9.81f, 0f);
                PhysicsBody created = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
                created.setPosition((float) visualPosition.x,
                    (float) (visualPosition.y + created.getCenterOfMassOffsetY()),
                    (float) visualPosition.z);
                return created;
            });

            Ref<EntityStore> ref = spawnLiveBlockBody(store, resource, space, body, visualPosition);
            double startY = visualPosition.y;

            return context.waitApproxTicksOnWorld(40).thenApply(ignored -> bodyAndEntityMovedDown(
                store,
                resource,
                ref,
                body,
                startY));
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static boolean bodyAndEntityMovedDown(Store<EntityStore> store,
        PhysicsWorldResource resource,
        Ref<EntityStore> ref,
        PhysicsBody body,
        double startY) {

        if (!ref.isValid()) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TRANSFORM_TYPE);
        if (transform == null) {
            return false;
        }
        double transformY = transform.getPosition().y;
        float bodyY = resource.callOnPhysicsOwner("read live crucible physics body position",
            () -> body.getPosition().y - body.getCenterOfMassOffsetY());
        return transformY < startY - 0.05 && bodyY < startY - 0.05f;
    }

    private static PhysicsSpace liveTestSpace(PhysicsWorldResource resource, World world) {
        SpaceId defaultSpaceId = resource.getDefaultSpaceId();
        if (defaultSpaceId != null && resource.hasSpace(defaultSpaceId)) {
            return resource.callOnPhysicsOwner("resolve live crucible default space",
                access -> access.requireSpace(defaultSpaceId));
        }
        SpaceId spaceId = resource.createSpace(ImpulsePlugin.get().getDefaultBackendId(),
            world.getName(),
            PhysicsSpaceSettings.defaults(),
            true);
        return resource.callOnPhysicsOwner("resolve live crucible test space",
            access -> access.requireSpace(spaceId));
    }

    private static Ref<EntityStore> spawnLiveBlockBody(Store<EntityStore> store,
        PhysicsWorldResource resource,
        PhysicsSpace space,
        PhysicsBody body,
        Vector3d visualPosition) {

        TimeResource time = store.getResource(TimeResource.getResourceType());
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            new Vector3d(visualPosition));
        holder.removeComponent(DESPAWN_TYPE);
        PhysicsBodyId bodyId = resource.callOnPhysicsOwner("register live crucible body",
            access -> access.addBody(space.getId(),
                body,
                PhysicsBodyKind.BODY,
                PhysicsBodyPersistenceMode.PERSISTENT));
        holder.addComponent(ATTACHMENT_TYPE,
            new PhysicsBodyAttachmentComponent(bodyId,
                space.getId(),
                TransformAuthority.BODY,
                AttachmentLifecycle.EXTERNAL_ENTITY));
        holder.addComponent(IMPULSE_CONTROLLABLE_TYPE, new ImpulseControllableComponent());

        return store.addEntity(holder, AddReason.SPAWN);
    }
}
