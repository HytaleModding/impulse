package dev.hytalemodding.impulse.core.systems;

import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsJoint;
import javax.annotation.Nonnull;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;

final class PhysicsDebugRenderer {

    private static final double SHAPE_INFLATION = 1.025;
    private static final double MIN_ARROW_LENGTH = 0.05;
    private static final double MAX_ARROW_LENGTH = 4.0;
    private static final double VELOCITY_SCALE = 0.15;
    private static final double ANGULAR_VELOCITY_SCALE = 0.35;
    private static final double CONTACT_NORMAL_SCALE = 0.75;
    private static final int SHAPE_FLAGS = DebugUtils.FLAG_NO_SOLID;
    private static final int VECTOR_FLAGS = DebugUtils.FLAG_FADE;

    private PhysicsDebugRenderer() {
    }

    static float lifetime(float dt) {
        if (dt <= 0f) {
            return 0.03f;
        }
        return Math.clamp(dt * 0.9f, 0.02f, 0.05f);
    }

    static void renderBodyShape(@Nonnull World world,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        float time) {
        Vector3f color = colorForBody(body);
        switch (body.getShapeType()) {
            case BOX -> renderBox(world, body, center, rotation, color, time);
            case SPHERE -> renderSphere(world, body, center, color, time);
            case CAPSULE -> renderCapsule(world, body, center, rotation, color, time);
            case CYLINDER -> renderCylinder(world, body, center, rotation, color, time);
            case CONE -> renderCone(world, body, center, rotation, color, time);
            case PLANE -> renderPlane(world, center, time);
            default -> renderUnknown(world, center, rotation, time);
        }
    }

    static void renderBodyMotion(@Nonnull World world,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d center,
        float time) {
        Vector3f linearVelocity = body.getLinearVelocity();
        renderArrow(world, center, toDebugDirection(linearVelocity, VELOCITY_SCALE),
            DebugUtils.COLOR_GREEN, time);

        Vector3f angularVelocity = body.getAngularVelocity();
        renderArrow(world, center, toDebugDirection(angularVelocity, ANGULAR_VELOCITY_SCALE),
            DebugUtils.COLOR_MAGENTA, time);
    }

    static void renderContact(@Nonnull World world, @Nonnull PhysicsContact contact, float time) {
        Vector3d point = toVector3d(contact.pointOnB());
        DebugUtils.addSphere(world, point, DebugUtils.COLOR_RED, 0.12, time);

        Vector3d normal = toVector3d(contact.normalOnB());
        double magnitude = Math.max(CONTACT_NORMAL_SCALE, Math.abs(contact.impulse()) * 0.05);
        if (normal.lengthSquared() > 0.0) {
            normal.normalize().mul(magnitude);
            DebugUtils.addArrow(world, point, normal, DebugUtils.COLOR_YELLOW, 0.8f, time,
                VECTOR_FLAGS);
        }
    }

    static void renderJoint(@Nonnull World world, @Nonnull PhysicsJoint joint, float time) {
        Vector3d anchorA = worldAnchor(joint.getBodyA(), joint.getAnchorA());
        Vector3d anchorB = worldAnchor(joint.getBodyB(), joint.getAnchorB());
        DebugUtils.addLine(world, anchorA, anchorB, DebugUtils.COLOR_PURPLE, 0.035, time,
            VECTOR_FLAGS);
        DebugUtils.addSphere(world, anchorA, DebugUtils.COLOR_PURPLE, 0.12, time);
        DebugUtils.addSphere(world, anchorB, DebugUtils.COLOR_PURPLE, 0.12, time);

        Vector3f axis = joint.getAxis();
        if (axis != null && axis.lengthSquared() > 0f) {
            Vector3d worldAxis = toVector3d(axis).normalize().mul(0.9);
            Quaterniond bodyRotation = toQuaterniond(joint.getBodyA().getRotation());
            bodyRotation.transform(worldAxis);
            DebugUtils.addArrow(world, anchorA, worldAxis, DebugUtils.COLOR_CYAN, 0.8f, time,
                VECTOR_FLAGS);
        }
    }

    static void renderRay(@Nonnull World world,
        @Nonnull Vector3d start,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color,
        float time) {
        renderArrow(world, start, direction, color, time);
    }

    static Vector3d centerFromSyncedTransform(@Nonnull PhysicsBody body,
        @Nonnull Vector3d transformPosition) {
        return new Vector3d(transformPosition).add(0.0, body.getCenterOfMassOffsetY(), 0.0);
    }

    private static void renderBox(@Nonnull World world,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        @Nonnull Vector3f color,
        float time) {
        Vector3f half = body.getBoxHalfExtents();
        if (half == null) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .rotate(rotation)
            .scale(half.x * 2 * SHAPE_INFLATION,
                half.y * 2 * SHAPE_INFLATION,
                half.z * 2 * SHAPE_INFLATION);
        DebugUtils.add(world, DebugShape.Cube, transform, color, time, SHAPE_FLAGS);
    }

    private static void renderSphere(@Nonnull World world,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d center,
        @Nonnull Vector3f color,
        float time) {
        float radius = body.getSphereRadius();
        if (radius <= 0f) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .scale(radius * 2 * SHAPE_INFLATION);
        DebugUtils.add(world, DebugShape.Sphere, transform, color, time, SHAPE_FLAGS);
    }

