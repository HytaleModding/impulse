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
import dev.hytalemodding.impulse.core.plugin.settings.VisualOcclusionMode;
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
    private final OptionalArg<String> predictionArg = this.withOptionalArg(
        "prediction",
        "Predict near dynamic visuals between snapshots: true or false",
        ArgTypes.STRING);
    private final OptionalArg<Float> predictionMaxSecondsArg = this.withOptionalArg(
        "predictionMaxSeconds",
        "Maximum visual snapshot prediction seconds (0-"
            + PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS
            + ")",
        ArgTypes.FLOAT);
    private final OptionalArg<String> smoothingArg = this.withOptionalArg(
        "smoothing",
        "Smooth near dynamic visuals toward snapshots: true or false",
        ArgTypes.STRING);
    private final OptionalArg<Float> smoothingRateArg = this.withOptionalArg(
        "smoothingRate",
        "Visual snapshot smoothing rate (>0-"
            + PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE
            + ")",
        ArgTypes.FLOAT);
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
    private final OptionalArg<Integer> materializationSpawnRateArg = this.withOptionalArg(
        "materializationSpawnRate",
        "Maximum detached visual proxy spawns per tick (1-"
            + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> materializationCapArg = this.withOptionalArg(
        "materializationCap",
        "Maximum detached visual proxies materialized in the default space (1-"
            + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_MATERIALIZED
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
        if (predictionMaxSecondsArg.provided(ctx)
            && outOfRange(predictionMaxSecondsArg.get(ctx),
                PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS)) {
            ctx.sender().sendMessage(Message.raw("predictionMaxSeconds must be 0-"
                + PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_PREDICTION_MAX_SECONDS
                + "."));
            return CompletableFuture.completedFuture(null);
        }
        if (smoothingRateArg.provided(ctx)
            && smoothingRateOutOfRange(smoothingRateArg.get(ctx),
                PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE)) {
            ctx.sender().sendMessage(Message.raw("smoothingRate must be > 0 and <= "
                + PhysicsSpaceSettings.MAX_VISUAL_SNAPSHOT_SMOOTHING_RATE
                + "."));
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
        if (materializationSpawnRateArg.provided(ctx)
            && outOfRange(materializationSpawnRateArg.get(ctx),
                PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK)) {
            ctx.sender().sendMessage(Message.raw("materializationSpawnRate must be 1-"
                + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK
                + "."));
            return CompletableFuture.completedFuture(null);
        }
        if (materializationCapArg.provided(ctx)
            && outOfRange(materializationCapArg.get(ctx),
                PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_MATERIALIZED)) {
            ctx.sender().sendMessage(Message.raw("materializationCap must be 1-"
                + PhysicsSpaceSettings.MAX_DETACHED_VISUAL_MAX_MATERIALIZED
                + "."));
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
        if (predictionArg.provided(ctx)) {
            Boolean prediction = parseBoolean(predictionArg.get(ctx));
            if (prediction == null) {
                ctx.sender().sendMessage(Message.raw("prediction must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            settings.setVisualSnapshotPredictionEnabled(prediction);
        }
        if (smoothingArg.provided(ctx)) {
            Boolean smoothing = parseBoolean(smoothingArg.get(ctx));
            if (smoothing == null) {
                ctx.sender().sendMessage(Message.raw("smoothing must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            settings.setVisualSnapshotSmoothingEnabled(smoothing);
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
        if (predictionMaxSecondsArg.provided(ctx)) {
            settings.setVisualSnapshotPredictionMaxSeconds(predictionMaxSecondsArg.get(ctx));
        }
        if (smoothingRateArg.provided(ctx)) {
            settings.setVisualSnapshotSmoothingRate(smoothingRateArg.get(ctx));
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
        if (materializationSpawnRateArg.provided(ctx)) {
            settings.setDetachedVisualMaxSpawnsPerTick(materializationSpawnRateArg.get(ctx));
        }
        if (materializationCapArg.provided(ctx)) {
            settings.setDetachedVisualMaxMaterialized(materializationCapArg.get(ctx));
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
            || predictionArg.provided(ctx)
            || predictionMaxSecondsArg.provided(ctx)
            || smoothingArg.provided(ctx)
            || smoothingRateArg.provided(ctx)
            || materializationInterestIntervalArg.provided(ctx)
            || materializationCandidateIntervalArg.provided(ctx)
            || materializationVisibilityIntervalArg.provided(ctx)
            || materializationSpawnRateArg.provided(ctx)
            || materializationCapArg.provided(ctx);
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
            + ": fullRadius=" + settings.getVisualFullSyncRadius()
            + " maxRadius=" + settings.getVisualMaxSyncRadius()
            + " farMode=" + (settings.isVisualFarSyncCutoffEnabled() ? "cutoff" : "lod")
            + " midInterval=" + settings.getVisualMidSyncIntervalTicks()
            + " farInterval=" + settings.getVisualFarSyncIntervalTicks()
            + " occlusion=" + settings.getVisualOcclusionMode().name().toLowerCase(Locale.ROOT)
            + " occlusionRaycasts=" + settings.getVisualOcclusionRaycastsPerTick()
            + " occlusionCache=" + settings.getVisualOcclusionCacheTicks()
            + " prediction=" + settings.isVisualSnapshotPredictionEnabled()
            + " predictionMaxSeconds=" + settings.getVisualSnapshotPredictionMaxSeconds()
            + " smoothing=" + settings.isVisualSnapshotSmoothingEnabled()
            + " smoothingRate=" + settings.getVisualSnapshotSmoothingRate()
            + " materializationInterestInterval="
            + settings.getDetachedVisualInterestRefreshIntervalTicks()
            + " materializationCandidateInterval="
            + settings.getDetachedVisualCandidateRefreshIntervalTicks()
            + " materializationVisibilityInterval="
            + settings.getDetachedVisualVisibilityCheckIntervalTicks()
            + " materializationSpawnRate="
            + settings.getDetachedVisualMaxSpawnsPerTick()
            + " materializationCap="
            + settings.getDetachedVisualMaxMaterialized()));
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

    private static Boolean parseBoolean(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "enabled" -> true;
            case "false", "no", "off", "disabled" -> false;
            default -> null;
        };
    }
}
