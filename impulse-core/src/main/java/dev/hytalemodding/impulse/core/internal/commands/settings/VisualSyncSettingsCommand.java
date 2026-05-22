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
import dev.hytalemodding.impulse.core.plugin.resources.VisualOcclusionMode;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class VisualSyncSettingsCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<Integer> fullRadiusArg = this.withOptionalArg(
        "fullRadius",
        "Radius for full-rate visual sync (1-"
            + PhysicsSpaceSettings.MAX_VISUAL_FULL_SYNC_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> maxRadiusArg = this.withOptionalArg(
        "maxRadius",
        "Radius where mid-rate sync becomes far-rate or cutoff (1-"
            + PhysicsSpaceSettings.MAX_VISUAL_MAX_SYNC_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> farModeArg = this.withOptionalArg(
        "farMode",
        "Far visual mode: cutoff or lod",
        ArgTypes.STRING);
    private final OptionalArg<Integer> midIntervalArg = this.withOptionalArg(
        "midInterval",
        "Minimum ticks between mid-range visual syncs (1-"
            + PhysicsSpaceSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> farIntervalArg = this.withOptionalArg(
        "farInterval",
        "Minimum ticks between far-range visual syncs when farMode=lod (1-"
            + PhysicsSpaceSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> occlusionArg = this.withOptionalArg(
        "occlusion",
        "Raycast occlusion mode: off, priority, or cull",
        ArgTypes.STRING);
    private final OptionalArg<Integer> occlusionRaycastsArg = this.withOptionalArg(
        "occlusionRaycasts",
        "Maximum visual occlusion raycasts per tick (1-"
            + PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> occlusionCacheArg = this.withOptionalArg(
        "occlusionCache",
        "Ticks to reuse visual occlusion raycast results (1-"
            + PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> materializationInterestIntervalArg = this.withOptionalArg(
        "materializationInterestInterval",
        "Ticks between detached visual interest refreshes (1-"
            + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> materializationCandidateIntervalArg = this.withOptionalArg(
        "materializationCandidateInterval",
        "Ticks between detached visual near-query/raycast candidate refreshes (1-"
            + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> materializationVisibilityIntervalArg = this.withOptionalArg(
        "materializationVisibilityInterval",
        "Ticks between detached generated-proxy visibility checks (1-"
            + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);

    public VisualSyncSettingsCommand() {
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
        if (outOfRange(fullRadius, PhysicsSpaceSettings.MAX_VISUAL_FULL_SYNC_RADIUS)
            || outOfRange(maxRadius, PhysicsSpaceSettings.MAX_VISUAL_MAX_SYNC_RADIUS)
            || fullRadius > maxRadius) {
            ctx.sender().sendMessage(Message.raw(
                "fullRadius must be 1-" + PhysicsSpaceSettings.MAX_VISUAL_FULL_SYNC_RADIUS
                    + ", maxRadius must be 1-"
                    + PhysicsSpaceSettings.MAX_VISUAL_MAX_SYNC_RADIUS
                    + ", and fullRadius must be <= maxRadius."));
            return CompletableFuture.completedFuture(null);
        }

        if (midIntervalArg.provided(ctx)
            && outOfRange(midIntervalArg.get(ctx),
                PhysicsSpaceSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS)) {
            ctx.sender().sendMessage(Message.raw("midInterval must be 1-"
                + PhysicsSpaceSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS
                + " ticks."));
            return CompletableFuture.completedFuture(null);
        }
        if (farIntervalArg.provided(ctx)
            && outOfRange(farIntervalArg.get(ctx),
                PhysicsSpaceSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS)) {
            ctx.sender().sendMessage(Message.raw("farInterval must be 1-"
                + PhysicsSpaceSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS
                + " ticks."));
            return CompletableFuture.completedFuture(null);
        }
        if (occlusionRaycastsArg.provided(ctx)
            && outOfRange(occlusionRaycastsArg.get(ctx),
                PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK)) {
            ctx.sender().sendMessage(Message.raw("occlusionRaycasts must be 1-"
                + PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK
                + "."));
            return CompletableFuture.completedFuture(null);
        }
        if (occlusionCacheArg.provided(ctx)
            && outOfRange(occlusionCacheArg.get(ctx),
                PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS)) {
            ctx.sender().sendMessage(Message.raw("occlusionCache must be 1-"
                + PhysicsSpaceSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS
                + " ticks."));
            return CompletableFuture.completedFuture(null);
        }
        if (materializationInterestIntervalArg.provided(ctx)
            && outOfRange(materializationInterestIntervalArg.get(ctx),
                PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS)) {
            sendMaterializationIntervalError(ctx, "materializationInterestInterval");
            return CompletableFuture.completedFuture(null);
        }
        if (materializationCandidateIntervalArg.provided(ctx)
            && outOfRange(materializationCandidateIntervalArg.get(ctx),
                PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS)) {
            sendMaterializationIntervalError(ctx, "materializationCandidateInterval");
            return CompletableFuture.completedFuture(null);
        }
        if (materializationVisibilityIntervalArg.provided(ctx)
            && outOfRange(materializationVisibilityIntervalArg.get(ctx),
                PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS)) {
            sendMaterializationIntervalError(ctx, "materializationVisibilityInterval");
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

        settings.setVisualSyncRadii(fullRadius, maxRadius);
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
        if (materializationInterestIntervalArg.provided(ctx)) {
            settings.setDetachedVisualInterestRefreshIntervalTicks(
                materializationInterestIntervalArg.get(ctx));
        }
        if (materializationCandidateIntervalArg.provided(ctx)) {
            settings.setDetachedVisualCandidateRefreshIntervalTicks(
                materializationCandidateIntervalArg.get(ctx));
        }
        if (materializationVisibilityIntervalArg.provided(ctx)) {
            settings.setDetachedVisualVisibilityCheckIntervalTicks(
                materializationVisibilityIntervalArg.get(ctx));
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
            || occlusionCacheArg.provided(ctx)
            || materializationInterestIntervalArg.provided(ctx)
            || materializationCandidateIntervalArg.provided(ctx)
            || materializationVisibilityIntervalArg.provided(ctx);
    }

    private static boolean outOfRange(int value, int maxValue) {
        return value < 1 || value > maxValue;
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
            + " occlusionCache=" + settings.getVisualOcclusionCacheTicks()
            + " materializationInterestInterval="
            + settings.getDetachedVisualInterestRefreshIntervalTicks()
            + " materializationCandidateInterval="
            + settings.getDetachedVisualCandidateRefreshIntervalTicks()
            + " materializationVisibilityInterval="
            + settings.getDetachedVisualVisibilityCheckIntervalTicks()));
    }

    private static void sendMaterializationIntervalError(@Nonnull CommandContext ctx,
        @Nonnull String name) {
        ctx.sender().sendMessage(Message.raw(name + " must be 1-"
            + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS
            + " ticks."));
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