    private static void renderCapsule(@Nonnull World world,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        @Nonnull Vector3f color,
        float time) {
        float radius = body.getSphereRadius();
        float halfHeight = body.getHalfHeight();
        if (radius <= 0f || halfHeight <= 0f) {
            return;
        }

        Quaterniond axisRotation = axisRotation(body.getShapeAxis());
        Matrix4d cylinder = new Matrix4d()
            .translate(center)
            .rotate(new Quaterniond(rotation).mul(axisRotation))
            .scale(radius * 2 * SHAPE_INFLATION,
                halfHeight * 2 * SHAPE_INFLATION,
                radius * 2 * SHAPE_INFLATION);
        DebugUtils.add(world, DebugShape.Cylinder, cylinder, color, time, SHAPE_FLAGS);

        Vector3d axis = axisVector(body.getShapeAxis());
        rotation.transform(axis);
        axis.mul(halfHeight);
        Matrix4d sphereA = new Matrix4d()
            .translate(new Vector3d(center).add(axis))
            .scale(radius * 2 * SHAPE_INFLATION);
        Matrix4d sphereB = new Matrix4d()
            .translate(new Vector3d(center).sub(axis))
            .scale(radius * 2 * SHAPE_INFLATION);
        DebugUtils.add(world, DebugShape.Sphere, sphereA, color, time, SHAPE_FLAGS);
        DebugUtils.add(world, DebugShape.Sphere, sphereB, color, time, SHAPE_FLAGS);
    }

    private static void renderCylinder(@Nonnull World world,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        @Nonnull Vector3f color,
        float time) {
        float radius = body.getSphereRadius();
        float halfHeight = body.getHalfHeight();
        if (radius <= 0f || halfHeight <= 0f) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .rotate(new Quaterniond(rotation).mul(axisRotation(body.getShapeAxis())))
            .scale(radius * 2 * SHAPE_INFLATION,
                halfHeight * 2 * SHAPE_INFLATION,
                radius * 2 * SHAPE_INFLATION);
        DebugUtils.add(world, DebugShape.Cylinder, transform, color, time, SHAPE_FLAGS);
    }

    private static void renderCone(@Nonnull World world,
        @Nonnull PhysicsBody body,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        @Nonnull Vector3f color,
        float time) {
        float radius = body.getSphereRadius();
        float halfHeight = body.getHalfHeight();
        if (radius <= 0f || halfHeight <= 0f) {
            return;
        }

        Matrix4d transform = new Matrix4d()
            .translate(center)
            .rotate(new Quaterniond(rotation).mul(axisRotation(body.getShapeAxis())))
            .scale(radius * 2 * SHAPE_INFLATION,
                halfHeight * 2 * SHAPE_INFLATION,
                radius * 2 * SHAPE_INFLATION);
        DebugUtils.add(world, DebugShape.Cone, transform, color, time, SHAPE_FLAGS);
    }

    private static void renderPlane(@Nonnull World world, @Nonnull Vector3d center, float time) {
        Matrix4d transform = new Matrix4d()
            .translate(0.0, center.y, 0.0)
            .scale(20.0, 0.05, 20.0);
        DebugUtils.add(world, DebugShape.Cube, transform, DebugUtils.COLOR_YELLOW, time,
            SHAPE_FLAGS);
    }

    private static void renderUnknown(@Nonnull World world,
        @Nonnull Vector3d center,
        @Nonnull Quaterniond rotation,
        float time) {
        Matrix4d transform = new Matrix4d()
            .translate(center)
            .rotate(rotation)
            .scale(SHAPE_INFLATION);
        DebugUtils.add(world, DebugShape.Cube, transform, DebugUtils.COLOR_RED, time,
            SHAPE_FLAGS);
    }

    private static void renderArrow(@Nonnull World world,
        @Nonnull Vector3d start,
        @Nonnull Vector3d direction,
        @Nonnull Vector3f color,
        float time) {
        if (direction.lengthSquared() < MIN_ARROW_LENGTH * MIN_ARROW_LENGTH) {
            return;
        }
        DebugUtils.addArrow(world, start, direction, color, 0.8f, time, VECTOR_FLAGS);
    }

    @Nonnull
    private static Vector3d toDebugDirection(@Nonnull Vector3f vector, double scale) {
        Vector3d direction = new Vector3d(vector.x, vector.y, vector.z).mul(scale);
        double length = direction.length();
        if (length > MAX_ARROW_LENGTH) {
            direction.mul(MAX_ARROW_LENGTH / length);
        }
        return direction;
    }

    @Nonnull
    private static Vector3d worldAnchor(@Nonnull PhysicsBody body, @Nonnull Vector3f localAnchor) {
        Vector3d anchor = new Vector3d(localAnchor.x, localAnchor.y, localAnchor.z);
        Quaterniond rotation = toQuaterniond(body.getRotation());
        rotation.transform(anchor);
        Vector3f position = body.getPosition();
        return anchor.add(position.x, position.y, position.z);
    }

    @Nonnull
    private static Vector3d toVector3d(@Nonnull Vector3f vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }

    @Nonnull
    private static Quaterniond toQuaterniond(@Nonnull org.joml.Quaternionf quaternion) {
        return new Quaterniond(quaternion.x, quaternion.y, quaternion.z, quaternion.w);
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
    private static Vector3f colorForBody(@Nonnull PhysicsBody body) {
        if (body.isSensor()) {
            return DebugUtils.COLOR_MAGENTA;
        }
        PhysicsBodyType type = body.getBodyType();
        return switch (type) {
            case STATIC -> DebugUtils.COLOR_YELLOW;
            case KINEMATIC -> DebugUtils.COLOR_BLUE;
            case DYNAMIC -> DebugUtils.COLOR_LIME;
        };
    }
}
