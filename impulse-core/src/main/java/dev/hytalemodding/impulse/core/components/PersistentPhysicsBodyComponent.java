package dev.hytalemodding.impulse.core.components;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3fUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsAxis;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.persistence.PersistentQuaternion;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

/**
 * Codec-backed physics body state that lives on a Hytale entity.
 *
 * <p>Hytale's serialization system persists this component alongside the entity.
 * On world load, the hydration systems read it to rebuild the live backend body
 * (stored in the runtime-only {@link PhysicsBodyComponent}) without respawning
 * the entity itself.</p>
 *
 * <p>The component stores the full body description (shape, type, mass, material
 * properties) and the last known dynamic state (position, rotation, velocities,
 * sleeping). The {@code needsBodyRebuild} flag is transient: it is set on decode
 * or when a manual snapshot load updates the state, and consumed by
 * {@link dev.hytalemodding.impulse.core.systems.PersistentPhysicsBodyHydrationSystem}.</p>
 *
 * <p>Shape-specific fields are only meaningful for their corresponding shape type
 * (e.g. {@code boxHalfExtents} for {@link ShapeType#BOX}, {@code sphereRadius}
 * for {@link ShapeType#SPHERE}). {@link ShapeType#VOXELS} is intentionally
 * unsupported because voxel terrain bodies are rebuilt from the world instead.</p>
 */
public class PersistentPhysicsBodyComponent implements Component<EntityStore> {

