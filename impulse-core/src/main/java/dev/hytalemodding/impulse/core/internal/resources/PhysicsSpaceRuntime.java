package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuningCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsContinuousCollisionCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsExtensionSettingsCapability;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuningCapability;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Live space topology and per-space settings for one physics world.
 */
public final class PhysicsSpaceRuntime {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private final Int2ObjectMap<PhysicsSpace> spaces = new Int2ObjectOpenHashMap<>();

    /**
     * Per-space settings (world collision mode, radius, TTL, etc.). Keyed by space id value.
     */
    private final Int2ObjectMap<PhysicsSpaceSettings> spaceSettings =
        new Int2ObjectOpenHashMap<>();

    @Nonnull
    public PhysicsSpace createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsSpaceSettings settings,
        @Nonnull PhysicsStepMode stepMode) {
        if (spaces.containsKey(spaceId.value())) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is already registered");
        }
        SpaceId.reserveAtLeast(spaceId.value());

        LOGGER.at(Level.FINE).log(
            "World %s creating physics space using backend %s collision=%s",
            worldName,
            backendId,
            settings.getWorldCollisionSettings().getWorldCollisionMode());

        PhysicsSpace space = Impulse.createSpace(backendId, spaceId);
        try {
            validateSpaceCompatibleWithStepMode(space, stepMode);
            applySolverTuning(space, settings);
        } catch (RuntimeException exception) {
            closeSpaceQuietly(space, worldName, "discarding failed physics space");
            throw exception;
        }
        spaces.put(space.id().value(), space);
        spaceSettings.put(space.id().value(), new PhysicsSpaceSettings(settings));

        LOGGER.at(Level.FINE).log(
            "World %s created physics space id=%s backend=%s collision=%s",
            worldName,
            space.id(),
            space.backendId(),
            settings.getWorldCollisionSettings().getWorldCollisionMode());
        return space;
    }

    @Nullable
    public PhysicsSpace getSpace(@Nonnull SpaceId spaceId) {
        return spaces.get(spaceId.value());
    }

    @Nonnull
    public PhysicsSpace requireSpace(@Nonnull SpaceId spaceId) {
        PhysicsSpace space = getSpace(spaceId);
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        return space;
    }

    @Nonnull
    public Collection<PhysicsSpace> getSpaces() {
        return new ArrayList<>(spaces.values());
    }

    @Nonnull
    public Collection<PhysicsSpace> liveSpaces() {
        return spaces.values();
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

    @Nonnull
    public List<SpaceId> getSpaceIds() {
        List<SpaceId> ids = new ArrayList<>(spaces.size());
        for (PhysicsSpace space : spaces.values()) {
            ids.add(space.id());
        }
        return ids;
    }

    @Nullable
    public PhysicsSpace removeSpace(@Nonnull SpaceId spaceId) {
        PhysicsSpace removed = spaces.remove(spaceId.value());
        spaceSettings.remove(spaceId.value());
        return removed;
    }

    @Nonnull
    public PhysicsRuntimeResetResult resetKeepingSpaces(
        @Nonnull String worldName,
        @Nonnull PhysicsStepMode stepMode,
        @Nonnull BiConsumer<SpaceId, PhysicsSpace> collisionClearer,
        @Nonnull Runnable worldChangedMarker) {
        List<SpaceReset> replacements = new ArrayList<>();
        for (PhysicsSpace previous : spaces.values()) {
            Vector3f gravity = previous.getGravity();
            PhysicsSpace replacement = Impulse.createSpace(previous.backendId(), previous.id());
            try {
                validateSpaceCompatibleWithStepMode(replacement, stepMode);
                replacement.setGravity(gravity.x, gravity.y, gravity.z);
                replacements.add(new SpaceReset(previous.id(), previous, replacement));
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
            collisionClearer.accept(reset.spaceId(), null);
            PhysicsSpace previous = replaceSpace(reset.spaceId(),
                reset.replacement(),
                worldName,
                stepMode,
                collisionClearer,
                worldChangedMarker);
            closeSpaceQuietly(previous, worldName, "cleaned physics space");
            keptSpaces++;
        }
        return new PhysicsRuntimeResetResult(removedBodies, removedJoints, keptSpaces);
    }

    @Nonnull
    public PhysicsSpaceSettings getSpaceSettings(@Nonnull SpaceId spaceId) {
        return new PhysicsSpaceSettings(getLiveSpaceSettings(spaceId));
    }

    @Nonnull
    public PhysicsSpaceSettings getLiveSpaceSettings(@Nonnull SpaceId spaceId) {
        PhysicsSpaceSettings settings = spaceSettings.get(spaceId.value());
        if (settings == null) {
            throw new IllegalStateException("Physics space settings are missing for id=" + spaceId);
        }
        return settings;
    }

    public void setSpaceSettings(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        PhysicsSpace space = spaces.get(spaceId.value());
        if (space == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId
                + " is not registered");
        }
        applySolverTuning(space, settings);
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));
    }

    public void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
        if (stepMode != PhysicsStepMode.CCD) {
            return;
        }

        List<String> unsupportedSpaces = new ArrayList<>();
        for (PhysicsSpace space : spaces.values()) {
            if (!supportsContinuousCollision(space)) {
                unsupportedSpaces.add(formatSpace(space));
            }
        }
        if (!unsupportedSpaces.isEmpty()) {
            throw new IllegalArgumentException("CCD mode is not available for: "
                + String.join(", ", unsupportedSpaces));
        }
    }

    @Nonnull
    public PhysicsSpace replaceSpace(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace replacement,
        @Nonnull String worldName,
        @Nonnull PhysicsStepMode stepMode,
        @Nonnull BiConsumer<SpaceId, PhysicsSpace> collisionClearer,
        @Nonnull Runnable worldChangedMarker) {
        if (!spaceId.equals(replacement.id())) {
            throw new IllegalArgumentException("Replacement space id " + replacement.id()
                + " does not match target id " + spaceId);
        }

        PhysicsSpace previous = spaces.get(spaceId.value());
        if (previous == null) {
            throw new IllegalStateException("Cannot replace missing physics space id=" + spaceId);
        }
        validateSpaceCompatibleWithStepMode(replacement, stepMode);
        collisionClearer.accept(spaceId, previous);

        PhysicsSpaceSettings settings = spaceSettings.get(spaceId.value());
        if (settings == null) {
            settings = PhysicsSpaceSettings.defaults();
        }
        applySolverTuning(replacement, settings);
        spaces.put(spaceId.value(), replacement);
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));
        worldChangedMarker.run();
        LOGGER.at(Level.INFO).log(
            "World %s replaced physics space id=%s backend=%s -> backend=%s",
            worldName,
            spaceId,
            previous.backendId(),
            replacement.backendId());
        return previous;
    }

    public void clearLiveTopology(@Nonnull String worldName) {
        for (PhysicsSpace space : new ArrayList<>(spaces.values())) {
            closeSpaceQuietly(space, worldName, "discarded copied physics space");
        }
        spaces.clear();
        spaceSettings.clear();
    }

    private static void validateSpaceCompatibleWithStepMode(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsStepMode stepMode) {
        if (stepMode == PhysicsStepMode.CCD && !supportsContinuousCollision(space)) {
            throw new IllegalArgumentException("CCD mode is not available for "
                + formatSpace(space));
        }
    }

    @Nonnull
    private static String formatSpace(@Nonnull PhysicsSpace space) {
        return "space " + space.id().value() + " (" + space.backendId().value() + ")";
    }

    private static void applySolverTuning(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        space.getCapability(PhysicsSolverTuningCapability.class)
            .ifPresent(tuning -> tuning.setSolverTuning(new PhysicsSolverTuning(
                settings.getSolverSettings().getSolverIterations(),
                settings.getSolverSettings().getStabilizationIterations())));
        applyActivationTuning(space, settings);
        applyExtensionSettings(space, settings);
    }

    private static void applyActivationTuning(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        space.getCapability(PhysicsActivationTuningCapability.class)
            .ifPresent(tuning -> tuning.setActivationTuning(new PhysicsActivationTuning(
                settings.getSolverSettings().getDynamicSleepLinearThreshold(),
                settings.getSolverSettings().getDynamicSleepAngularThreshold(),
                settings.getSolverSettings().getDynamicSleepTimeUntilSleep())));
    }

    private static void applyExtensionSettings(@Nonnull PhysicsSpace space,
        @Nonnull PhysicsSpaceSettings settings) {
        space.getCapability(PhysicsExtensionSettingsCapability.class)
            .ifPresent(capability -> {
                for (PhysicsBackendExtensionId extensionId : settings.getExtensionSettings().asMap().keySet()) {
                    capability.applyExtensionSettings(new PhysicsCapabilityId(extensionId.value()),
                        settings.getExtensionSettings().asStringMap(extensionId));
                }
            });
    }

    private static boolean supportsContinuousCollision(@Nonnull PhysicsSpace space) {
        return space.getCapability(PhysicsContinuousCollisionCapability.class).isPresent();
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
                space.id(),
                space.backendId(),
                exception.getMessage());
        }
    }

    private record SpaceReset(@Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpace previous,
        @Nonnull PhysicsSpace replacement) {
    }
}
