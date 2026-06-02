package dev.hytalemodding.impulse.core.internal.resources;

/**
 * Backend-local physics-body handle.
 *
 * <p>This is an internal runtime identity. Stable plugin code should retain
 * {@code RigidBodyKey}; core unwraps this value only at the backend-runtime boundary.</p>
 */
public record BackendBodyHandle(long value) {
}
