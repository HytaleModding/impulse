package dev.hytalemodding.impulse.core.internal.systems.debug;

import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugContactView;
import dev.hytalemodding.impulse.core.internal.simulation.PhysicsDebugJointView;
import dev.hytalemodding.impulse.core.internal.modules.worldcollision.SectionCollisionGeometry.BoxCollider;
import java.util.Collection;
import javax.annotation.Nonnull;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class PhysicsDebugRenderer {

    private static final double SHAPE_INFLATION = 1.025;
    private static final double WORLD_COLLISION_EDGE_PADDING = 0.0125;
    private static final float MIN_DEBUG_LIFETIME = 0.08f;
    private static final double MIN_ARROW_LENGTH = 0.05;
    private static final double MAX_ARROW_LENGTH = 4.0;
    private static final double VELOCITY_SCALE = 0.15;
    private static final double ANGULAR_VELOCITY_SCALE = 0.35;
    private static final double CONTACT_NORMAL_SCALE = 0.75;
    private static final int SHAPE_FLAGS = DebugUtils.FLAG_NO_SOLID;
    private static final int VECTOR_FLAGS = DebugUtils.FLAG_FADE;
    private PhysicsDebugRenderer() {
    }

    static float lifetimeForRefresh(float refreshSeconds, float dt) {
        float redrawSeconds = Math.max(refreshSeconds, dt);
        return Math.max(MIN_DEBUG_LIFETIME, redrawSeconds * 1.25f);
    }

    static void renderBodyShape(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        float time) {
        switch (snapshot.shapeType()) {
            case BOX -> renderBox(viewers, snapshot, center, rotation, colorForSnapshot(snapshot), time);
            case SPHERE -> renderSphere(viewers, snapshot, center, colorForSnapshot(snapshot), time);
            case CAPSULE -> renderCapsule(viewers, snapshot, center, rotation, colorForSnapshot(snapshot), time);
            case CYLINDER -> renderCylinder(viewers, snapshot, center, rotation, colorForSnapshot(snapshot), time);
            case CONE -> renderCone(viewers, snapshot, center, rotation, colorForSnapshot(snapshot), time);
            case PLANE -> renderPlane(viewers, center, time);
            default -> renderUnknown(viewers, center, rotation, time);
        }
    }

    static void renderBodyMotion(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d center,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity,
        float time) {
        renderArrow(viewers, center, toDebugDirection(linearVelocity, VELOCITY_SCALE),
            DebugUtils.COLOR_GREEN, time);

        renderArrow(viewers, center, toDebugDirection(angularVelocity, ANGULAR_VELOCITY_SCALE),
            DebugUtils.COLOR_MAGENTA, time);
    }

    static void renderBodyMotion(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d center,
        @Nonnull PhysicsBodySnapshot snapshot,
        float time) {
        renderArrow(viewers,
            center,
            toDebugDirection(snapshot.linearVelocityX(),
                snapshot.linearVelocityY(),
                snapshot.linearVelocityZ(),
                VELOCITY_SCALE),
            DebugUtils.COLOR_GREEN,
            time);

        renderArrow(viewers,
            center,
            toDebugDirection(snapshot.angularVelocityX(),
                snapshot.angularVelocityY(),
                snapshot.angularVelocityZ(),
                ANGULAR_VELOCITY_SCALE),
            DebugUtils.COLOR_MAGENTA,
            time);
    }

    static void renderContact(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull ContactDebugPrimitive primitive,
        float time) {
        Vector3d point = primitive.point();
        PhysicsDebugPackets.addSphere(viewers,
            point,
            DebugUtils.COLOR_RED,
            0.8f,
            0.12,
            time,
            VECTOR_FLAGS);

        Vector3d normal = primitive.normal();
        if (normal != null && normal.lengthSquared() > 0.0) {
            PhysicsDebugPackets.addArrow(viewers,
                point,
                normal,
                DebugUtils.COLOR_YELLOW,
                0.8f,
                time,
                VECTOR_FLAGS);
        }
    }

    static void renderContact(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsDebugContactView view,
        float time) {
        Vector3d point = new Vector3d(view.pointX(), view.pointY(), view.pointZ());
        PhysicsDebugPackets.addSphere(viewers,
            point,
            DebugUtils.COLOR_RED,
            0.8f,
            0.12,
            time,
            VECTOR_FLAGS);

        if (view.hasNormal()) {
            Vector3d normal = new Vector3d(view.normalX(), view.normalY(), view.normalZ());
            if (normal.lengthSquared() > 0.0) {
                PhysicsDebugPackets.addArrow(viewers,
                    point,
                    normal,
                    DebugUtils.COLOR_YELLOW,
                    0.8f,
                    time,
                    VECTOR_FLAGS);
            }
        }
    }

    static void renderJoint(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull JointDebugPrimitive primitive,
        float time) {
        Vector3d anchorA = primitive.anchorA();
        Vector3d anchorB = primitive.anchorB();
        Vector3d axis = primitive.axis();
        PhysicsDebugPackets.addLine(viewers,
            anchorA,
            anchorB,
            DebugUtils.COLOR_PURPLE,
            0.035,
            0.8f,
            time,
            VECTOR_FLAGS);
        PhysicsDebugPackets.addSphere(viewers,
            anchorA,
            DebugUtils.COLOR_PURPLE,
            0.8f,
            0.12,
            time,
            VECTOR_FLAGS);
        PhysicsDebugPackets.addSphere(viewers,
            anchorB,
            DebugUtils.COLOR_PURPLE,
            0.8f,
            0.12,
            time,
            VECTOR_FLAGS);

        if (axis != null && axis.lengthSquared() > 0.0) {
            PhysicsDebugPackets.addArrow(viewers,
                anchorA,
                axis,
                DebugUtils.COLOR_CYAN,
                0.8f,
                time,
                VECTOR_FLAGS);
        }
    }

    static void renderJoint(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsDebugJointView view,
        float time) {
        Vector3d anchorA = new Vector3d(view.anchorAX(), view.anchorAY(), view.anchorAZ());
        Vector3d anchorB = new Vector3d(view.anchorBX(), view.anchorBY(), view.anchorBZ());
        PhysicsDebugPackets.addLine(viewers,
            anchorA,
            anchorB,
            DebugUtils.COLOR_PURPLE,
            0.035,
            0.8f,
            time,
            VECTOR_FLAGS);
        PhysicsDebugPackets.addSphere(viewers,
            anchorA,
            DebugUtils.COLOR_PURPLE,
            0.8f,
            0.12,
            time,
            VECTOR_FLAGS);
        PhysicsDebugPackets.addSphere(viewers,
            anchorB,
            DebugUtils.COLOR_PURPLE,
            0.8f,
            0.12,
            time,
            VECTOR_FLAGS);

        if (view.hasAxis()) {
            Vector3d axis = new Vector3d(view.axisX(), view.axisY(), view.axisZ());
            if (axis.lengthSquared() > 0.0) {
                PhysicsDebugPackets.addArrow(viewers,
                    anchorA,
                    axis,
                    DebugUtils.COLOR_CYAN,
                    0.8f,
                    time,
                    VECTOR_FLAGS);
            }
        }
    }

    static void renderRay(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d start,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color,
        float time) {
        renderArrow(viewers, start, direction, color, time);
    }

    static void renderWorldCollisionSection(@Nonnull Collection<PlayerRef> viewers,
        int chunkX,
        int sectionY,
        int chunkZ,
        boolean voxelTerrain,
        float time) {
        Vector3f color = voxelTerrain ? DebugUtils.COLOR_TEAL : DebugUtils.COLOR_NAVY;
        double halfSection = ChunkUtil.SIZE * 0.5;
        renderDebugCubeWithPadding(viewers,
            new Vector3d((chunkX << ChunkUtil.BITS) + halfSection,
                (sectionY << ChunkUtil.BITS) + halfSection,
                (chunkZ << ChunkUtil.BITS) + halfSection),
            new Vector3d(halfSection, halfSection, halfSection),
            color,
            time,
            WORLD_COLLISION_EDGE_PADDING);
    }

    static void renderWorldCollisionBox(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull BoxCollider box,
        @Nonnull Vector3f color,
        float time) {
        renderDebugCubeWithPadding(viewers,
            new Vector3d(box.centerX(), box.centerY(), box.centerZ()),
            new Vector3d(box.halfX(), box.halfY(), box.halfZ()),
            color,
            time,
            WORLD_COLLISION_EDGE_PADDING);
    }

    static Vector3d centerFromSyncedTransform(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Vector3d transformPosition) {
        return new Vector3d(transformPosition).add(0.0, snapshot.centerOfMassOffsetY(), 0.0);
    }

    private static void renderBox(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        @Nonnull Vector3f color,
        float time) {
        Vector3f half = snapshot.boxHalfExtents();
        if (half == null) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .rotate(rotation)
            .scale(half.x * 2 * SHAPE_INFLATION,
                half.y * 2 * SHAPE_INFLATION,
                half.z * 2 * SHAPE_INFLATION);
        PhysicsDebugPackets.add(viewers, DebugShape.Cube, transform, color, 0.8f, time, SHAPE_FLAGS);
    }

    private static void renderDebugCube(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d center,
        @Nonnull Vector3d halfExtents,
        @Nonnull Vector3f color,
        float time) {
        renderDebugCube(viewers, center, halfExtents, color, time, SHAPE_INFLATION);
    }

    private static void renderDebugCube(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d center,
        @Nonnull Vector3d halfExtents,
        @Nonnull Vector3f color,
        float time,
        double inflation) {
        if (halfExtents.x <= 0.0 || halfExtents.y <= 0.0 || halfExtents.z <= 0.0) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .scale(halfExtents.x * 2.0 * inflation,
                halfExtents.y * 2.0 * inflation,
                halfExtents.z * 2.0 * inflation);
        PhysicsDebugPackets.add(viewers, DebugShape.Cube, transform, color, 0.8f, time, SHAPE_FLAGS);
    }

    private static void renderDebugCubeWithPadding(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d center,
        @Nonnull Vector3d halfExtents,
        @Nonnull Vector3f color,
        float time,
        double edgePadding) {
        if (halfExtents.x <= 0.0 || halfExtents.y <= 0.0 || halfExtents.z <= 0.0) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .scale((halfExtents.x + edgePadding) * 2.0,
                (halfExtents.y + edgePadding) * 2.0,
                (halfExtents.z + edgePadding) * 2.0);
        PhysicsDebugPackets.add(viewers, DebugShape.Cube, transform, color, 0.8f, time, SHAPE_FLAGS);
    }

    private static void renderSphere(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Vector3d center,
        @Nonnull Vector3f color,
        float time) {
        float radius = snapshot.sphereRadius();
        if (radius <= 0f) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .scale(radius * 2 * SHAPE_INFLATION);
        PhysicsDebugPackets.add(viewers, DebugShape.Sphere, transform, color, 0.8f, time, SHAPE_FLAGS);
    }

    private static void renderCapsule(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        @Nonnull Vector3f color,
        float time) {
        float radius = snapshot.sphereRadius();
        float halfHeight = snapshot.halfHeight();
        if (radius <= 0f || halfHeight <= 0f) {
            return;
        }

        Quaterniond axisRotation = axisRotation(snapshot.shapeAxis());
        Matrix4d cylinder = new Matrix4d()
            .translate(center)
            .rotate(new Quaterniond(rotation).mul(axisRotation))
            .scale(radius * 2 * SHAPE_INFLATION,
                halfHeight * 2 * SHAPE_INFLATION,
                radius * 2 * SHAPE_INFLATION);
        PhysicsDebugPackets.add(viewers,
            DebugShape.Cylinder,
            cylinder,
            color,
            0.8f,
            time,
            SHAPE_FLAGS);

        Vector3d axis = axisVector(snapshot.shapeAxis());
        rotation.transform(axis);
        axis.mul(halfHeight);
        Matrix4d sphereA = new Matrix4d()
            .translate(new Vector3d(center).add(axis))
            .scale(radius * 2 * SHAPE_INFLATION);
        Matrix4d sphereB = new Matrix4d()
            .translate(new Vector3d(center).sub(axis))
            .scale(radius * 2 * SHAPE_INFLATION);
        PhysicsDebugPackets.add(viewers, DebugShape.Sphere, sphereA, color, 0.8f, time, SHAPE_FLAGS);
        PhysicsDebugPackets.add(viewers, DebugShape.Sphere, sphereB, color, 0.8f, time, SHAPE_FLAGS);
    }

    private static void renderCylinder(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        @Nonnull Vector3f color,
        float time) {
        float radius = snapshot.sphereRadius();
        float halfHeight = snapshot.halfHeight();
        if (radius <= 0f || halfHeight <= 0f) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .rotate(new Quaterniond(rotation).mul(axisRotation(snapshot.shapeAxis())))
            .scale(radius * 2 * SHAPE_INFLATION,
                halfHeight * 2 * SHAPE_INFLATION,
                radius * 2 * SHAPE_INFLATION);
        PhysicsDebugPackets.add(viewers,
            DebugShape.Cylinder,
            transform,
            color,
            0.8f,
            time,
            SHAPE_FLAGS);
    }

    private static void renderCone(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        @Nonnull Vector3f color,
        float time) {
        float radius = snapshot.sphereRadius();
        float halfHeight = snapshot.halfHeight();
        if (radius <= 0f || halfHeight <= 0f) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .rotate(new Quaterniond(rotation).mul(axisRotation(snapshot.shapeAxis())))
            .scale(radius * 2 * SHAPE_INFLATION,
                halfHeight * 2 * SHAPE_INFLATION,
                radius * 2 * SHAPE_INFLATION);
        PhysicsDebugPackets.add(viewers, DebugShape.Cone, transform, color, 0.8f, time, SHAPE_FLAGS);
    }

    private static void renderPlane(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d center,
        float time) {
        Matrix4d transform = new Matrix4d()
            .translate(0.0, center.y, 0.0)
            .scale(20.0, 0.05, 20.0);
        PhysicsDebugPackets.add(viewers,
            DebugShape.Cube,
            transform,
            DebugUtils.COLOR_YELLOW,
            0.8f,
            time,
            SHAPE_FLAGS);
    }

    private static void renderUnknown(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        float time) {
        Matrix4d transform = new Matrix4d()
            .translate(center)
            .rotate(rotation)
            .scale(SHAPE_INFLATION);
        PhysicsDebugPackets.add(viewers,
            DebugShape.Cube,
            transform,
            DebugUtils.COLOR_RED,
            0.8f,
            time,
            SHAPE_FLAGS);
    }

    private static void renderArrow(@Nonnull Collection<PlayerRef> viewers,
        @Nonnull Vector3d start,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color,
        float time) {
        if (direction.lengthSquared() < MIN_ARROW_LENGTH * MIN_ARROW_LENGTH) {
            return;
        }
        PhysicsDebugPackets.addArrow(viewers, start, direction, color, 0.8f, time, VECTOR_FLAGS);
    }

    @Nonnull
    private static Vector3d toDebugDirection(@Nonnull Vector3f vector, double scale) {
        return toDebugDirection(vector.x, vector.y, vector.z, scale);
    }

    @Nonnull
    private static Vector3d toDebugDirection(float x, float y, float z, double scale) {
        Vector3d direction = new Vector3d(x, y, z).mul(scale);
        double length = direction.length();
        if (length > MAX_ARROW_LENGTH) {
            direction.mul(MAX_ARROW_LENGTH / length);
        }
        return direction;
    }

    @Nonnull
    private static Quaterniond axisRotation(@Nonnull PhysicsAxis axis) {
        return switch (axis) {
            case X -> new Quaterniond().rotateZ(Math.PI / 2.0);
            case Y -> new Quaterniond();
            case Z -> new Quaterniond().rotateX(Math.PI / 2.0);
        };
    }

    @Nonnull
    private static Vector3d axisVector(@Nonnull PhysicsAxis axis) {
        return switch (axis) {
            case X -> new Vector3d(1.0, 0.0, 0.0);
            case Y -> new Vector3d(0.0, 1.0, 0.0);
            case Z -> new Vector3d(0.0, 0.0, 1.0);
        };
    }

    @Nonnull
    private static Vector3f colorForSnapshot(@Nonnull PhysicsBodySnapshot snapshot) {
        if (snapshot.sensor()) {
            return DebugUtils.COLOR_MAGENTA;
        }
        return switch (snapshot.bodyType()) {
            case STATIC -> DebugUtils.COLOR_YELLOW;
            case KINEMATIC -> DebugUtils.COLOR_BLUE;
            case DYNAMIC -> DebugUtils.COLOR_LIME;
        };
    }

    record JointDebugPrimitive(@Nonnull Vector3d anchorA,
                               @Nonnull Vector3d anchorB,
                               Vector3d axis) {

        JointDebugPrimitive {
            anchorA = new Vector3d(anchorA);
            anchorB = new Vector3d(anchorB);
            axis = axis != null ? new Vector3d(axis) : null;
        }
    }

    record ContactDebugPrimitive(@Nonnull Vector3d point,
                                 Vector3d normal) {

        ContactDebugPrimitive {
            point = new Vector3d(point);
            normal = normal != null ? new Vector3d(normal) : null;
        }
    }
}
