package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.voxel.SectionCollisionGeometry.BoxCollider;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache.DebugSection;
import javax.annotation.Nonnull;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

public class PhysicsDebugSystem extends TickingSystem<ChunkStore> {

    private static final int MAX_WORLD_COLLISION_DEBUG_SECTIONS = 512;
    private static final int MAX_WORLD_COLLISION_DEBUG_BOXES = 2048;

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
        boolean debugShapes = resource.isDebugShapesEnabled();
        boolean debugContacts = resource.isDebugContactsEnabled();
        boolean debugJoints = resource.isDebugJointsEnabled();
        boolean debugWorldCollision = resource.isDebugWorldCollisionEnabled();
        if (!debugShapes && !debugContacts && !debugJoints && !debugWorldCollision) {
            return;
        }

        for (PhysicsSpace space : resource.iterateSpaces(world.getName())) {
            if (debugShapes) {
                renderSpaceOnlyShapes(world, space, time);
            }
            if (debugContacts) {
                renderContacts(world, space, time);
            }
            if (debugJoints) {
                renderJoints(world, space, time);
            }
            if (debugWorldCollision) {
                renderWorldCollision(world, resource, space, time);
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

    private static void renderWorldCollision(@Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull PhysicsSpace space,
        float time) {
        int[] sectionCount = {0};
        int[] boxCount = {0};
        resource.getWorldVoxelCollisionCache().forEachDebugSection(space.getId(), section -> {
            if (sectionCount[0] < MAX_WORLD_COLLISION_DEBUG_SECTIONS) {
                PhysicsDebugRenderer.renderWorldCollisionSection(world,
                    section.chunkX(),
                    section.sectionY(),
                    section.chunkZ(),
                    section.voxelTerrain(),
                    time);
            }
            sectionCount[0]++;
            boxCount[0] = renderWorldCollisionBoxes(world,
                section,
                boxCount[0],
                time);
        });
    }

    private static int renderWorldCollisionBoxes(@Nonnull World world,
        @Nonnull DebugSection section,
        int boxCount,
        float time) {
        boxCount = renderWorldCollisionBoxes(world,
            section.fullCubeBoxes(),
            DebugUtils.COLOR_CYAN,
            boxCount,
            time);
        return renderWorldCollisionBoxes(world,
            section.detailBoxes(),
            DebugUtils.COLOR_MAGENTA,
            boxCount,
            time);
    }

    private static int renderWorldCollisionBoxes(@Nonnull World world,
        @Nonnull Iterable<BoxCollider> boxes,
        @Nonnull Vector3f color,
        int boxCount,
        float time) {
        for (BoxCollider box : boxes) {
            if (boxCount >= MAX_WORLD_COLLISION_DEBUG_BOXES) {
                return boxCount;
            }
            PhysicsDebugRenderer.renderWorldCollisionBox(world, box, color, time);
            boxCount++;
        }
        return boxCount;
    }
}
