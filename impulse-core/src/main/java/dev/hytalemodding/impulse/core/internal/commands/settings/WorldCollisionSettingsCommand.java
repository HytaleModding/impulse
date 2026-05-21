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
import dev.hytalemodding.impulse.core.plugin.resources.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.voxel.WorldCollisionMode;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WorldCollisionSettingsCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "World collision mode: none, manual, or streaming",
        ArgTypes.STRING);
    private final OptionalArg<Integer> playerRadiusArg = this.withOptionalArg(
        "playerRadius",
        "Block radius streamed around players (1-"
            + PhysicsSpaceSettings.MAX_WORLD_COLLISION_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> bodyRadiusArg = this.withOptionalArg(
        "bodyRadius",
        "Block radius streamed around awake dynamic bodies (1-"
            + PhysicsSpaceSettings.MAX_WORLD_COLLISION_BODY_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> ttlArg = this.withOptionalArg(
        "ttl",
        "Ticks before unused streamed sections are pruned (1-"
            + PhysicsSpaceSettings.MAX_WORLD_COLLISION_TTL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> chunkBoundaryArg = this.withOptionalArg(
        "chunkBoundary",
        "Entity body chunk-boundary mode: pause or load",
        ArgTypes.STRING);

    public WorldCollisionSettingsCommand() {
        super("world-collision", "Get or set world collision streaming settings for the default physics space");
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

        WorldCollisionMode mode = settings.getWorldCollisionMode();
        if (modeArg.provided(ctx)) {
            mode = parseMode(modeArg.get(ctx));
            if (mode == null) {
                ctx.sender().sendMessage(Message.raw("mode must be none, manual, or streaming."));
                return CompletableFuture.completedFuture(null);
            }
        }

        EntityChunkBoundaryMode chunkBoundaryMode = settings.getEntityChunkBoundaryMode();
        if (chunkBoundaryArg.provided(ctx)) {
            chunkBoundaryMode = parseChunkBoundaryMode(chunkBoundaryArg.get(ctx));
            if (chunkBoundaryMode == null) {
                ctx.sender().sendMessage(Message.raw("chunkBoundary must be pause or load."));
                return CompletableFuture.completedFuture(null);
            }
        }

        int playerRadius = playerRadiusArg.provided(ctx)
            ? playerRadiusArg.get(ctx)
            : settings.getWorldCollisionRadius();
        int bodyRadius = bodyRadiusArg.provided(ctx)
            ? bodyRadiusArg.get(ctx)
            : settings.getWorldCollisionBodyRadius();
        int ttl = ttlArg.provided(ctx) ? ttlArg.get(ctx) : settings.getWorldCollisionTtlTicks();
        if (outOfRange(playerRadius, PhysicsSpaceSettings.MAX_WORLD_COLLISION_RADIUS)
            || outOfRange(bodyRadius, PhysicsSpaceSettings.MAX_WORLD_COLLISION_BODY_RADIUS)
            || outOfRange(ttl, PhysicsSpaceSettings.MAX_WORLD_COLLISION_TTL_TICKS)) {
            ctx.sender().sendMessage(Message.raw(
                "playerRadius must be 1-" + PhysicsSpaceSettings.MAX_WORLD_COLLISION_RADIUS
                    + ", bodyRadius must be 1-"
                    + PhysicsSpaceSettings.MAX_WORLD_COLLISION_BODY_RADIUS
                    + ", and ttl must be 1-"
                    + PhysicsSpaceSettings.MAX_WORLD_COLLISION_TTL_TICKS
                    + "."));
            return CompletableFuture.completedFuture(null);
        }

        settings.setWorldCollisionMode(mode);
        settings.setEntityChunkBoundaryMode(chunkBoundaryMode);
        settings.setWorldCollisionRadius(playerRadius);
        settings.setWorldCollisionBodyRadius(bodyRadius);
        settings.setWorldCollisionTtlTicks(ttl);
        resource.setSpaceSettings(defaultSpaceId, settings);
        sendSummary(ctx, defaultSpaceId, settings);
        return CompletableFuture.completedFuture(null);
    }

    private boolean anyArgProvided(@Nonnull CommandContext ctx) {
        return modeArg.provided(ctx)
            || playerRadiusArg.provided(ctx)
            || bodyRadiusArg.provided(ctx)
            || ttlArg.provided(ctx)
            || chunkBoundaryArg.provided(ctx);
    }

    private static boolean outOfRange(int value, int maxValue) {
        return value < 1 || value > maxValue;
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse world collision settings for space "
            + spaceId.value()
            + ": mode=" + settings.getWorldCollisionMode().name().toLowerCase(Locale.ROOT)
            + " playerRadius=" + settings.getWorldCollisionRadius()
            + " bodyRadius=" + settings.getWorldCollisionBodyRadius()
            + " ttl=" + settings.getWorldCollisionTtlTicks()
            + " chunkBoundary="
            + settings.getEntityChunkBoundaryMode().name().toLowerCase(Locale.ROOT)));
    }

    @Nullable
    private static WorldCollisionMode parseMode(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none", "off", "disabled" -> WorldCollisionMode.NONE;
            case "manual" -> WorldCollisionMode.MANUAL;
            case "streaming", "stream", "on", "enabled" -> WorldCollisionMode.STREAMING;
            default -> null;
        };
    }

    @Nullable
    private static EntityChunkBoundaryMode parseChunkBoundaryMode(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "pause", "freeze", "pause_until_loaded" -> EntityChunkBoundaryMode.PAUSE_UNTIL_LOADED;
            case "load", "load_ticking_chunk", "tick" -> EntityChunkBoundaryMode.LOAD_TICKING_CHUNK;
            default -> null;
        };
    }
}
