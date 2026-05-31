package dev.hytalemodding.impulse.api;

import java.nio.file.Path;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Backend factory and lifecycle hooks.
 * <p>
 * This is a semantic contract, not a promise of identical simulation results. Backends may
 * differ in solver details, contact ordering, and numerical edge cases.
 * <ul>
 *     <li>{@link #init()} must be idempotent and safe when called multiple times.</li>
 *     <li>Backends are expected to be used through {@link Impulse}, which provides
 *     thread-safe one-time initialization.</li>
 *     <li>{@link #createSpace()} and {@link #createSpace(SpaceId)} may be called from multiple
 *     owner lanes after initialization. Implementations with mutable factory state must
 *     synchronize internally.</li>
 *     <li>Different spaces may run concurrently on different owner lanes. Each individual
 *     {@link PhysicsSpace} remains serialized by its own owner lane.</li>
 * </ul>
 */
public interface PhysicsBackend {

    @Nonnull
    BackendId getId();

    /**
     * Set the plugin data directory where backend-specific files (e.g. native libraries)
     * can be extracted. Called by the plugin before {@link #init()}.
     * Backends that do not need a data directory can implement this as a no-op.
     */
    default void setDataDirectory(@Nullable Path dataDirectory) {
    }

    /**
     * Set the verbosity of the backend's internal library logging.
     * Backends should map this level to their own logging system.
     * The default implementation is a no-op.
     *
     * @param level the desired logging level; Level.OFF suppresses all internal logging,
     *              Level.INFO allows standard output, Level.FINEST enables verbose diagnostics
     */
    default void setInternalLoggingLevel(@Nonnull Level level) {
    }

    /**
     * Initialize backend-global state such as native libraries.
     */
    void init();

    /**
     * Create a new independent simulation space. This method may be called concurrently after
     * {@link #init()} has completed.
     */
    @Nonnull
    PhysicsSpace createSpace();

    /**
     * Create a new independent simulation space with a specific logical id.
     * <p>
     * This method may be called concurrently after {@link #init()} has completed.
     * Implementations should preserve this id on the returned space object.
     */
    @Nonnull
    default PhysicsSpace createSpace(@Nonnull SpaceId spaceId) {
        return createSpace();
    }
}
