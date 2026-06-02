package dev.hytalemodding.impulse.api.runtime;

/**
 * Primitive quaternion callback used by backend-runtime providers.
 */
@FunctionalInterface
public interface BackendQuatSink {

    void accept(float x, float y, float z, float w);
}
