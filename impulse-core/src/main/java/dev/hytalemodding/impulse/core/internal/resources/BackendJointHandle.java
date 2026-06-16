package dev.hytalemodding.impulse.core.internal.resources;

/**
 * Backend-local physics-joint handle.
 *
 * <p>This is an internal runtime identity. Plugin-facing code should retain durable joint UUIDs
 * or live PhysicsStore row refs and keep backend handles inside runtime resources.</p>
 */
public record BackendJointHandle(long value) {
}
