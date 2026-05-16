package dev.hytalemodding.impulse.core.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * ECS resource that holds the physics spaces for a world.
 *
 * <p>Spaces are created explicitly by the consumer via {@link #createSpace}.
 * No space is created implicitly; the default space is opt-in and set by
 * the consumer at creation time or via {@link #setDefaultSpaceId}.</p>
 */
public class PhysicsWorldResource implements Resource<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");
    public static final int MIN_SIMULATION_STEPS = 1;
    public static final int MAX_SIMULATION_STEPS = 16;
    public static final float DEFAULT_MAX_STEP_DT = 1f / 20f;

    private final Int2ObjectMap<PhysicsSpace> spaces = new Int2ObjectOpenHashMap<>();

    /**
     * Per-space settings (world collision mode, radius, TTL, etc.). Keyed by space id value.
     */
    private final Int2ObjectMap<PhysicsSpaceSettings> spaceSettings = new Int2ObjectOpenHashMap<>();

    private final Map<PhysicsBody, Ref<EntityStore>> bodyOwners = new IdentityHashMap<>();

    @Getter
    private final WorldVoxelCollisionCache worldVoxelCollisionCache = new WorldVoxelCollisionCache();

    /**
     * The default space for this world, if one has been designated.
     * This is an optional convenience -- integrators can manage multiple spaces
     * without designating a default.
     */
    @Nullable
    private SpaceId defaultSpaceId;
    @Getter
    private int simulationSteps = MIN_SIMULATION_STEPS;

    @Nonnull
    private PhysicsStepMode stepMode = PhysicsStepMode.PROGRESSIVE_REFINEMENT;

    @Getter
    @Setter
    private float maxStepDt = DEFAULT_MAX_STEP_DT;

    private final Set<PhysicsBody> forcedContinuousCollisionBodies =
        Collections.newSetFromMap(new IdentityHashMap<>());

    private final Map<PhysicsBody, ChunkBoundarySafeState> chunkBoundarySafeStates =
        new IdentityHashMap<>();
    private final Map<PhysicsBody, ChunkBoundaryPauseState> chunkBoundaryPauseStates =
        new IdentityHashMap<>();

    public PhysicsWorldResource() {
    }

    @Nullable
    public SpaceId getDefaultSpaceId() {
        return defaultSpaceId;
    }

    @Nonnull
    public SpaceId requireDefaultSpaceId() {
        if (defaultSpaceId == null) {
            throw new IllegalStateException("No default physics space is configured");
        }
        return defaultSpaceId;
    }

    @Nullable
    public PhysicsSpace getDefaultSpace() {
        if (defaultSpaceId == null) {
            return null;
        }
        return getSpace(defaultSpaceId);
    }

    @Nonnull
    public PhysicsSpace requireDefaultSpace() {
        SpaceId spaceId = requireDefaultSpaceId();
        PhysicsSpace space = getSpace(spaceId);
        if (space == null) {
            throw new IllegalStateException("Default physics space id=" + spaceId
                + " is not registered");
        }
        return space;
    }

    public void setDefaultSpaceId(@Nullable SpaceId defaultSpaceId) {
        if (defaultSpaceId != null && !spaces.containsKey(defaultSpaceId.value())) {
            throw new IllegalArgumentException("Physics space id=" + defaultSpaceId
                + " is not registered");
        }
        this.defaultSpaceId = defaultSpaceId;
    }

    @Nonnull
    public PhysicsStepMode getStepMode() {
        return stepMode;
    }

    public void setStepMode(@Nonnull PhysicsStepMode stepMode) {
        this.stepMode = stepMode;
    }

    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId) {
        return createSpace(backendId, "<unknown>", PhysicsSpaceSettings.defaults(), false);
    }

    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId, @Nonnull String worldName) {
        return createSpace(backendId, worldName, PhysicsSpaceSettings.defaults(), false);
    }

    /**
     * Creates a new physics space with the given backend and settings.
     *
     * @param makeDefault if true, this space becomes the default space
     */
    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault) {
        return createSpace(backendId, SpaceId.next(), worldName, settings, makeDefault);
    }

    /**
     * Creates a new physics space with the given backend, explicit logical id, and settings.
     *
     * @param makeDefault if true, this space becomes the default space
     */
    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        boolean makeDefault) {
        if (spaces.containsKey(spaceId.value())) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is already registered");
        }
        SpaceId.reserveAtLeast(spaceId.value());

        LOGGER.at(Level.FINE).log(
            "World %s creating physics space using backend %s collision=%s",
            worldName,
            backendId,
            settings.getWorldCollisionMode());

        PhysicsSpace space = Impulse.createSpace(backendId, spaceId);
        spaces.put(space.getId().value(), space);
        spaceSettings.put(space.getId().value(), new PhysicsSpaceSettings(settings));
        if (makeDefault) {
            defaultSpaceId = space.getId();
        }

        LOGGER.at(Level.FINE).log(
            "World %s created physics space id=%s backend=%s collision=%s",
            worldName,
            space.getId(),
            space.getBackendId(),
            settings.getWorldCollisionMode());
        return space;
    }

    @Nullable
    public PhysicsSpace getSpace(@Nonnull SpaceId spaceId) {
        return spaces.get(spaceId.value());
    }

    @Nonnull
    public Collection<PhysicsSpace> getSpaces() {
        return new ArrayList<>(spaces.values());
    }

    /**
     * Iterate spaces without allocating a snapshot collection.
     * Use this from tick systems that do not mutate the space map while iterating.
     */
    @Nonnull
    public Iterable<PhysicsSpace> iterateSpaces() {
        return spaces.values();
    }

    public void removeSpace(@Nonnull SpaceId spaceId) {
        removeSpace(spaceId, "<unknown>");
    }

    public void removeSpace(@Nonnull SpaceId spaceId, @Nonnull String worldName) {
        PhysicsSpace removed = spaces.remove(spaceId.value());
        spaceSettings.remove(spaceId.value());
        worldVoxelCollisionCache.clear(spaceId, removed);
        if (defaultSpaceId != null && defaultSpaceId.equals(spaceId)) {
            defaultSpaceId = null;
        }
        if (removed != null) {
            LOGGER.at(Level.FINE).log(
                "World %s removed physics space id=%s backend=%s",
                worldName,
                removed.getId(),
                removed.getBackendId());
        }
    }

    public void clearAllSpaces(@Nonnull String worldName) {
        List<SpaceId> ids = new ArrayList<>(spaces.size());
        for (PhysicsSpace space : spaces.values()) {
            ids.add(space.getId());
        }
        for (SpaceId spaceId : ids) {
            removeSpace(spaceId, worldName);
        }
    }

    @Nonnull
    public PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId) {
        PhysicsSpaceSettings settings = spaceSettings.get(spaceId.value());
        if (settings == null) {
            throw new IllegalStateException("Physics space settings are missing for id=" + spaceId);
        }
        return settings;
    }

    public void setSpaceSettings(@Nonnull SpaceId spaceId, @Nonnull PhysicsSpaceSettings settings) {
        if (!spaces.containsKey(spaceId.value())) {
            throw new IllegalArgumentException("Physics space id=" + spaceId
                + " is not registered");
        }
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getBodyOwners() {
        List<Ref<EntityStore>> owners = new ArrayList<>();
        List<PhysicsBody> staleBodies = new ArrayList<>();
        for (Map.Entry<PhysicsBody, Ref<EntityStore>> entry : bodyOwners.entrySet()) {
            Ref<EntityStore> owner = entry.getValue();
            if (owner != null && owner.isValid()) {
                owners.add(owner);
            } else {
                staleBodies.add(entry.getKey());
            }
        }
        for (PhysicsBody body : staleBodies) {
            bodyOwners.remove(body);
            clearBodyRuntimeState(body);
        }
        return owners;
    }

    public void registerBodyOwner(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> owner) {
        bodyOwners.put(body, owner);
    }

    public void unregisterBodyOwner(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> owner) {
        Ref<EntityStore> current = bodyOwners.get(body);
        if (current == owner || owner.equals(current)) {
            bodyOwners.remove(body);
            clearBodyRuntimeState(body);
        }
    }

    public void clearBodyOwners() {
        bodyOwners.clear();
        forcedContinuousCollisionBodies.clear();
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
    }

    public void updateChunkBoundarySafeState(@Nonnull PhysicsBody body,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        ChunkBoundarySafeState state = chunkBoundarySafeStates.computeIfAbsent(body,
            ignored -> new ChunkBoundarySafeState());
        state.set(position, rotation);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(@Nonnull PhysicsBody body) {
        return chunkBoundarySafeStates.get(body);
    }

    public void pauseChunkBoundaryBody(@Nonnull PhysicsBody body,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        ChunkBoundaryPauseState state = chunkBoundaryPauseStates.computeIfAbsent(body,
            ignored -> new ChunkBoundaryPauseState());
        state.set(targetChunkIndex, originalBodyType, linearVelocity, angularVelocity);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull PhysicsBody body) {
        return chunkBoundaryPauseStates.get(body);
    }

    public void clearChunkBoundaryPauseState(@Nonnull PhysicsBody body) {
        chunkBoundaryPauseStates.remove(body);
    }

    public void clearBodyRuntimeState(@Nonnull PhysicsBody body) {
        forcedContinuousCollisionBodies.remove(body);
        chunkBoundarySafeStates.remove(body);
        chunkBoundaryPauseStates.remove(body);
    }

    public void markContinuousCollisionForced(@Nonnull PhysicsBody body) {
        forcedContinuousCollisionBodies.add(body);
    }

    @Nonnull
    public Collection<PhysicsBody> getForcedContinuousCollisionBodies() {
        return new ArrayList<>(forcedContinuousCollisionBodies);
    }

    public void clearForcedContinuousCollisionBodies() {
        forcedContinuousCollisionBodies.clear();
    }

    @Nullable
    public Ref<EntityStore> getBodyOwner(@Nonnull PhysicsBody body) {
        Ref<EntityStore> owner = bodyOwners.get(body);
        if (owner != null && !owner.isValid()) {
            bodyOwners.remove(body);
            clearBodyRuntimeState(body);
            return null;
        }
        return owner;
    }

    public void copyFrom(@Nonnull PhysicsWorldResource other) {
        if (this == other) {
            return;
        }
        spaces.clear();
        spaceSettings.clear();
        bodyOwners.clear();
        forcedContinuousCollisionBodies.clear();
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
        worldVoxelCollisionCache.copyFrom(new WorldVoxelCollisionCache());
        defaultSpaceId = other.defaultSpaceId;
        simulationSteps = other.simulationSteps;
        stepMode = other.stepMode;
        maxStepDt = other.maxStepDt;
        for (var entry : other.spaceSettings.int2ObjectEntrySet()) {
            spaceSettings.put(entry.getIntKey(), new PhysicsSpaceSettings(entry.getValue()));
        }
    }

    /**
     * Set how many fixed substeps are run for each server tick.
     * Higher values can improve stability at the cost of backend step time.
     */
    public void setSimulationSteps(int simulationSteps) {
        if (simulationSteps < MIN_SIMULATION_STEPS || simulationSteps > MAX_SIMULATION_STEPS) {
            throw new IllegalArgumentException("Simulation steps must be between "
                + MIN_SIMULATION_STEPS + " and " + MAX_SIMULATION_STEPS);
        }
        this.simulationSteps = simulationSteps;
    }

    @Nonnull
    public PhysicsSpace replaceSpace(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName) {
        if (!spaceId.equals(replacement.getId())) {
            throw new IllegalArgumentException("Replacement space id " + replacement.getId()
                + " does not match target id " + spaceId);
        }

        PhysicsSpace previous = spaces.get(spaceId.value());
        if (previous == null) {
            throw new IllegalStateException("Cannot replace missing physics space id=" + spaceId);
        }

        spaces.put(spaceId.value(), replacement);
        spaceSettings.putIfAbsent(spaceId.value(), PhysicsSpaceSettings.defaults());
        LOGGER.at(Level.INFO).log(
            "World %s replaced physics space id=%s backend=%s -> backend=%s",
            worldName,
            spaceId,
            previous.getBackendId(),
            replacement.getBackendId());
        return previous;
    }

    public static final class ChunkBoundarySafeState {

        @Nonnull
        private final Vector3f position = new Vector3f();
        @Nonnull
        private final Quaternionf rotation = new Quaternionf();

        public void set(@Nonnull Vector3f position, @Nonnull Quaternionf rotation) {
            this.position.set(position);
            this.rotation.set(rotation);
        }

        @Nonnull
        public Vector3f getPosition() {
            return position;
        }

        @Nonnull
        public Quaternionf getRotation() {
            return rotation;
        }
    }

    public static final class ChunkBoundaryPauseState {

        private long targetChunkIndex;
        @Nonnull
        private PhysicsBodyType originalBodyType = PhysicsBodyType.DYNAMIC;
        @Nonnull
        private final Vector3f linearVelocity = new Vector3f();
        @Nonnull
        private final Vector3f angularVelocity = new Vector3f();

        public void set(long targetChunkIndex,
            @Nonnull PhysicsBodyType originalBodyType,
            @Nonnull Vector3f linearVelocity,
            @Nonnull Vector3f angularVelocity) {
            this.targetChunkIndex = targetChunkIndex;
            this.originalBodyType = originalBodyType;
            this.linearVelocity.set(linearVelocity);
            this.angularVelocity.set(angularVelocity);
        }

        public long getTargetChunkIndex() {
            return targetChunkIndex;
        }

        @Nonnull
        public PhysicsBodyType getOriginalBodyType() {
            return originalBodyType;
        }

        @Nonnull
        public Vector3f getLinearVelocity() {
            return linearVelocity;
        }

        @Nonnull
        public Vector3f getAngularVelocity() {
            return angularVelocity;
        }
    }

    public static ResourceType<EntityStore, PhysicsWorldResource> getResourceType()
    {
        return ImpulsePlugin.get().getPhysicsWorldResourceType();
    }

    @Nonnull
    @Override
    public PhysicsWorldResource clone() {
        PhysicsWorldResource copy = new PhysicsWorldResource();
        /*
         * This resource owns live backend-native runtime state. Spaces, body-owner
         * maps, and generated terrain cache entries are excluded here because
         * aliasing them would couple two logically separate resources to the same
         * physics objects. The clone only preserves structural configuration.
         */
        copy.copyFrom(this);
        return copy;
    }
}
