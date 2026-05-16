package dev.hytalemodding.impulse.core.crucible;

import com.hypixel.hytale.component.AddReason;
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
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.components.ImpulseControllableComponent;
import dev.hytalemodding.impulse.core.components.PersistentPhysicsBodyComponent;
import dev.hytalemodding.impulse.core.components.PhysicsBodyComponent;
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
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
            space.setGravity(0f, -9.81f, 0f);

            Vector3d visualPosition = new Vector3d(
                context.wx(0),
                context.wy(20),
                context.wz(0));
            PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 1.0f);
            body.setPosition((float) visualPosition.x,
                (float) (visualPosition.y + body.getCenterOfMassOffsetY()),
                (float) visualPosition.z);

            Ref<EntityStore> ref = spawnLiveBlockBody(store, space, body, visualPosition);
            double startY = visualPosition.y;

            return context.waitApproxTicksOnWorld(40).thenApply(ignored -> bodyAndEntityMovedDown(
                store,
                ref,
                body,
                startY));
        } catch (ReflectiveOperationException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static boolean bodyAndEntityMovedDown(Store<EntityStore> store,
        Ref<EntityStore> ref,
        PhysicsBody body,
        double startY) {

        if (!ref.isValid()) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return false;
        }
        double transformY = transform.getPosition().y;
        float bodyY = body.getPosition().y - body.getCenterOfMassOffsetY();
        return transformY < startY - 0.05 && bodyY < startY - 0.05f;
    }

    private static PhysicsSpace liveTestSpace(PhysicsWorldResource resource, World world) {
        PhysicsSpace existing = resource.getDefaultSpace();
        if (existing != null) {
            return existing;
        }
        return resource.createSpace(ImpulsePlugin.get().getDefaultBackendId(),
            world.getName(),
            PhysicsSpaceSettings.defaults(),
            true);
    }

    private static Ref<EntityStore> spawnLiveBlockBody(Store<EntityStore> store,
        PhysicsSpace space,
        PhysicsBody body,
        Vector3d visualPosition) {

        TimeResource time = store.getResource(TimeResource.getResourceType());
        Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
            time,
            DEFAULT_BLOCK_TYPE,
            new Vector3d(visualPosition));
        holder.removeComponent(DespawnComponent.getComponentType());
        holder.addComponent(PhysicsBodyComponent.getComponentType(),
            new PhysicsBodyComponent(body, space.getId()));
        holder.addComponent(PersistentPhysicsBodyComponent.getComponentType(),
            PersistentPhysicsBodyComponent.fromBody(body, space.getId()));
        holder.addComponent(ImpulseControllableComponent.getComponentType(),
            new ImpulseControllableComponent());

        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        space.addBody(body);
        return ref;
    }
}
