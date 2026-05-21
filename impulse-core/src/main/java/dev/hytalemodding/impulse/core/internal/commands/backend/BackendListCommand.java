package dev.hytalemodding.impulse.core.internal.commands.backend;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.Impulse;
import dev.hytalemodding.impulse.api.PhysicsBackend;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class BackendListCommand extends AbstractAsyncPlayerCommand {

    public BackendListCommand() {
        super("list", "List discovered backends and active physics spaces");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        List<String> backendIds = new ArrayList<>();
        for (PhysicsBackend backend : Impulse.getBackends()) {
            backendIds.add(backend.getId().value());
        }
        backendIds.sort(String::compareTo);

        ctx.sender().sendMessage(Message.raw("Impulse backends: "
            + (backendIds.isEmpty() ? "<none>" : String.join(", ", backendIds))));

        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        List<PhysicsSpace> spaces = new ArrayList<>(resource.getSpaces());
        spaces.sort(Comparator.comparingInt(space -> space.getId().value()));
        SpaceId defaultSpaceId = resource.getDefaultSpaceId();

        ctx.sender().sendMessage(Message.raw("Physics spaces in world " + world.getName() + ":"));
        if (spaces.isEmpty()) {
            ctx.sender().sendMessage(Message.raw("- <none>"));
            return CompletableFuture.completedFuture(null);
        }
        for (PhysicsSpace space : spaces) {
            String marker = space.getId().equals(defaultSpaceId) ? " (default)" : "";
            ctx.sender().sendMessage(Message.raw("- id=" + space.getId().value()
                + " backend=" + space.getBackendId().value() + marker));
        }

        return CompletableFuture.completedFuture(null);
    }
}
