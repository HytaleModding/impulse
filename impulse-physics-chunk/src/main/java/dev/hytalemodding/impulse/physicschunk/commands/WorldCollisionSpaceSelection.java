package dev.hytalemodding.impulse.physicschunk.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Comparator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class WorldCollisionSpaceSelection {

    private WorldCollisionSpaceSelection() {
    }

    @Nullable
    static SpaceId resolve(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg,
        @Nonnull PhysicsWorldResource resource) {
        if (spaceArg.provided(context)) {
            int rawSpaceId = spaceArg.get(context);
            if (rawSpaceId <= 0) {
                context.sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = new SpaceId(rawSpaceId);
            if (!resource.hasSpace(spaceId)) {
                context.sendMessage(Message.raw("No physics space id=" + rawSpaceId
                    + " exists in world " + world.getName() + "."));
                return null;
            }
            return spaceId;
        }

        SpaceId firstSpaceId = resource.getSpaceIds()
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
        if (firstSpaceId == null) {
            context.sendMessage(Message.raw("No physics space exists. Run "
                + "`/impulse space create --backend=<id>` before targeting space settings."));
        }
        return firstSpaceId;
    }
}
