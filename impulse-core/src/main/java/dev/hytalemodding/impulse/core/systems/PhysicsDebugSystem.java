package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class PhysicsDebugSystem extends TickingSystem<ChunkStore> {

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        World world = store.getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();
        PhysicsWorldResource resource = entityStore.getResource(
            PhysicsWorldResource.getResourceType());

        if (!resource.isDebugEnabled()) {
            return;
        }

        float time = PhysicsDebugRenderer.lifetime(dt);
        for (PhysicsSpace space : resource.getSpaces(world.getName())) {
            if (resource.isDebugShapesEnabled()) {
                renderSpaceOnlyShapes(world, space, time);
            }
            if (resource.isDebugContactsEnabled()) {
                renderContacts(world, space, time);
            }
            if (resource.isDebugJointsEnabled()) {
                renderJoints(world, space, time);
            }
        }
    }

    private static void renderSpaceOnlyShapes(@Nonnull World world,
        @Nonnull PhysicsSpace space,
        float time) {
        for (PhysicsBody body : space.getBodies()) {
            if (body.getShapeType() != ShapeType.PLANE) {
                continue;
            }

            Vector3f pos = body.getPosition();
            PhysicsDebugRenderer.renderBodyShape(world, body, new Vector3d(pos.x, pos.y, pos.z),
                new Quaterniond(), time);
        }
    }

    private static void renderContacts(@Nonnull World world,
        @Nonnull PhysicsSpace space,
        float time) {
        for (PhysicsContact contact : space.getContacts()) {
            PhysicsDebugRenderer.renderContact(world, contact, time);
        }
    }

    private static void renderJoints(@Nonnull World world,
        @Nonnull PhysicsSpace space,
        float time) {
        for (PhysicsJoint joint : space.getJoints()) {
            PhysicsDebugRenderer.renderJoint(world, joint, time);
        }
    }
}
