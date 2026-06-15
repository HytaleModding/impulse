package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import java.util.Comparator;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SpaceSelection {

    private SpaceSelection() {
    }

    @Nullable
    public static SpaceId resolve(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg) {
        PhysicsSpaceCompatibilityIndexResource compatibility = compatibility(world);
        if (spaceArg.provided(context)) {
            int rawSpaceId = spaceArg.get(context);
            if (rawSpaceId <= 0) {
                context.sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = specifiedSpaceId(compatibility, rawSpaceId);
            if (spaceId == null) {
                context.sendMessage(Message.raw("No physics space id=" + rawSpaceId
                    + " exists in world " + world.getName() + "."));
                return null;
            }
            return spaceId;
        }

        SpaceId firstSpaceId = firstRegisteredSpaceId(compatibility);
        if (firstSpaceId == null) {
            context.sendMessage(Message.raw("No physics space exists. Run "
                + "`/impulse space create --backend=<id>` before targeting space settings."));
        }
        return firstSpaceId;
    }

    @Nullable
    static SpaceId specifiedSpaceId(@Nonnull PhysicsSpaceCompatibilityIndexResource compatibility,
        int rawSpaceId) {
        if (rawSpaceId <= 0) {
            return null;
        }
        SpaceId spaceId = new SpaceId(rawSpaceId);
        return compatibility.hasSpace(spaceId) ? spaceId : null;
    }

    @Nullable
    static SpaceId firstRegisteredSpaceId(
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility) {
        return compatibility.spaceIds()
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
    }

    @Nonnull
    private static PhysicsSpaceCompatibilityIndexResource compatibility(@Nonnull World world) {
        Store<PhysicsStore> store = ((PhysicsStoreWorld) Objects.requireNonNull(world, "world"))
            .getPhysicsStore()
            .getStore();
        PhysicsStoreThreading.requireWorldThread(store, "select a PhysicsStore space");
        return store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType());
    }
}
