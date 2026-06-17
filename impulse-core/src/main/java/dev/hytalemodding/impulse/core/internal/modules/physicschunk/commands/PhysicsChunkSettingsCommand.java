package dev.hytalemodding.impulse.core.internal.modules.physicschunk.commands;

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
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.api.SpaceId;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsSpaces;
import dev.hytalemodding.impulse.core.plugin.physicsstore.PhysicsThreading;
import dev.hytalemodding.impulse.core.plugin.modules.physicschunk.PhysicsChunkTerrainMode;
import dev.hytalemodding.impulse.core.plugin.settings.EntityChunkBoundaryMode;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsChunkTerrainSettings;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PhysicsChunkSettingsCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> modeArg = this.withOptionalArg(
        "mode",
        "PhysicsChunk mode: none, manual, or streaming",
        ArgTypes.STRING);
    private final OptionalArg<Integer> playerRadiusArg = this.withOptionalArg(
        "playerRadius",
        "Block radius streamed around players (1-"
            + PhysicsChunkTerrainSettings.MAX_TERRAIN_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> bodyRadiusArg = this.withOptionalArg(
        "bodyRadius",
        "Block radius streamed around awake dynamic bodies (1-"
            + PhysicsChunkTerrainSettings.MAX_BODY_TERRAIN_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> ttlArg = this.withOptionalArg(
        "ttl",
        "Ticks before unused streamed sections are pruned (1-"
            + PhysicsChunkTerrainSettings.MAX_TERRAIN_TTL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> chunkBoundaryArg = this.withOptionalArg(
        "chunkBoundary",
        "Entity body chunk-boundary mode: pause or load",
        ArgTypes.STRING);
    private final OptionalArg<String> terrainArg = this.withOptionalArg(
        "terrain",
        "PhysicsChunk terrain collider: boxes or native_voxels",
        ArgTypes.STRING);
    private final OptionalArg<Integer> spaceArg = this.withOptionalArg(
        "space",
        "Physics space id to target",
        ArgTypes.INTEGER);

    public PhysicsChunkSettingsCommand() {
        super("settings", "Get or set PhysicsChunk streaming settings for a physics space");
    }

    @Nonnull
    @Override
    protected CompletableFuture<Void> executeAsync(@Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world) {
        Store<PhysicsStore> physicsStore = PhysicsThreading.store(world);
        PhysicsChunkSpaceSelection.Selection selection =
            PhysicsChunkSpaceSelection.resolve(ctx, world, spaceArg, physicsStore);
        if (selection == null) {
            return CompletableFuture.completedFuture(null);
        }

        PhysicsSpaceSettings currentSettings = PhysicsSpaces.settings(physicsStore,
            selection.spaceRef());
        if (currentSettings == null) {
            ctx.sender().sendMessage(Message.raw("Physics space id=" + selection.spaceId().value()
                + " no longer exists."));
            return CompletableFuture.completedFuture(null);
        }
        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(currentSettings);
        if (!anyArgProvided(ctx)) {
            sendSummary(ctx, selection.spaceId(), settings);
            return CompletableFuture.completedFuture(null);
        }

        PhysicsChunkTerrainMode mode = settings.getPhysicsChunkTerrainSettings().getTerrainMode();
        if (modeArg.provided(ctx)) {
            mode = parseMode(modeArg.get(ctx));
            if (mode == null) {
                ctx.sender().sendMessage(Message.raw("mode must be none, manual, or streaming."));
                return CompletableFuture.completedFuture(null);
            }
        }

        EntityChunkBoundaryMode chunkBoundaryMode = settings.getPhysicsChunkTerrainSettings().getEntityChunkBoundaryMode();
        if (chunkBoundaryArg.provided(ctx)) {
            chunkBoundaryMode = parseChunkBoundaryMode(chunkBoundaryArg.get(ctx));
            if (chunkBoundaryMode == null) {
                ctx.sender().sendMessage(Message.raw("chunkBoundary must be pause or load."));
                return CompletableFuture.completedFuture(null);
            }
        }

        boolean nativeVoxelTerrainEnabled = settings.getPhysicsChunkTerrainSettings().isNativeVoxelTerrainEnabled();
        if (terrainArg.provided(ctx)) {
            Boolean parsedTerrain = parseTerrain(terrainArg.get(ctx));
            if (parsedTerrain == null) {
                ctx.sender().sendMessage(Message.raw("terrain must be boxes or native_voxels."));
                return CompletableFuture.completedFuture(null);
            }
            nativeVoxelTerrainEnabled = parsedTerrain;
        }

        int playerRadius = playerRadiusArg.provided(ctx)
            ? playerRadiusArg.get(ctx)
            : settings.getPhysicsChunkTerrainSettings().getTerrainRadius();
        int bodyRadius = bodyRadiusArg.provided(ctx)
            ? bodyRadiusArg.get(ctx)
            : settings.getPhysicsChunkTerrainSettings().getBodyTerrainRadius();
        int ttl = ttlArg.provided(ctx) ? ttlArg.get(ctx) : settings.getPhysicsChunkTerrainSettings().getTerrainTtlTicks();
        if (outOfRange(playerRadius, PhysicsChunkTerrainSettings.MAX_TERRAIN_RADIUS)
            || outOfRange(bodyRadius, PhysicsChunkTerrainSettings.MAX_BODY_TERRAIN_RADIUS)
            || outOfRange(ttl, PhysicsChunkTerrainSettings.MAX_TERRAIN_TTL_TICKS)) {
            ctx.sender().sendMessage(Message.raw(
                "playerRadius must be 1-" + PhysicsChunkTerrainSettings.MAX_TERRAIN_RADIUS
                    + ", bodyRadius must be 1-"
                    + PhysicsChunkTerrainSettings.MAX_BODY_TERRAIN_RADIUS
                    + ", and ttl must be 1-"
                    + PhysicsChunkTerrainSettings.MAX_TERRAIN_TTL_TICKS
                    + "."));
            return CompletableFuture.completedFuture(null);
        }

        settings.getPhysicsChunkTerrainSettings().setTerrainMode(mode);
        settings.getPhysicsChunkTerrainSettings().setEntityChunkBoundaryMode(chunkBoundaryMode);
        settings.getPhysicsChunkTerrainSettings().setNativeVoxelTerrainEnabled(nativeVoxelTerrainEnabled);
        settings.getPhysicsChunkTerrainSettings().setTerrainRadius(playerRadius);
        settings.getPhysicsChunkTerrainSettings().setBodyTerrainRadius(bodyRadius);
        settings.getPhysicsChunkTerrainSettings().setTerrainTtlTicks(ttl);
        PhysicsSpaces.putSettings(physicsStore, selection.spaceRef(), settings);
        sendSummary(ctx, selection.spaceId(), settings);
        return CompletableFuture.completedFuture(null);
    }

    private boolean anyArgProvided(@Nonnull CommandContext ctx) {
        return modeArg.provided(ctx)
            || playerRadiusArg.provided(ctx)
            || bodyRadiusArg.provided(ctx)
            || ttlArg.provided(ctx)
            || chunkBoundaryArg.provided(ctx)
            || terrainArg.provided(ctx);
    }

    private static boolean outOfRange(int value, int maxValue) {
        return value < 1 || value > maxValue;
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse PhysicsChunk settings for space "
            + spaceId.value()
            + ": mode=" + settings.getPhysicsChunkTerrainSettings().getTerrainMode().name().toLowerCase(Locale.ROOT)
            + " playerRadius=" + settings.getPhysicsChunkTerrainSettings().getTerrainRadius()
            + " bodyRadius=" + settings.getPhysicsChunkTerrainSettings().getBodyTerrainRadius()
            + " ttl=" + settings.getPhysicsChunkTerrainSettings().getTerrainTtlTicks()
            + " chunkBoundary="
            + settings.getPhysicsChunkTerrainSettings().getEntityChunkBoundaryMode().name().toLowerCase(Locale.ROOT)
            + " terrain="
            + (settings.getPhysicsChunkTerrainSettings().isNativeVoxelTerrainEnabled()
                ? "native_voxels"
                : "boxes")));
    }

    @Nullable
    private static PhysicsChunkTerrainMode parseMode(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none", "off", "disabled" -> PhysicsChunkTerrainMode.NONE;
            case "manual" -> PhysicsChunkTerrainMode.MANUAL;
            case "streaming", "stream", "on", "enabled" -> PhysicsChunkTerrainMode.STREAMING;
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

    @Nullable
    private static Boolean parseTerrain(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "boxes", "box", "merged", "merged_boxes" -> Boolean.FALSE;
            case "native", "native_voxels", "voxels", "voxel" -> Boolean.TRUE;
            default -> null;
        };
    }
}
