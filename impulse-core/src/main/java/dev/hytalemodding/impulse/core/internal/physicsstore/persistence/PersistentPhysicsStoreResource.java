package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.validation.Validators;
import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreTypes;
import java.util.Arrays;
import javax.annotation.Nonnull;

/**
 * Canonical compact DTO persistence for PhysicsStore rows.
 */
public final class PersistentPhysicsStoreResource implements Resource<PhysicsStore> {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final PersistentSpaceDto[] EMPTY_SPACES = new PersistentSpaceDto[0];
    private static final PersistentBodyDto[] EMPTY_BODIES = new PersistentBodyDto[0];
    private static final PersistentColliderDto[] EMPTY_COLLIDERS = new PersistentColliderDto[0];
    private static final PersistentShapeDto[] EMPTY_SHAPES = new PersistentShapeDto[0];
    private static final PersistentMaterialDto[] EMPTY_MATERIALS = new PersistentMaterialDto[0];
    private static final PersistentJointDto[] EMPTY_JOINTS = new PersistentJointDto[0];
    private static final PersistentTerrainColliderDto[] EMPTY_TERRAIN_COLLIDERS =
        new PersistentTerrainColliderDto[0];

    @Nonnull
    public static final BuilderCodec<PersistentPhysicsStoreResource> CODEC =
        BuilderCodec.builder(PersistentPhysicsStoreResource.class,
                PersistentPhysicsStoreResource::new)
            .append(new KeyedCodec<>("SchemaVersion", Codec.INTEGER, false),
                PersistentPhysicsStoreResource::setSchemaVersion,
                PersistentPhysicsStoreResource::getSchemaVersion)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.range(CURRENT_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION))
            .add()
            .append(new KeyedCodec<>("Spaces",
                    new ArrayCodec<>(PersistentSpaceDto.CODEC, PersistentSpaceDto[]::new),
                    false),
                (resource, value) -> resource.spaces = copySpaces(value),
                PersistentPhysicsStoreResource::getSpaces)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonNullArrayElements())
            .add()
            .append(new KeyedCodec<>("Bodies",
                    new ArrayCodec<>(PersistentBodyDto.CODEC, PersistentBodyDto[]::new),
                    false),
                (resource, value) -> resource.bodies = copyBodies(value),
                PersistentPhysicsStoreResource::getBodies)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonNullArrayElements())
            .add()
            .append(new KeyedCodec<>("Colliders",
                    new ArrayCodec<>(PersistentColliderDto.CODEC, PersistentColliderDto[]::new),
                    false),
                (resource, value) -> resource.colliders = copyColliders(value),
                PersistentPhysicsStoreResource::getColliders)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonNullArrayElements())
            .add()
            .append(new KeyedCodec<>("Shapes",
                    new ArrayCodec<>(PersistentShapeDto.CODEC, PersistentShapeDto[]::new),
                    false),
                (resource, value) -> resource.shapes = copyShapes(value),
                PersistentPhysicsStoreResource::getShapes)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonNullArrayElements())
            .add()
            .append(new KeyedCodec<>("Materials",
                    new ArrayCodec<>(PersistentMaterialDto.CODEC, PersistentMaterialDto[]::new),
                    false),
                (resource, value) -> resource.materials = copyMaterials(value),
                PersistentPhysicsStoreResource::getMaterials)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonNullArrayElements())
            .add()
            .append(new KeyedCodec<>("Joints",
                    new ArrayCodec<>(PersistentJointDto.CODEC, PersistentJointDto[]::new),
                    false),
                (resource, value) -> resource.joints = copyJoints(value),
                PersistentPhysicsStoreResource::getJoints)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonNullArrayElements())
            .add()
            .append(new KeyedCodec<>("TerrainColliders",
                    new ArrayCodec<>(PersistentTerrainColliderDto.CODEC,
                        PersistentTerrainColliderDto[]::new),
                    false),
                (resource, value) -> resource.terrainColliders = copyTerrainColliders(value),
                PersistentPhysicsStoreResource::getTerrainColliders)
            .addValidator(Validators.nonNull())
            .addValidator(Validators.nonNullArrayElements())
            .add()
            .build();

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    @Nonnull
    private PersistentSpaceDto[] spaces = EMPTY_SPACES;
    @Nonnull
    private PersistentBodyDto[] bodies = EMPTY_BODIES;
    @Nonnull
    private PersistentColliderDto[] colliders = EMPTY_COLLIDERS;
    @Nonnull
    private PersistentShapeDto[] shapes = EMPTY_SHAPES;
    @Nonnull
    private PersistentMaterialDto[] materials = EMPTY_MATERIALS;
    @Nonnull
    private PersistentJointDto[] joints = EMPTY_JOINTS;
    @Nonnull
    private PersistentTerrainColliderDto[] terrainColliders = EMPTY_TERRAIN_COLLIDERS;

    public PersistentPhysicsStoreResource() {
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Schema version must be "
                + CURRENT_SCHEMA_VERSION);
        }
        this.schemaVersion = schemaVersion;
    }

    @Nonnull
    public PersistentSpaceDto[] getSpaces() {
        return copySpaces(spaces);
    }

    public void setSpaces(@Nonnull PersistentSpaceDto[] spaces) {
        this.spaces = copySpaces(spaces);
    }

    @Nonnull
    public PersistentBodyDto[] getBodies() {
        return copyBodies(bodies);
    }

    public void setBodies(@Nonnull PersistentBodyDto[] bodies) {
        this.bodies = copyBodies(bodies);
    }

    @Nonnull
    public PersistentColliderDto[] getColliders() {
        return copyColliders(colliders);
    }

    public void setColliders(@Nonnull PersistentColliderDto[] colliders) {
        this.colliders = copyColliders(colliders);
    }

    @Nonnull
    public PersistentShapeDto[] getShapes() {
        return copyShapes(shapes);
    }

    public void setShapes(@Nonnull PersistentShapeDto[] shapes) {
        this.shapes = copyShapes(shapes);
    }

    @Nonnull
    public PersistentMaterialDto[] getMaterials() {
        return copyMaterials(materials);
    }

    public void setMaterials(@Nonnull PersistentMaterialDto[] materials) {
        this.materials = copyMaterials(materials);
    }

    @Nonnull
    public PersistentJointDto[] getJoints() {
        return copyJoints(joints);
    }

    public void setJoints(@Nonnull PersistentJointDto[] joints) {
        this.joints = copyJoints(joints);
    }

    @Nonnull
    public PersistentTerrainColliderDto[] getTerrainColliders() {
        return copyTerrainColliders(terrainColliders);
    }

    public void setTerrainColliders(@Nonnull PersistentTerrainColliderDto[] terrainColliders) {
        this.terrainColliders = copyTerrainColliders(terrainColliders);
    }

    @Nonnull
    public PersistentPhysicsStorePreflight.Result preflight() {
        return PersistentPhysicsStorePreflight.validate(this);
    }

    @Nonnull
    @Override
    public PersistentPhysicsStoreResource clone() {
        PersistentPhysicsStoreResource copy = new PersistentPhysicsStoreResource();
        copy.schemaVersion = schemaVersion;
        copy.spaces = copySpaces(spaces);
        copy.bodies = copyBodies(bodies);
        copy.colliders = copyColliders(colliders);
        copy.shapes = copyShapes(shapes);
        copy.materials = copyMaterials(materials);
        copy.joints = copyJoints(joints);
        copy.terrainColliders = copyTerrainColliders(terrainColliders);
        return copy;
    }

    @Nonnull
    public static ResourceType<PhysicsStore, PersistentPhysicsStoreResource> getResourceType() {
        return PhysicsStoreTypes.persistentStoreResourceType();
    }

    @Nonnull
    private static PersistentSpaceDto[] copySpaces(PersistentSpaceDto[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_SPACES;
        }
        return Arrays.stream(values).map(PersistentSpaceDto::copy).toArray(PersistentSpaceDto[]::new);
    }

    @Nonnull
    private static PersistentBodyDto[] copyBodies(PersistentBodyDto[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_BODIES;
        }
        return Arrays.stream(values).map(PersistentBodyDto::copy).toArray(PersistentBodyDto[]::new);
    }

    @Nonnull
    private static PersistentColliderDto[] copyColliders(PersistentColliderDto[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_COLLIDERS;
        }
        return Arrays.stream(values).map(PersistentColliderDto::copy)
            .toArray(PersistentColliderDto[]::new);
    }

    @Nonnull
    private static PersistentShapeDto[] copyShapes(PersistentShapeDto[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_SHAPES;
        }
        return Arrays.stream(values).map(PersistentShapeDto::copy)
            .toArray(PersistentShapeDto[]::new);
    }

    @Nonnull
    private static PersistentMaterialDto[] copyMaterials(PersistentMaterialDto[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_MATERIALS;
        }
        return Arrays.stream(values).map(PersistentMaterialDto::copy)
            .toArray(PersistentMaterialDto[]::new);
    }

    @Nonnull
    private static PersistentJointDto[] copyJoints(PersistentJointDto[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_JOINTS;
        }
        return Arrays.stream(values).map(PersistentJointDto::copy)
            .toArray(PersistentJointDto[]::new);
    }

    @Nonnull
    private static PersistentTerrainColliderDto[] copyTerrainColliders(
        PersistentTerrainColliderDto[] values) {
        if (values == null || values.length == 0) {
            return EMPTY_TERRAIN_COLLIDERS;
        }
        return Arrays.stream(values).map(PersistentTerrainColliderDto::copy)
            .toArray(PersistentTerrainColliderDto[]::new);
    }
}
