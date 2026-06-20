package dev.hytalemodding.impulse.core.internal.benchmark;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistry;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.EmptyResourceStorage;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class IdentityFootprintProbeTest {

    private static final int DEFAULT_ENTITY_COUNT = 10_000;
    private static final int DEFAULT_LOOKUP_PASSES = 5;
    private static final long GENERATION = 1L;
    private static final long SLOT_MASK = 0x0000_FFFF_FFFF_FFFFL;

    @Test
    void reportsUuidAndGenerationalIdentityCosts() throws IOException {
        int entityCount = intProperty("impulse.identityProbe.entityCount",
            DEFAULT_ENTITY_COUNT);
        int lookupPasses = intProperty("impulse.identityProbe.lookupPasses",
            DEFAULT_LOOKUP_PASSES);

        List<ScenarioResult> results = List.of(
            runUuid("uuid-component-only", entityCount, lookupPasses, false),
            runGenerationalId("generational-id-component-only",
                entityCount,
                lookupPasses,
                false),
            runUuid("uuid-component-object-index", entityCount, lookupPasses, true),
            runGenerationalId("generational-id-component-primitive-index",
                entityCount,
                lookupPasses,
                true));

        Path report = Path.of("build",
            "reports",
            "impulse",
            "identity-footprint-probe.tsv");
        Files.createDirectories(report.getParent());
        Files.writeString(report, ScenarioResult.tsv(results));

        assertEquals(4, results.size());
        for (ScenarioResult result : results) {
            assertEquals(result.entities(), result.storeEntityCount(), result.name());
            assertTrue(result.archetypeChunkCount() > 0, result.name());
            assertNotEquals(0L, result.scanChecksum(), result.name());
            if (!"none".equals(result.indexKind())) {
                assertNotEquals(0L, result.lookupChecksum(), result.name());
            }
        }
    }

    @Nonnull
    private static ScenarioResult runUuid(@Nonnull String name,
        int entities,
        int lookupPasses,
        boolean indexed) {
        ComponentRegistry<BenchmarkWorld> registry = new ComponentRegistry<>();
        ComponentType<BenchmarkWorld, UuidIdentityComponent> type =
            registry.registerComponent(UuidIdentityComponent.class,
                () -> new UuidIdentityComponent(uuidFor(0)));
        Store<BenchmarkWorld> store = registry.addStore(new BenchmarkWorld(name),
            EmptyResourceStorage.get());
        try {
            forceGc();
            long heapBefore = usedHeap();
            long buildStart = System.nanoTime();
            Ref<BenchmarkWorld>[] refs = addUuidRows(store, registry, type, entities);
            Object2ObjectOpenHashMap<UUID, Ref<BenchmarkWorld>> index =
                indexed ? uuidIndex(refs) : null;
            long buildNanos = System.nanoTime() - buildStart;
            forceGc();
            long heapAfterBuild = usedHeap();

            ScanResult scan = scanUuid(store, type);
            LookupResult lookup = index != null
                ? lookupUuid(index, entities, lookupPasses)
                : LookupResult.EMPTY;

            return result(name,
                entities,
                store,
                "uuid",
                indexed ? "Object2ObjectOpenHashMap<UUID, Ref>" : "none",
                heapAfterBuild - heapBefore,
                buildNanos,
                scan,
                lookup);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static ScenarioResult runGenerationalId(@Nonnull String name,
        int entities,
        int lookupPasses,
        boolean indexed) {
        ComponentRegistry<BenchmarkWorld> registry = new ComponentRegistry<>();
        ComponentType<BenchmarkWorld, GenerationalIdComponent> type =
            registry.registerComponent(GenerationalIdComponent.class,
                () -> new GenerationalIdComponent(generationalId(0)));
        Store<BenchmarkWorld> store = registry.addStore(new BenchmarkWorld(name),
            EmptyResourceStorage.get());
        try {
            forceGc();
            long heapBefore = usedHeap();
            long buildStart = System.nanoTime();
            Ref<BenchmarkWorld>[] refs = addGenerationalRows(store, registry, type, entities);
            Long2ObjectOpenHashMap<Ref<BenchmarkWorld>> index =
                indexed ? generationalIndex(refs) : null;
            long buildNanos = System.nanoTime() - buildStart;
            forceGc();
            long heapAfterBuild = usedHeap();

            ScanResult scan = scanGenerationalId(store, type);
            LookupResult lookup = index != null
                ? lookupGenerationalId(index, entities, lookupPasses)
                : LookupResult.EMPTY;

            return result(name,
                entities,
                store,
                "generational-long",
                indexed ? "Long2ObjectOpenHashMap<Ref>" : "none",
                heapAfterBuild - heapBefore,
                buildNanos,
                scan,
                lookup);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static ScenarioResult result(@Nonnull String name,
        int entities,
        @Nonnull Store<BenchmarkWorld> store,
        @Nonnull String componentKind,
        @Nonnull String indexKind,
        long heapBytes,
        long buildNanos,
        @Nonnull ScanResult scan,
        @Nonnull LookupResult lookup) {
        return new ScenarioResult(name,
            entities,
            store.getEntityCount(),
            componentKind,
            indexKind,
            Math.max(0L, heapBytes),
            buildNanos,
            scan.nanos(),
            lookup.nanos(),
            lookup.count(),
            store.getArchetypeChunkCount(),
            store.collectArchetypeChunkData().length,
            scan.checksum(),
            lookup.checksum());
    }

    @Nonnull
    private static Ref<BenchmarkWorld>[] addUuidRows(@Nonnull Store<BenchmarkWorld> store,
        @Nonnull ComponentRegistry<BenchmarkWorld> registry,
        @Nonnull ComponentType<BenchmarkWorld, UuidIdentityComponent> type,
        int entities) {
        @SuppressWarnings("unchecked")
        Holder<BenchmarkWorld>[] holders = new Holder[entities];
        for (int entity = 0; entity < entities; entity++) {
            Holder<BenchmarkWorld> holder = registry.newHolder();
            holder.addComponent(type, new UuidIdentityComponent(uuidFor(entity)));
            holders[entity] = holder;
        }
        return store.addEntities(holders, AddReason.SPAWN);
    }

    @Nonnull
    private static Ref<BenchmarkWorld>[] addGenerationalRows(
        @Nonnull Store<BenchmarkWorld> store,
        @Nonnull ComponentRegistry<BenchmarkWorld> registry,
        @Nonnull ComponentType<BenchmarkWorld, GenerationalIdComponent> type,
        int entities) {
        @SuppressWarnings("unchecked")
        Holder<BenchmarkWorld>[] holders = new Holder[entities];
        for (int entity = 0; entity < entities; entity++) {
            Holder<BenchmarkWorld> holder = registry.newHolder();
            holder.addComponent(type, new GenerationalIdComponent(generationalId(entity)));
            holders[entity] = holder;
        }
        return store.addEntities(holders, AddReason.SPAWN);
    }

    @Nonnull
    private static Object2ObjectOpenHashMap<UUID, Ref<BenchmarkWorld>> uuidIndex(
        @Nonnull Ref<BenchmarkWorld>[] refs) {
        Object2ObjectOpenHashMap<UUID, Ref<BenchmarkWorld>> index =
            new Object2ObjectOpenHashMap<>(refs.length);
        for (int entity = 0; entity < refs.length; entity++) {
            index.put(uuidFor(entity), refs[entity]);
        }
        return index;
    }

    @Nonnull
    private static Long2ObjectOpenHashMap<Ref<BenchmarkWorld>> generationalIndex(
        @Nonnull Ref<BenchmarkWorld>[] refs) {
        Long2ObjectOpenHashMap<Ref<BenchmarkWorld>> index =
            new Long2ObjectOpenHashMap<>(refs.length);
        for (int entity = 0; entity < refs.length; entity++) {
            index.put(generationalId(entity), refs[entity]);
        }
        return index;
    }

    @Nonnull
    private static ScanResult scanUuid(@Nonnull Store<BenchmarkWorld> store,
        @Nonnull ComponentType<BenchmarkWorld, UuidIdentityComponent> type) {
        long start = System.nanoTime();
        long[] checksum = {0L};
        BiConsumer<ArchetypeChunk<BenchmarkWorld>, CommandBuffer<BenchmarkWorld>> consumer =
            (chunk, _) -> {
                for (int index = 0; index < chunk.size(); index++) {
                    checksum[0] += chunk.getComponent(index, type).checksum();
                }
            };
        store.forEachChunk(type, consumer);
        return new ScanResult(System.nanoTime() - start, checksum[0]);
    }

    @Nonnull
    private static ScanResult scanGenerationalId(@Nonnull Store<BenchmarkWorld> store,
        @Nonnull ComponentType<BenchmarkWorld, GenerationalIdComponent> type) {
        long start = System.nanoTime();
        long[] checksum = {0L};
        BiConsumer<ArchetypeChunk<BenchmarkWorld>, CommandBuffer<BenchmarkWorld>> consumer =
            (chunk, _) -> {
                for (int index = 0; index < chunk.size(); index++) {
                    checksum[0] += chunk.getComponent(index, type).checksum();
                }
            };
        store.forEachChunk(type, consumer);
        return new ScanResult(System.nanoTime() - start, checksum[0]);
    }

    @Nonnull
    private static LookupResult lookupUuid(
        @Nonnull Object2ObjectOpenHashMap<UUID, Ref<BenchmarkWorld>> index,
        int entities,
        int passes) {
        UUID[] keys = new UUID[entities];
        for (int entity = 0; entity < entities; entity++) {
            keys[entity] = uuidFor(entity);
        }
        long checksum = 0L;
        long start = System.nanoTime();
        for (int pass = 0; pass < passes; pass++) {
            for (UUID key : keys) {
                Ref<BenchmarkWorld> ref = index.get(key);
                if (ref == null) {
                    throw new AssertionError("Missing UUID index entry: " + key);
                }
                checksum += ref.getIndex();
            }
        }
        return new LookupResult(System.nanoTime() - start, (long) entities * passes, checksum);
    }

    @Nonnull
    private static LookupResult lookupGenerationalId(
        @Nonnull Long2ObjectOpenHashMap<Ref<BenchmarkWorld>> index,
        int entities,
        int passes) {
        long[] keys = new long[entities];
        for (int entity = 0; entity < entities; entity++) {
            keys[entity] = generationalId(entity);
        }
        long checksum = 0L;
        long start = System.nanoTime();
        for (int pass = 0; pass < passes; pass++) {
            for (long key : keys) {
                Ref<BenchmarkWorld> ref = index.get(key);
                if (ref == null) {
                    throw new AssertionError("Missing generational id index entry: " + key);
                }
                checksum += ref.getIndex();
            }
        }
        return new LookupResult(System.nanoTime() - start, (long) entities * passes, checksum);
    }

    @Nonnull
    private static UUID uuidFor(int index) {
        return new UUID(0x496d_7075_6c73_6500L, index + 1L);
    }

    private static long generationalId(int index) {
        return (GENERATION << 48) | (index & SLOT_MASK);
    }

    private static int intProperty(@Nonnull String name, int defaultValue) {
        Integer propertyValue = Integer.getInteger(name);
        if (propertyValue != null) {
            return propertyValue;
        }
        String environmentName = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .toUpperCase()
            .replace('.', '_')
            .replace('-', '_');
        String environmentValue = System.getenv(environmentName);
        if (environmentValue == null || environmentValue.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(environmentValue);
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void forceGc() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(10L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private record ScanResult(long nanos, long checksum) {
    }

    private record LookupResult(long nanos, long count, long checksum) {

        private static final LookupResult EMPTY = new LookupResult(0L, 0L, 0L);
    }

    private record ScenarioResult(@Nonnull String name,
                                  int entities,
                                  int storeEntityCount,
                                  @Nonnull String componentKind,
                                  @Nonnull String indexKind,
                                  long heapBytes,
                                  long buildNanos,
                                  long scanNanos,
                                  long lookupNanos,
                                  long lookupCount,
                                  int archetypeChunkCount,
                                  int archetypeDataCount,
                                  long scanChecksum,
                                  long lookupChecksum) {

        @Nonnull
        static String tsv(@Nonnull List<ScenarioResult> results) {
            String header = String.join("\t",
                "scenario",
                "entities",
                "componentKind",
                "indexKind",
                "heapBytes",
                "bytesPerEntity",
                "buildNsPerEntity",
                "scanNsPerEntity",
                "lookupNsPerLookup",
                "lookupCount",
                "archetypeChunkCount",
                "archetypeDataCount",
                "scanChecksum",
                "lookupChecksum");
            String rows = results.stream()
                .map(ScenarioResult::tsvRow)
                .collect(Collectors.joining(System.lineSeparator()));
            return header + System.lineSeparator() + rows + System.lineSeparator();
        }

        @Nonnull
        private String tsvRow() {
            return String.join("\t",
                name,
                Integer.toString(entities),
                componentKind,
                indexKind,
                Long.toString(heapBytes),
                Long.toString(heapBytes / Math.max(1, entities)),
                Long.toString(buildNanos / Math.max(1, entities)),
                Long.toString(scanNanos / Math.max(1, entities)),
                Long.toString(lookupNanos / Math.max(1L, lookupCount)),
                Long.toString(lookupCount),
                Integer.toString(archetypeChunkCount),
                Integer.toString(archetypeDataCount),
                Long.toString(scanChecksum),
                Long.toString(lookupChecksum));
        }
    }

    private record BenchmarkWorld(@Nonnull String name) {
    }

    private static final class UuidIdentityComponent implements Component<BenchmarkWorld> {

        @Nonnull
        private final UUID uuid;

        private UuidIdentityComponent(@Nonnull UUID uuid) {
            this.uuid = uuid;
        }

        private long checksum() {
            return uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        }

        @Nonnull
        @Override
        public UuidIdentityComponent clone() {
            return new UuidIdentityComponent(uuid);
        }
    }

    private static final class GenerationalIdComponent implements Component<BenchmarkWorld> {

        private final long id;

        private GenerationalIdComponent(long id) {
            this.id = id;
        }

        private long checksum() {
            return id;
        }

        @Nonnull
        @Override
        public GenerationalIdComponent clone() {
            return new GenerationalIdComponent(id);
        }
    }
}
