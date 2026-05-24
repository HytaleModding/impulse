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
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualSyncSettings;
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class VisualSyncSettingsCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<Integer> fullRadiusArg = this.withOptionalArg(
        "fullRadius",
        "Radius for full-rate visual sync (1-"
            + PhysicsVisualSyncSettings.MAX_VISUAL_FULL_SYNC_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> maxRadiusArg = this.withOptionalArg(
        "maxRadius",
        "Radius where mid-rate sync becomes far-rate or cutoff (1-"
            + PhysicsVisualSyncSettings.MAX_VISUAL_MAX_SYNC_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> farModeArg = this.withOptionalArg(
        "farMode",
        "Far visual mode: cutoff or lod",
        ArgTypes.STRING);
    private final OptionalArg<String> entityCullingArg = this.withOptionalArg(
        "entityCulling",
        "Cull entity-backed visual sync by player interest: true or false",
        ArgTypes.STRING);
    private final OptionalArg<String> visibilityCullingArg = this.withOptionalArg(
        "visibilityCulling",
        "Cull visual sync by approximate player view cone: true or false",
        ArgTypes.STRING);
    private final OptionalArg<Integer> midIntervalArg = this.withOptionalArg(
        "midInterval",
        "Minimum ticks between mid-range visual syncs (1-"
            + PhysicsVisualSyncSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> farIntervalArg = this.withOptionalArg(
        "farInterval",
        "Minimum ticks between far-range visual syncs when farMode=lod (1-"
            + PhysicsVisualSyncSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> occlusionArg = this.withOptionalArg(
        "occlusion",
        "Raycast occlusion mode: off, priority, or cull",
        ArgTypes.STRING);
    private final OptionalArg<Integer> occlusionRaycastsArg = this.withOptionalArg(
        "occlusionRaycasts",
        "Maximum visual occlusion raycasts per tick (1-"
            + PhysicsVisualSyncSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> occlusionCacheArg = this.withOptionalArg(
        "occlusionCache",
        "Ticks to reuse visual occlusion raycast results (1-"
            + PhysicsVisualSyncSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> predictionArg = this.withOptionalArg(
        "prediction",
        "Predict near dynamic visuals between snapshots: true or false",
        ArgTypes.STRING);
    private final OptionalArg<Float> predictionMaxSecondsArg = this.withOptionalArg(
        "predictionMaxSeconds",
        "Maximum visual snapshot prediction seconds (0-"
            + PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS
            + ")",
        ArgTypes.FLOAT);
    private final OptionalArg<String> smoothingArg = this.withOptionalArg(
        "smoothing",
        "Smooth near dynamic visuals toward snapshots: true or false",
        ArgTypes.STRING);
    private final OptionalArg<Float> smoothingRateArg = this.withOptionalArg(
        "smoothingRate",
        "Visual snapshot smoothing rate (>0-"
            + PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE
            + ")",
        ArgTypes.FLOAT);

    public VisualSyncSettingsCommand() {
        super("sync", "Get or set visual sync LOD settings for the default physics space");
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

        PhysicsSpaceSettings settings = new PhysicsSpaceSettings(
            resource.getSpaceSettings(defaultSpaceId));
        if (!anyArgProvided(ctx)) {
            sendSummary(ctx, defaultSpaceId, settings);
            return CompletableFuture.completedFuture(null);
        }

        int fullRadius = fullRadiusArg.provided(ctx)
            ? fullRadiusArg.get(ctx)
            : settings.getVisualSyncSettings().getVisualFullSyncRadius();
        int maxRadius = maxRadiusArg.provided(ctx)
            ? maxRadiusArg.get(ctx)
            : settings.getVisualSyncSettings().getVisualMaxSyncRadius();
        if (outOfRange(fullRadius, PhysicsVisualSyncSettings.MAX_VISUAL_FULL_SYNC_RADIUS)
            || outOfRange(maxRadius, PhysicsVisualSyncSettings.MAX_VISUAL_MAX_SYNC_RADIUS)
            || fullRadius > maxRadius) {
            ctx.sender().sendMessage(Message.raw(
                "fullRadius must be 1-" + PhysicsVisualSyncSettings.MAX_VISUAL_FULL_SYNC_RADIUS
                    + ", maxRadius must be 1-"
                    + PhysicsVisualSyncSettings.MAX_VISUAL_MAX_SYNC_RADIUS
                    + ", and fullRadius must be <= maxRadius."));
            return CompletableFuture.completedFuture(null);
        }

        if (midIntervalArg.provided(ctx)
            && outOfRange(midIntervalArg.get(ctx),
                PhysicsVisualSyncSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS)) {
            ctx.sender().sendMessage(Message.raw("midInterval must be 1-"
                + PhysicsVisualSyncSettings.MAX_VISUAL_MID_SYNC_INTERVAL_TICKS
                + " ticks."));
            return CompletableFuture.completedFuture(null);
        }
        if (farIntervalArg.provided(ctx)
            && outOfRange(farIntervalArg.get(ctx),
                PhysicsVisualSyncSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS)) {
            ctx.sender().sendMessage(Message.raw("farInterval must be 1-"
                + PhysicsVisualSyncSettings.MAX_VISUAL_FAR_SYNC_INTERVAL_TICKS
                + " ticks."));
            return CompletableFuture.completedFuture(null);
        }
        if (occlusionRaycastsArg.provided(ctx)
            && outOfRange(occlusionRaycastsArg.get(ctx),
                PhysicsVisualSyncSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK)) {
            ctx.sender().sendMessage(Message.raw("occlusionRaycasts must be 1-"
                + PhysicsVisualSyncSettings.MAX_VISUAL_OCCLUSION_RAYCASTS_PER_TICK
                + "."));
            return CompletableFuture.completedFuture(null);
        }
        if (occlusionCacheArg.provided(ctx)
            && outOfRange(occlusionCacheArg.get(ctx),
                PhysicsVisualSyncSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS)) {
            ctx.sender().sendMessage(Message.raw("occlusionCache must be 1-"
                + PhysicsVisualSyncSettings.MAX_VISUAL_OCCLUSION_CACHE_TICKS
                + " ticks."));
            return CompletableFuture.completedFuture(null);
        }
        if (predictionMaxSecondsArg.provided(ctx)
            && outOfRange(predictionMaxSecondsArg.get(ctx),
                PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS)) {
            ctx.sender().sendMessage(Message.raw("predictionMaxSeconds must be 0-"
                + PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS
                + "."));
            return CompletableFuture.completedFuture(null);
        }
        if (smoothingRateArg.provided(ctx)
            && smoothingRateOutOfRange(smoothingRateArg.get(ctx),
                PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE)) {
            ctx.sender().sendMessage(Message.raw("smoothingRate must be > 0 and <= "
                + PhysicsVisualSyncSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE
                + "."));
            return CompletableFuture.completedFuture(null);
        }
        if (farModeArg.provided(ctx)) {
            Boolean cutoff = parseFarCutoff(farModeArg.get(ctx));
            if (cutoff == null) {
                ctx.sender().sendMessage(Message.raw("farMode must be cutoff or lod."));
                return CompletableFuture.completedFuture(null);
            }
            settings.getVisualSyncSettings().setVisualFarSyncCutoffEnabled(cutoff);
        }
        if (occlusionArg.provided(ctx)) {
            VisualOcclusionMode occlusionMode = parseOcclusionMode(occlusionArg.get(ctx));
            if (occlusionMode == null) {
                ctx.sender().sendMessage(Message.raw("occlusion must be off, priority, or cull."));
                return CompletableFuture.completedFuture(null);
            }
            settings.getVisualSyncSettings().setVisualOcclusionMode(occlusionMode);
        }
        if (entityCullingArg.provided(ctx)) {
            Boolean entityCulling = parseBoolean(entityCullingArg.get(ctx));
            if (entityCulling == null) {
                ctx.sender().sendMessage(Message.raw("entityCulling must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            settings.getVisualSyncSettings().setEntityVisualSyncCullingEnabled(entityCulling);
        }
        if (visibilityCullingArg.provided(ctx)) {
            Boolean visibilityCulling = parseBoolean(visibilityCullingArg.get(ctx));
            if (visibilityCulling == null) {
                ctx.sender().sendMessage(Message.raw("visibilityCulling must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            settings.getVisualSyncSettings().setVisualVisibilityCullingEnabled(visibilityCulling);
        }
        if (predictionArg.provided(ctx)) {
            Boolean prediction = parseBoolean(predictionArg.get(ctx));
            if (prediction == null) {
                ctx.sender().sendMessage(Message.raw("prediction must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            settings.getVisualSyncSettings().setVisualSnapshotPredictionEnabled(prediction);
        }
        if (smoothingArg.provided(ctx)) {
            Boolean smoothing = parseBoolean(smoothingArg.get(ctx));
            if (smoothing == null) {
                ctx.sender().sendMessage(Message.raw("smoothing must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            settings.getVisualSyncSettings().setVisualSnapshotSmoothingEnabled(smoothing);
        }

        settings.getVisualSyncSettings().setVisualSyncRadii(fullRadius, maxRadius);
        if (midIntervalArg.provided(ctx)) {
            settings.getVisualSyncSettings().setVisualMidSyncIntervalTicks(midIntervalArg.get(ctx));
        }
        if (farIntervalArg.provided(ctx)) {
            settings.getVisualSyncSettings().setVisualFarSyncIntervalTicks(farIntervalArg.get(ctx));
        }
        if (occlusionRaycastsArg.provided(ctx)) {
            settings.getVisualSyncSettings().setVisualOcclusionRaycastsPerTick(occlusionRaycastsArg.get(ctx));
        }
        if (occlusionCacheArg.provided(ctx)) {
            settings.getVisualSyncSettings().setVisualOcclusionCacheTicks(occlusionCacheArg.get(ctx));
        }
        if (predictionMaxSecondsArg.provided(ctx)) {
            settings.getVisualSyncSettings().setVisualSnapshotPredictionMaxSeconds(predictionMaxSecondsArg.get(ctx));
        }
        if (smoothingRateArg.provided(ctx)) {
            settings.getVisualSyncSettings().setVisualSnapshotSmoothingRate(smoothingRateArg.get(ctx));
        }
        resource.setSpaceSettings(defaultSpaceId, settings);
        sendSummary(ctx, defaultSpaceId, settings);
        return CompletableFuture.completedFuture(null);
    }

    private boolean anyArgProvided(@Nonnull CommandContext ctx) {
        return fullRadiusArg.provided(ctx)
            || maxRadiusArg.provided(ctx)
            || farModeArg.provided(ctx)
            || entityCullingArg.provided(ctx)
            || visibilityCullingArg.provided(ctx)
            || midIntervalArg.provided(ctx)
            || farIntervalArg.provided(ctx)
            || occlusionArg.provided(ctx)
            || occlusionRaycastsArg.provided(ctx)
            || occlusionCacheArg.provided(ctx)
            || predictionArg.provided(ctx)
            || predictionMaxSecondsArg.provided(ctx)
            || smoothingArg.provided(ctx)
            || smoothingRateArg.provided(ctx);
    }

    private static boolean outOfRange(int value, int maxValue) {
        return value < 1 || value > maxValue;
    }

    private static boolean outOfRange(float value, float maxValue) {
        return !Float.isFinite(value) || value < 0.0f || value > maxValue;
    }

    private static boolean smoothingRateOutOfRange(float value, float maxValue) {
        return !Float.isFinite(value) || value <= 0.0f || value > maxValue;
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse visual sync settings for space "
            + spaceId.value()
            + ": fullRadius=" + settings.getVisualSyncSettings().getVisualFullSyncRadius()
            + " maxRadius=" + settings.getVisualSyncSettings().getVisualMaxSyncRadius()
            + " farMode=" + (settings.getVisualSyncSettings().isVisualFarSyncCutoffEnabled() ? "cutoff" : "lod")
            + " midInterval=" + settings.getVisualSyncSettings().getVisualMidSyncIntervalTicks()
            + " farInterval=" + settings.getVisualSyncSettings().getVisualFarSyncIntervalTicks()
            + " occlusion=" + settings.getVisualSyncSettings().getVisualOcclusionMode().name().toLowerCase(Locale.ROOT)
            + " occlusionRaycasts=" + settings.getVisualSyncSettings().getVisualOcclusionRaycastsPerTick()
            + " occlusionCache=" + settings.getVisualSyncSettings().getVisualOcclusionCacheTicks()
            + " prediction=" + settings.getVisualSyncSettings().isVisualSnapshotPredictionEnabled()
            + " predictionMaxSeconds=" + settings.getVisualSyncSettings().getVisualSnapshotPredictionMaxSeconds()
            + " smoothing=" + settings.getVisualSyncSettings().isVisualSnapshotSmoothingEnabled()
            + " smoothingRate=" + settings.getVisualSyncSettings().getVisualSnapshotSmoothingRate()
            + " entityCulling=" + settings.getVisualSyncSettings().isEntityVisualSyncCullingEnabled()
            + " visibilityCulling=" + settings.getVisualSyncSettings().isVisualVisibilityCullingEnabled()));
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

    private static Boolean parseBoolean(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "enabled" -> true;
            case "false", "no", "off", "disabled" -> false;
            default -> null;
        };
    }
}