    public static final int DEFAULT_SPACE_ID = 0;

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsBodyComponent> CODEC = BuilderCodec.builder(
            PersistentPhysicsBodyComponent.class,
            PersistentPhysicsBodyComponent::new)
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER),
            (component, value) -> component.spaceId = sanitizeSpaceId(value),
            component -> component.spaceId)
        .add()
        .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class)),
            (component, value) -> component.shapeType = value != null ? value : ShapeType.UNKNOWN,
            component -> component.shapeType)
        .add()
        .append(new KeyedCodec<>("ShapeAxis", new EnumCodec<>(PhysicsAxis.class)),
            (component, value) -> component.shapeAxis = value != null ? value : PhysicsAxis.Y,
            component -> component.shapeAxis)
        .add()
        .append(new KeyedCodec<>("BoxHalfExtents", Vector3fUtil.CODEC),
            (component, value) -> copyFiniteVectorOrZero(component.boxHalfExtents, value),
            component -> component.boxHalfExtents)
        .add()
        .append(new KeyedCodec<>("SphereRadius", Codec.FLOAT),
            (component, value) -> component.sphereRadius = positiveFiniteOrZeroForRestore(value),
            component -> component.sphereRadius)
        .add()
        .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT),
            (component, value) -> component.halfHeight = positiveFiniteOrZeroForRestore(value),
            component -> component.halfHeight)
        .add()
        .append(new KeyedCodec<>("PlaneGroundY", Codec.FLOAT, false),
            (component, value) -> component.planeGroundY = finiteOrZero(value),
            component -> component.planeGroundY)
        .add()
        .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class)),
            (component, value) -> component.bodyType = value != null ? value : PhysicsBodyType.DYNAMIC,
            component -> component.bodyType)
        .add()
        .append(new KeyedCodec<>("Mass", Codec.FLOAT),
            (component, value) -> component.mass = nonNegativeFiniteOrDefaultForRestore(value, 1.0f),
            component -> component.mass)
        .add()
        .append(new KeyedCodec<>("Position", Vector3fUtil.CODEC),
            (component, value) -> copyVectorOrZeroIfNull(component.position, value),
            component -> component.position)
        .add()
        .append(new KeyedCodec<>("Rotation", PersistentQuaternion.CODEC),
            (component, value) -> component.rotation = value != null ? value.copy() : new PersistentQuaternion(),
            component -> component.rotation)
        .add()
        .append(new KeyedCodec<>("LinearVelocity", Vector3fUtil.CODEC),
            (component, value) -> copyVectorOrZeroIfNull(component.linearVelocity, value),
            component -> component.linearVelocity)
        .add()
        .append(new KeyedCodec<>("AngularVelocity", Vector3fUtil.CODEC),
            (component, value) -> copyVectorOrZeroIfNull(component.angularVelocity, value),
            component -> component.angularVelocity)
        .add()
        .append(new KeyedCodec<>("Friction", Codec.FLOAT),
            (component, value) -> component.friction = nonNegativeFiniteOrZeroForRestore(value),
            component -> component.friction)
        .add()
        .append(new KeyedCodec<>("Restitution", Codec.FLOAT),
            (component, value) -> component.restitution = nonNegativeFiniteOrZeroForRestore(value),
            component -> component.restitution)
        .add()
        .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT),
            (component, value) -> component.linearDamping = nonNegativeFiniteOrZeroForRestore(value),
            component -> component.linearDamping)
        .add()
        .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT),
            (component, value) -> component.angularDamping = nonNegativeFiniteOrZeroForRestore(value),
            component -> component.angularDamping)
        .add()
        .append(new KeyedCodec<>("Sensor", Codec.BOOLEAN),
            (component, value) -> component.sensor = value,
            component -> component.sensor)
        .add()
        .append(new KeyedCodec<>("CollisionGroup", Codec.INTEGER),
            (component, value) -> component.collisionGroup = value,
            component -> component.collisionGroup)
        .add()
        .append(new KeyedCodec<>("CollisionMask", Codec.INTEGER),
            (component, value) -> component.collisionMask = value,
            component -> component.collisionMask)
        .add()
        .append(new KeyedCodec<>("ContinuousCollision", Codec.BOOLEAN),
            (component, value) -> component.continuousCollisionEnabled = value,
            component -> component.continuousCollisionEnabled)
        .add()
        .append(new KeyedCodec<>("Sleeping", Codec.BOOLEAN),
            (component, value) -> component.sleeping = value,
            component -> component.sleeping)
        .add()
        .afterDecode(component -> component.needsBodyRebuild = true)
        .build();

    @Setter
    @Getter
    private int spaceId = DEFAULT_SPACE_ID;
    @Nonnull
    private ShapeType shapeType = ShapeType.UNKNOWN;
    @Nonnull
    private PhysicsAxis shapeAxis = PhysicsAxis.Y;
    @Nonnull
    private final Vector3f boxHalfExtents = new Vector3f();
    @Setter
    @Getter
    private float sphereRadius;
    @Setter
    @Getter
    private float halfHeight;
    @Getter
    private float planeGroundY;
    @Nonnull
    private PhysicsBodyType bodyType = PhysicsBodyType.DYNAMIC;
    @Setter
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
    @Setter
    @Getter
    private float friction;
    @Setter
    @Getter
    private float restitution;
    @Setter
    @Getter
    private float linearDamping;
    @Setter
    @Getter
    private float angularDamping;
    @Setter
    @Getter
    private boolean sensor;
    @Setter
    @Getter
    private int collisionGroup;
    @Setter
    @Getter
    private int collisionMask;
    @Setter
    @Getter
    private boolean continuousCollisionEnabled;
    @Setter
    @Getter
    private boolean sleeping;
    private transient boolean needsBodyRebuild;
    private transient int sleepingSyncSkipTicks;
    private transient int staticSyncSkipTicks;

    public PersistentPhysicsBodyComponent() {
    }

    @Nonnull
    public static PersistentPhysicsBodyComponent fromBody(@Nonnull PhysicsBody body,
        @Nullable SpaceId spaceId) {
        PersistentPhysicsBodyComponent component = new PersistentPhysicsBodyComponent();
        component.updateFromBody(body, spaceId);
        component.needsBodyRebuild = false;
        return component;
    }

    public static ComponentType<EntityStore, PersistentPhysicsBodyComponent> getComponentType() {
        return ImpulsePlugin.get().getPersistentPhysicsBodyComponentType();
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
        if (bodyRotation != null) {
            rotation.set(bodyRotation);
        } else {
            rotation = new PersistentQuaternion();
        }
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
        sleepingSyncSkipTicks = 0;
        staticSyncSkipTicks = 0;
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

    private static boolean isStaticBodyType(@Nullable PhysicsBodyType type) {
        return type == PhysicsBodyType.STATIC;
    }

    private static boolean usesRadiusAndHalfHeight(@Nullable ShapeType type) {
        return type == ShapeType.CAPSULE || type == ShapeType.CYLINDER || type == ShapeType.CONE;
    }

    @Nullable
    public String restoreValidationFailureReason() {
        if (shapeType == null || shapeType == ShapeType.UNKNOWN || shapeType == ShapeType.VOXELS) {
            return "unsupported body shape";
        }
        if (bodyType == null) {
            return "missing body type";
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

    public boolean shouldDeferStaticUpdate(@Nonnull PhysicsBody body,
        @Nullable SpaceId bodySpaceId,
        int intervalTicks) {
        if (intervalTicks <= 0 || needsBodyRebuild || !isStaticBodyType(body.getBodyType())) {
            staticSyncSkipTicks = 0;
            return false;
        }
        int resolvedSpaceId = bodySpaceId != null ? bodySpaceId.value() : DEFAULT_SPACE_ID;
        if (spaceId != resolvedSpaceId || !isStaticBodyType(bodyType)) {
            staticSyncSkipTicks = 0;
            return false;
        }
        if (staticSyncSkipTicks < intervalTicks) {
            staticSyncSkipTicks++;
            return true;
        }
        staticSyncSkipTicks = 0;
        return false;
    }

    public boolean shouldDeferSleepingUpdate(@Nonnull PhysicsBody body,
        @Nullable SpaceId bodySpaceId,
        int intervalTicks) {
        if (intervalTicks <= 0 || needsBodyRebuild) {
            return false;
        }
        int resolvedSpaceId = bodySpaceId != null ? bodySpaceId.value() : DEFAULT_SPACE_ID;
        if (!sleeping || spaceId != resolvedSpaceId || !body.isSleeping()) {
            sleepingSyncSkipTicks = 0;
            return false;
        }
        if (sleepingSyncSkipTicks < intervalTicks) {
            sleepingSyncSkipTicks++;
            return true;
        }
        sleepingSyncSkipTicks = 0;
        return false;
    }

    @Nonnull
    public ShapeType getShapeType() {
        return shapeType;
    }

    public void setShapeType(@Nonnull ShapeType shapeType) {
        this.shapeType = shapeType;
    }

    @Nonnull
    public PhysicsAxis getShapeAxis() {
        return shapeAxis;
    }

    public void setShapeAxis(@Nonnull PhysicsAxis shapeAxis) {
        this.shapeAxis = shapeAxis;
    }

    public void setPlaneGroundY(float planeGroundY) {
        this.planeGroundY = finiteOrZero(planeGroundY);
    }

    @Nonnull
    public Vector3f getBoxHalfExtents() {
        return boxHalfExtents;
    }

    @Nonnull
    public PhysicsBodyType getBodyType() {
        return bodyType;
    }

    public void setBodyType(@Nonnull PhysicsBodyType bodyType) {
        this.bodyType = bodyType;
    }

    @Nonnull
    public Vector3f getPosition() {
        return position;
    }

    @Nonnull
    public PersistentQuaternion getRotation() {
        return rotation;
    }

    public void setRotation(@Nonnull PersistentQuaternion rotation) {
        this.rotation = rotation;
    }

    @Nonnull
    public Vector3f getLinearVelocity() {
        return linearVelocity;
    }

    @Nonnull
    public Vector3f getAngularVelocity() {
        return angularVelocity;
    }

    public boolean needsBodyRebuild() {
        return needsBodyRebuild;
    }

    public void markForBodyRebuild() {
        needsBodyRebuild = true;
    }

    public void clearBodyRebuildFlag() {
        needsBodyRebuild = false;
    }

    public int resolveSpaceId(@Nullable SpaceId defaultSpaceId) {
        if (spaceId > 0) {
            return spaceId;
        }
        return defaultSpaceId != null ? defaultSpaceId.value() : DEFAULT_SPACE_ID;
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
                "PersistentPhysicsBodyComponent cannot rebuild streamed voxel terrain bodies");
            case UNKNOWN -> throw new IllegalStateException("Persistent body shape is unknown");
        };
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
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
    @Override
    public PersistentPhysicsBodyComponent clone() {
        PersistentPhysicsBodyComponent copy = new PersistentPhysicsBodyComponent();
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
        copy.needsBodyRebuild = needsBodyRebuild;
        return copy;
    }
}
