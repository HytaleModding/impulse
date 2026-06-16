package dev.hytalemodding.impulse.core.internal.commands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.early.PhysicsStoreWorld;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsIdentityIndexResource;
import dev.hytalemodding.impulse.core.internal.physicsstore.resources.PhysicsSpaceCompatibilityIndexResource;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsStoreThreading;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class SpaceSelection {

    private SpaceSelection() {
    }

    @Nullable
    public static SpaceId resolve(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg) {
        PhysicsSpaceCompatibilityIndexResource compatibility = compatibility(store(world));
        return resolveSpaceId(context, world, spaceArg, compatibility);
    }

    @Nullable
    public static SelectedSpace resolveStoreSpace(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg) {
        Store<PhysicsStore> store = store(world);
        PhysicsSpaceCompatibilityIndexResource compatibility = compatibility(store);
        SpaceId spaceId = resolveSpaceId(context, world, spaceArg, compatibility);
        if (spaceId == null) {
            return null;
        }

        UUID spaceUuid = compatibility.getSpaceUuid(spaceId);
        Ref<PhysicsStore> spaceRef = spaceUuid != null
            ? store.getResource(PhysicsIdentityIndexResource.getResourceType()).getByUuid(spaceUuid)
            : null;
        if (spaceRef == null || spaceRef.getStore() != store || !spaceRef.isValid()) {
            context.sendMessage(Message.raw("PhysicsStore space id=" + spaceId.value()
                + " is not bound in world " + world.getName() + "."));
            return null;
        }
        return new SelectedSpace(spaceId, spaceRef);
    }

    @Nullable
    private static SpaceId resolveSpaceId(@Nonnull CommandContext context,
        @Nonnull World world,
        @Nonnull OptionalArg<Integer> spaceArg,
        @Nonnull PhysicsSpaceCompatibilityIndexResource compatibility) {
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
    private static Store<PhysicsStore> store(@Nonnull World world) {
        Store<PhysicsStore> store = ((PhysicsStoreWorld) Objects.requireNonNull(world, "world"))
            .getPhysicsStore()
            .getStore();
        PhysicsStoreThreading.requireWorldThread(store, "select a PhysicsStore space");
        return store;
    }

    @Nonnull
    private static PhysicsSpaceCompatibilityIndexResource compatibility(
        @Nonnull Store<PhysicsStore> store) {
        return store.getResource(PhysicsSpaceCompatibilityIndexResource.getResourceType());
    }

    public record SelectedSpace(@Nonnull SpaceId spaceId,
                                @Nonnull Ref<PhysicsStore> spaceRef) {

        public SelectedSpace {
            Objects.requireNonNull(spaceId, "spaceId");
            Objects.requireNonNull(spaceRef, "spaceRef");
        }
    }
}
