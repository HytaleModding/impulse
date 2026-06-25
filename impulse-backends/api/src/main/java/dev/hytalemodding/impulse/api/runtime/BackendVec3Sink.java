package dev.hytalemodding.impulse.api.runtime;

/**
 * Primitive vector callback used by backend-runtime providers.
 */
@FunctionalInterface
public interface BackendVec3Sink {

    void accept(float x, float y, float z);
}
