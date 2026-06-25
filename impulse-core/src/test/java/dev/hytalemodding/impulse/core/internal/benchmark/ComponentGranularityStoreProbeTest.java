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
import com.hypixel.hytale.component.query.Query;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class ComponentGranularityStoreProbeTest {

    private static final int DEFAULT_SPACE_COUNT = 1024;
    private static final int DEFAULT_BODY_COUNT = 10_000;
    private static final int OPTIONAL_STRIDE = 10;

    @Test
    void reportsGroupedDomainAndTinyComponentCosts() throws IOException {
        int spaceCount = intProperty("impulse.componentProbe.spaceCount", DEFAULT_SPACE_COUNT);
        int bodyCount = intProperty("impulse.componentProbe.bodyCount", DEFAULT_BODY_COUNT);
        List<ScenarioResult> results = new ArrayList<>();

        results.add(run("space-grouped-settings", spaceCount, ComponentLayout.GROUPED, false));
        results.add(run("space-split-domain-settings", spaceCount, ComponentLayout.DOMAIN_SPLIT, false));
        results.add(run("space-tiny-settings", spaceCount, ComponentLayout.TINY, false));
        results.add(run("space-split-domain-settings-10pct-optional",
            spaceCount,
            ComponentLayout.DOMAIN_SPLIT,
            true));
        results.add(run("body-grouped-authoring", bodyCount, ComponentLayout.GROUPED, false));
        results.add(run("body-current-domain-authoring", bodyCount, ComponentLayout.DOMAIN_SPLIT, false));
        results.add(run("body-tiny-authoring", bodyCount, ComponentLayout.TINY, false));
        results.add(run("body-current-domain-authoring-10pct-optional",
            bodyCount,
            ComponentLayout.DOMAIN_SPLIT,
            true));

        Path report = Path.of("build",
            "reports",
            "impulse",
            "component-granularity-probe.tsv");
        Files.createDirectories(report.getParent());
        Files.writeString(report, ScenarioResult.tsv(results));

        assertEquals(8, results.size());
        for (ScenarioResult result : results) {
            assertEquals(result.entities(), result.storeEntityCount(), result.name());
            assertTrue(result.archetypeChunkCount() > 0, result.name());
            assertNotEquals(0L, result.iterationChecksum(), result.name());
        }
    }

    @Nonnull
    private static ScenarioResult run(@Nonnull String name,
        int entities,
        @Nonnull ComponentLayout layout,
        boolean optionalFragmentation) {
        ComponentRegistry<BenchmarkWorld> registry = new ComponentRegistry<>();
        List<ComponentSpec<? extends ProbeComponent>> required = layout.registerRequired(registry);
        ComponentSpec<OptionalComponent> optional =
            optionalFragmentation ? optional(registry) : null;
        Store<BenchmarkWorld> store = registry.addStore(new BenchmarkWorld(name),
            EmptyResourceStorage.get());
        try {
            forceGc();
            long heapBefore = usedHeap();
            long addStart = System.nanoTime();
            Ref<BenchmarkWorld>[] refs = addEntities(store,
                registry,
                required,
                optional,
                entities);
            long addNanos = System.nanoTime() - addStart;
            forceGc();
            long heapAfterAdd = usedHeap();

            long iterateStart = System.nanoTime();
            long checksum = iterate(store, required);
            long iterateNanos = System.nanoTime() - iterateStart;

            long targetedIterateStart = System.nanoTime();
            long targetedChecksum = iterate(store, List.of(required.getFirst()));
            long targetedIterateNanos = System.nanoTime() - targetedIterateStart;

            long mutateStart = System.nanoTime();
            replaceComponent(store, required.getFirst(), refs);
            long mutateNanos = System.nanoTime() - mutateStart;

            int entityCount = store.getEntityCount();
            int archetypeChunkCount = store.getArchetypeChunkCount();
            int archetypeDataCount = store.collectArchetypeChunkData().length;
            int requiredPayloadInts = required.stream().mapToInt(ComponentSpec::payloadInts).sum();
            long optionalInstances = optional != null
                ? ((long) (entities - 1) / OPTIONAL_STRIDE) + 1L
                : 0L;
            long componentInstances = (long) entities * required.size() + optionalInstances;
            long payloadBytes = ((long) entities * requiredPayloadInts
                + optionalInstances * (optional != null ? optional.payloadInts() : 0))
                * Integer.BYTES;

            return new ScenarioResult(name,
                entities,
                entityCount,
                required.size(),
                optionalFragmentation ? 1 : 0,
                requiredPayloadInts,
                componentInstances,
                payloadBytes,
                Math.max(0L, heapAfterAdd - heapBefore),
                addNanos,
                iterateNanos,
                targetedIterateNanos,
                mutateNanos,
                archetypeChunkCount,
                archetypeDataCount,
                checksum,
                targetedChecksum);
        } finally {
            registry.removeStore(store);
            registry.shutdown();
        }
    }

    @Nonnull
    private static Ref<BenchmarkWorld>[] addEntities(@Nonnull Store<BenchmarkWorld> store,
        @Nonnull ComponentRegistry<BenchmarkWorld> registry,
        @Nonnull List<ComponentSpec<? extends ProbeComponent>> required,
        ComponentSpec<OptionalComponent> optional,
        int entities) {
        @SuppressWarnings("unchecked")
        Holder<BenchmarkWorld>[] holders = new Holder[entities];
        for (int entity = 0; entity < entities; entity++) {
            Holder<BenchmarkWorld> holder = registry.newHolder();
            for (ComponentSpec<? extends ProbeComponent> spec : required) {
                addComponent(holder, spec, entity);
            }
            if (optional != null && entity % OPTIONAL_STRIDE == 0) {
                addComponent(holder, optional, entity);
            }
            holders[entity] = holder;
        }
        return store.addEntities(holders, AddReason.SPAWN);
    }

    private static <T extends ProbeComponent> void addComponent(
        @Nonnull Holder<BenchmarkWorld> holder,
        @Nonnull ComponentSpec<T> spec,
        int seed) {
        holder.addComponent(spec.type(), spec.factory().apply(seed));
    }

    private static long iterate(@Nonnull Store<BenchmarkWorld> store,
        @Nonnull List<ComponentSpec<? extends ProbeComponent>> required) {
        Query<BenchmarkWorld> query = query(required);
        long[] checksum = {0L};
        BiConsumer<ArchetypeChunk<BenchmarkWorld>, CommandBuffer<BenchmarkWorld>> consumer =
            (chunk, _) -> checksum[0] += checksum(required, chunk);
        store.forEachChunk(query, consumer);
        return checksum[0];
    }

    private static long checksum(@Nonnull List<ComponentSpec<? extends ProbeComponent>> specs,
        @Nonnull ArchetypeChunk<BenchmarkWorld> chunk) {
        long checksum = 0L;
        for (int index = 0; index < chunk.size(); index++) {
            for (ComponentSpec<? extends ProbeComponent> spec : specs) {
                checksum += component(chunk, index, spec).checksum();
            }
        }
        return checksum;
    }

    private static <T extends ProbeComponent> T component(
        @Nonnull ArchetypeChunk<BenchmarkWorld> chunk,
        int index,
        @Nonnull ComponentSpec<T> spec) {
        return chunk.getComponent(index, spec.type());
    }

    private static <T extends ProbeComponent> void replaceComponent(
        @Nonnull Store<BenchmarkWorld> store,
        @Nonnull ComponentSpec<T> spec,
        @Nonnull Ref<BenchmarkWorld>[] refs) {
        for (int index = 0; index < refs.length; index++) {
            store.putComponent(refs[index], spec.type(), spec.factory().apply(index + 1));
        }
    }

    @Nonnull
    @SuppressWarnings("unchecked")
    private static Query<BenchmarkWorld> query(
        @Nonnull List<ComponentSpec<? extends ProbeComponent>> specs) {
        Query<BenchmarkWorld>[] queries = specs.stream()
            .map(ComponentSpec::type)
            .toArray(Query[]::new);
        return Query.and(queries);
    }

    @Nonnull
    private static ComponentSpec<OptionalComponent> optional(
        @Nonnull ComponentRegistry<BenchmarkWorld> registry) {
        return spec(registry, OptionalComponent.class, OptionalComponent::new, 1);
    }

    @Nonnull
    private static <T extends ProbeComponent> ComponentSpec<T> spec(
        @Nonnull ComponentRegistry<BenchmarkWorld> registry,
        @Nonnull Class<T> typeClass,
        @Nonnull IntFunction<T> factory,
        int payloadInts) {
        return new ComponentSpec<>(registry.registerComponent(typeClass,
            () -> factory.apply(0)), factory, payloadInts);
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

    private enum ComponentLayout {
        GROUPED {
            @Nonnull
            @Override
            List<ComponentSpec<? extends ProbeComponent>> registerRequired(
                @Nonnull ComponentRegistry<BenchmarkWorld> registry) {
                return List.of(spec(registry,
                    GroupedComponent.class,
                    GroupedComponent::new,
                    35));
            }
        },
        DOMAIN_SPLIT {
            @Nonnull
            @Override
            List<ComponentSpec<? extends ProbeComponent>> registerRequired(
                @Nonnull ComponentRegistry<BenchmarkWorld> registry) {
                return List.of(
                    spec(registry, DomainComponentA.class, DomainComponentA::new, 5),
                    spec(registry, DomainComponentB.class, DomainComponentB::new, 5),
                    spec(registry, DomainComponentC.class, DomainComponentC::new, 5),
                    spec(registry, DomainComponentD.class, DomainComponentD::new, 5),
                    spec(registry, DomainComponentE.class, DomainComponentE::new, 5),
                    spec(registry, DomainComponentF.class, DomainComponentF::new, 5),
                    spec(registry, DomainComponentG.class, DomainComponentG::new, 5));
            }
        },
        TINY {
            @Nonnull
            @Override
            List<ComponentSpec<? extends ProbeComponent>> registerRequired(
                @Nonnull ComponentRegistry<BenchmarkWorld> registry) {
                return List.of(
                    spec(registry, TinyComponent01.class, TinyComponent01::new, 1),
                    spec(registry, TinyComponent02.class, TinyComponent02::new, 1),
                    spec(registry, TinyComponent03.class, TinyComponent03::new, 1),
                    spec(registry, TinyComponent04.class, TinyComponent04::new, 1),
                    spec(registry, TinyComponent05.class, TinyComponent05::new, 1),
                    spec(registry, TinyComponent06.class, TinyComponent06::new, 1),
                    spec(registry, TinyComponent07.class, TinyComponent07::new, 1),
                    spec(registry, TinyComponent08.class, TinyComponent08::new, 1),
                    spec(registry, TinyComponent09.class, TinyComponent09::new, 1),
                    spec(registry, TinyComponent10.class, TinyComponent10::new, 1),
                    spec(registry, TinyComponent11.class, TinyComponent11::new, 1),
                    spec(registry, TinyComponent12.class, TinyComponent12::new, 1),
                    spec(registry, TinyComponent13.class, TinyComponent13::new, 1),
                    spec(registry, TinyComponent14.class, TinyComponent14::new, 1),
                    spec(registry, TinyComponent15.class, TinyComponent15::new, 1),
                    spec(registry, TinyComponent16.class, TinyComponent16::new, 1),
                    spec(registry, TinyComponent17.class, TinyComponent17::new, 1),
                    spec(registry, TinyComponent18.class, TinyComponent18::new, 1),
                    spec(registry, TinyComponent19.class, TinyComponent19::new, 1),
                    spec(registry, TinyComponent20.class, TinyComponent20::new, 1),
                    spec(registry, TinyComponent21.class, TinyComponent21::new, 1),
                    spec(registry, TinyComponent22.class, TinyComponent22::new, 1),
                    spec(registry, TinyComponent23.class, TinyComponent23::new, 1),
                    spec(registry, TinyComponent24.class, TinyComponent24::new, 1),
                    spec(registry, TinyComponent25.class, TinyComponent25::new, 1),
                    spec(registry, TinyComponent26.class, TinyComponent26::new, 1),
                    spec(registry, TinyComponent27.class, TinyComponent27::new, 1),
                    spec(registry, TinyComponent28.class, TinyComponent28::new, 1),
                    spec(registry, TinyComponent29.class, TinyComponent29::new, 1),
                    spec(registry, TinyComponent30.class, TinyComponent30::new, 1),
                    spec(registry, TinyComponent31.class, TinyComponent31::new, 1),
                    spec(registry, TinyComponent32.class, TinyComponent32::new, 1),
                    spec(registry, TinyComponent33.class, TinyComponent33::new, 1),
                    spec(registry, TinyComponent34.class, TinyComponent34::new, 1),
                    spec(registry, TinyComponent35.class, TinyComponent35::new, 1));
            }
        };

        @Nonnull
        abstract List<ComponentSpec<? extends ProbeComponent>> registerRequired(
            @Nonnull ComponentRegistry<BenchmarkWorld> registry);
    }

    private record ComponentSpec<T extends ProbeComponent>(
        @Nonnull ComponentType<BenchmarkWorld, T> type,
        @Nonnull IntFunction<T> factory,
        int payloadInts) {
    }

    private record ScenarioResult(@Nonnull String name,
                                  int entities,
                                  int storeEntityCount,
                                  int requiredComponents,
                                  int optionalComponents,
                                  int requiredPayloadInts,
                                  long componentInstances,
                                  long payloadBytes,
                                  long heapBytes,
                                  long addNanos,
                                  long iterateNanos,
                                  long targetedIterateNanos,
                                  long mutateNanos,
                                  int archetypeChunkCount,
                                  int archetypeDataCount,
                                  long iterationChecksum,
                                  long targetedIterationChecksum) {

        @Nonnull
        static String tsv(@Nonnull List<ScenarioResult> results) {
            String header = String.join("\t",
                "scenario",
                "entities",
                "requiredComponents",
                "optionalComponents",
                "requiredPayloadInts",
                "componentInstances",
                "payloadBytes",
                "payloadBytesPerEntity",
                "heapBytes",
                "bytesPerEntity",
                "measuredOverheadBytes",
                "overheadBytesPerEntity",
                "bytesPerComponentInstance",
                "addNsPerEntity",
                "iterateNsPerEntity",
                "targetedIterateNsPerEntity",
                "mutateNsPerEntity",
                "archetypeChunkCount",
                "archetypeDataCount",
                "iterationChecksum",
                "targetedIterationChecksum");
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
                Integer.toString(requiredComponents),
                Integer.toString(optionalComponents),
                Integer.toString(requiredPayloadInts),
                Long.toString(componentInstances),
                Long.toString(payloadBytes),
                Long.toString(payloadBytes / Math.max(1, entities)),
                Long.toString(heapBytes),
                Long.toString(heapBytes / Math.max(1, entities)),
                Long.toString(Math.max(0L, heapBytes - payloadBytes)),
                Long.toString(Math.max(0L, heapBytes - payloadBytes) / Math.max(1, entities)),
                Long.toString(heapBytes / Math.max(1L, componentInstances)),
                Long.toString(addNanos / Math.max(1, entities)),
                Long.toString(iterateNanos / Math.max(1, entities)),
                Long.toString(targetedIterateNanos / Math.max(1, entities)),
                Long.toString(mutateNanos / Math.max(1, entities)),
                Integer.toString(archetypeChunkCount),
                Integer.toString(archetypeDataCount),
                Long.toString(iterationChecksum),
                Long.toString(targetedIterationChecksum));
        }
    }

    private record BenchmarkWorld(@Nonnull String name) {
    }

    private interface ProbeComponent extends Component<BenchmarkWorld> {

        long checksum();
    }

    private abstract static class BaseComponent implements ProbeComponent {

        protected abstract int seed();

        @Override
        public abstract BaseComponent clone();
    }

    private abstract static class GroupedBase extends BaseComponent {

        private final int value00;
        private final int value01;
        private final int value02;
        private final int value03;
        private final int value04;
        private final int value05;
        private final int value06;
        private final int value07;
        private final int value08;
        private final int value09;
        private final int value10;
        private final int value11;
        private final int value12;
        private final int value13;
        private final int value14;
        private final int value15;
        private final int value16;
        private final int value17;
        private final int value18;
        private final int value19;
        private final int value20;
        private final int value21;
        private final int value22;
        private final int value23;
        private final int value24;
        private final int value25;
        private final int value26;
        private final int value27;
        private final int value28;
        private final int value29;
        private final int value30;
        private final int value31;
        private final int value32;
        private final int value33;
        private final int value34;

        private GroupedBase(int seed) {
            value00 = seed;
            value01 = seed + 1;
            value02 = seed + 2;
            value03 = seed + 3;
            value04 = seed + 4;
            value05 = seed + 5;
            value06 = seed + 6;
            value07 = seed + 7;
            value08 = seed + 8;
            value09 = seed + 9;
            value10 = seed + 10;
            value11 = seed + 11;
            value12 = seed + 12;
            value13 = seed + 13;
            value14 = seed + 14;
            value15 = seed + 15;
            value16 = seed + 16;
            value17 = seed + 17;
            value18 = seed + 18;
            value19 = seed + 19;
            value20 = seed + 20;
            value21 = seed + 21;
            value22 = seed + 22;
            value23 = seed + 23;
            value24 = seed + 24;
            value25 = seed + 25;
            value26 = seed + 26;
            value27 = seed + 27;
            value28 = seed + 28;
            value29 = seed + 29;
            value30 = seed + 30;
            value31 = seed + 31;
            value32 = seed + 32;
            value33 = seed + 33;
            value34 = seed + 34;
        }

        @Override
        protected int seed() {
            return value00;
        }

        @Override
        public long checksum() {
            return (long) value00 + value01 + value02 + value03 + value04
                + value05 + value06 + value07 + value08 + value09
                + value10 + value11 + value12 + value13 + value14
                + value15 + value16 + value17 + value18 + value19
                + value20 + value21 + value22 + value23 + value24
                + value25 + value26 + value27 + value28 + value29
                + value30 + value31 + value32 + value33 + value34;
        }
    }

    private abstract static class DomainBase extends BaseComponent {

        private final int value0;
        private final int value1;
        private final int value2;
        private final int value3;
        private final int value4;

        private DomainBase(int seed) {
            value0 = seed;
            value1 = seed + 1;
            value2 = seed + 2;
            value3 = seed + 3;
            value4 = seed + 4;
        }

        @Override
        protected int seed() {
            return value0;
        }

        @Override
        public long checksum() {
            return (long) value0 + value1 + value2 + value3 + value4;
        }
    }

    private abstract static class TinyBase extends BaseComponent {

        private final int value;

        private TinyBase(int seed) {
            value = seed;
        }

        @Override
        protected int seed() {
            return value;
        }

        @Override
        public long checksum() {
            return value;
        }
    }

    private static final class GroupedComponent extends GroupedBase {
        private GroupedComponent(int seed) { super(seed); }
        @Override public GroupedComponent clone() { return new GroupedComponent(seed()); }
    }

    private static final class DomainComponentA extends DomainBase {
        private DomainComponentA(int seed) { super(seed); }
        @Override public DomainComponentA clone() { return new DomainComponentA(seed()); }
    }

    private static final class DomainComponentB extends DomainBase {
        private DomainComponentB(int seed) { super(seed); }
        @Override public DomainComponentB clone() { return new DomainComponentB(seed()); }
    }

    private static final class DomainComponentC extends DomainBase {
        private DomainComponentC(int seed) { super(seed); }
        @Override public DomainComponentC clone() { return new DomainComponentC(seed()); }
    }

    private static final class DomainComponentD extends DomainBase {
        private DomainComponentD(int seed) { super(seed); }
        @Override public DomainComponentD clone() { return new DomainComponentD(seed()); }
    }

    private static final class DomainComponentE extends DomainBase {
        private DomainComponentE(int seed) { super(seed); }
        @Override public DomainComponentE clone() { return new DomainComponentE(seed()); }
    }

    private static final class DomainComponentF extends DomainBase {
        private DomainComponentF(int seed) { super(seed); }
        @Override public DomainComponentF clone() { return new DomainComponentF(seed()); }
    }

    private static final class DomainComponentG extends DomainBase {
        private DomainComponentG(int seed) { super(seed); }
        @Override public DomainComponentG clone() { return new DomainComponentG(seed()); }
    }

    private static final class OptionalComponent extends TinyBase {
        private OptionalComponent(int seed) { super(seed); }
        @Override public OptionalComponent clone() { return new OptionalComponent(seed()); }
    }

    private static final class TinyComponent01 extends TinyBase {
        private TinyComponent01(int seed) { super(seed); }
        @Override public TinyComponent01 clone() { return new TinyComponent01(seed()); }
    }

    private static final class TinyComponent02 extends TinyBase {
        private TinyComponent02(int seed) { super(seed); }
        @Override public TinyComponent02 clone() { return new TinyComponent02(seed()); }
    }

    private static final class TinyComponent03 extends TinyBase {
        private TinyComponent03(int seed) { super(seed); }
        @Override public TinyComponent03 clone() { return new TinyComponent03(seed()); }
    }

    private static final class TinyComponent04 extends TinyBase {
        private TinyComponent04(int seed) { super(seed); }
        @Override public TinyComponent04 clone() { return new TinyComponent04(seed()); }
    }

    private static final class TinyComponent05 extends TinyBase {
        private TinyComponent05(int seed) { super(seed); }
        @Override public TinyComponent05 clone() { return new TinyComponent05(seed()); }
    }

    private static final class TinyComponent06 extends TinyBase {
        private TinyComponent06(int seed) { super(seed); }
        @Override public TinyComponent06 clone() { return new TinyComponent06(seed()); }
    }

    private static final class TinyComponent07 extends TinyBase {
        private TinyComponent07(int seed) { super(seed); }
        @Override public TinyComponent07 clone() { return new TinyComponent07(seed()); }
    }

    private static final class TinyComponent08 extends TinyBase {
        private TinyComponent08(int seed) { super(seed); }
        @Override public TinyComponent08 clone() { return new TinyComponent08(seed()); }
    }

    private static final class TinyComponent09 extends TinyBase {
        private TinyComponent09(int seed) { super(seed); }
        @Override public TinyComponent09 clone() { return new TinyComponent09(seed()); }
    }

    private static final class TinyComponent10 extends TinyBase {
        private TinyComponent10(int seed) { super(seed); }
        @Override public TinyComponent10 clone() { return new TinyComponent10(seed()); }
    }

    private static final class TinyComponent11 extends TinyBase {
        private TinyComponent11(int seed) { super(seed); }
        @Override public TinyComponent11 clone() { return new TinyComponent11(seed()); }
    }

    private static final class TinyComponent12 extends TinyBase {
        private TinyComponent12(int seed) { super(seed); }
        @Override public TinyComponent12 clone() { return new TinyComponent12(seed()); }
    }

    private static final class TinyComponent13 extends TinyBase {
        private TinyComponent13(int seed) { super(seed); }
        @Override public TinyComponent13 clone() { return new TinyComponent13(seed()); }
    }

    private static final class TinyComponent14 extends TinyBase {
        private TinyComponent14(int seed) { super(seed); }
        @Override public TinyComponent14 clone() { return new TinyComponent14(seed()); }
    }

    private static final class TinyComponent15 extends TinyBase {
        private TinyComponent15(int seed) { super(seed); }
        @Override public TinyComponent15 clone() { return new TinyComponent15(seed()); }
    }

    private static final class TinyComponent16 extends TinyBase {
        private TinyComponent16(int seed) { super(seed); }
        @Override public TinyComponent16 clone() { return new TinyComponent16(seed()); }
    }

    private static final class TinyComponent17 extends TinyBase {
        private TinyComponent17(int seed) { super(seed); }
        @Override public TinyComponent17 clone() { return new TinyComponent17(seed()); }
    }

    private static final class TinyComponent18 extends TinyBase {
        private TinyComponent18(int seed) { super(seed); }
        @Override public TinyComponent18 clone() { return new TinyComponent18(seed()); }
    }

    private static final class TinyComponent19 extends TinyBase {
        private TinyComponent19(int seed) { super(seed); }
        @Override public TinyComponent19 clone() { return new TinyComponent19(seed()); }
    }

    private static final class TinyComponent20 extends TinyBase {
        private TinyComponent20(int seed) { super(seed); }
        @Override public TinyComponent20 clone() { return new TinyComponent20(seed()); }
    }

    private static final class TinyComponent21 extends TinyBase {
        private TinyComponent21(int seed) { super(seed); }
        @Override public TinyComponent21 clone() { return new TinyComponent21(seed()); }
    }

    private static final class TinyComponent22 extends TinyBase {
        private TinyComponent22(int seed) { super(seed); }
        @Override public TinyComponent22 clone() { return new TinyComponent22(seed()); }
    }

    private static final class TinyComponent23 extends TinyBase {
        private TinyComponent23(int seed) { super(seed); }
        @Override public TinyComponent23 clone() { return new TinyComponent23(seed()); }
    }

    private static final class TinyComponent24 extends TinyBase {
        private TinyComponent24(int seed) { super(seed); }
        @Override public TinyComponent24 clone() { return new TinyComponent24(seed()); }
    }

    private static final class TinyComponent25 extends TinyBase {
        private TinyComponent25(int seed) { super(seed); }
        @Override public TinyComponent25 clone() { return new TinyComponent25(seed()); }
    }

    private static final class TinyComponent26 extends TinyBase {
        private TinyComponent26(int seed) { super(seed); }
        @Override public TinyComponent26 clone() { return new TinyComponent26(seed()); }
    }

    private static final class TinyComponent27 extends TinyBase {
        private TinyComponent27(int seed) { super(seed); }
        @Override public TinyComponent27 clone() { return new TinyComponent27(seed()); }
    }

    private static final class TinyComponent28 extends TinyBase {
        private TinyComponent28(int seed) { super(seed); }
        @Override public TinyComponent28 clone() { return new TinyComponent28(seed()); }
    }

    private static final class TinyComponent29 extends TinyBase {
        private TinyComponent29(int seed) { super(seed); }
        @Override public TinyComponent29 clone() { return new TinyComponent29(seed()); }
    }

    private static final class TinyComponent30 extends TinyBase {
        private TinyComponent30(int seed) { super(seed); }
        @Override public TinyComponent30 clone() { return new TinyComponent30(seed()); }
    }

    private static final class TinyComponent31 extends TinyBase {
        private TinyComponent31(int seed) { super(seed); }
        @Override public TinyComponent31 clone() { return new TinyComponent31(seed()); }
    }

    private static final class TinyComponent32 extends TinyBase {
        private TinyComponent32(int seed) { super(seed); }
        @Override public TinyComponent32 clone() { return new TinyComponent32(seed()); }
    }

    private static final class TinyComponent33 extends TinyBase {
        private TinyComponent33(int seed) { super(seed); }
        @Override public TinyComponent33 clone() { return new TinyComponent33(seed()); }
    }

    private static final class TinyComponent34 extends TinyBase {
        private TinyComponent34(int seed) { super(seed); }
        @Override public TinyComponent34 clone() { return new TinyComponent34(seed()); }
    }

    private static final class TinyComponent35 extends TinyBase {
        private TinyComponent35(int seed) { super(seed); }
        @Override public TinyComponent35 clone() { return new TinyComponent35(seed()); }
    }
}
