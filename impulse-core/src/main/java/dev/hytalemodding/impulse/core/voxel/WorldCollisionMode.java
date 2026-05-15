package dev.hytalemodding.impulse.core.voxel;

/**
 * Controls how a physics space interacts with Hytale world voxel collision.
 *
 * <p>This is an opt-in per-space policy. Impulse does not impose world collision
 * on any space by default; the integrator chooses the level of intrusion.</p>
 *
 * <ul>
 *   <li>{@link #NONE} - No world collision. The space is pure physics with no terrain.</li>
 *   <li>{@link #MANUAL} - World collision exists but must be built/cleared explicitly
 *       by the integrator (e.g. via commands or a custom system).</li>
 *   <li>{@link #STREAMING} - Impulse automatically streams section collision around
 *       tracked players/bodies and prunes unused sections after a TTL.</li>
 * </ul>
 */
public enum WorldCollisionMode {
    NONE,
    MANUAL,
    STREAMING
}
