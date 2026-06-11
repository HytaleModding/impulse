package dev.hytalemodding.impulse.core.plugin.simulation;

import dev.hytalemodding.impulse.core.plugin.body.RigidBodyKey;
import dev.hytalemodding.impulse.core.plugin.snapshot.PhysicsBodySnapshotEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.joml.Vector3f;

/**
 * Small reusable physics command recipes for common gameplay impulses and forces.
 */
public final class PhysicsRecipes {

    private static final float EPSILON = 0.000001f;
    private static final PhysicsCommandRecipe EMPTY = commands -> {
    };

    private PhysicsRecipes() {
    }

    @Nonnull
    public static PhysicsCommandRecipe applyImpulse(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f impulse) {
        Objects.requireNonNull(bodyKey, "bodyKey");
        Objects.requireNonNull(impulse, "impulse");
        float x = finite(impulse.x, "impulse.x");
        float y = finite(impulse.y, "impulse.y");
        float z = finite(impulse.z, "impulse.z");
        return commands -> commands.applyBodyImpulse(bodyKey, x, y, z);
    }

    @Nonnull
    public static PhysicsCommandRecipe applyImpulse(@Nonnull Iterable<RigidBodyKey> bodyKeys,
        @Nonnull Vector3f impulse) {
        List<RigidBodyKey> keys = copyBodyKeys(bodyKeys);
        Objects.requireNonNull(impulse, "impulse");
        float x = finite(impulse.x, "impulse.x");
        float y = finite(impulse.y, "impulse.y");
        float z = finite(impulse.z, "impulse.z");
        if (keys.isEmpty()) {
            return EMPTY;
        }
        return commands -> {
            for (RigidBodyKey bodyKey : keys) {
                commands.applyBodyImpulse(bodyKey, x, y, z);
            }
        };
    }

    @Nonnull
    public static PhysicsCommandRecipe applyForce(@Nonnull RigidBodyKey bodyKey,
        @Nonnull Vector3f force) {
        Objects.requireNonNull(bodyKey, "bodyKey");
        Objects.requireNonNull(force, "force");
        float x = finite(force.x, "force.x");
        float y = finite(force.y, "force.y");
        float z = finite(force.z, "force.z");
        return commands -> commands.applyBodyForce(bodyKey, x, y, z);
    }

    @Nonnull
    public static PhysicsCommandRecipe applyForce(@Nonnull Iterable<RigidBodyKey> bodyKeys,
        @Nonnull Vector3f force) {
        List<RigidBodyKey> keys = copyBodyKeys(bodyKeys);
        Objects.requireNonNull(force, "force");
        float x = finite(force.x, "force.x");
        float y = finite(force.y, "force.y");
        float z = finite(force.z, "force.z");
        if (keys.isEmpty()) {
            return EMPTY;
        }
        return commands -> {
            for (RigidBodyKey bodyKey : keys) {
                commands.applyBodyForce(bodyKey, x, y, z);
            }
        };
    }

    @Nonnull
    public static PhysicsCommandRecipe radialImpulse(
        @Nonnull Iterable<PhysicsBodySnapshotEntry> bodies,
        @Nonnull Vector3f origin,
        float strength,
        float radius,
        @Nonnull PhysicsFalloff falloff) {
        List<VectorCommand> commands = radialCommands(bodies, origin, strength, radius, falloff);
        if (commands.isEmpty()) {
            return EMPTY;
        }
        return context -> {
            for (VectorCommand command : commands) {
                context.applyBodyImpulse(command.bodyKey(), command.x(), command.y(), command.z());
            }
        };
    }

    @Nonnull
    public static PhysicsCommandRecipe radialForce(
        @Nonnull Iterable<PhysicsBodySnapshotEntry> bodies,
        @Nonnull Vector3f origin,
        float strength,
        float radius,
        @Nonnull PhysicsFalloff falloff) {
        List<VectorCommand> commands = radialCommands(bodies, origin, strength, radius, falloff);
        if (commands.isEmpty()) {
            return EMPTY;
        }
        return context -> {
            for (VectorCommand command : commands) {
                context.applyBodyForce(command.bodyKey(), command.x(), command.y(), command.z());
            }
        };
    }

    @Nonnull
    private static List<RigidBodyKey> copyBodyKeys(@Nonnull Iterable<RigidBodyKey> bodyKeys) {
        Objects.requireNonNull(bodyKeys, "bodyKeys");
        List<RigidBodyKey> keys = new ArrayList<>();
        for (RigidBodyKey bodyKey : bodyKeys) {
            keys.add(Objects.requireNonNull(bodyKey, "bodyKey"));
        }
        return List.copyOf(keys);
    }

    @Nonnull
    private static List<VectorCommand> radialCommands(
        @Nonnull Iterable<PhysicsBodySnapshotEntry> bodies,
        @Nonnull Vector3f origin,
        float strength,
        float radius,
        @Nonnull PhysicsFalloff falloff) {
        Objects.requireNonNull(bodies, "bodies");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(falloff, "falloff");
        float originX = finite(origin.x, "origin.x");
        float originY = finite(origin.y, "origin.y");
        float originZ = finite(origin.z, "origin.z");
        float finiteStrength = finite(strength, "strength");
        float finiteRadius = finite(radius, "radius");
        if (finiteRadius <= 0.0f || finiteStrength == 0.0f) {
            return List.of();
        }

        List<VectorCommand> commands = new ArrayList<>();
        for (PhysicsBodySnapshotEntry entry : bodies) {
            Objects.requireNonNull(entry, "entry");
            if (!entry.snapshot().isDynamic() || entry.snapshot().sensor()) {
                continue;
            }
            float dx = entry.snapshot().positionX() - originX;
            float dy = entry.snapshot().positionY() - originY;
            float dz = entry.snapshot().positionZ() - originZ;
            float distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared <= EPSILON || distanceSquared > finiteRadius * finiteRadius) {
                continue;
            }
            float distance = (float) Math.sqrt(distanceSquared);
            float scale = safeScale(falloff.scale(distance, finiteRadius));
            if (scale <= 0.0f) {
                continue;
            }
            float magnitude = finiteStrength * scale / distance;
            commands.add(new VectorCommand(entry.bodyKey(),
                dx * magnitude,
                dy * magnitude,
                dz * magnitude));
        }
        return List.copyOf(commands);
    }

    private static float safeScale(float scale) {
        if (!Float.isFinite(scale)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, scale));
    }

    private static float finite(float value, @Nonnull String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private record VectorCommand(@Nonnull RigidBodyKey bodyKey, float x, float y, float z) {

        private VectorCommand {
            Objects.requireNonNull(bodyKey, "bodyKey");
        }
    }
}
