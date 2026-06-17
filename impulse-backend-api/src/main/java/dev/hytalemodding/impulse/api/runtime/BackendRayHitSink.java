package dev.hytalemodding.impulse.api.runtime;

/**
 * Primitive ray-hit callback using backend-local body ids.
 */
@FunctionalInterface
public interface BackendRayHitSink {

    void accept(long bodyId,
        float pointX,
        float pointY,
        float pointZ,
        float normalX,
        float normalY,
        float normalZ,
        float fraction,
        float distance);
}
