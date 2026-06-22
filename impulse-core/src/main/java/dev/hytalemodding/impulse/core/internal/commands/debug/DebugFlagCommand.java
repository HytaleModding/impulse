package dev.hytalemodding.impulse.core.internal.commands.debug;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.internal.physics.resources.PhysicsDebugResource;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsThreading;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

public final class DebugFlagCommand extends AbstractAsyncPlayerCommand {

    private final String label;
    private final Function<PhysicsDebugResource, Boolean> getter;
    private final BiConsumer<PhysicsDebugResource, Boolean> setter;

    public DebugFlagCommand(@Nonnull String name,
        @Nonnull String label,
        @Nonnull Function<PhysicsDebugResource, Boolean> getter,
        @Nonnull BiConsumer<PhysicsDebugResource, Boolean> setter) {
        super(name, "Toggle Impulse " + label + " debug rendering");
        this.label = label;
        this.getter = getter;
        this.setter = setter;
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        PhysicsDebugResource resource =
            physicsStore.getResource(PhysicsDebugResource.getResourceType());
        boolean enabled = !getter.apply(resource);
        setter.accept(resource, enabled);
        ctx.sender().sendMessage(Message.raw("Impulse " + label + " debug "
            + (enabled ? "enabled" : "disabled")));
        return CompletableFuture.completedFuture(null);
    }
}
