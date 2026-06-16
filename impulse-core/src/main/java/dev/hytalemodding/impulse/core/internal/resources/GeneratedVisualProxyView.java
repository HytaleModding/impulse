package dev.hytalemodding.impulse.core.internal.resources;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Copied generated-proxy index row with durable body identity plus optional live body ref.
 */
public record GeneratedVisualProxyView(@Nonnull UUID bodyUuid,
                                       @Nullable Ref<PhysicsStore> bodyRef,
                                       @Nonnull Ref<EntityStore> proxy) {
}
