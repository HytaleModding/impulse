package dev.hytalemodding.impulse.api.runtime;

import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.ShapeType;
import javax.annotation.Nonnull;

/**
 * Stable primitive codes for the backend-runtime port.
 */
public final class BackendRuntimeCodes {

    public static final int SHAPE_BOX = 1;
    public static final int SHAPE_SPHERE = 2;
    public static final int SHAPE_CAPSULE = 3;
    public static final int SHAPE_CYLINDER = 4;
    public static final int SHAPE_CONE = 5;
    public static final int SHAPE_PLANE = 6;
    public static final int SHAPE_VOXELS = 7;
    public static final int SHAPE_UNKNOWN = -1;

    public static final int BODY_STATIC = 1;
    public static final int BODY_DYNAMIC = 2;
    public static final int BODY_KINEMATIC = 3;

    public static final int AXIS_X = 1;
    public static final int AXIS_Y = 2;
    public static final int AXIS_Z = 3;

    public static final int JOINT_FIXED = 1;
    public static final int JOINT_POINT = 2;
    public static final int JOINT_HINGE = 3;
    public static final int JOINT_SLIDER = 4;
    public static final int JOINT_SPRING = 5;

    private BackendRuntimeCodes() {
    }

    public static int shapeTypeCode(@Nonnull ShapeType shapeType) {
        return switch (shapeType) {
            case BOX -> SHAPE_BOX;
            case SPHERE -> SHAPE_SPHERE;
            case CAPSULE -> SHAPE_CAPSULE;
            case CYLINDER -> SHAPE_CYLINDER;
            case CONE -> SHAPE_CONE;
            case PLANE -> SHAPE_PLANE;
            case VOXELS -> SHAPE_VOXELS;
            case UNKNOWN -> SHAPE_UNKNOWN;
        };
    }

    @Nonnull
    public static ShapeType shapeType(int code) {
        return switch (code) {
            case SHAPE_BOX -> ShapeType.BOX;
            case SHAPE_SPHERE -> ShapeType.SPHERE;
            case SHAPE_CAPSULE -> ShapeType.CAPSULE;
            case SHAPE_CYLINDER -> ShapeType.CYLINDER;
            case SHAPE_CONE -> ShapeType.CONE;
            case SHAPE_PLANE -> ShapeType.PLANE;
            case SHAPE_VOXELS -> ShapeType.VOXELS;
            case SHAPE_UNKNOWN -> ShapeType.UNKNOWN;
            default -> throw new IllegalArgumentException("Unknown backend shape code: " + code);
        };
    }

    public static int bodyTypeCode(@Nonnull PhysicsBodyType bodyType) {
        return switch (bodyType) {
            case STATIC -> BODY_STATIC;
            case DYNAMIC -> BODY_DYNAMIC;
            case KINEMATIC -> BODY_KINEMATIC;
        };
    }

    @Nonnull
    public static PhysicsBodyType bodyType(int code) {
        return switch (code) {
            case BODY_STATIC -> PhysicsBodyType.STATIC;
            case BODY_DYNAMIC -> PhysicsBodyType.DYNAMIC;
            case BODY_KINEMATIC -> PhysicsBodyType.KINEMATIC;
            default -> throw new IllegalArgumentException("Unknown backend body type code: " + code);
        };
    }

    public static int axisCode(@Nonnull PhysicsAxis axis) {
        return switch (axis) {
            case X -> AXIS_X;
            case Y -> AXIS_Y;
            case Z -> AXIS_Z;
        };
    }

    @Nonnull
    public static PhysicsAxis axis(int code) {
        return switch (code) {
            case AXIS_X -> PhysicsAxis.X;
            case AXIS_Y -> PhysicsAxis.Y;
            case AXIS_Z -> PhysicsAxis.Z;
            default -> throw new IllegalArgumentException("Unknown backend axis code: " + code);
        };
    }

    public static int jointTypeCode(@Nonnull BackendJointType type) {
        return switch (type) {
            case FIXED -> JOINT_FIXED;
            case POINT -> JOINT_POINT;
            case HINGE -> JOINT_HINGE;
            case SLIDER -> JOINT_SLIDER;
            case SPRING -> JOINT_SPRING;
        };
    }

    @Nonnull
    public static BackendJointType jointType(int code) {
        return switch (code) {
            case JOINT_FIXED -> BackendJointType.FIXED;
            case JOINT_POINT -> BackendJointType.POINT;
            case JOINT_HINGE -> BackendJointType.HINGE;
            case JOINT_SLIDER -> BackendJointType.SLIDER;
            case JOINT_SPRING -> BackendJointType.SPRING;
            default -> throw new IllegalArgumentException("Unknown backend joint type code: " + code);
        };
    }
}
