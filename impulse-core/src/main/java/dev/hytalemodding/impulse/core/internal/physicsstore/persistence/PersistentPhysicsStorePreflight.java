package dev.hytalemodding.impulse.core.internal.physicsstore.persistence;

import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldCollisionSettings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Preflight validation for persisted PhysicsStore DTOs before backend mutation.
 */
public final class PersistentPhysicsStorePreflight {

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private PersistentPhysicsStorePreflight() {
    }

    @Nonnull
    public static Result validate(@Nonnull PersistentPhysicsStoreResource resource) {
        List<String> errors = new ArrayList<>();
        if (resource.getSchemaVersion() != PersistentPhysicsStoreResource.CURRENT_SCHEMA_VERSION) {
            errors.add("Malformed PhysicsStore schema version: " + resource.getSchemaVersion());
        }

        Set<UUID> spaces = collectSpaces(resource.getSpaces(), errors);
        Set<UUID> bodies = collectBodies(resource.getBodies(), spaces, errors);
        Set<UUID> shapes = collectShapes(resource.getShapes(), errors);
        Set<UUID> materials = collectMaterials(resource.getMaterials(), errors);
        Set<UUID> colliders = collectColliders(resource.getColliders(),
            bodies,
            shapes,
            materials,
            errors);
        validateBodyColliderRefs(resource.getBodies(), colliders, errors);
        validateJoints(resource.getJoints(), spaces, bodies, errors);
        validateTerrain(resource.getTerrainColliders(), spaces, errors);
        return new Result(errors.isEmpty(), errors);
    }

    @Nonnull
    private static Set<UUID> collectSpaces(@Nonnull PersistentSpaceDto[] spaces,
        @Nonnull List<String> errors) {
        Set<UUID> seen = new HashSet<>();
        for (PersistentSpaceDto space : spaces) {
            UUID uuid = space.getSpaceUuid();
            requireUuid("space", uuid, errors);
            if (!seen.add(uuid)) {
                errors.add("Duplicate PhysicsStore space UUID " + uuid);
            }
            if (space.getBackendId().isBlank()) {
                errors.add("PhysicsStore space " + uuid + " has blank backend id");
            } else {
                try {
                    Impulse.getRuntimeProvider(new BackendId(space.getBackendId()));
                } catch (RuntimeException exception) {
                    errors.add("PhysicsStore space " + uuid
                        + " references unavailable backend id " + space.getBackendId());
                }
            }
            if (!PhysicsStorePersistenceValidation.isFinite(space.getGravity())) {
                errors.add("PhysicsStore space " + uuid + " has non-finite gravity");
            }
            if (space.getWorldCollisionRadius() < 1
                || space.getWorldCollisionRadius()
                > PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_RADIUS) {
                errors.add("PhysicsStore space " + uuid
                    + " has invalid world collision radius");
            }
            if (space.getWorldCollisionBodyRadius() < 1
                || space.getWorldCollisionBodyRadius()
                > PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_BODY_RADIUS) {
                errors.add("PhysicsStore space " + uuid
                    + " has invalid world collision body radius");
            }
            if (space.getWorldCollisionTtlTicks() < 1
                || space.getWorldCollisionTtlTicks()
                > PhysicsWorldCollisionSettings.MAX_WORLD_COLLISION_TTL_TICKS) {
                errors.add("PhysicsStore space " + uuid
                    + " has invalid world collision TTL");
            }
            if (!Float.isFinite(space.getTerrainFriction())
                || space.getTerrainFriction() < 0.0f) {
                errors.add("PhysicsStore space " + uuid
                    + " has invalid terrain friction");
            }
            if (!Float.isFinite(space.getTerrainRestitution())
                || space.getTerrainRestitution() < 0.0f) {
                errors.add("PhysicsStore space " + uuid
                    + " has invalid terrain restitution");
            }
            try {
                space.toSettings();
            } catch (RuntimeException exception) {
                errors.add("PhysicsStore space " + uuid + " has invalid space settings: "
                    + exception.getMessage());
            }
        }
        return seen;
    }

    @Nonnull
    private static Set<UUID> collectBodies(@Nonnull PersistentBodyDto[] bodies,
        @Nonnull Set<UUID> spaces,
        @Nonnull List<String> errors) {
        Set<UUID> seen = new HashSet<>();
        for (PersistentBodyDto body : bodies) {
            UUID uuid = body.getBodyUuid();
            requireUuid("body", uuid, errors);
            if (!seen.add(uuid)) {
                errors.add("Duplicate PhysicsStore body UUID " + uuid);
            }
            if (!spaces.contains(body.getSpaceUuid())) {
                errors.add("Body " + uuid + " references missing space " + body.getSpaceUuid());
            }
            if (!Float.isFinite(body.getMass()) || body.getMass() < 0.0f) {
                errors.add("Body " + uuid + " has invalid mass");
            }
            if (!Float.isFinite(body.getLinearDamping()) || body.getLinearDamping() < 0.0f) {
                errors.add("Body " + uuid + " has invalid linear damping");
            }
            if (!Float.isFinite(body.getAngularDamping()) || body.getAngularDamping() < 0.0f) {
                errors.add("Body " + uuid + " has invalid angular damping");
            }
            PersistentBodyRuntimeStateDto runtime = body.getRuntimeState();
            if (!PhysicsStorePersistenceValidation.isFinite(runtime.getPosition())
                || !PhysicsStorePersistenceValidation.isFinite(runtime.getRotation())
                || !PhysicsStorePersistenceValidation.isFinite(runtime.getLinearVelocity())
                || !PhysicsStorePersistenceValidation.isFinite(runtime.getAngularVelocity())) {
                errors.add("Body " + uuid + " has non-finite runtime state");
            }
        }
        return seen;
    }

