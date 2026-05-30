package dev.hytalemodding.impulse.core.internal.simulation;

/**
 * Copied contact point used by the internal debug renderer.
 */
public record PhysicsDebugContactView(float pointX,
                                      float pointY,
                                      float pointZ,
                                      boolean hasNormal,
                                      float normalX,
                                      float normalY,
                                      float normalZ) {
}
