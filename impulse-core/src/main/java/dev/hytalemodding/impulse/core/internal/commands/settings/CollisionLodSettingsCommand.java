package dev.hytalemodding.impulse.core.internal.commands.settings;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CollisionLodSettingsCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> enabledArg = this.withOptionalArg(
        "enabled",
        "Enable collision LOD for default dynamic body filters: true or false",
        ArgTypes.STRING);
    private final OptionalArg<Integer> nearRadiusArg = this.withOptionalArg(
        "nearRadius",
        "Radius for full terrain plus body-body collision (1-"
            + PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> midRadiusArg = this.withOptionalArg(
        "midRadius",
        "Radius for terrain-only dynamic collision (1-"
            + PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> hysteresisArg = this.withOptionalArg(
        "hysteresis",
        "Extra radius before downgrading a collision tier (0-"
            + PhysicsSpaceSettings.MAX_COLLISION_LOD_HYSTERESIS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> intervalArg = this.withOptionalArg(
        "interval",
        "Ticks between collision LOD refreshes (1-"
            + PhysicsSpaceSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> farSleepArg = this.withOptionalArg(
        "farSleep",
        "Put far collision-LOD bodies to sleep: true or false",
        ArgTypes.STRING);

    public CollisionLodSettingsCommand() {
        super("collision-lod", "Get or set collision LOD settings for the default physics space");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        SpaceId defaultSpaceId = resource.getDefaultSpaceId();
        if (defaultSpaceId == null || resource.getSpace(defaultSpaceId) == null) {
            ctx.sender().sendMessage(Message.raw("No default physics space exists yet."));
            return CompletableFuture.completedFuture(null);
        }

        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(resource.getSpaceSettings(defaultSpaceId));
        if (!anyArgProvided(ctx)) {
            sendSummary(ctx, defaultSpaceId, settings);
            return CompletableFuture.completedFuture(null);
        }

        Boolean enabled = settings.isCollisionLodEnabled();
        if (enabledArg.provided(ctx)) {
            enabled = parseBoolean(enabledArg.get(ctx));
            if (enabled == null) {
                ctx.sender().sendMessage(Message.raw("enabled must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
        }

        Boolean farSleep = settings.isCollisionLodFarSleepEnabled();
        if (farSleepArg.provided(ctx)) {
            farSleep = parseBoolean(farSleepArg.get(ctx));
            if (farSleep == null) {
                ctx.sender().sendMessage(Message.raw("farSleep must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
        }

        int nearRadius = nearRadiusArg.provided(ctx)
            ? nearRadiusArg.get(ctx)
            : settings.getCollisionLodNearRadius();
        int midRadius = midRadiusArg.provided(ctx)
            ? midRadiusArg.get(ctx)
            : settings.getCollisionLodMidRadius();
        int hysteresis = hysteresisArg.provided(ctx)
            ? hysteresisArg.get(ctx)
            : settings.getCollisionLodHysteresis();
        int interval = intervalArg.provided(ctx)
            ? intervalArg.get(ctx)
            : settings.getCollisionLodRefreshIntervalTicks();
        if (outOfRange(nearRadius, PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS)
            || outOfRange(midRadius, PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS)
            || nearRadius > midRadius
            || hysteresis < 0
            || hysteresis > PhysicsSpaceSettings.MAX_COLLISION_LOD_HYSTERESIS
            || outOfRange(interval, PhysicsSpaceSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS)) {
            ctx.sender().sendMessage(Message.raw(
                "nearRadius and midRadius must be 1-"
                    + PhysicsSpaceSettings.MAX_COLLISION_LOD_RADIUS
                    + " with nearRadius <= midRadius, hysteresis must be 0-"
                    + PhysicsSpaceSettings.MAX_COLLISION_LOD_HYSTERESIS
                    + ", and interval must be 1-"
                    + PhysicsSpaceSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS
                    + "."));
            return CompletableFuture.completedFuture(null);
        }

        settings.setCollisionLodEnabled(enabled);
        settings.setCollisionLodRadii(nearRadius, midRadius);
        settings.setCollisionLodHysteresis(hysteresis);
        settings.setCollisionLodRefreshIntervalTicks(interval);
        settings.setCollisionLodFarSleepEnabled(farSleep);
        resource.setSpaceSettings(defaultSpaceId, settings);
        sendSummary(ctx, defaultSpaceId, settings);
        return CompletableFuture.completedFuture(null);
    }

    private boolean anyArgProvided(@Nonnull CommandContext ctx) {
        return enabledArg.provided(ctx)
            || nearRadiusArg.provided(ctx)
            || midRadiusArg.provided(ctx)
            || hysteresisArg.provided(ctx)
            || intervalArg.provided(ctx)
            || farSleepArg.provided(ctx);
    }

    private static boolean outOfRange(int value, int maxValue) {
        return value < 1 || value > maxValue;
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse collision LOD settings for space "
            + spaceId.value()
            + ": enabled=" + settings.isCollisionLodEnabled()
            + " nearRadius=" + settings.getCollisionLodNearRadius()
            + " midRadius=" + settings.getCollisionLodMidRadius()
            + " hysteresis=" + settings.getCollisionLodHysteresis()
            + " interval=" + settings.getCollisionLodRefreshIntervalTicks()
            + " farSleep=" + settings.isCollisionLodFarSleepEnabled()
            + " tiers=near:terrain+body mid:terrain far:terrain+sleep"));
    }

    @Nullable
    private static Boolean parseBoolean(@Nonnull String value) {
        return switch (value.toLowerCase(java.util.Locale.ROOT)) {
            case "true", "yes", "on", "enabled" -> true;
            case "false", "no", "off", "disabled" -> false;
            default -> null;
        };
    }
}
