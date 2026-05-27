package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.universe.world.World;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.Comparator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SpaceSelection {

    private SpaceSelection() {
    }

    @Nullable
    public static SpaceId resolve(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull PhysicsWorldResource resource,
        @Nonnull OptionalArg<Integer> spaceArg) {
        if (spaceArg.provided(context)) {
            int rawSpaceId = spaceArg.get(context);
            if (rawSpaceId <= 0) {
                context.sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = specifiedSpaceId(resource, rawSpaceId);
            if (spaceId == null) {
                context.sendMessage(Message.raw("No physics space id=" + rawSpaceId
                    + " exists in world " + world.getName() + "."));
                return null;
            }
            return spaceId;
        }

        SpaceId firstSpaceId = firstRegisteredSpaceId(resource);
        if (firstSpaceId == null) {
            context.sendMessage(Message.raw("No physics space exists. Run "
                + "`/impulse space create --backend=<id>` before targeting space settings."));
        }
        return firstSpaceId;
    }

    @Nullable
    static SpaceId specifiedSpaceId(@Nonnull PhysicsWorldResource resource, int rawSpaceId) {
        if (rawSpaceId <= 0) {
            return null;
        }
        SpaceId spaceId = new SpaceId(rawSpaceId);
        return resource.hasSpace(spaceId) ? spaceId : null;
    }

    @Nullable
    static SpaceId firstRegisteredSpaceId(@Nonnull PhysicsWorldResource resource) {
        return resource.getSpaceIds()
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
    }
}
