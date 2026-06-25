package dev.hytalemodding.impulse.api.runtime;

import java.util.function.LongConsumer;
import javax.annotation.Nonnull;

/**
 * Primitive body-id source used for selected snapshot refresh.
 */
@FunctionalInterface
public interface BackendBodyIdSource {

    void forEachBodyId(@Nonnull LongConsumer consumer);
}
