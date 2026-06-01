package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.capability.PhysicsActivationTuning;
import dev.hytalemodding.impulse.api.capability.PhysicsCapabilityId;
import dev.hytalemodding.impulse.api.capability.PhysicsSolverTuning;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsBackendExtensionId;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsStepMode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3f;

/**
 * Id-only space topology and per-space settings for one physics world.
 */
public final class PhysicsSpaceRuntime {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private final Int2ObjectMap<PhysicsSpaceBinding> spaces = new Int2ObjectOpenHashMap<>();

    /**
     * Per-space settings (world collision mode, radius, TTL, etc.). Keyed by space id value.
     */
    private final Int2ObjectMap<PhysicsSpaceSettings> spaceSettings =
        new Int2ObjectOpenHashMap<>();

    @Nonnull
    public PhysicsSpaceBinding createSpace(@Nonnull BackendId backendId,
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

        PhysicsBackendRuntime runtime = Impulse.createRuntime(backendId);
        BackendSpaceHandle backendSpaceHandle = new BackendSpaceHandle(runtime.createSpace(spaceId));
        PhysicsSpaceBinding binding =
            new PhysicsSpaceBinding(backendId, spaceId, backendSpaceHandle, runtime);
        try {
            validateSpaceCompatibleWithStepMode(binding, stepMode);
            applySolverTuning(binding, settings);
        } catch (RuntimeException exception) {
            closeBindingQuietly(binding, worldName, "discarding failed physics space");
            throw exception;
        }
        spaces.put(spaceId.value(), binding);
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));

        LOGGER.at(Level.FINE).log(
            "World %s created physics space id=%s backend=%s collision=%s",
            worldName,
            spaceId,
            backendId,
            settings.getWorldCollisionSettings().getWorldCollisionMode());
        return binding;
    }

    @Nullable
    public PhysicsSpaceBinding getBinding(@Nonnull SpaceId spaceId) {
        return spaces.get(spaceId.value());
    }

    @Nonnull
    public PhysicsSpaceBinding requireBinding(@Nonnull SpaceId spaceId) {
        PhysicsSpaceBinding binding = getBinding(spaceId);
        if (binding == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is not registered");
        }
        return binding;
    }

    public int getSpaceCount() {
        return spaces.size();
    }

    @Nonnull
    public Collection<PhysicsSpaceBinding> getBindings() {
        return new ArrayList<>(spaces.values());
    }

    @Nonnull
    public Iterable<PhysicsSpaceBinding> iterateBindings() {
        return spaces.values();
    }

    @Nonnull
    public List<SpaceId> getSpaceIds() {
        List<SpaceId> ids = new ArrayList<>(spaces.size());
        for (PhysicsSpaceBinding binding : spaces.values()) {
            ids.add(binding.spaceId());
        }
        return ids;
    }

    @Nullable
    public PhysicsSpaceBinding removeSpace(@Nonnull SpaceId spaceId) {
        PhysicsSpaceBinding removed = spaces.remove(spaceId.value());
        spaceSettings.remove(spaceId.value());
        return removed;
    }

    @Nonnull
    public PhysicsRuntimeResetResult resetKeepingSpaces(
        @Nonnull String worldName,
        @Nonnull PhysicsStepMode stepMode) {
        List<PhysicsSpaceBinding> previousBindings = new ArrayList<>(spaces.values());
        List<PhysicsSpaceBinding> replacements = new ArrayList<>(previousBindings.size());
        for (PhysicsSpaceBinding previous : previousBindings) {
            Vector3f gravity = new Vector3f();
            previous.runtime().getGravity(previous.backendSpaceHandle().value(), gravity::set);
            PhysicsBackendRuntime runtime = Impulse.createRuntime(previous.backendId());
            BackendSpaceHandle backendSpaceHandle =
                new BackendSpaceHandle(runtime.createSpace(previous.spaceId()));
            PhysicsSpaceBinding replacement =
                new PhysicsSpaceBinding(previous.backendId(),
                    previous.spaceId(),
                    backendSpaceHandle,
                    runtime);
            try {
                validateSpaceCompatibleWithStepMode(replacement, stepMode);
                replacement.runtime().setGravity(backendSpaceHandle.value(), gravity.x, gravity.y, gravity.z);
                replacements.add(replacement);
            } catch (RuntimeException exception) {
                closeBindingQuietly(replacement, worldName, "discarding failed clean replacement");
                throw exception;
            }
        }

        int removedBodies = 0;
        int removedJoints = 0;
        for (PhysicsSpaceBinding previous : previousBindings) {
            removedBodies += previous.runtime().bodyCount(previous.backendSpaceHandle().value());
            removedJoints += previous.runtime().jointCount(previous.backendSpaceHandle().value());
        }
        spaces.clear();
        for (PhysicsSpaceBinding replacement : replacements) {
            PhysicsSpaceSettings settings = spaceSettings.get(replacement.spaceId().value());
            applySolverTuning(replacement, settings != null ? settings : PhysicsSpaceSettings.defaults());
            spaces.put(replacement.spaceId().value(), replacement);
        }
        for (PhysicsSpaceBinding previous : previousBindings) {
            closeBindingQuietly(previous, worldName, "cleaned physics space");
        }
        return new PhysicsRuntimeResetResult(removedBodies, removedJoints, replacements.size());
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
        PhysicsSpaceBinding binding = spaces.get(spaceId.value());
        if (binding == null) {
            throw new IllegalArgumentException("Physics space id=" + spaceId
                + " is not registered");
        }
        applySolverTuning(binding, settings);
        spaceSettings.put(spaceId.value(), new PhysicsSpaceSettings(settings));
    }

    public void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
        if (stepMode != PhysicsStepMode.CCD) {
            return;
        }

        List<String> unsupportedSpaces = new ArrayList<>();
        for (PhysicsSpaceBinding binding : spaces.values()) {
            if (!supportsContinuousCollision(binding)) {
                unsupportedSpaces.add(formatSpace(binding));
            }
        }
        if (!unsupportedSpaces.isEmpty()) {
            throw new IllegalArgumentException("CCD mode is not available for: "
                + String.join(", ", unsupportedSpaces));
        }
    }

    public void clearLiveTopology(@Nonnull String worldName) {
        for (PhysicsSpaceBinding binding : new ArrayList<>(spaces.values())) {
            closeBindingQuietly(binding, worldName, "discarded copied physics space");
        }
        spaces.clear();
        spaceSettings.clear();
    }

    private static void validateSpaceCompatibleWithStepMode(@Nonnull PhysicsSpaceBinding binding,
        @Nonnull PhysicsStepMode stepMode) {
        if (stepMode == PhysicsStepMode.CCD && !supportsContinuousCollision(binding)) {
            throw new IllegalArgumentException("CCD mode is not available for "
                + formatSpace(binding));
        }
    }

    @Nonnull
    private static String formatSpace(@Nonnull PhysicsSpaceBinding binding) {
        return "space " + binding.spaceId().value() + " (" + binding.backendId().value() + ")";
    }

    private static void applySolverTuning(@Nonnull PhysicsSpaceBinding binding,
        @Nonnull PhysicsSpaceSettings settings) {
        if (binding.runtime().supportsSolverTuning(binding.backendSpaceHandle().value())) {
            binding.runtime().applySolverTuning(binding.backendSpaceHandle().value(),
                new PhysicsSolverTuning(
                    settings.getSolverSettings().getSolverIterations(),
                    settings.getSolverSettings().getStabilizationIterations()));
        }
        if (binding.runtime().supportsActivationTuning(binding.backendSpaceHandle().value())) {
            binding.runtime().applyActivationTuning(binding.backendSpaceHandle().value(),
                new PhysicsActivationTuning(
                    settings.getSolverSettings().getDynamicSleepLinearThreshold(),
                    settings.getSolverSettings().getDynamicSleepAngularThreshold(),
                    settings.getSolverSettings().getDynamicSleepTimeUntilSleep()));
        }
        for (PhysicsBackendExtensionId extensionId : settings.getExtensionSettings().asMap().keySet()) {
            binding.runtime().applyExtensionSettings(binding.backendSpaceHandle().value(),
                new PhysicsCapabilityId(extensionId.value()),
                consumer -> settings.getExtensionSettings().asStringMap(extensionId).forEach(consumer));
        }
    }

    private static boolean supportsContinuousCollision(@Nonnull PhysicsSpaceBinding binding) {
        return binding.runtime().supportsContinuousCollision(binding.backendSpaceHandle().value());
    }

    static void closeBindingQuietly(@Nonnull PhysicsSpaceBinding binding,
        @Nonnull String worldName,
        @Nonnull String action) {
        try {
            binding.runtime().destroySpace(binding.backendSpaceHandle().value());
        } catch (RuntimeException exception) {
            LOGGER.at(Level.WARNING).log(
                "World %s failed to close %s id=%s backend=%s: %s",
                worldName,
                action,
                binding.spaceId(),
                binding.backendId(),
                exception.getMessage());
        }
    }
}
