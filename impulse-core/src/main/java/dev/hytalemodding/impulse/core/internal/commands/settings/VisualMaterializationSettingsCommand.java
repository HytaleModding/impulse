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
import dev.hytalemodding.impulse.core.plugin.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsSpaceSettings;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsVisualMaterializationSettings;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;

public class VisualMaterializationSettingsCommand extends AbstractAsyncPlayerCommand {

    private final OptionalArg<String> enabledArg = this.withOptionalArg(
        "enabled",
        "Enable detached visual proxy materialization: true or false",
        ArgTypes.STRING);
    private final OptionalArg<Integer> materializationRadiusArg = this.withOptionalArg(
        "materializationRadius",
        "Radius where detached bodies materialize visual proxies (1-"
            + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MATERIALIZATION_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> dematerializationRadiusArg = this.withOptionalArg(
        "dematerializationRadius",
        "Radius where detached visual proxies are removed (1-"
            + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_DEMATERIALIZATION_RADIUS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> interestIntervalArg = this.withOptionalArg(
        "interestInterval",
        "Ticks between detached visual interest refreshes (1-"
            + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> candidateIntervalArg = this.withOptionalArg(
        "candidateInterval",
        "Ticks between detached visual near-query/raycast candidate refreshes (1-"
            + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> visibilityIntervalArg = this.withOptionalArg(
        "visibilityInterval",
        "Ticks between detached generated-proxy visibility checks (1-"
            + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_CACHE_INTERVAL_TICKS
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> spawnRateArg = this.withOptionalArg(
        "spawnRate",
        "Maximum detached visual proxy spawns per tick (1-"
            + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MAX_SPAWNS_PER_TICK
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<Integer> capArg = this.withOptionalArg(
        "cap",
        "Maximum detached visual proxies materialized in the default space (1-"
            + PhysicsVisualMaterializationSettings.MAX_DETACHED_VISUAL_MAX_MATERIALIZED
            + ")",
        ArgTypes.INTEGER);
    private final OptionalArg<String> blockTypeArg = this.withOptionalArg(
        "blockType",
        "Default Hytale block type for detached visual proxies",
        ArgTypes.STRING);

    public VisualMaterializationSettingsCommand() {
        super("materialization", "Get or set detached visual materialization settings");
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

        if (enabledArg.provided(ctx)) {
            Boolean enabled = parseBoolean(enabledArg.get(ctx));
            if (enabled == null) {
                ctx.sender().sendMessage(Message.raw("enabled must be true or false."));
                return CompletableFuture.completedFuture(null);
            }
            settings.getVisualMaterializationSettings().setDetachedVisualMaterializationEnabled(enabled);
        }

        int materializationRadius = materializationRadiusArg.provided(ctx)
            ? materializationRadiusArg.get(ctx)
            : settings.getVisualMaterializationSettings().getDetachedVisualMaterializationRadius();
        int dematerializationRadius = dematerializationRadiusArg.provided(ctx)
            ? dematerializationRadiusArg.get(ctx)
            : settings.getVisualMaterializationSettings().getDetachedVisualDematerializationRadius();
        try {
            settings.getVisualMaterializationSettings().setDetachedVisualRadii(materializationRadius, dematerializationRadius);
            if (interestIntervalArg.provided(ctx)) {
                settings.getVisualMaterializationSettings().setDetachedVisualInterestRefreshIntervalTicks(
                    interestIntervalArg.get(ctx));
            }
            if (candidateIntervalArg.provided(ctx)) {
                settings.getVisualMaterializationSettings().setDetachedVisualCandidateRefreshIntervalTicks(
                    candidateIntervalArg.get(ctx));
            }
            if (visibilityIntervalArg.provided(ctx)) {
                settings.getVisualMaterializationSettings().setDetachedVisualVisibilityCheckIntervalTicks(
                    visibilityIntervalArg.get(ctx));
            }
            if (spawnRateArg.provided(ctx)) {
                settings.getVisualMaterializationSettings().setDetachedVisualMaxSpawnsPerTick(spawnRateArg.get(ctx));
            }
            if (capArg.provided(ctx)) {
                settings.getVisualMaterializationSettings().setDetachedVisualMaxMaterialized(capArg.get(ctx));
            }
            if (blockTypeArg.provided(ctx)) {
                settings.getVisualMaterializationSettings().setDetachedVisualBlockType(blockTypeArg.get(ctx));
            }
        } catch (IllegalArgumentException exception) {
            ctx.sender().sendMessage(Message.raw(exception.getMessage()));
            return CompletableFuture.completedFuture(null);
        }

        resource.setSpaceSettings(defaultSpaceId, settings);
        sendSummary(ctx, defaultSpaceId, settings);
        return CompletableFuture.completedFuture(null);
    }

    private boolean anyArgProvided(@Nonnull CommandContext ctx) {
        return enabledArg.provided(ctx)
            || materializationRadiusArg.provided(ctx)
            || dematerializationRadiusArg.provided(ctx)
            || interestIntervalArg.provided(ctx)
            || candidateIntervalArg.provided(ctx)
            || visibilityIntervalArg.provided(ctx)
            || spawnRateArg.provided(ctx)
            || capArg.provided(ctx)
            || blockTypeArg.provided(ctx);
    }

    private static void sendSummary(@Nonnull CommandContext ctx,
        @Nonnull SpaceId spaceId,
        @Nonnull PhysicsSpaceSettings settings) {
        ctx.sender().sendMessage(Message.raw("Impulse visual materialization settings for space "
            + spaceId.value()
            + ": enabled=" + settings.getVisualMaterializationSettings().isDetachedVisualMaterializationEnabled()
            + " materializationRadius=" + settings.getVisualMaterializationSettings().getDetachedVisualMaterializationRadius()
            + " dematerializationRadius=" + settings.getVisualMaterializationSettings().getDetachedVisualDematerializationRadius()
            + " interestInterval=" + settings.getVisualMaterializationSettings().getDetachedVisualInterestRefreshIntervalTicks()
            + " candidateInterval=" + settings.getVisualMaterializationSettings().getDetachedVisualCandidateRefreshIntervalTicks()
            + " visibilityInterval=" + settings.getVisualMaterializationSettings().getDetachedVisualVisibilityCheckIntervalTicks()
            + " spawnRate=" + settings.getVisualMaterializationSettings().getDetachedVisualMaxSpawnsPerTick()
            + " cap=" + settings.getVisualMaterializationSettings().getDetachedVisualMaxMaterialized()
            + " blockType=" + settings.getVisualMaterializationSettings().getDetachedVisualBlockType()));
    }

    private static Boolean parseBoolean(@Nonnull String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "enabled" -> true;
            case "false", "no", "off", "disabled" -> false;
            default -> null;
        };
    }
}
