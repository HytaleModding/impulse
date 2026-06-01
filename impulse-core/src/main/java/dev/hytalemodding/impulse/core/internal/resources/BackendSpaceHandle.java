package dev.hytalemodding.impulse.core.internal.resources;

/**
 * Backend-local physics-space handle.
 *
 * <p>This is an internal runtime identity. It is valid only for the owning backend runtime and
 * should be unwrapped only when calling {@code PhysicsBackendRuntime}.</p>
 */
public record BackendSpaceHandle(int value) {
}
