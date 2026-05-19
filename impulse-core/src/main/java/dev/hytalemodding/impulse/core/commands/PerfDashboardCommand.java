package dev.hytalemodding.impulse.core.commands;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import dev.hytalemodding.impulse.api.ShapeType;
import dev.hytalemodding.impulse.core.diagnostics.PhysicsEntityDiagnostics;
import dev.hytalemodding.impulse.core.resources.PhysicsRuntimeProfilingResource;
import dev.hytalemodding.impulse.core.resources.PhysicsWorldResource;
import dev.hytalemodding.impulse.core.resources.WorldCollisionProfilingResource;
import dev.hytalemodding.impulse.core.voxel.WorldVoxelCollisionCache;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import javax.annotation.Nonnull;

public class PerfDashboardCommand extends AbstractWorldCommand {

    private static final double TARGET_TICK_MS = 1000.0 / 30.0;
    private static final DateTimeFormatter FILE_TIME =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

    public PerfDashboardCommand() {
        super("dashboard", "Write an HTML dashboard for Impulse profiling metrics");
    }

    @Override
    protected void execute(@Nonnull CommandContext ctx,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store) {
        PhysicsWorldResource physics = store.getResource(PhysicsWorldResource.getResourceType());
        PhysicsRuntimeProfilingResource runtime = store.getResource(
            PhysicsRuntimeProfilingResource.getResourceType());
        WorldCollisionProfilingResource worldCollision = store.getResource(
            WorldCollisionProfilingResource.getResourceType());
        PhysicsEntityDiagnostics.Snapshot entities = PhysicsEntityDiagnostics.collect(store);
        SpaceStats totals = collectTotals(physics);

        DashboardData data = DashboardData.collect(world.getName(),
            physics,
            runtime,
            worldCollision,
            entities,
            totals);
        Path output = Path.of("impulse-perf",
            "dashboard-" + sanitizeFileName(world.getName()) + "-" + FILE_TIME.format(LocalDateTime.now()) + ".html");
        try {
            Files.createDirectories(output.getParent());
            Files.writeString(output, renderDashboard(data), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            ctx.sender().sendMessage(Message.raw("Failed to write Impulse perf dashboard: "
                + exception.getMessage()));
            return;
        }

        ctx.sender().sendMessage(Message.raw("Wrote Impulse perf dashboard: "
            + output.toAbsolutePath()));
    }

    @Nonnull
    private static SpaceStats collectTotals(@Nonnull PhysicsWorldResource resource) {
        SpaceStats totals = new SpaceStats();
        WorldVoxelCollisionCache cache = resource.getWorldVoxelCollisionCache();
        for (PhysicsSpace space : resource.getSpaces()) {
            space.forEachBody(body -> classifyBody(resource, cache, space, body, totals));
            totals.joints += space.jointCount();
            totals.contacts += space.getContacts().size();
        }
        return totals;
    }

    private static void classifyBody(@Nonnull PhysicsWorldResource resource,
        @Nonnull WorldVoxelCollisionCache cache,
        @Nonnull PhysicsSpace space,
        @Nonnull PhysicsBody body,
        @Nonnull SpaceStats stats) {
        stats.bodies++;
        if (body.isDynamic()) {
            stats.dynamicBodies++;
            if (body.isSleeping()) {
                stats.sleepingDynamicBodies++;
            } else {
                stats.awakeDynamicBodies++;
            }
        } else if (body.isKinematic()) {
            stats.kinematicBodies++;
        } else {
            stats.staticBodies++;
        }

        PhysicsWorldResource.BodyRegistration registration = resource.getBodyRegistration(body);
        if (registration != null && registration.ownerKind() == PhysicsWorldResource.BodyOwnerKind.ENTITY) {
            stats.entityOwnedBodies++;
            return;
        }
        if (registration != null && registration.ownerKind() == PhysicsWorldResource.BodyOwnerKind.DETACHED) {
            stats.detachedBodies++;
            return;
        }
        if (body.getShapeType() == ShapeType.PLANE) {
            stats.planeBodies++;
            return;
        }
        if (cache.containsBody(space.getId(), body)) {
            stats.worldCollisionBodies++;
            return;
        }
        stats.rawBodies++;
    }

    @Nonnull
    private static String renderDashboard(@Nonnull DashboardData data) {
        StringBuilder html = new StringBuilder();
        html.append("""
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Impulse perf dashboard</title>
            <style>
            :root {
              --paper: #f4efe7;
              --ink: #221b14;
              --muted: #766b5d;
              --line: #d8caba;
              --red: #b83f2f;
              --amber: #c9822c;
              --green: #477b4b;
              --blue: #315f7d;
              --card: rgba(255, 252, 246, 0.86);
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              font-family: Georgia, 'Times New Roman', serif;
              color: var(--ink);
              background:
                radial-gradient(circle at 12% 18%, rgba(184, 63, 47, 0.16), transparent 28rem),
                radial-gradient(circle at 88% 8%, rgba(49, 95, 125, 0.18), transparent 24rem),
                linear-gradient(135deg, #f8f1e7, var(--paper));
            }
            main { width: min(1280px, calc(100vw - 32px)); margin: 0 auto; padding: 32px 0 48px; }
            header { display: flex; justify-content: space-between; gap: 24px; align-items: end; margin-bottom: 24px; }
            h1 { margin: 0; font-size: clamp(2.2rem, 5vw, 5rem); letter-spacing: -0.06em; line-height: 0.92; }
            h2 { margin: 0 0 14px; font-size: 1.1rem; text-transform: uppercase; letter-spacing: 0.12em; color: var(--muted); }
            .stamp { color: var(--muted); text-align: right; line-height: 1.5; }
            .grid { display: grid; grid-template-columns: repeat(12, 1fr); gap: 16px; }
            .card {
              background: var(--card);
              border: 1px solid var(--line);
              border-radius: 22px;
              padding: 18px;
              box-shadow: 0 18px 45px rgba(74, 54, 33, 0.10);
              backdrop-filter: blur(6px);
            }
            .span-3 { grid-column: span 3; }
            .span-4 { grid-column: span 4; }
            .span-6 { grid-column: span 6; }
            .span-8 { grid-column: span 8; }
            .span-12 { grid-column: span 12; }
            .metric { font-size: 2.1rem; line-height: 1; font-weight: 700; letter-spacing: -0.04em; }
            .label { color: var(--muted); margin-top: 8px; }
            .bar { height: 16px; background: #e6dacb; border-radius: 999px; overflow: hidden; margin: 10px 0 3px; }
            .fill { height: 100%; border-radius: inherit; }
            .red { background: var(--red); } .amber { background: var(--amber); }
            .green { background: var(--green); } .blue { background: var(--blue); }
            .row { display: grid; grid-template-columns: 170px 1fr 90px; gap: 12px; align-items: center; margin: 10px 0; }
            .mini { font-variant-numeric: tabular-nums; text-align: right; color: var(--muted); }
            .callout { font-size: 1.05rem; line-height: 1.5; }
            table { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
            th, td { text-align: left; border-bottom: 1px solid var(--line); padding: 8px 6px; }
            th { color: var(--muted); font-weight: 400; }
            @media (max-width: 900px) {
              header { display: block; }
              .stamp { text-align: left; margin-top: 12px; }
              .span-3, .span-4, .span-6, .span-8 { grid-column: span 12; }
              .row { grid-template-columns: 1fr; gap: 4px; }
              .mini { text-align: left; }
            }
            </style>
            </head>
            <body><main>
            """);
        html.append("<header><div><h1>Impulse<br>Perf</h1></div><div class=\"stamp\">World ")
            .append(escape(data.worldName))
            .append("<br>")
            .append(escape(data.generatedAt))
            .append("<br>Target 30 TPS / ")
            .append(formatMs(TARGET_TICK_MS))
            .append("ms</div></header>");

        html.append("<section class=\"grid\">");
        metricCard(html, "Measured Impulse avg", formatMs(data.impulseMeasuredMs) + "ms",
            percentOf(data.impulseMeasuredMs, TARGET_TICK_MS), "Lower is better. This excludes unknown server overhead.");
        metricCard(html, "Physics step", formatMs(data.physicsStepMs) + "ms",
            percentOf(data.physicsStepMs, TARGET_TICK_MS), "Native backend step cost.");
        metricCard(html, "Snapshot", formatMs(data.snapshotMs) + "ms",
            percentOf(data.snapshotMs, TARGET_TICK_MS), "Pose publication and spatial index refresh.");
        metricCard(html, "World collision", formatMs(data.worldCollisionMs) + "ms",
            percentOf(data.worldCollisionMs, TARGET_TICK_MS), "Streaming terrain collision.");

        html.append("<article class=\"card span-8\"><h2>Tick budget</h2>");
        budgetRow(html, "Physics step", data.physicsStepMs, TARGET_TICK_MS, "red");
        budgetRow(html, "Snapshot", data.snapshotMs, TARGET_TICK_MS, "amber");
        budgetRow(html, "Sync", data.syncMs, TARGET_TICK_MS, "blue");
        budgetRow(html, "World collision", data.worldCollisionMs, TARGET_TICK_MS, "green");
        html.append("</article>");

        html.append("<article class=\"card span-4\"><h2>Read</h2><div class=\"callout\">");
        html.append(escape(data.readout()));
        html.append("</div></article>");

        html.append("<article class=\"card span-6\"><h2>Bodies</h2><table>");
        tableRow(html, "Total backend bodies", data.bodies);
        tableRow(html, "Dynamic", data.dynamicBodies);
        tableRow(html, "Awake dynamic", data.awakeBodies);
        tableRow(html, "Sleeping dynamic", data.sleepingBodies);
        tableRow(html, "World collision static", data.worldCollisionBodies);
        tableRow(html, "Contacts", data.contacts);
        html.append("</table></article>");

        html.append("<article class=\"card span-6\"><h2>Visual / Hytale</h2><table>");
        tableRow(html, "Detached bodies", data.detachedBodies);
        tableRow(html, "Detached visual proxies", data.detachedVisualProxies);
        tableRow(html, "Transform entities", data.transformEntities);
        tableRow(html, "Visible entities", data.visibleEntities);
        tableRow(html, "Visual followers", data.visualFollowers);
        html.append("</table></article>");

        html.append("<article class=\"card span-12\"><h2>World collision stream</h2>");
        budgetRow(html, "Body targets per tick", data.bodyTargetsPerTick, Math.max(1.0, data.bodyIndexCandidatesPerTick), "green");
        budgetRow(html, "Section builds", data.sectionsBuilt, Math.max(1.0, data.sectionRequests), "amber");
        budgetRow(html, "Cache hits", data.sectionCacheHits, Math.max(1.0, data.sectionRequests), "blue");
        html.append("</article>");

        html.append("</section></main></body></html>");
        return html.toString();
    }

    private static void metricCard(@Nonnull StringBuilder html,
        @Nonnull String title,
        @Nonnull String value,
        double ratio,
        @Nonnull String label) {
        html.append("<article class=\"card span-3\"><h2>")
            .append(escape(title))
            .append("</h2><div class=\"metric\">")
            .append(escape(value))
            .append("</div><div class=\"bar\"><div class=\"fill ")
            .append(ratio > 1.0 ? "red" : ratio > 0.55 ? "amber" : "green")
            .append("\" style=\"width:")
            .append(formatPercent(Math.min(1.0, ratio)))
            .append("\"></div></div><div class=\"label\">")
            .append(escape(label))
            .append("</div></article>");
    }

    private static void budgetRow(@Nonnull StringBuilder html,
        @Nonnull String label,
        double value,
        double budget,
        @Nonnull String color) {
        html.append("<div class=\"row\"><div>")
            .append(escape(label))
            .append("</div><div class=\"bar\"><div class=\"fill ")
            .append(color)
            .append("\" style=\"width:")
            .append(formatPercent(Math.min(1.0, value / Math.max(0.001, budget))))
            .append("\"></div></div><div class=\"mini\">")
            .append(formatOne(value))
            .append("</div></div>");
    }

    private static void tableRow(@Nonnull StringBuilder html, @Nonnull String label, int value) {
        html.append("<tr><th>")
            .append(escape(label))
            .append("</th><td>")
            .append(value)
            .append("</td></tr>");
    }

    private static double percentOf(double value, double budget) {
        return value / Math.max(0.001, budget);
    }

    @Nonnull
    private static String formatMs(double millis) {
        return String.format(Locale.ROOT, "%.2f", millis);
    }

    @Nonnull
    private static String formatOne(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Nonnull
    private static String formatPercent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    @Nonnull
    private static String sanitizeFileName(@Nonnull String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    @Nonnull
    private static String escape(@Nonnull String value) {
        return value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private static final class SpaceStats {

        private int bodies;
        private int dynamicBodies;
        private int awakeDynamicBodies;
        private int sleepingDynamicBodies;
        private int staticBodies;
        private int kinematicBodies;
        private int entityOwnedBodies;
        private int detachedBodies;
        private int worldCollisionBodies;
        private int planeBodies;
        private int rawBodies;
        private int joints;
        private int contacts;
    }

    private record DashboardData(@Nonnull String worldName,
        @Nonnull String generatedAt,
        double physicsStepMs,
        double snapshotMs,
        double syncMs,
        double worldCollisionMs,
        double impulseMeasuredMs,
        int bodies,
        int dynamicBodies,
        int awakeBodies,
        int sleepingBodies,
        int worldCollisionBodies,
        int contacts,
        int detachedBodies,
        int detachedVisualProxies,
        int transformEntities,
        int visibleEntities,
        int visualFollowers,
        double bodyTargetsPerTick,
        double bodyIndexCandidatesPerTick,
        int sectionsBuilt,
        int sectionRequests,
        int sectionCacheHits) {

        @Nonnull
        private static DashboardData collect(@Nonnull String worldName,
            @Nonnull PhysicsWorldResource physics,
            @Nonnull PhysicsRuntimeProfilingResource runtime,
            @Nonnull WorldCollisionProfilingResource worldCollision,
            @Nonnull PhysicsEntityDiagnostics.Snapshot entities,
            @Nonnull SpaceStats totals) {
            PhysicsRuntimeProfilingResource.StepSnapshot step = runtime.getCumulativeStep();
            PhysicsRuntimeProfilingResource.SyncSnapshot sync = runtime.getCumulativeSync();
            WorldCollisionProfilingResource.Snapshot collision = worldCollision.getCumulativeSnapshot();
            double physicsStepMs = averageMs(step.getTickNanos(), step.getTickSamples());
            double snapshotMs = averageMs(step.getSnapshotNanos(), step.getTickSamples());
            double syncMs = averageMs(sync.getTickNanos(), sync.getTickSamples());
            double worldCollisionMs = averageMs(collision.getTickNanos(), collision.getTickSamples());
            int detachedVisualProxies = 0;
            Collection<PhysicsBody> detached = physics.getDetachedBodies();
            for (PhysicsBody body : detached) {
                if (physics.getDetachedVisualProxy(body) != null) {
                    detachedVisualProxies++;
                }
            }
            return new DashboardData(worldName,
                LocalDateTime.now().toString(),
                physicsStepMs,
                snapshotMs,
                syncMs,
                worldCollisionMs,
                physicsStepMs + snapshotMs + syncMs + worldCollisionMs,
                totals.bodies,
                totals.dynamicBodies,
                totals.awakeDynamicBodies,
                totals.sleepingDynamicBodies,
                totals.worldCollisionBodies,
                totals.contacts,
                detached.size(),
                detachedVisualProxies,
                entities.transformEntities(),
                entities.visibleEntities(),
                entities.physicsVisualEntities(),
                average(collision.getBodyStreamingTargets(), collision.getTickSamples()),
                average(collision.getBodySpatialIndexCandidates(), collision.getTickSamples()),
                collision.getSectionsBuilt(),
                collision.getSectionRequests(),
                collision.getSectionCacheHits());
        }

        @Nonnull
        private String readout() {
            if (physicsStepMs >= snapshotMs && physicsStepMs >= worldCollisionMs) {
                return "Main pressure is native physics step. For full body-body collision, focus on active islands, contact count, and solver cost.";
            }
            if (snapshotMs >= worldCollisionMs) {
                return "Main pressure is snapshot publication. Batch pose reads and lower per-body Java/native calls are the next lever.";
            }
            return "Main pressure is world collision streaming. Budget section work and avoid body-wide scans during rebuilds.";
        }
    }

    private static double averageMs(long nanos, int samples) {
        if (samples <= 0) {
            return 0.0;
        }
        return nanos / (double) samples / 1_000_000.0;
    }

    private static double average(int value, int samples) {
        if (samples <= 0) {
            return 0.0;
        }
        return value / (double) samples;
    }
}
