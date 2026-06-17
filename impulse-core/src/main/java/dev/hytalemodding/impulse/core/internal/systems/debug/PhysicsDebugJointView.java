package dev.hytalemodding.impulse.core.internal.systems.debug;

/**
 * Copied joint debug shape used by the internal debug renderer.
 */
public record PhysicsDebugJointView(float anchorAX,
                                    float anchorAY,
                                    float anchorAZ,
                                    float anchorBX,
                                    float anchorBY,
                                    float anchorBZ,
                                    boolean hasAxis,
                                    float axisX,
                                    float axisY,
                                    float axisZ) {
}
