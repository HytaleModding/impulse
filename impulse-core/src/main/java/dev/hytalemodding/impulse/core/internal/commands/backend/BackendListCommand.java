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
import java.util.stream.Collectors;
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
        List<SpaceEntry> spaces = resource.callOnPhysicsOwner("list backend physics spaces", access -> {
            List<SpaceEntry> entries = new ArrayList<>();
            for (PhysicsSpace space : access.getSpaces()) {
                List<String> capabilityIds = space.getCapabilityDescriptors().stream()
                    .map(descriptor -> descriptor.id().value())
                    .sorted()
                    .toList();
                entries.add(new SpaceEntry(space.getId(),
                    space.getBackendId().value(),
                    capabilityIds));
            }
            entries.sort(Comparator.comparingInt(entry -> entry.spaceId().value()));
            return entries;
        });

        return CompletableFuture.completedFuture(null);
    }

    @Nonnull
    private static String formatCapabilities(@Nonnull List<String> capabilityIds) {
        if (capabilityIds.isEmpty()) {
            return "<none>";
        }
        return capabilityIds.stream().collect(Collectors.joining(","));
    }

    private record SpaceEntry(@Nonnull SpaceId spaceId,
                              @Nonnull String backendId,
                              @Nonnull List<String> capabilityIds) {
    }
}
