package dev.hytalemodding.impulse.examples.testsupport;

import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlLifecycle;
import dev.hytalemodding.impulse.core.internal.modules.control.ControlTypeRegistry;
import javax.annotation.Nonnull;

public final class ExampleControlTestSupport {

    private ExampleControlTestSupport() {
    }

    public static void enableControl(@Nonnull ComponentRegistry<EntityStore> registry) {
        ControlLifecycle.enable();
        ControlTypeRegistry.registerComponentTypes(registry);
    }

    public static void clearControl() {
        ControlLifecycle.disable();
        ControlTypeRegistry.clearComponentTypes();
    }
}
