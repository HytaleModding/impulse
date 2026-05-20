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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
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
    private final Set<PhysicsBodyId> controlledBodies = new HashSet<>();
    private final Map<PhysicsBodyId, ChunkBoundarySafeState> chunkBoundarySafeStates = new LinkedHashMap<>();
    private final Map<PhysicsBodyId, ChunkBoundaryPauseState> chunkBoundaryPauseStates = new LinkedHashMap<>();
    private final Map<PhysicsBodyId, PhysicsBodySnapshot> bodySnapshots = new LinkedHashMap<>();
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
        Set<PhysicsBodyId> liveBodies = new HashSet<>();
        for (PhysicsSpace space : spaces.values()) {
            SpaceId spaceId = space.getId();
            space.snapshotBodies(body -> {
                PhysicsBodyId bodyId = bodyRegistry.getBodyId(body);
                return bodyId != null ? bodySnapshots.get(bodyId) : null;
            }, snapshot -> {
                PhysicsBody body = snapshot.body();
                PhysicsBodyId bodyId = bodyRegistry.getBodyId(body);
                if (bodyId == null) {
                    return;
                }
                liveBodies.add(bodyId);
                PhysicsBodySnapshot previous = bodySnapshots.get(bodyId);
                if (snapshot != previous) {
                    bodySnapshots.put(bodyId, snapshot);
                }
                bodySpatialIndex.update(bodyId, snapshot, spaceId, bodyRegistry.getRegistration(body));
            });
        }

        Iterator<PhysicsBodyId> iterator = bodySnapshots.keySet().iterator();
        while (iterator.hasNext()) {
            PhysicsBodyId bodyId = iterator.next();
            if (!liveBodies.contains(bodyId)) {
                iterator.remove();
                bodySpatialIndex.remove(bodyId);
            }
        }
        return liveBodies.size();
    }

    @Nonnull
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBodyId bodyId) {
        PhysicsBodySnapshot snapshot = bodySnapshots.get(bodyId);
        if (snapshot != null) {
            return snapshot;
        }
        BodyRegistration registration = requireBodyRegistration(bodyId);
        PhysicsBody body = registration.body();
        snapshot = PhysicsBodySnapshot.from(body);
        bodySnapshots.put(bodyId, snapshot);
        bodySpatialIndex.update(bodyId, snapshot, registration.spaceId(), registration);
        return snapshot;
    }

    @Nonnull
    public PhysicsBodySnapshot getBodySnapshot(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = getBodyId(body);
        if (bodyId == null) {
            return PhysicsBodySnapshot.from(body);
        }
        return getBodySnapshot(bodyId);
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
                PhysicsBodyId bodyId = getBodyId(body);
                if (bodyId != null) {
                    destroyBody(bodyId, false);
                }
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
    public PhysicsBodyId addBody(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return addBody(PhysicsBodyId.random(), spaceId, body, kind, persistenceMode);
    }

    @Nonnull
    public PhysicsBodyId addBody(@Nonnull PhysicsBodyId bodyId,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBody body,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        PhysicsSpace space = getSpace(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        if (!containsBody(space, body)) {
            space.addBody(body);
        }
        bodyRegistry.registerBody(bodyId, body, spaceId, kind, persistenceMode);
        return bodyId;
    }

    public void destroyBody(@Nonnull PhysicsBodyId bodyId) {
        destroyBody(bodyId, true);
    }

    public void destroyBody(@Nonnull PhysicsBodyId bodyId, boolean removeFromSpace) {
        BodyRegistration registration = bodyRegistry.unregisterBody(bodyId);
        clearBodyRuntimeState(bodyId);
        if (removeFromSpace && registration != null) {
            removeBodyFromSpace(registration.body(), registration);
        }
    }

    public void destroyBody(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = getBodyId(body);
        if (bodyId != null) {
            destroyBody(bodyId);
        } else {
            removeBodyFromSpace(body, null);
        }
    }

    @Nullable
    public PhysicsBody getBody(@Nonnull PhysicsBodyId bodyId) {
        BodyRegistration registration = bodyRegistry.getRegistration(bodyId);
        return registration != null ? registration.body() : null;
    }

    @Nullable
    public PhysicsBodyId getBodyId(@Nonnull PhysicsBody body) {
        return bodyRegistry.getBodyId(body);
    }

    @Nullable
    public BodyRegistration getBodyRegistration(@Nonnull PhysicsBody body) {
        return bodyRegistry.getRegistration(body);
    }

    @Nullable
    public BodyRegistration getRegistration(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getRegistration(bodyId);
    }

    @Nonnull
    public BodyRegistration requireBodyRegistration(@Nonnull PhysicsBodyId bodyId) {
        BodyRegistration registration = getRegistration(bodyId);
        if (registration == null) {
            throw new IllegalArgumentException("Physics body id=" + bodyId + " is not registered");
        }
        return registration;
    }

    @Nonnull
    public Collection<BodyRegistration> getBodyRegistrations() {
        return bodyRegistry.getRegistrations();
    }

    @Nonnull
    public Collection<BodyRegistration> getBodyRegistrations(@Nonnull PhysicsBodyKind kind) {
        return bodyRegistry.getRegistrations(kind);
    }

    @Nonnull
    public Collection<Ref<EntityStore>> getBodyAttachments(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getAttachments(bodyId);
    }

    public void registerBodyAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        bodyRegistry.registerAttachment(bodyId, attachment);
    }

    public void unregisterBodyAttachment(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> attachment) {
        bodyRegistry.unregisterAttachment(bodyId, attachment);
    }

    @Nullable
    public Ref<EntityStore> getGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getGeneratedVisualProxy(bodyId);
    }

    @Nonnull
    public Collection<PhysicsBodyId> getGeneratedVisualProxyBodyIds() {
        return bodyRegistry.getGeneratedVisualProxyBodyIds();
    }

    public void setGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId, @Nonnull Ref<EntityStore> proxy) {
        bodyRegistry.setGeneratedVisualProxy(bodyId, proxy);
    }

    public void clearGeneratedVisualProxy(@Nonnull PhysicsBodyId bodyId) {
        bodyRegistry.clearGeneratedVisualProxy(bodyId);
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
        List<PhysicsBodyId> remappedBodyIds = new ArrayList<>();
        for (PhysicsBody sourceBody : bodyRemaps.keySet()) {
            PhysicsBodyId bodyId = getBodyId(sourceBody);
            if (bodyId != null) {
                remappedBodyIds.add(bodyId);
            }
        }
        bodyRegistry.remapBodies(bodyRemaps);
        remapBodySet(forcedContinuousCollisionBodies, bodyRemaps);
        for (PhysicsBodyId bodyId : remappedBodyIds) {
            bodySnapshots.remove(bodyId);
            bodySpatialIndex.remove(bodyId);
        }
    }

    public void clearBodies() {
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
    public BodyVisualInterestState getOrCreateBodyVisualInterestState(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getOrCreateBodyVisualInterestState(bodyId);
    }

    @Nullable
    public BodyVisualInterestState getBodyVisualInterestState(@Nonnull PhysicsBodyId bodyId) {
        return bodyRegistry.getBodyVisualInterestState(bodyId);
    }

    public void markBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        controlledBodies.add(bodyId);
    }

    public void clearControlledBody(@Nonnull PhysicsBodyId bodyId) {
        controlledBodies.remove(bodyId);
    }

    public boolean isBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        return controlledBodies.contains(bodyId);
    }

    public void updateChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        ChunkBoundarySafeState state = chunkBoundarySafeStates.computeIfAbsent(bodyId,
            ignored -> new ChunkBoundarySafeState());
        state.set(position, rotation);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId) {
        return chunkBoundarySafeStates.get(bodyId);
    }

    public void pauseChunkBoundaryBody(@Nonnull PhysicsBodyId bodyId,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        ChunkBoundaryPauseState state = chunkBoundaryPauseStates.computeIfAbsent(bodyId,
            ignored -> new ChunkBoundaryPauseState());
        state.set(targetChunkIndex, originalBodyType, linearVelocity, angularVelocity);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        return chunkBoundaryPauseStates.get(bodyId);
    }

    public void clearChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        chunkBoundaryPauseStates.remove(bodyId);
    }

    public void clearBodyRuntimeState(@Nonnull PhysicsBodyId bodyId) {
        bodyRegistry.clearBodyRuntimeState(bodyId);
        controlledBodies.remove(bodyId);
        chunkBoundarySafeStates.remove(bodyId);
        chunkBoundaryPauseStates.remove(bodyId);
        bodySnapshots.remove(bodyId);
        bodySpatialIndex.remove(bodyId);
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

    private static boolean containsBody(@Nonnull PhysicsSpace space, @Nonnull PhysicsBody body) {
        for (PhysicsBody existing : space.getBodies()) {
            if (existing == body) {
                return true;
            }
        }
        return false;
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

    public record BodyRegistration(@Nonnull PhysicsBodyId id,
        @Nonnull PhysicsBody body,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
    }

    public record BodySnapshotEntry(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull BodyRegistration registration) {
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
