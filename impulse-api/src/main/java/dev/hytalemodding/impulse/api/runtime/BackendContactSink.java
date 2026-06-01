package dev.hytalemodding.impulse.api.runtime;

/**
 * Primitive contact callback using backend-local body ids.
 */
@FunctionalInterface
public interface BackendContactSink {

    void accept(long bodyAId,
        long bodyBId,
        float pointAX,
        float pointAY,
        float pointAZ,
        float pointBX,
        float pointBY,
        float pointBZ,
        float normalBX,
        float normalBY,
        float normalBZ,
        float distance,
        float impulse);
}
