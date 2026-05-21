package dev.hytalemodding.impulse.core.plugin.resources;

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
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRegistry;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodyRuntimeState;
import dev.hytalemodding.impulse.core.internal.resources.PhysicsBodySnapshotStore;
import dev.hytalemodding.impulse.core.internal.voxel.WorldVoxelCollisionCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    private final PhysicsBodyRuntimeState runtimeState = new PhysicsBodyRuntimeState();
    private final PhysicsBodySnapshotStore bodySnapshots = new PhysicsBodySnapshotStore();

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

    public int getSpaceCount() {
        return spaces.size();
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
        return bodySnapshots.refresh(spaces.values(), bodyRegistry);
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
        bodySnapshots.put(bodyId, snapshot, registration.spaceId(), registration);
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
        return bodySnapshots.bodyCount();
    }

    public int getBodySnapshotCount(@Nonnull SpaceId spaceId) {
        return bodySnapshots.bodyCount(spaceId);
    }

    public int getBodySnapshotCellCount() {
        return bodySnapshots.cellCount();
    }

    @Nonnull
    public WorldVoxelCollisionCache getWorldVoxelCollisionCache() {
        return worldVoxelCollisionCache;
    }

    public void forEachBodySnapshot(@Nonnull SpaceId spaceId,
        @Nonnull Consumer<BodySnapshotEntry> consumer) {
        bodySnapshots.forEach(spaceId, consumer);
    }

    public int forEachBodySnapshotNear(@Nonnull SpaceId spaceId,
        @Nonnull Vector3f center,
        float radius,
        @Nonnull Consumer<BodySnapshotEntry> consumer) {
        return bodySnapshots.forEachNear(spaceId, center, radius, consumer);
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

    /**
     * Clears runtime physics state by replacing each native backend space with an empty
     * space that keeps the same logical id, backend, settings, gravity, and default-space mapping.
     */
    @Nonnull
    public RuntimeResetResult resetRuntimeStateKeepingSpaces(@Nonnull String worldName) {
        List<SpaceReset> replacements = new ArrayList<>();
        for (PhysicsSpace previous : spaces.values()) {
            Vector3f gravity = previous.getGravity();
            PhysicsSpace replacement = Impulse.createSpace(previous.getBackendId(), previous.getId());
            try {
                validateSpaceCompatibleWithStepMode(replacement, stepMode);
                replacement.setGravity(gravity.x, gravity.y, gravity.z);
                replacements.add(new SpaceReset(previous.getId(), previous, replacement));
            } catch (RuntimeException exception) {
                closeSpaceQuietly(replacement, worldName, "discarding failed clean replacement");
                throw exception;
            }
        }

        int removedBodies = 0;
        int removedJoints = 0;
        int keptSpaces = 0;
        for (SpaceReset reset : replacements) {
            removedBodies += reset.previous().bodyCount();
            removedJoints += reset.previous().jointCount();
            worldVoxelCollisionCache.clear(reset.spaceId(), null);
            PhysicsSpace previous = replaceSpace(reset.spaceId(), reset.replacement(), worldName);
            closeSpaceQuietly(previous, worldName, "cleaned physics space");
            keptSpaces++;
        }
        clearBodies();
        return new RuntimeResetResult(removedBodies, removedJoints, keptSpaces);
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
        BodyRegistration registration = bodyRegistry.registerBody(bodyId, body, spaceId, kind, persistenceMode);
        PhysicsBodySnapshot snapshot = PhysicsBodySnapshot.from(body);
        bodySnapshots.put(bodyId, snapshot, spaceId, registration);
        return bodyId;
    }

    public void destroyBody(@Nonnull PhysicsBodyId bodyId) {
        destroyBody(bodyId, true);
    }

    public void destroyBody(@Nonnull PhysicsBodyId bodyId, boolean removeFromSpace) {
        BodyRegistration registration = bodyRegistry.unregisterBody(bodyId);
        if (registration != null) {
            clearBodyRuntimeState(registration.body(), bodyId);
        } else {
            clearBodyRuntimeState(bodyId);
        }
        if (removeFromSpace && registration != null) {
            removeBodyFromSpace(registration.body(), registration);
        }
    }

    public void destroyBody(@Nonnull PhysicsBody body) {
        PhysicsBodyId bodyId = getBodyId(body);
        if (bodyId != null) {
            destroyBody(bodyId);
        } else {
            runtimeState.clearBody(body);
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

    public int getBodyRegistrationCount() {
        return bodyRegistry.getRegistrationCount();
    }

    public int getBodyRegistrationCount(@Nonnull PhysicsBodyPersistenceMode persistenceMode) {
        return bodyRegistry.getRegistrationCount(persistenceMode);
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
        runtimeState.remapBodies(bodyRemaps);
        for (PhysicsBodyId bodyId : remappedBodyIds) {
            bodySnapshots.remove(bodyId);
        }
    }

    public void clearBodies() {
        bodyRegistry.clear();
        runtimeState.clear();
        bodySnapshots.clear();
    }

    @Nonnull
    public BodySyncState getOrCreateBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return runtimeState.getOrCreateBodySyncState(entityRef);
    }

    @Nullable
    public BodySyncState getBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        return runtimeState.getBodySyncState(entityRef);
    }

    public void clearBodySyncState(@Nonnull Ref<EntityStore> entityRef) {
        runtimeState.clearBodySyncState(entityRef);
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
        runtimeState.markBodyControlled(bodyId);
    }

    public void clearControlledBody(@Nonnull PhysicsBodyId bodyId) {
        runtimeState.clearControlledBody(bodyId);
    }

    public boolean isBodyControlled(@Nonnull PhysicsBodyId bodyId) {
        return runtimeState.isBodyControlled(bodyId);
    }

    public void updateChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId,
        @Nonnull Vector3f position,
        @Nonnull Quaternionf rotation) {
        runtimeState.updateChunkBoundarySafeState(bodyId, position, rotation);
    }

    @Nullable
    public ChunkBoundarySafeState getChunkBoundarySafeState(@Nonnull PhysicsBodyId bodyId) {
        return runtimeState.getChunkBoundarySafeState(bodyId);
    }

    public void pauseChunkBoundaryBody(@Nonnull PhysicsBodyId bodyId,
        long targetChunkIndex,
        @Nonnull PhysicsBodyType originalBodyType,
        @Nonnull Vector3f linearVelocity,
        @Nonnull Vector3f angularVelocity) {
        runtimeState.pauseChunkBoundaryBody(bodyId,
            targetChunkIndex,
            originalBodyType,
            linearVelocity,
            angularVelocity);
    }

    @Nullable
    public ChunkBoundaryPauseState getChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        return runtimeState.getChunkBoundaryPauseState(bodyId);
    }

    public void clearChunkBoundaryPauseState(@Nonnull PhysicsBodyId bodyId) {
        runtimeState.clearChunkBoundaryPauseState(bodyId);
    }

    public void clearBodyRuntimeState(@Nonnull PhysicsBodyId bodyId) {
        bodyRegistry.clearBodyRuntimeState(bodyId);
        runtimeState.clearBody(bodyId);
        bodySnapshots.remove(bodyId);
    }

    private void clearBodyRuntimeState(@Nonnull PhysicsBody body, @Nonnull PhysicsBodyId bodyId) {
        bodyRegistry.clearBodyRuntimeState(bodyId);
        runtimeState.clearBody(body, bodyId);
        bodySnapshots.remove(bodyId);
    }

    public void markContinuousCollisionForced(@Nonnull PhysicsBody body) {
        runtimeState.markContinuousCollisionForced(body);
    }

    @Nonnull
    public Collection<PhysicsBody> getForcedContinuousCollisionBodies() {
        return runtimeState.getForcedContinuousCollisionBodies();
    }

    public void clearForcedContinuousCollisionBodies() {
        runtimeState.clearForcedContinuousCollisionBodies();
    }

    public void copyFrom(@Nonnull PhysicsWorldResource other) {
        if (this == other) {
            return;
        }
        spaces.clear();
        spaceSettings.clear();
        bodyRegistry.clear();
        runtimeState.clear();
        bodySnapshots.clear();
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
        if (registration != null) {
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

    public record BodyRegistration(@Nonnull PhysicsBodyId id,
        @Nonnull PhysicsBody body,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsBodyKind kind,
        @Nonnull PhysicsBodyPersistenceMode persistenceMode) {
    }

    public record RuntimeResetResult(int removedBodies, int removedJoints, int keptSpaces) {
    }

    private record SpaceReset(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace previous,
        @Nonnull PhysicsSpace replacement) {
    }

    public record BodySnapshotEntry(@Nonnull PhysicsBodyId bodyId,
        @Nonnull PhysicsBodySnapshot snapshot,
        @Nonnull SpaceId spaceId,
        @Nonnull BodyRegistration registration) {
    }

    public record VisualInterest(@Nonnull Vector3f position, @Nullable Vector3f direction) {
    }

    /**
     * Per-body visual-interest cache produced by detached visual materialization.
     *
     * <p>Physics sync can reuse fresh raycast results from this state when
     * {@code VisualOcclusionMode.CULL} is enabled, so materialization and sync
     * share one occlusion decision window instead of spending duplicate raycasts.</p>
     */
    public static final class BodyVisualInterestState {

        @Getter
        private float nearestDistanceSquared = Float.POSITIVE_INFINITY;
        @Getter
        private boolean inRange;
        @Getter
        private boolean likelyVisible;
        @Getter
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
        @Getter
        private boolean initialized;
        @Getter
        private boolean sleeping;
        @Getter
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

    }

    public static final class ChunkBoundaryPauseState {

        @Getter
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
        copy.copyFrom(this);
        return copy;
    }
}
