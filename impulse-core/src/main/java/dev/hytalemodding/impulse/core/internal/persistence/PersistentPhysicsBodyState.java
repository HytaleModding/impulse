package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.internal.resources.body.PhysicsBodyRegistration;
import dev.hytalemodding.impulse.core.plugin.codec.ImpulseCodecs;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * World-level persistent state for one physics body.
 */
public class PersistentPhysicsBodyState {

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsBodyState> CODEC = BuilderCodec.builder(
            PersistentPhysicsBodyState.class,
            PersistentPhysicsBodyState::new)
        .append(new KeyedCodec<>("BodyId", Codec.UUID_BINARY),
            (state, value) -> state.bodyId = value,
            PersistentPhysicsBodyState::getBodyIdValue)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER),
            (state, value) -> state.spaceId = value,
            PersistentPhysicsBodyState::getSpaceId)
        .addValidator(Validators.nonNull())
        .addValidator(Validators.range(1, Integer.MAX_VALUE))
        .add()
        .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class)),
            (state, value) -> state.shapeType = value,
            PersistentPhysicsBodyState::getShapeType)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.persistentShapeType())
        .add()
        .append(new KeyedCodec<>("ShapeAxis", new EnumCodec<>(PhysicsAxis.class)),
            (state, value) -> state.shapeAxis = value,
            PersistentPhysicsBodyState::getShapeAxis)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("BoxHalfExtents", Vector3fUtil.CODEC),
            (state, value) -> state.boxHalfExtents.set(value),
            PersistentPhysicsBodyState::getBoxHalfExtents)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.finiteVector(
            "Persisted body box half extents must be finite"))
        .add()
        .append(new KeyedCodec<>("SphereRadius", Codec.FLOAT),
            (state, value) -> state.sphereRadius = value,
            PersistentPhysicsBodyState::getSphereRadius)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted body sphere radius must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT),
            (state, value) -> state.halfHeight = value,
            PersistentPhysicsBodyState::getHalfHeight)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted body half height must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class)),
            (state, value) -> state.bodyType = value,
            PersistentPhysicsBodyState::getBodyType)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("Mass", Codec.FLOAT),
            (state, value) -> state.mass = value,
            PersistentPhysicsBodyState::getMass)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted body mass must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("Position", Vector3fUtil.CODEC),
            (state, value) -> state.position.set(value),
            PersistentPhysicsBodyState::getPosition)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.finiteVector(
            "Persisted body position must be finite"))
        .add()
        .append(new KeyedCodec<>("Rotation", ImpulseCodecs.QUATERNIONF),
            (state, value) -> {
                if (value != null) {
                    state.rotation.set(value);
                }
            },
            PersistentPhysicsBodyState::getRotation)
        .addValidator(Validators.nonNull())
        .add()
        .append(new KeyedCodec<>("LinearVelocity", Vector3fUtil.CODEC),
            (state, value) -> state.linearVelocity.set(value),
            PersistentPhysicsBodyState::getLinearVelocity)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.finiteVector(
            "Persisted body linear velocity must be finite"))
        .add()
        .append(new KeyedCodec<>("AngularVelocity", Vector3fUtil.CODEC),
            (state, value) -> state.angularVelocity.set(value),
            PersistentPhysicsBodyState::getAngularVelocity)
        .addValidator(Validators.nonNull())
        .addValidator(PersistentPhysicsValidation.finiteVector(
            "Persisted body angular velocity must be finite"))
        .add()
        .append(new KeyedCodec<>("Friction", Codec.FLOAT),
            (state, value) -> state.friction = value,
            PersistentPhysicsBodyState::getFriction)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted body friction must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("Restitution", Codec.FLOAT),
            (state, value) -> state.restitution = value,
            PersistentPhysicsBodyState::getRestitution)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted body restitution must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT),
            (state, value) -> state.linearDamping = value,
            PersistentPhysicsBodyState::getLinearDamping)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted body linear damping must be finite and >= 0"))
        .add()
        .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT),
            (state, value) -> state.angularDamping = value,
            PersistentPhysicsBodyState::getAngularDamping)
        .addValidator(PersistentPhysicsValidation.nonNegativeFiniteFloat(
            "Persisted body angular damping must be finite and >= 0"))
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
    private int spaceId;
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
    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
    @Getter
    private float mass = 1.0f;
    @Nonnull
    private final Vector3f position = new Vector3f();
    @Nonnull
    private final Quaternionf rotation = new Quaternionf();
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
    public static PersistentPhysicsBodyState from(@Nonnull PhysicsBodyRegistration registration) {
        PersistentPhysicsBodyState state = new PersistentPhysicsBodyState();
        state.bodyId = registration.id().value();
        state.updateFromBody(registration.body(), registration.spaceId());
        return state;
    }

    @Nullable
    public RigidBodyKey getBodyId() {
        return getBodyKey();
    }

    @Nullable
    public RigidBodyKey getBodyKey() {
        return bodyId != null ? RigidBodyKey.of(bodyId) : null;
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
    public Quaternionf getRotation() {
        return new Quaternionf(rotation);
    }

    @Nonnull
    public Vector3f getLinearVelocity() {
        return linearVelocity;
    }

    @Nonnull
    public Vector3f getAngularVelocity() {
        return angularVelocity;
    }

    public int resolveSpaceId() {
        return spaceId;
    }

    @Nullable
    public String restoreValidationFailureReason() {
        if (bodyId == null) {
            return "missing body key";
        }
        if (shapeType == ShapeType.UNKNOWN || shapeType == ShapeType.VOXELS) {
            return "unsupported body shape";
        }
        if (!isFiniteVector(position)) {
            return "invalid position";
        }
        if (!ImpulseCodecs.isFiniteAndNonZero(rotation)) {
            return "invalid rotation";
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
        if (usesRadiusAndHalfHeight(shapeType)
            && (!isPositiveFiniteForRestore(sphereRadius) || !isPositiveFiniteForRestore(halfHeight))) {
            return "invalid swept shape dimensions";
        }
        return null;
    }

    public void updateFromBody(@Nonnull PhysicsBody body, @Nonnull SpaceId spaceId) {
        Objects.requireNonNull(spaceId, "spaceId");
        if (spaceId.value() <= 0) {
            throw new IllegalArgumentException(
                "Persistent body state requires a positive explicit space id");
        }
        this.spaceId = spaceId.value();
        shapeType = body.getShapeType();
        shapeAxis = body.getShapeAxis();
        Vector3f halfExtents = body.getBoxHalfExtents();
        if (isFiniteVector(halfExtents)) {
            boxHalfExtents.set(halfExtents);
        } else {
            boxHalfExtents.zero();
        }
        sphereRadius = positiveFiniteOrZeroForRestore(body.getSphereRadius());
        halfHeight = positiveFiniteOrZeroForRestore(body.getHalfHeight());
        bodyType = body.getBodyType();
        mass = nonNegativeFiniteOrDefaultForSnapshot(body.getMass(), 1.0f);
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
            case PLANE -> space.createStaticPlane(finiteOrZero(position.y));
            case VOXELS -> throw new IllegalStateException(
                "PersistentPhysicsBodyState cannot rebuild streamed voxel terrain bodies");
            case UNKNOWN -> throw new IllegalStateException("Persistent body shape is unknown");
        };
    }

    public void applyToBody(@Nonnull PhysicsBody body) {
        body.setBodyType(bodyType);
        body.setMass(mass);
        body.setPosition(position);
        body.setRotation(rotation);
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
        copy.bodyType = bodyType;
        copy.mass = mass;
        copy.position.set(position);
        copy.rotation.set(rotation);
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

    private static void copyFiniteVectorOrZero(@Nonnull Vector3f target, @Nullable Vector3f value) {
        if (isFiniteVector(value)) {
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

    private static float nonNegativeFiniteOrDefaultForSnapshot(float value, float defaultValue) {
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
