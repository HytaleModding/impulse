package dev.hytalemodding.impulse.core.internal.resources;

/**
 * Backend-local physics-joint handle.
 *
 * <p>This is an internal runtime identity. Stable plugin code should retain {@code JointKey};
 * core unwraps this value only at the backend-runtime boundary.</p>
 */
public record BackendJointHandle(long value) {
}
