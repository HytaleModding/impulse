package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.annotation.Nonnull;

final class DebugFlagCommand extends AbstractAsyncPlayerCommand {

    private final String label;
    private final Function<PhysicsWorldResource, Boolean> getter;
    private final BiConsumer<PhysicsWorldResource, Boolean> setter;

    DebugFlagCommand(@Nonnull String name,
        @Nonnull String label,
        @Nonnull Function<PhysicsWorldResource, Boolean> getter,
        @Nonnull BiConsumer<PhysicsWorldResource, Boolean> setter) {
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
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        boolean enabled = !getter.apply(resource);
        setter.accept(resource, enabled);
        ctx.sender().sendMessage(Message.raw("Impulse " + label + " debug "
            + (enabled ? "enabled" : "disabled")));
        return CompletableFuture.completedFuture(null);
    }
}

