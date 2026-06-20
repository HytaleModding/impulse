package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.logger.HytaleLogger;
import dev.hytalemodding.impulse.api.BackendId;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.api.runtime.PhysicsBackendRuntime;
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
 * Id-only space topology for one direct physics world runtime.
 */
public final class PhysicsSpaceRuntime {

    private static final HytaleLogger LOGGER = HytaleLogger.get("Impulse");

    private final Int2ObjectMap<PhysicsSpaceBinding> spaces = new Int2ObjectOpenHashMap<>();

    @Nonnull
    public synchronized PhysicsSpaceBinding createSpace(@Nonnull BackendId backendId,
        @Nonnull SpaceId spaceId,
        @Nonnull String worldName,
        @Nonnull PhysicsStepMode stepMode) {
        if (spaces.containsKey(spaceId.value())) {
            throw new IllegalArgumentException("Physics space id=" + spaceId + " is already registered");
        }
        SpaceId.reserveAtLeast(spaceId.value());

        LOGGER.at(Level.FINE).log(
            "World %s creating physics space using backend %s",
            worldName,
            backendId);

        PhysicsBackendRuntime runtime = Impulse.createRuntime(backendId);
        BackendSpaceHandle backendSpaceHandle = new BackendSpaceHandle(runtime.createSpace(spaceId));
        PhysicsSpaceBinding binding =
            new PhysicsSpaceBinding(backendId, spaceId, backendSpaceHandle, runtime);
        try {
            validateSpaceCompatibleWithStepMode(binding, stepMode);
        } catch (RuntimeException exception) {
            closeBindingSilently(binding, worldName, "discarding failed physics space");
            throw exception;
        }
        spaces.put(spaceId.value(), binding);

        LOGGER.at(Level.FINE).log(
            "World %s created physics space id=%s backend=%s",
            worldName,
            spaceId,
            backendId);
        return binding;
    }

    @Nullable
    public synchronized PhysicsSpaceBinding getBinding(@Nonnull SpaceId spaceId) {
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

    public synchronized int getSpaceCount() {
        return spaces.size();
    }

    @Nonnull
    public synchronized Collection<PhysicsSpaceBinding> getBindings() {
        return new ArrayList<>(spaces.values());
    }

    @Nonnull
    public synchronized List<SpaceId> getSpaceIds() {
        List<SpaceId> ids = new ArrayList<>(spaces.size());
        for (PhysicsSpaceBinding binding : spaces.values()) {
            ids.add(binding.spaceId());
        }
        return ids;
    }

    @Nullable
    public synchronized PhysicsSpaceBinding removeSpace(@Nonnull SpaceId spaceId) {
        return spaces.remove(spaceId.value());
    }

    @Nonnull
    public synchronized PhysicsRuntimeResetResult resetKeepingSpaces(
        @Nonnull String worldName,
        @Nonnull PhysicsStepMode stepMode) {
        List<PhysicsSpaceBinding> previousBindings = new ArrayList<>(spaces.values());
        List<PhysicsSpaceBinding> replacements = new ArrayList<>(previousBindings.size());
        for (PhysicsSpaceBinding previous : previousBindings) {
            PhysicsSpaceBinding replacement = null;
            Vector3f gravity = new Vector3f();
            try {
                previous.runtime().getGravity(previous.backendSpaceHandle().value(), gravity::set);
                PhysicsBackendRuntime runtime = Impulse.createRuntime(previous.backendId());
                BackendSpaceHandle backendSpaceHandle =
                    new BackendSpaceHandle(runtime.createSpace(previous.spaceId()));
                replacement = new PhysicsSpaceBinding(previous.backendId(),
                    previous.spaceId(),
                    backendSpaceHandle,
                    runtime);
                validateSpaceCompatibleWithStepMode(replacement, stepMode);
                replacement.runtime().setGravity(backendSpaceHandle.value(), gravity.x, gravity.y, gravity.z);
                replacements.add(replacement);
            } catch (RuntimeException exception) {
                if (replacement != null) {
                    closeBindingSilently(replacement, worldName, "discarding failed clean replacement");
                }
                closeBindingsSilently(replacements, worldName, "discarding clean replacements");
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
            spaces.put(replacement.spaceId().value(), replacement);
        }
        for (PhysicsSpaceBinding previous : previousBindings) {
            closeBindingSilently(previous, worldName, "cleaned physics space");
        }
        return new PhysicsRuntimeResetResult(removedBodies, removedJoints, replacements.size());
    }

    private static void closeBindingsSilently(@Nonnull Iterable<PhysicsSpaceBinding> bindings,
        @Nonnull String worldName,
        @Nonnull String reason) {
        for (PhysicsSpaceBinding binding : bindings) {
            closeBindingSilently(binding, worldName, reason);
        }
    }

    public synchronized void validateStepModeSupported(@Nonnull PhysicsStepMode stepMode) {
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

    public synchronized void clearLiveTopology(@Nonnull String worldName) {
        for (PhysicsSpaceBinding binding : new ArrayList<>(spaces.values())) {
            closeBindingSilently(binding, worldName, "discarded copied physics space");
        }
        spaces.clear();
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

    private static boolean supportsContinuousCollision(@Nonnull PhysicsSpaceBinding binding) {
        return binding.runtime().supportsContinuousCollision(binding.backendSpaceHandle().value());
    }

    static void closeBindingSilently(@Nonnull PhysicsSpaceBinding binding,
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
