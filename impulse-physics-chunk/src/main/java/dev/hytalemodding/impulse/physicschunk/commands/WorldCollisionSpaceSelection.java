package dev.hytalemodding.impulse.physicschunk.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import java.util.Comparator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class WorldCollisionSpaceSelection {

    private WorldCollisionSpaceSelection() {
    }

    @Nullable
    static Selection resolve(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg,
        @Nonnull Store<PhysicsStore> physicsStore) {
        if (spaceArg.provided(context)) {
            int rawSpaceId = spaceArg.get(context);
            if (rawSpaceId <= 0) {
                context.sendMessage(Message.raw("Space id must be a positive integer."));
                return null;
            }
            SpaceId spaceId = new SpaceId(rawSpaceId);
            Ref<PhysicsStore> spaceRef = PhysicsSpaces.resolveRef(physicsStore, spaceId);
            if (spaceRef == null) {
                context.sendMessage(Message.raw("No physics space id=" + rawSpaceId
                    + " exists in world " + world.getName() + "."));
                return null;
            }
            return new Selection(spaceId, spaceRef);
        }

        SpaceId firstSpaceId = PhysicsSpaces.spaceIds(physicsStore)
            .stream()
            .min(Comparator.comparingInt(SpaceId::value))
            .orElse(null);
        if (firstSpaceId == null) {
            context.sendMessage(Message.raw("No physics space exists. Run "
                + "`/impulse space create --backend=<id>` before targeting space settings."));
            return null;
        }
        Ref<PhysicsStore> spaceRef = PhysicsSpaces.resolveRef(physicsStore, firstSpaceId);
        if (spaceRef == null) {
            context.sendMessage(Message.raw("No physics space id=" + firstSpaceId.value()
                + " exists in world " + world.getName() + "."));
            return null;
        }
        return new Selection(firstSpaceId, spaceRef);
    }

    record Selection(@Nonnull SpaceId spaceId, @Nonnull Ref<PhysicsStore> spaceRef) {
    }
}
