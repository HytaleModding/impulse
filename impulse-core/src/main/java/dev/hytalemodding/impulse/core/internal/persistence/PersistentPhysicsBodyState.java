package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.PhysicsBodyId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * World-level persistent state for one physics body.
 */
public class PersistentPhysicsBodyState {

    public static final int DEFAULT_SPACE_ID = 0;

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsBodyState> CODEC = BuilderCodec.builder(
            PersistentPhysicsBodyState.class,
            PersistentPhysicsBodyState::new)
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY),
            (state, value) -> state.bodyId = value,
            PersistentPhysicsBodyState::getBodyIdValue)
        .add()
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER),
            (state, value) -> state.spaceId = sanitizeSpaceId(value),
            PersistentPhysicsBodyState::getSpaceId)
        .add()
        .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class)),
            (state, value) -> state.shapeType = value != null ? value : ShapeType.UNKNOWN,
            PersistentPhysicsBodyState::getShapeType)
        .add()
        .append(new KeyedCodec<>("ShapeAxis", new EnumCodec<>(PhysicsAxis.class)),
            (state, value) -> state.shapeAxis = value != null ? value : PhysicsAxis.Y,
            PersistentPhysicsBodyState::getShapeAxis)
        .add()
        .append(new KeyedCodec<>("BoxHalfExtents", Vector3fUtil.CODEC),
            (state, value) -> copyFiniteVectorOrZero(state.boxHalfExtents, value),
            PersistentPhysicsBodyState::getBoxHalfExtents)
        .add()
        .append(new KeyedCodec<>("SphereRadius", Codec.FLOAT),
            (state, value) -> state.sphereRadius = positiveFiniteOrZeroForRestore(value),
            PersistentPhysicsBodyState::getSphereRadius)
        .add()
        .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT),
            (state, value) -> state.halfHeight = positiveFiniteOrZeroForRestore(value),
            PersistentPhysicsBodyState::getHalfHeight)
        .add()
        .append(new KeyedCodec<>("PlaneGroundY", Codec.FLOAT, false),
            (state, value) -> state.planeGroundY = finiteOrZero(value),
            PersistentPhysicsBodyState::getPlaneGroundY)
        .add()
        .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class)),
            (state, value) -> state.bodyType = value != null ? value : PhysicsBodyType.DYNAMIC,
            PersistentPhysicsBodyState::getBodyType)
        .add()
        .append(new KeyedCodec<>("Mass", Codec.FLOAT),
            (state, value) -> state.mass = nonNegativeFiniteOrDefaultForRestore(value, 1.0f),
            PersistentPhysicsBodyState::getMass)
        .add()
        .append(new KeyedCodec<>("Position", Vector3fUtil.CODEC),
            (state, value) -> copyVectorOrZeroIfNull(state.position, value),
            PersistentPhysicsBodyState::getPosition)
        .add()
        .append(new KeyedCodec<>("Rotation", PersistentQuaternion.CODEC),
            (state, value) -> state.rotation = value != null ? value.copy() : new PersistentQuaternion(),
            PersistentPhysicsBodyState::getRotation)
        .add()
        .append(new KeyedCodec<>("LinearVelocity", Vector3fUtil.CODEC),
            (state, value) -> copyVectorOrZeroIfNull(state.linearVelocity, value),
            PersistentPhysicsBodyState::getLinearVelocity)
        .add()
        .append(new KeyedCodec<>("AngularVelocity", Vector3fUtil.CODEC),
            (state, value) -> copyVectorOrZeroIfNull(state.angularVelocity, value),
            PersistentPhysicsBodyState::getAngularVelocity)
        .add()
        .append(new KeyedCodec<>("Friction", Codec.FLOAT),
            (state, value) -> state.friction = nonNegativeFiniteOrZeroForRestore(value),
            PersistentPhysicsBodyState::getFriction)
        .add()
        .append(new KeyedCodec<>("Restitution", Codec.FLOAT),
            (state, value) -> state.restitution = nonNegativeFiniteOrZeroForRestore(value),
            PersistentPhysicsBodyState::getRestitution)
        .add()
        .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT),
            (state, value) -> state.linearDamping = nonNegativeFiniteOrZeroForRestore(value),
            PersistentPhysicsBodyState::getLinearDamping)
        .add()
        .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT),
            (state, value) -> state.angularDamping = nonNegativeFiniteOrZeroForRestore(value),
            PersistentPhysicsBodyState::getAngularDamping)
        .add()
        .append(new KeyedCodec<>("Sensor", Codec.BOOLEAN),
            (state, value) -> state.sensor = value,
            PersistentPhysicsBodyState::isSensor)
        .add()
        .append(new KeyedCodec<>("CollisionGroup", Codec.INTEGER),
            (state, value) -> state.collisionGroup = value,
            PersistentPhysicsBodyState::getCollisionGroup)
        .add()
        .append(new KeyedCodec<>("CollisionMask", Codec.INTEGER),
            (state, value) -> state.collisionMask = value,
            PersistentPhysicsBodyState::getCollisionMask)
        .add()
        .append(new KeyedCodec<>("ContinuousCollision", Codec.BOOLEAN),
            (state, value) -> state.continuousCollisionEnabled = value,
            PersistentPhysicsBodyState::isContinuousCollisionEnabled)
        .add()
        .append(new KeyedCodec<>("Sleeping", Codec.BOOLEAN),
            (state, value) -> state.sleeping = value,
            PersistentPhysicsBodyState::isSleeping)
        .add()
        .build();

    @Nullable
    private UUID bodyId;
    @Getter
    private int spaceId = DEFAULT_SPACE_ID;
    @Nonnull
    private ShapeType shapeType = ShapeType.UNKNOWN;
    @Nonnull
    private PhysicsAxis shapeAxis = PhysicsAxis.Y;
    @Nonnull
    private final Vector3f boxHalfExtents = new Vector3f();
    @Getter
    private float sphereRadius;
    @Getter
    private float halfHeight;
    @Getter
    private float planeGroundY;
    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
    @Getter
    private float mass = 1.0f;
    @Nonnull
    private final Vector3f position = new Vector3f();
    @Nonnull
    private PersistentQuaternion rotation = new PersistentQuaternion();
    @Nonnull
    private final Vector3f linearVelocity = new Vector3f();
    @Nonnull
    private final Vector3f angularVelocity = new Vector3f();
    @Getter
    private float friction;
    @Getter
    private float restitution;
    @Getter
    private float linearDamping;
    @Getter
    private float angularDamping;
    @Getter
    private boolean sensor;
    @Getter
    private int collisionGroup;
    @Getter
    private int collisionMask;
    @Getter
    private boolean continuousCollisionEnabled;
    @Getter
    private boolean sleeping;

    public PersistentPhysicsBodyState() {
    }

    @Nonnull
    public static PersistentPhysicsBodyState from(@Nonnull PhysicsWorldResource.BodyRegistration registration) {
        PersistentPhysicsBodyState state = new PersistentPhysicsBodyState();
        state.bodyId = registration.id().value();
        state.updateFromBody(registration.body(), registration.spaceId());
        return state;
    }

    @Nullable
    public PhysicsBodyId getBodyId() {
        return bodyId != null ? PhysicsBodyId.of(bodyId) : null;
    }

    @Nullable
    public UUID getBodyIdValue() {
        return bodyId;
    }

    @Nonnull
    public ShapeType getShapeType() {
        return shapeType;
    }

    @Nonnull
    public PhysicsAxis getShapeAxis() {
        return shapeAxis;
    }

    @Nonnull
    public Vector3f getBoxHalfExtents() {
        return boxHalfExtents;
    }

    @Nonnull
    public PhysicsBodyType getBodyType() {
        return bodyType;
    }

    @Nonnull
    public Vector3f getPosition() {
        return position;
    }

    @Nonnull
    public PersistentQuaternion getRotation() {
        return rotation;
    }

    @Nonnull
    public Vector3f getLinearVelocity() {
        return linearVelocity;
    }

    @Nonnull
    public Vector3f getAngularVelocity() {
        return angularVelocity;
    }

    public int resolveSpaceId(@Nullable SpaceId defaultSpaceId) {
        if (spaceId > 0) {
            return spaceId;
        }
        return defaultSpaceId != null ? defaultSpaceId.value() : DEFAULT_SPACE_ID;
    }

    @Nullable
    public String restoreValidationFailureReason() {
        if (bodyId == null) {
            return "missing body id";
        }
        if (shapeType == ShapeType.UNKNOWN || shapeType == ShapeType.VOXELS) {
            return "unsupported body shape";
        }
        if (!isFiniteVector(position)) {
            return "invalid position";
        }
        if (!isFiniteVector(linearVelocity)) {
            return "invalid linear velocity";
        }
        if (!isFiniteVector(angularVelocity)) {
            return "invalid angular velocity";
        }
        if (!isNonNegativeFiniteForRestore(mass)) {
            return "invalid mass";
        }
        if (bodyType == PhysicsBodyType.DYNAMIC && mass <= 0.0f) {
            return "invalid dynamic mass";
        }
        if (!isNonNegativeFiniteForRestore(friction)) {
            return "invalid friction";
        }
        if (!isNonNegativeFiniteForRestore(restitution)) {
            return "invalid restitution";
        }
        if (!isNonNegativeFiniteForRestore(linearDamping)) {
            return "invalid linear damping";
        }
        if (!isNonNegativeFiniteForRestore(angularDamping)) {
            return "invalid angular damping";
        }
        if (shapeType == ShapeType.BOX && !isPositiveFiniteVector(boxHalfExtents)) {
            return "invalid box half extents";
        }
        if (shapeType == ShapeType.SPHERE && !isPositiveFiniteForRestore(sphereRadius)) {
            return "invalid sphere radius";
        }
        if (shapeType == ShapeType.PLANE && !Float.isFinite(planeGroundY)) {
            return "invalid plane height";
        }
        if (usesRadiusAndHalfHeight(shapeType)
            && (!isPositiveFiniteForRestore(sphereRadius) || !isPositiveFiniteForRestore(halfHeight))) {
            return "invalid swept shape dimensions";
        }
        return null;
    }

    public void updateFromBody(@Nonnull PhysicsBody body, @Nullable SpaceId spaceId) {
        this.spaceId = spaceId != null ? sanitizeSpaceId(spaceId.value()) : DEFAULT_SPACE_ID;
        shapeType = body.getShapeType() != null ? body.getShapeType() : ShapeType.UNKNOWN;
        shapeAxis = body.getShapeAxis() != null ? body.getShapeAxis() : PhysicsAxis.Y;
        Vector3f halfExtents = body.getBoxHalfExtents();
        if (isFiniteVector(halfExtents)) {
            boxHalfExtents.set(halfExtents);
        } else {
            boxHalfExtents.zero();
        }
        sphereRadius = positiveFiniteOrZeroForRestore(body.getSphereRadius());
        halfHeight = positiveFiniteOrZeroForRestore(body.getHalfHeight());
        planeGroundY = shapeType == ShapeType.PLANE ? finiteOrZero(body.getPlaneGroundY()) : 0.0f;
        bodyType = body.getBodyType() != null ? body.getBodyType() : PhysicsBodyType.DYNAMIC;
        mass = nonNegativeFiniteOrDefaultForRestore(body.getMass(), 1.0f);
        copyFiniteVectorOrZero(position, body.getPosition());
        var bodyRotation = body.getRotation();
        rotation.set(bodyRotation);
        copyFiniteVectorOrZero(linearVelocity, body.getLinearVelocity());
        copyFiniteVectorOrZero(angularVelocity, body.getAngularVelocity());
        friction = nonNegativeFiniteOrZeroForRestore(body.getFriction());
        restitution = nonNegativeFiniteOrZeroForRestore(body.getRestitution());
        linearDamping = nonNegativeFiniteOrZeroForRestore(body.getLinearDamping());
        angularDamping = nonNegativeFiniteOrZeroForRestore(body.getAngularDamping());
        sensor = body.isSensor();
        collisionGroup = body.getCollisionGroup();
        collisionMask = body.getCollisionMask();
        continuousCollisionEnabled = body.isContinuousCollisionEnabled();
        sleeping = body.isSleeping();
    }

    @Nonnull
    public PhysicsBody createBody(@Nonnull PhysicsSpace space) {
        float dynamicMass = bodyType == PhysicsBodyType.DYNAMIC ? mass : 0.0f;
        return switch (shapeType) {
            case BOX -> space.createBox(boxHalfExtents, dynamicMass);
            case SPHERE -> space.createSphere(sphereRadius, dynamicMass);
            case CAPSULE -> space.createCapsule(sphereRadius, halfHeight, shapeAxis, dynamicMass);
            case CYLINDER -> space.createCylinder(sphereRadius, halfHeight, shapeAxis, dynamicMass);
            case CONE -> space.createCone(sphereRadius, halfHeight, shapeAxis, dynamicMass);
            case PLANE -> space.createStaticPlane(finiteOrZero(planeGroundY));
            case VOXELS -> throw new IllegalStateException(
                "PersistentPhysicsBodyState cannot rebuild streamed voxel terrain bodies");
            case UNKNOWN -> throw new IllegalStateException("Persistent body shape is unknown");
        };
    }

    public void applyToBody(@Nonnull PhysicsBody body) {
        body.setBodyType(bodyType);
        body.setMass(mass);
        body.setPosition(position);
        body.setRotation(rotation.toQuaternionf());
        body.setLinearVelocity(linearVelocity);
        body.setAngularVelocity(angularVelocity);
        body.setFriction(friction);
        body.setRestitution(restitution);
        body.setDamping(linearDamping, angularDamping);
        body.setSensor(sensor);
        body.setCollisionFilter(collisionGroup, collisionMask);
        body.setContinuousCollisionEnabled(continuousCollisionEnabled);
        if (sleeping && bodyType == PhysicsBodyType.DYNAMIC) {
            body.sleep();
        } else {
            body.activate();
        }
    }

    @Nonnull
    public PersistentPhysicsBodyState copy() {
        PersistentPhysicsBodyState copy = new PersistentPhysicsBodyState();
        copy.bodyId = bodyId;
        copy.spaceId = spaceId;
        copy.shapeType = shapeType;
        copy.shapeAxis = shapeAxis;
        copy.boxHalfExtents.set(boxHalfExtents);
        copy.sphereRadius = sphereRadius;
        copy.halfHeight = halfHeight;
        copy.planeGroundY = planeGroundY;
        copy.bodyType = bodyType;
        copy.mass = mass;
        copy.position.set(position);
        copy.rotation = rotation.copy();
        copy.linearVelocity.set(linearVelocity);
        copy.angularVelocity.set(angularVelocity);
        copy.friction = friction;
        copy.restitution = restitution;
        copy.linearDamping = linearDamping;
        copy.angularDamping = angularDamping;
        copy.sensor = sensor;
        copy.collisionGroup = collisionGroup;
        copy.collisionMask = collisionMask;
        copy.continuousCollisionEnabled = continuousCollisionEnabled;
        copy.sleeping = sleeping;
        return copy;
    }

    private static int sanitizeSpaceId(@Nullable Integer value) {
        return value != null && value > 0 ? value : DEFAULT_SPACE_ID;
    }

    private static void copyFiniteVectorOrZero(@Nonnull Vector3f target, @Nullable Vector3f value) {
        if (isFiniteVector(value)) {
            target.set(value);
        } else {
            target.zero();
        }
    }

    private static void copyVectorOrZeroIfNull(@Nonnull Vector3f target, @Nullable Vector3f value) {
        if (value != null) {
            target.set(value);
        } else {
            target.zero();
        }
    }

    private static boolean isFiniteVector(@Nullable Vector3f value) {
        return value != null
            && Float.isFinite(value.x)
            && Float.isFinite(value.y)
            && Float.isFinite(value.z);
    }

    private static boolean isPositiveFiniteVector(@Nonnull Vector3f value) {
        return isPositiveFiniteForRestore(value.x)
            && isPositiveFiniteForRestore(value.y)
            && isPositiveFiniteForRestore(value.z);
    }

    private static float positiveFiniteOrZeroForRestore(float value) {
        return isPositiveFiniteForRestore(value) ? value : 0.0f;
    }

    private static boolean isPositiveFiniteForRestore(float value) {
        return Float.isFinite(value) && value > 0.0f;
    }

    private static float nonNegativeFiniteOrZeroForRestore(float value) {
        return isNonNegativeFiniteForRestore(value) ? value : 0.0f;
    }

    private static float nonNegativeFiniteOrDefaultForRestore(float value, float defaultValue) {
        return isNonNegativeFiniteForRestore(value) ? value : defaultValue;
    }

    private static boolean isNonNegativeFiniteForRestore(float value) {
        return Float.isFinite(value) && value >= 0.0f;
    }

    private static boolean usesRadiusAndHalfHeight(@Nullable ShapeType type) {
        return type == ShapeType.CAPSULE || type == ShapeType.CYLINDER || type == ShapeType.CONE;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }
}
