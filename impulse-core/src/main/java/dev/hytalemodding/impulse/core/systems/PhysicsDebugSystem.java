package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import javax.annotation.Nonnull;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Renders collision shapes as debug overlays each tick when enabled.
 */
public class PhysicsDebugSystem extends TickingSystem<ChunkStore> {

    @Override
    public void tick(float dt, int index, @Nonnull Store<ChunkStore> store) {
        var world = store.getExternalData().getWorld();
        var entityStore = world.getEntityStore().getStore();
        PhysicsWorldResource resource = entityStore.getResource(
            PhysicsWorldResource.getResourceType());

        if (!resource.isDebugEnabled()) {
            return;
        }

        for (PhysicsSpace space : resource.getSpaces(world.getName())) {
            for (PhysicsBody body : space.getBodies()) {
                Vector3f pos = body.getPosition();
                Quaternionf rot = body.getRotation();
                Vector3d posD = new Vector3d(pos.x, pos.y, pos.z);
                Quaterniond rotD = new Quaterniond(rot.x, rot.y, rot.z, rot.w);

                switch (body.getShapeType()) {
                    case BOX -> {
                        Vector3f half = body.getBoxHalfExtents();
                        if (half == null) {
                            break;
                        }

                        Matrix4d transform = new Matrix4d()
                            .translate(posD)
                            .rotate(rotD)
                            .scale(half.x * 2, half.y * 2, half.z * 2);
                        DebugUtils.add(world, DebugShape.Cube, transform, DebugUtils.COLOR_LIME,
                            0.1f, DebugUtils.FLAG_NONE);
                    }
                    case SPHERE -> {
                        float radius = body.getSphereRadius();
                        if (radius > 0) {
                            DebugUtils.addSphere(world, posD, DebugUtils.COLOR_CYAN, radius, 0.1f);
                        }
                    }
                    case PLANE -> {
                        Matrix4d transform = new Matrix4d()
                            .translate(0, pos.y, 0)
                            .scale(20, 0.1, 20);
                        DebugUtils.add(world, DebugShape.Cube, transform, DebugUtils.COLOR_YELLOW,
                            0.1f, DebugUtils.FLAG_NONE);
                    }
                    default -> {
                        Matrix4d transform = new Matrix4d()
                            .translate(posD)
                            .rotate(rotD)
                            .scale(1);
                        DebugUtils.add(world, DebugShape.Cube, transform, DebugUtils.COLOR_RED,
                            0.1f, DebugUtils.FLAG_NONE);
                    }
                }
            }
        }
    }
}
