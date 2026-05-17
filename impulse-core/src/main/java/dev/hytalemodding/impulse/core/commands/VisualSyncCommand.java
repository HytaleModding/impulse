package dev.hytalemodding.impulse.core.commands;

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
import dev.hytalemodding.impulse.core.resources.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.VisualOcclusionMode;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class VisualSyncCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<Integer> fullRadiusArg = this.withOptionalArg(
        "fullRadius",
        "Radius for full-rate visual sync",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> maxRadiusArg = this.withOptionalArg(
        "maxRadius",
        "Radius where mid-rate sync becomes far-rate or cutoff",
        ArgTypes.INTEGER);
    private final OptionalArg<String> farModeArg = this.withOptionalArg(
        "farMode",
        "Far visual mode: cutoff or lod",
        ArgTypes.STRING);
    private final OptionalArg<Integer> midIntervalArg = this.withOptionalArg(
        "midInterval",
        "Minimum ticks between mid-range visual syncs",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> farIntervalArg = this.withOptionalArg(
        "farInterval",
        "Minimum ticks between far-range visual syncs when farMode=lod",
        ArgTypes.INTEGER);
    private final OptionalArg<String> occlusionArg = this.withOptionalArg(
        "occlusion",
        "Raycast occlusion mode: off, priority, or cull",
        ArgTypes.STRING);
    private final OptionalArg<Integer> occlusionRaycastsArg = this.withOptionalArg(
        "occlusionRaycasts",
        "Maximum visual occlusion raycasts per tick",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> occlusionCacheArg = this.withOptionalArg(
        "occlusionCache",
        "Ticks to reuse visual occlusion raycast results",
        ArgTypes.INTEGER);

    public VisualSyncCommand() {
        super("visual-sync", "Get or set visual sync LOD settings for the default physics space");
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

        int fullRadius = fullRadiusArg.provided(ctx)
            ? fullRadiusArg.get(ctx)
            : settings.getVisualFullSyncRadius();
        int maxRadius = maxRadiusArg.provided(ctx)
            ? maxRadiusArg.get(ctx)
            : settings.getVisualMaxSyncRadius();
        if (fullRadius < 1 || maxRadius < 1 || fullRadius > maxRadius) {
            ctx.sender().sendMessage(Message.raw(
                "Visual sync radii must be positive and fullRadius must be <= maxRadius."));
            return CompletableFuture.completedFuture(null);
        }

        if (midIntervalArg.provided(ctx) && midIntervalArg.get(ctx) < 1) {
            ctx.sender().sendMessage(Message.raw("midInterval must be >= 1 tick."));
            return CompletableFuture.completedFuture(null);
        }
        if (farIntervalArg.provided(ctx) && farIntervalArg.get(ctx) < 1) {
            ctx.sender().sendMessage(Message.raw("farInterval must be >= 1 tick."));
            return CompletableFuture.completedFuture(null);
        }
        if (occlusionRaycastsArg.provided(ctx) && occlusionRaycastsArg.get(ctx) < 1) {
            ctx.sender().sendMessage(Message.raw("occlusionRaycasts must be >= 1."));
            return CompletableFuture.completedFuture(null);
        }
        if (occlusionCacheArg.provided(ctx) && occlusionCacheArg.get(ctx) < 1) {
            ctx.sender().sendMessage(Message.raw("occlusionCache must be >= 1 tick."));
            return CompletableFuture.completedFuture(null);
        }

        if (farModeArg.provided(ctx)) {
            Boolean cutoff = parseFarCutoff(farModeArg.get(ctx));
            if (cutoff == null) {
                ctx.sender().sendMessage(Message.raw("farMode must be cutoff or lod."));
                return CompletableFuture.completedFuture(null);
            }
            settings.setVisualFarSyncCutoffEnabled(cutoff);
        }
        if (occlusionArg.provided(ctx)) {
            VisualOcclusionMode occlusionMode = parseOcclusionMode(occlusionArg.get(ctx));
            if (occlusionMode == null) {
                ctx.sender().sendMessage(Message.raw("occlusion must be off, priority, or cull."));
                return CompletableFuture.completedFuture(null);
            }
            settings.setVisualOcclusionMode(occlusionMode);
        }

        settings.setVisualMaxSyncRadius(maxRadius);
        settings.setVisualFullSyncRadius(fullRadius);
        if (midIntervalArg.provided(ctx)) {
            settings.setVisualMidSyncIntervalTicks(midIntervalArg.get(ctx));
        }
        if (farIntervalArg.provided(ctx)) {
            settings.setVisualFarSyncIntervalTicks(farIntervalArg.get(ctx));
        }
        if (occlusionRaycastsArg.provided(ctx)) {
            settings.setVisualOcclusionRaycastsPerTick(occlusionRaycastsArg.get(ctx));
        }
        if (occlusionCacheArg.provided(ctx)) {
            settings.setVisualOcclusionCacheTicks(occlusionCacheArg.get(ctx));
        }
        resource.setSpaceSettings(defaultSpaceId, settings);
        sendSummary(ctx, defaultSpaceId, settings);
        return CompletableFuture.completedFuture(null);
    }

    private boolean anyArgProvided(@Nonnull CommandContext ctx) {
        return fullRadiusArg.provided(ctx)
            || maxRadiusArg.provided(ctx)
            || farModeArg.provided(ctx)
            || midIntervalArg.provided(ctx)
            || farIntervalArg.provided(ctx)
            || occlusionArg.provided(ctx)
            || occlusionRaycastsArg.provided(ctx)
            || occlusionCacheArg.provided(ctx);
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse visual sync settings for space "
            + spaceId.value()
            + ": fullRadius=" + settings.getVisualFullSyncRadius()
            + " maxRadius=" + settings.getVisualMaxSyncRadius()
            + " farMode=" + (settings.isVisualFarSyncCutoffEnabled() ? "cutoff" : "lod")
            + " midInterval=" + settings.getVisualMidSyncIntervalTicks()
            + " farInterval=" + settings.getVisualFarSyncIntervalTicks()
            + " occlusion=" + settings.getVisualOcclusionMode().name().toLowerCase(Locale.ROOT)
            + " occlusionRaycasts=" + settings.getVisualOcclusionRaycastsPerTick()
            + " occlusionCache=" + settings.getVisualOcclusionCacheTicks()));
    }

    private static Boolean parseFarCutoff(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "cutoff", "hard", "off", "true", "yes", "enabled" -> true;
            case "lod", "reduced", "slow", "false", "no", "disabled" -> false;
            default -> null;
        };
    }

    private static VisualOcclusionMode parseOcclusionMode(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "off", "none", "disabled" -> VisualOcclusionMode.OFF;
            case "priority", "prioritize", "sort" -> VisualOcclusionMode.PRIORITY;
            case "cull", "cutoff", "hide" -> VisualOcclusionMode.CULL;
            default -> null;
        };
    }
}
