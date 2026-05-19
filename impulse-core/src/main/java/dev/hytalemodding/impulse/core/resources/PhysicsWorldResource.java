package dev.hytalemodding.impulse.core.resources;

import com.hypixel.hytale.component.Resource;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsBodySnapshot;
import dev.hytalemodding.impulse.api.PhysicsBodyType;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.ImpulsePlugin;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
    public static final float DEFAULT_MAX_STEP_DT = 1f / 30f;

    private final Int2ObjectMap<PhysicsSpace> spaces = new Int2ObjectOpenHashMap<>();

    /**
     * Per-space settings (world collision mode, radius, TTL, etc.). Keyed by space id value.
     */
    private final Int2ObjectMap<PhysicsSpaceSettings> spaceSettings = new Int2ObjectOpenHashMap<>();

    private final PhysicsBodyRegistry bodyRegistry = new PhysicsBodyRegistry(this::clearBodySyncState);

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

    private final Map<Ref<EntityStore>, BodySyncState> bodySyncStates = new IdentityHashMap<>();
    private final Set<PhysicsBody> controlledBodies =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<PhysicsBody, ChunkBoundarySafeState> chunkBoundarySafeStates =
        new IdentityHashMap<>();
    private final Map<PhysicsBody, ChunkBoundaryPauseState> chunkBoundaryPauseStates =
        new IdentityHashMap<>();
    private final Map<PhysicsBody, PhysicsBodySnapshot> bodySnapshots = new IdentityHashMap<>();
    private final PhysicsBodySpatialIndex bodySpatialIndex = new PhysicsBodySpatialIndex();

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
        validateStepModeSupported(stepMode);
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
        try {
            validateSpaceCompatibleWithStepMode(space, stepMode);
            applySolverTuning(space, settings);
        } catch (RuntimeException exception) {
            closeSpaceQuietly(space, worldName, "discarding failed physics space");
            throw exception;
        }
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

    public int refreshBodySnapshots() {
        Set<PhysicsBody> liveBodies = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PhysicsSpace space : spaces.values()) {
            SpaceId spaceId = space.getId();
            space.snapshotBodies(bodySnapshots::get, snapshot -> {
                PhysicsBody body = snapshot.body();
                liveBodies.add(body);
                PhysicsBodySnapshot previous = bodySnapshots.get(body);
                if (snapshot != previous) {
                    bodySnapshots.put(body, snapshot);
                }
                bodySpatialIndex.update(snapshot, spaceId, bodyRegistry.getBodyRegistration(body));
            });
        }

        Iterator<PhysicsBody> iterator = bodySnapshots.keySet().iterator();
        while (iterator.hasNext()) {
            PhysicsBody body = iterator.next();
            if (!liveBodies.contains(body)) {
                iterator.remove();
                bodySpatialIndex.remove(body);
            }
        }
        return liveBodies.size();
    }

    @Nonnull
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBody body) {
        PhysicsBodySnapshot snapshot = bodySnapshots.get(body);
        if (snapshot != null) {
            return snapshot;
        }
        snapshot = PhysicsBodySnapshot.from(body);
        bodySnapshots.put(body, snapshot);
        BodyRegistration registration = bodyRegistry.getBodyRegistration(body);
        if (registration != null && registration.spaceId() != null) {
            bodySpatialIndex.update(snapshot, registration.spaceId(), registration);
        }
        return snapshot;
    }

    public int getBodySnapshotCount() {
        return bodySpatialIndex.bodyCount();
    }

    public int getBodySnapshotCount(@Nonnull SpaceId spaceId) {
        return bodySpatialIndex.bodyCount(spaceId);
    }

    public int getBodySnapshotCellCount() {
        return bodySpatialIndex.cellCount();
    }

    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<BodySnapshotEntry> consumer) {
        bodySpatialIndex.forEach(spaceId, consumer);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<BodySnapshotEntry> consumer) {
        return bodySpatialIndex.forEachNear(spaceId, center, radius, consumer);
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
            for (PhysicsBody body : new ArrayList<>(removed.getBodies())) {
                unregisterBody(body, false);
            }
            LOGGER.at(Level.FINE).log(
                "World %s removed physics space id=%s backend=%s",
                worldName,
                removed.getId(),
                removed.getBackendId());
            closeSpaceQuietly(removed, worldName, "removed physics space");
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
        PhysicsSpace space = spaces.get(spaceId.value());
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId
                + " is not registered");
        }
        applySolverTuning(space, settings);
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));
    }

    private void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
        if (stepMode != PhysicsStepMode.CCD) {
            return;
        }

        List<String> unsupportedSpaces = new ArrayList<>();
        for (PhysicsSpace space : spaces.values()) {
            if (!space.supportsContinuousCollision()) {
                unsupportedSpaces.add(formatSpace(space));
            }
        }
        if (!unsupportedSpaces.isEmpty()) {
            throw new IllegalArgumentException("CCD mode is not available for: "
                + String.join(", ", unsupportedSpaces));
        }
    }

    private static void validateSpaceCompatibleWithStepMode(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsStepMode stepMode) {
        if (stepMode == PhysicsStepMode.CCD && !space.supportsContinuousCollision()) {
            throw new IllegalArgumentException("CCD mode is not available for "
                + formatSpace(space));
        }
    }

    @Nonnull
    private static String formatSpace(@Nonnull PhysicsSpace space) {
        return "space " + space.getId().value() + " (" + space.getBackendId().value() + ")";
    }

    private static void applySolverTuning(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        if (!(space instanceof PhysicsSolverTuning tuning)) {
            applyActivationTuning(space, settings);
            return;
        }
        tuning.setSolverTuning(settings.getSolverIterations(),
            settings.getInternalPgsIterations(),
            settings.getStabilizationIterations(),
            settings.getMinIslandSize());
        applyActivationTuning(space, settings);
    }

    private static void applyActivationTuning(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        if (!(space instanceof PhysicsActivationTuning tuning)) {
            return;
        }
        tuning.setDynamicSleepTuning(settings.getDynamicSleepLinearThreshold(),
            settings.getDynamicSleepAngularThreshold(),
            settings.getDynamicSleepTimeUntilSleep());
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getBodyOwners() {
        List<PhysicsBody> staleBodies = new ArrayList<>();
        Collection<Ref<EntityStore>> owners = bodyRegistry.getBodyOwners(staleBodies);
        for (PhysicsBody body : staleBodies) {
            clearBodyRuntimeState(body);
        }
        return owners;
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getBodyVisualFollowers(@Nonnull PhysicsBody body) {
        return bodyRegistry.getBodyVisualFollowers(body);
    }

    public void registerBodyVisualFollower(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> follower) {
        bodyRegistry.registerBodyVisualFollower(body, follower);
    }

    public void unregisterBodyVisualFollower(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> follower) {
        bodyRegistry.unregisterBodyVisualFollower(body, follower);
    }

    public void registerBodyOwner(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> owner) {
        registerEntityBody(body, null, owner);
    }

    public void registerEntityBody(@Nonnull PhysicsBody body,
        @Nullable SpaceId spaceId,
        @Nonnull Ref<EntityStore> owner) {
        bodyRegistry.registerEntityBody(body, spaceId, owner);
    }

    public void registerDetachedBody(@Nonnull PhysicsBody body, @Nullable SpaceId spaceId) {
        bodyRegistry.registerDetachedBody(body, spaceId);
    }

    public void unregisterBodyOwner(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> owner) {
        if (bodyRegistry.unregisterBodyOwner(body, owner)) {
            clearBodyRuntimeState(body);
        }
    }

    public void unregisterBody(@Nonnull PhysicsBody body, boolean removeFromSpace) {
        BodyRegistration registration = bodyRegistry.unregisterBody(body);
        clearBodyRuntimeState(body);
        if (removeFromSpace) {
            removeBodyFromSpace(body, registration);
        }
    }

    @Nullable
    public BodyRegistration getBodyRegistration(@Nonnull PhysicsBody body) {
        BodyRegistration registration = bodyRegistry.getBodyRegistration(body);
        if (registration == null) {
            return null;
        }
        Ref<EntityStore> owner = registration.ownerRef();
        if (registration.ownerKind() == BodyOwnerKind.ENTITY && owner != null && !owner.isValid()) {
            bodyRegistry.removeInvalidEntityOwner(body);
            clearBodyRuntimeState(body);
            return null;
        }
        return registration;
    }

    @Nonnull
    public Collection<PhysicsBody> getDetachedBodies() {
        return bodyRegistry.getDetachedBodies();
    }

    @Nullable
    public Ref<EntityStore> getDetachedVisualProxy(@Nonnull PhysicsBody body) {
        return bodyRegistry.getDetachedVisualProxy(body);
    }

    @Nonnull
    public Collection<PhysicsBody> getDetachedVisualProxyBodies() {
        return bodyRegistry.getDetachedVisualProxyBodies();
    }

    public void setDetachedVisualProxy(@Nonnull PhysicsBody body, @Nonnull Ref<EntityStore> proxy) {
        bodyRegistry.setDetachedVisualProxy(body, proxy);
    }

    public void clearDetachedVisualProxy(@Nonnull PhysicsBody body) {
        bodyRegistry.clearDetachedVisualProxy(body);
    }

    public void setSyntheticVisualInterests(@Nonnull Collection<VisualInterest> interests) {
        bodyRegistry.setSyntheticVisualInterests(interests);
    }

    @Nonnull
    public List<VisualInterest> getSyntheticVisualInterests() {
        return bodyRegistry.getSyntheticVisualInterests();
    }

    public void clearSyntheticVisualInterests() {
        bodyRegistry.clearSyntheticVisualInterests();
    }

    public void remapMigratedBodies(@Nonnull Map<PhysicsBody, PhysicsBody> bodyRemaps) {
        bodyRegistry.remapBodies(bodyRemaps);
        remapBodySet(forcedContinuousCollisionBodies, bodyRemaps);
        remapBodySet(controlledBodies, bodyRemaps);
        remapBodyKeyedMap(chunkBoundarySafeStates, bodyRemaps);
        remapBodyKeyedMap(chunkBoundaryPauseStates, bodyRemaps);
        for (PhysicsBody sourceBody : bodyRemaps.keySet()) {
            bodySnapshots.remove(sourceBody);
            bodySpatialIndex.remove(sourceBody);
        }
    }

    public void clearBodyOwners() {
        bodyRegistry.clear();
        forcedContinuousCollisionBodies.clear();
        bodySyncStates.clear();
        controlledBodies.clear();
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
        bodySnapshots.clear();
        bodySpatialIndex.clear();
    }

    @Nonnull
    public BodySyncState getOrCreateBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return bodySyncStates.computeIfAbsent(entityRef, ignored -> new BodySyncState());
    }

    @Nullable
    public BodySyncState getBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return bodySyncStates.get(entityRef);
    }

    public void clearBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        bodySyncStates.remove(entityRef);
    }

    @Nonnull
    public BodyVisualInterestState getOrCreateBodyVisualInterestState(@Nonnull PhysicsBody body) {
        return bodyRegistry.getOrCreateBodyVisualInterestState(body);
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(@Nonnull PhysicsBody body) {
        return bodyRegistry.getBodyVisualInterestState(body);
    }

    public void markBodyControlled(@Nonnull PhysicsBody body) {
        controlledBodies.add(body);
    }

    public void clearControlledBody(@Nonnull PhysicsBody body) {
        controlledBodies.remove(body);
    }

    public boolean isBodyControlled(@Nonnull PhysicsBody body) {
        return controlledBodies.contains(body);
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
        bodyRegistry.clearBodyRuntimeState(body);
        forcedContinuousCollisionBodies.remove(body);
        controlledBodies.remove(body);
        chunkBoundarySafeStates.remove(body);
        chunkBoundaryPauseStates.remove(body);
        bodySnapshots.remove(body);
        bodySpatialIndex.remove(body);
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
        Ref<EntityStore> owner = bodyRegistry.getBodyOwner(body);
        if (owner != null && !owner.isValid()) {
            bodyRegistry.removeInvalidEntityOwner(body);
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
        bodyRegistry.clear();
        forcedContinuousCollisionBodies.clear();
        bodySyncStates.clear();
        controlledBodies.clear();
        chunkBoundarySafeStates.clear();
        chunkBoundaryPauseStates.clear();
        bodySnapshots.clear();
        bodySpatialIndex.clear();
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
        validateSpaceCompatibleWithStepMode(replacement, stepMode);

        PhysicsSpaceSettings settings = spaceSettings.get(spaceId.value());
        if (settings == null) {
            settings = PhysicsSpaceSettings.defaults();
        }
        applySolverTuning(replacement, settings);
        spaces.put(spaceId.value(), replacement);
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));
        LOGGER.at(Level.INFO).log(
            "World %s replaced physics space id=%s backend=%s -> backend=%s",
            worldName,
            spaceId,
            previous.getBackendId(),
            replacement.getBackendId());
        return previous;
    }

    private static void closeSpaceQuietly(@Nonnull PhysicsSpace space,
        @Nonnull String worldName,
        @Nonnull String action) {
        try {
            space.close();
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log(
                "World %s failed to close %s id=%s backend=%s: %s",
                worldName,
                action,
                space.getId(),
                space.getBackendId(),
                exception.getMessage());
        }
    }

    private void removeBodyFromSpace(@Nonnull PhysicsBody body, @Nullable BodyRegistration registration) {
        if (registration != null && registration.spaceId() != null) {
            PhysicsSpace space = getSpace(registration.spaceId());
            if (space != null) {
                space.removeBody(body);
                return;
            }
        }

        for (PhysicsSpace space : spaces.values()) {
            space.removeBody(body);
        }
    }

    private static void remapBodySet(@Nonnull Set<PhysicsBody> bodies,
        @Nonnull Map<PhysicsBody, PhysicsBody> bodyRemaps) {
        for (Map.Entry<PhysicsBody, PhysicsBody> entry : bodyRemaps.entrySet()) {
            PhysicsBody sourceBody = entry.getKey();
            PhysicsBody targetBody = entry.getValue();
            if (sourceBody != targetBody && bodies.remove(sourceBody)) {
                bodies.add(targetBody);
            }
        }
    }

    private static <T> void remapBodyKeyedMap(@Nonnull Map<PhysicsBody, T> states,
        @Nonnull Map<PhysicsBody, PhysicsBody> bodyRemaps) {
        for (Map.Entry<PhysicsBody, PhysicsBody> entry : bodyRemaps.entrySet()) {
            PhysicsBody sourceBody = entry.getKey();
            PhysicsBody targetBody = entry.getValue();
            if (sourceBody == targetBody || !states.containsKey(sourceBody)) {
                continue;
            }

            T state = states.remove(sourceBody);
            states.put(targetBody, state);
        }
    }

    public enum BodyOwnerKind {
        ENTITY,
        DETACHED,
        WORLD_COLLISION
    }

    public record BodyRegistration(@Nonnull PhysicsBody body,
        @Nullable SpaceId spaceId,
        @Nonnull BodyOwnerKind ownerKind,
        @Nullable Ref<EntityStore> ownerRef,
        boolean removeWithOwner) {
    }

    public record BodySnapshotEntry(@Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nullable BodyRegistration registration) {
    }

    public record VisualInterest(@Nonnull Vector3f position, @Nullable Vector3f direction) {
    }

    public static final class BodyVisualInterestState {

        private float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        private boolean inRange;
        private boolean likelyVisible;
        private boolean raycastVisible;
        private int ticksSinceRaycast = Integer.MAX_VALUE;

        public void recordInterest(float nearestDistanceSquared,
            boolean likelyVisible,
            boolean raycastVisible,
            boolean raycastEvaluated) {
            this.nearestDistanceSquared = nearestDistanceSquared;
            inRange = nearestDistanceSquared != Float.POSITIVE_INFINITY;
            this.likelyVisible = likelyVisible;
            if (raycastEvaluated) {
                this.raycastVisible = raycastVisible;
                ticksSinceRaycast = 0;
            } else if (ticksSinceRaycast < Integer.MAX_VALUE) {
                ticksSinceRaycast++;
            }
        }

        public boolean hasFreshRaycast(int cacheTicks) {
            return ticksSinceRaycast <= cacheTicks;
        }

        public float getNearestDistanceSquared() {
            return nearestDistanceSquared;
        }

        public boolean isInRange() {
            return inRange;
        }

        public boolean isLikelyVisible() {
            return likelyVisible;
        }

        public boolean isRaycastVisible() {
            return raycastVisible;
        }
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

    public static final class BodySyncState {

        @Nonnull
        private final Vector3f lastSyncedPosition = new Vector3f();
        @Nonnull
        private final Quaternionf lastSyncedRotation = new Quaternionf();
        private boolean initialized;
        private boolean sleeping;
        private float secondsSinceSync;

        public void recordSync(@Nonnull Vector3f position,
            @Nonnull Quaternionf rotation,
            boolean sleeping) {
            lastSyncedPosition.set(position);
            lastSyncedRotation.set(rotation);
            initialized = true;
            this.sleeping = sleeping;
            secondsSinceSync = 0.0f;
        }

        public void recordSkip(float dt) {
            secondsSinceSync += Math.max(dt, 0.0f);
        }

        @Nonnull
        public Vector3f getLastSyncedPosition() {
            return lastSyncedPosition;
        }

        @Nonnull
        public Quaternionf getLastSyncedRotation() {
            return lastSyncedRotation;
        }

        public boolean isInitialized() {
            return initialized;
        }

        public boolean isSleeping() {
            return sleeping;
        }

        public float getSecondsSinceSync() {
            return secondsSinceSync;
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
