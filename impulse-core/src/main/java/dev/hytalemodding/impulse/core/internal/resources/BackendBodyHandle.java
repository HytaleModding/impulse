package dev.hytalemodding.impulse.core.internal.resources;

/**
 * Backend-local physics-body handle.
 *
 * <p>This is an internal runtime identity. Plugin-facing code should retain durable body UUIDs
 * or live PhysicsStore row refs and keep backend handles inside runtime resources.</p>
 */
public record BackendBodyHandle(long value) {
}