    @Nonnull
    private static Set<UUID> collectShapes(@Nonnull PersistentShapeDto[] shapes,
        @Nonnull List<String> errors) {
        Set<UUID> seen = new HashSet<>();
        for (PersistentShapeDto shape : shapes) {
            UUID uuid = shape.getShapeUuid();
            requireUuid("shape", uuid, errors);
            if (!seen.add(uuid)) {
                errors.add("Duplicate PhysicsStore shape UUID " + uuid);
            }
            if (!Float.isFinite(shape.getGroundY())) {
                errors.add("Shape " + uuid + " has non-finite groundY");
            }
        }
        return seen;
    }

    @Nonnull
    private static Set<UUID> collectMaterials(@Nonnull PersistentMaterialDto[] materials,
        @Nonnull List<String> errors) {
        Set<UUID> seen = new HashSet<>();
        for (PersistentMaterialDto material : materials) {
            UUID uuid = material.getMaterialUuid();
            requireUuid("material", uuid, errors);
            if (!seen.add(uuid)) {
                errors.add("Duplicate PhysicsStore material UUID " + uuid);
            }
            if (!Float.isFinite(material.getFriction()) || material.getFriction() < 0.0f) {
                errors.add("Material " + uuid + " has invalid friction");
            }
            if (!Float.isFinite(material.getRestitution()) || material.getRestitution() < 0.0f) {
                errors.add("Material " + uuid + " has invalid restitution");
            }
        }
        return seen;
    }

    @Nonnull
    private static Set<UUID> collectColliders(@Nonnull PersistentColliderDto[] colliders,
        @Nonnull Set<UUID> bodies,
        @Nonnull Set<UUID> shapes,
        @Nonnull Set<UUID> materials,
        @Nonnull List<String> errors) {
        Set<UUID> seen = new HashSet<>();
        for (PersistentColliderDto collider : colliders) {
            UUID uuid = collider.getColliderUuid();
            requireUuid("collider", uuid, errors);
            if (!seen.add(uuid)) {
                errors.add("Duplicate PhysicsStore collider UUID " + uuid);
            }
            if (!bodies.contains(collider.getBodyUuid())) {
                errors.add("Collider " + uuid + " references missing body "
                    + collider.getBodyUuid());
            }
            if (!shapes.contains(collider.getShapeUuid())) {
                errors.add("Collider " + uuid + " references missing shape "
                    + collider.getShapeUuid());
            }
            if (!materials.contains(collider.getMaterialUuid())) {
                errors.add("Collider " + uuid + " references missing material "
                    + collider.getMaterialUuid());
            }
        }
        return seen;
    }

    private static void validateBodyColliderRefs(@Nonnull PersistentBodyDto[] bodies,
        @Nonnull Set<UUID> colliders,
        @Nonnull List<String> errors) {
        for (PersistentBodyDto body : bodies) {
            for (UUID colliderUuid : body.getColliderUuids()) {
                if (!colliders.contains(colliderUuid)) {
                    errors.add("Body " + body.getBodyUuid()
                        + " references missing collider " + colliderUuid);
                }
            }
        }
    }

    private static void validateJoints(@Nonnull PersistentJointDto[] joints,
        @Nonnull Set<UUID> spaces,
        @Nonnull Set<UUID> bodies,
        @Nonnull List<String> errors) {
        Set<UUID> seen = new HashSet<>();
        for (PersistentJointDto joint : joints) {
            UUID uuid = joint.getJointUuid();
            requireUuid("joint", uuid, errors);
            if (!seen.add(uuid)) {
                errors.add("Duplicate PhysicsStore joint UUID " + uuid);
            }
            if (!spaces.contains(joint.getSpaceUuid())) {
                errors.add("Joint " + uuid + " references missing space "
                    + joint.getSpaceUuid());
            }
        }
    }

    private static void validateTerrain(@Nonnull PersistentTerrainColliderDto[] terrainColliders,
        @Nonnull Set<UUID> spaces,
        @Nonnull List<String> errors) {
        Set<UUID> seen = new HashSet<>();
        for (PersistentTerrainColliderDto terrain : terrainColliders) {
            UUID uuid = terrain.getTerrainColliderUuid();
            requireUuid("terrain collider", uuid, errors);
            if (!seen.add(uuid)) {
                errors.add("Duplicate PhysicsStore terrain collider UUID " + uuid);
            }
            if (!spaces.contains(terrain.getSpaceUuid())) {
                errors.add("Terrain collider " + uuid + " references missing space "
                    + terrain.getSpaceUuid());
            }
            if (terrain.getSourceKey().isBlank()) {
                errors.add("Terrain collider " + uuid + " has blank source key");
            }
        }
    }

    private static void requireUuid(@Nonnull String kind,
        @Nonnull UUID uuid,
        @Nonnull List<String> errors) {
        if (NIL_UUID.equals(uuid)) {
            errors.add("PhysicsStore " + kind + " UUID cannot be nil");
        }
    }

    public record Result(boolean valid, @Nonnull List<String> errors) {

        public Result {
            errors = List.copyOf(errors);
        }

        @Nonnull
        public static Result success() {
            return new Result(true, List.of());
        }
    }
}
