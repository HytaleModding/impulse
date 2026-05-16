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
        .append(new KeyedCodec<>("SpaceId", Codec.INTEGER), (component, value) -> component.spaceId = value,
            component -> component.spaceId)
        .add()
        .append(new KeyedCodec<>("ShapeType", new EnumCodec<>(ShapeType.class)),
            (component, value) -> component.shapeType = value,
            component -> component.shapeType)
        .add()
        .append(new KeyedCodec<>("ShapeAxis", new EnumCodec<>(PhysicsAxis.class)),
            (component, value) -> component.shapeAxis = value,
            component -> component.shapeAxis)
        .add()
        .append(new KeyedCodec<>("BoxHalfExtents", Vector3fUtil.CODEC),
            (component, value) -> component.boxHalfExtents.set(value),
            component -> component.boxHalfExtents)
        .add()
        .append(new KeyedCodec<>("SphereRadius", Codec.FLOAT),
            (component, value) -> component.sphereRadius = value,
            component -> component.sphereRadius)
        .add()
        .append(new KeyedCodec<>("HalfHeight", Codec.FLOAT),
            (component, value) -> component.halfHeight = value,
            component -> component.halfHeight)
        .add()
        .append(new KeyedCodec<>("PlaneGroundY", Codec.FLOAT),
            (component, value) -> component.planeGroundY = value,
            component -> component.planeGroundY)
        .add()
        .append(new KeyedCodec<>("BodyType", new EnumCodec<>(PhysicsBodyType.class)),
            (component, value) -> component.bodyType = value,
            component -> component.bodyType)
        .add()
        .append(new KeyedCodec<>("Mass", Codec.FLOAT),
            (component, value) -> component.mass = value,
            component -> component.mass)
        .add()
        .append(new KeyedCodec<>("Position", Vector3fUtil.CODEC),
            (component, value) -> component.position.set(value),
            component -> component.position)
        .add()
        .append(new KeyedCodec<>("Rotation", PersistentQuaternion.CODEC),
            (component, value) -> component.rotation = value.copy(),
            component -> component.rotation)
        .add()
        .append(new KeyedCodec<>("LinearVelocity", Vector3fUtil.CODEC),
            (component, value) -> component.linearVelocity.set(value),
            component -> component.linearVelocity)
        .add()
        .append(new KeyedCodec<>("AngularVelocity", Vector3fUtil.CODEC),
            (component, value) -> component.angularVelocity.set(value),
            component -> component.angularVelocity)
        .add()
        .append(new KeyedCodec<>("Friction", Codec.FLOAT),
            (component, value) -> component.friction = value,
            component -> component.friction)
        .add()
        .append(new KeyedCodec<>("Restitution", Codec.FLOAT),
            (component, value) -> component.restitution = value,
            component -> component.restitution)
        .add()
        .append(new KeyedCodec<>("LinearDamping", Codec.FLOAT),
            (component, value) -> component.linearDamping = value,
            component -> component.linearDamping)
        .add()
        .append(new KeyedCodec<>("AngularDamping", Codec.FLOAT),
            (component, value) -> component.angularDamping = value,
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
    @Setter
    @Getter
    private float planeGroundY = Float.NaN;
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
        this.spaceId = spaceId != null ? spaceId.value() : DEFAULT_SPACE_ID;
        shapeType = body.getShapeType();
        shapeAxis = body.getShapeAxis();
        Vector3f halfExtents = body.getBoxHalfExtents();
        if (halfExtents != null) {
            boxHalfExtents.set(halfExtents);
        } else {
            boxHalfExtents.zero();
        }
        sphereRadius = body.getSphereRadius();
        halfHeight = body.getHalfHeight();
        planeGroundY = body.getPlaneGroundY();
        bodyType = body.getBodyType();
        mass = body.getMass();
        position.set(body.getPosition());
        rotation.set(body.getRotation());
        linearVelocity.set(body.getLinearVelocity());
        angularVelocity.set(body.getAngularVelocity());
        friction = body.getFriction();
        restitution = body.getRestitution();
        linearDamping = body.getLinearDamping();
        angularDamping = body.getAngularDamping();
        sensor = body.isSensor();
        collisionGroup = body.getCollisionGroup();
        collisionMask = body.getCollisionMask();
        continuousCollisionEnabled = body.isContinuousCollisionEnabled();
        sleeping = body.isSleeping();
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
            case PLANE -> space.createStaticPlane(planeGroundY);
            case VOXELS -> throw new IllegalStateException(
                "PersistentPhysicsBodyComponent cannot rebuild streamed voxel terrain bodies");
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
