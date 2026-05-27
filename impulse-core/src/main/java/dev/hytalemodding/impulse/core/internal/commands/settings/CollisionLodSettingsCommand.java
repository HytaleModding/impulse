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
import dev.hytalemodding.impulse.core.internal.commands.SpaceSelection;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsCollisionLodSettings;
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
            + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> midRadiusArg = this.withOptionalArg(
        "midRadius",
        "Radius for terrain-only dynamic collision (1-"
            + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> hysteresisArg = this.withOptionalArg(
        "hysteresis",
        "Extra radius before downgrading a collision tier (0-"
            + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_HYSTERESIS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> intervalArg = this.withOptionalArg(
        "interval",
        "Ticks between collision LOD refreshes (1-"
            + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> farSleepArg = this.withOptionalArg(
        "farSleep",
        "Put far collision-LOD bodies to sleep: true or false",
        ArgTypes.STRING);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public CollisionLodSettingsCommand() {
        super("lod", "Get or set collision LOD settings for a physics space");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        PhysicsWorldResource resource = store.getResource(PhysicsWorldResource.getResourceType());
        SpaceId spaceId = SpaceSelection.resolve(ctx, world, resource, spaceArg);
        if (spaceId == null) {
            return CompletableFuture.completedFuture(null);
        }

        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(resource.getSpaceSettings(spaceId));
        if (!anyArgProvided(ctx)) {
            sendSummary(ctx, spaceId, settings);
            return CompletableFuture.completedFuture(null);
        }

        Boolean enabled = settings.getCollisionLodSettings().isCollisionLodEnabled();
        if (enabledArg.provided(ctx)) {
            enabled = parseBoolean(enabledArg.get(ctx));
            if (enabled == null) {
                ctx.sender().sendMessage(Message.raw("enabled must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
        }

        Boolean farSleep = settings.getCollisionLodSettings().isCollisionLodFarSleepEnabled();
        if (farSleepArg.provided(ctx)) {
            farSleep = parseBoolean(farSleepArg.get(ctx));
            if (farSleep == null) {
                ctx.sender().sendMessage(Message.raw("farSleep must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
        }

        int nearRadius = nearRadiusArg.provided(ctx)
            ? nearRadiusArg.get(ctx)
            : settings.getCollisionLodSettings().getCollisionLodNearRadius();
        int midRadius = midRadiusArg.provided(ctx)
            ? midRadiusArg.get(ctx)
            : settings.getCollisionLodSettings().getCollisionLodMidRadius();
        int hysteresis = hysteresisArg.provided(ctx)
            ? hysteresisArg.get(ctx)
            : settings.getCollisionLodSettings().getCollisionLodHysteresis();
        int interval = intervalArg.provided(ctx)
            ? intervalArg.get(ctx)
            : settings.getCollisionLodSettings().getCollisionLodRefreshIntervalTicks();
        if (outOfRange(nearRadius, PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS)
            || outOfRange(midRadius, PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS)
            || nearRadius > midRadius
            || hysteresis < 0
            || hysteresis > PhysicsCollisionLodSettings.MAX_COLLISION_LOD_HYSTERESIS
            || outOfRange(interval, PhysicsCollisionLodSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS)) {
            ctx.sender().sendMessage(Message.raw(
                "nearRadius and midRadius must be 1-"
                    + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_RADIUS
                    + " with nearRadius <= midRadius, hysteresis must be 0-"
                    + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_HYSTERESIS
                    + ", and interval must be 1-"
                    + PhysicsCollisionLodSettings.MAX_COLLISION_LOD_REFRESH_INTERVAL_TICKS
                    + "."));
            return CompletableFuture.completedFuture(null);
        }

        settings.getCollisionLodSettings().setCollisionLodEnabled(enabled);
        settings.getCollisionLodSettings().setCollisionLodRadii(nearRadius, midRadius);
        settings.getCollisionLodSettings().setCollisionLodHysteresis(hysteresis);
        settings.getCollisionLodSettings().setCollisionLodRefreshIntervalTicks(interval);
        settings.getCollisionLodSettings().setCollisionLodFarSleepEnabled(farSleep);
        resource.setSpaceSettings(spaceId, settings);
        sendSummary(ctx, spaceId, settings);
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
            + ": enabled=" + settings.getCollisionLodSettings().isCollisionLodEnabled()
            + " nearRadius=" + settings.getCollisionLodSettings().getCollisionLodNearRadius()
            + " midRadius=" + settings.getCollisionLodSettings().getCollisionLodMidRadius()
            + " hysteresis=" + settings.getCollisionLodSettings().getCollisionLodHysteresis()
            + " interval=" + settings.getCollisionLodSettings().getCollisionLodRefreshIntervalTicks()
            + " farSleep=" + settings.getCollisionLodSettings().isCollisionLodFarSleepEnabled()
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
