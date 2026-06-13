package dev.hytalemodding.impulse.early;

import com.hypixel.hytale.plugin.early.ClassTransformer;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassElement;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import javax.annotation.Nonnull;

public final class PhysicsStoreEarlyTransformer implements ClassTransformer {

    private static final String PLUGIN_BASE_NAME =
        "com.hypixel.hytale.server.core.plugin.PluginBase";
    private static final String WORLD_NAME =
        "com.hypixel.hytale.server.core.universe.world.World";
    private static final String WORLD_CONFIG_SAVE_SYSTEM_NAME =
        "com.hypixel.hytale.server.core.universe.system.WorldConfigSaveSystem";

    private static final String PLUGIN_SHUTDOWN_TASKS_FIELD = "shutdownTasks";
    private static final String WORLD_CHUNK_STORE_FIELD = "chunkStore";
    private static final String WORLD_IS_TICKING_FIELD = "isTicking";
    private static final String WORLD_IS_PAUSED_FIELD = "isPaused";
    private static final String WORLD_STORE_FIELD = "physicsStore";
    private static final String WORLD_PATCH_MARKER_FIELD = "impulse$physicsStoreLifecyclePatched";
    private static final String WORLD_SAVE_PATCH_MARKER_FIELD =
        "impulse$physicsStoreResourceSavePatched";
    private static final String PLUGIN_REGISTRY_METHOD = "getPhysicsStoreRegistry";
    private static final String WORLD_STORE_METHOD = "getPhysicsStore";
    private static final String SAVE_WORLD_CONFIG_AND_RESOURCES_METHOD =
        "saveWorldConfigAndResources";

    private static final ClassFile CLASS_FILE = ClassFile.of();
    private static final ClassDesc CD_CHUNK_STORE =
        ClassDesc.of("com.hypixel.hytale.server.core.universe.world.storage.ChunkStore");
    private static final ClassDesc CD_COMPLETABLE_FUTURE =
        ClassDesc.of("java.util.concurrent.CompletableFuture");
    private static final ClassDesc CD_COMPONENT_REGISTRY =
        ClassDesc.of("com.hypixel.hytale.component.ComponentRegistry");
    private static final ClassDesc CD_COMPONENT_REGISTRY_PROXY =
        ClassDesc.of("com.hypixel.hytale.component.ComponentRegistryProxy");
    private static final ClassDesc CD_COPY_ON_WRITE_ARRAY_LIST =
        ClassDesc.of("java.util.concurrent.CopyOnWriteArrayList");
    private static final ClassDesc CD_ENTITY_STORE =
        ClassDesc.of("com.hypixel.hytale.server.core.universe.world.storage.EntityStore");
    private static final ClassDesc CD_I_RESOURCE_STORAGE =
        ClassDesc.of("com.hypixel.hytale.component.IResourceStorage");
    private static final ClassDesc CD_LIST = ClassDesc.of("java.util.List");
    private static final ClassDesc CD_PATH = ClassDesc.of("java.nio.file.Path");
    private static final ClassDesc CD_PHYSICS_STORE =
        ClassDesc.of("com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore");
    private static final ClassDesc CD_PHYSICS_STORE_HOOKS =
        ClassDesc.of("dev.hytalemodding.impulse.early.PhysicsStoreHooks");
    private static final ClassDesc CD_PLUGIN_BASE =
        ClassDesc.of("com.hypixel.hytale.server.core.plugin.PluginBase");
    private static final ClassDesc CD_STORE = ClassDesc.of("com.hypixel.hytale.component.Store");
    private static final ClassDesc CD_UNIVERSE =
        ClassDesc.of("com.hypixel.hytale.server.core.universe.Universe");
    private static final ClassDesc CD_WORLD =
        ClassDesc.of("com.hypixel.hytale.server.core.universe.world.World");
    private static final ClassDesc CD_WORLD_CONFIG =
        ClassDesc.of("com.hypixel.hytale.server.core.universe.world.WorldConfig");
    private static final ClassDesc CD_WORLD_CONFIG_PROVIDER =
        ClassDesc.of("com.hypixel.hytale.server.core.universe.world.WorldConfigProvider");

    private static final MethodTypeDesc MTD_BOOLEAN = MethodTypeDesc.of(ConstantDescs.CD_boolean);
    private static final MethodTypeDesc MTD_CHUNK_STORE = MethodTypeDesc.of(CD_CHUNK_STORE);
    private static final MethodTypeDesc MTD_COMPONENT_REGISTRY_PROXY =
        MethodTypeDesc.of(CD_COMPONENT_REGISTRY_PROXY);
    private static final MethodTypeDesc MTD_COMPONENT_REGISTRY_PROXY_INIT =
        MethodTypeDesc.of(ConstantDescs.CD_void, CD_LIST, CD_COMPONENT_REGISTRY);
    private static final MethodTypeDesc MTD_COMPLETABLE_FUTURE =
        MethodTypeDesc.of(CD_COMPLETABLE_FUTURE);
    private static final MethodTypeDesc MTD_COMPLETABLE_FUTURE_ARRAY_TO_FUTURE =
        MethodTypeDesc.of(CD_COMPLETABLE_FUTURE, CD_COMPLETABLE_FUTURE.arrayType());
    private static final MethodTypeDesc MTD_ENTITY_STORE = MethodTypeDesc.of(CD_ENTITY_STORE);
    private static final MethodTypeDesc MTD_PHYSICS_STORE = MethodTypeDesc.of(CD_PHYSICS_STORE);
    private static final MethodTypeDesc MTD_PHYSICS_STORE_INIT =
        MethodTypeDesc.of(ConstantDescs.CD_void, CD_WORLD);
    private static final MethodTypeDesc MTD_PHYSICS_START =
        MethodTypeDesc.of(ConstantDescs.CD_void, CD_PHYSICS_STORE, CD_I_RESOURCE_STORAGE);
    private static final MethodTypeDesc MTD_PHYSICS_TICK =
        MethodTypeDesc.of(ConstantDescs.CD_void,
            CD_PHYSICS_STORE,
            ConstantDescs.CD_float,
            ConstantDescs.CD_boolean,
            ConstantDescs.CD_boolean);
    private static final MethodTypeDesc MTD_PHYSICS_SAVE =
        MethodTypeDesc.of(CD_COMPLETABLE_FUTURE, CD_PHYSICS_STORE);
    private static final MethodTypeDesc MTD_PHYSICS_SHUTDOWN =
        MethodTypeDesc.of(ConstantDescs.CD_void, CD_PHYSICS_STORE);
    private static final MethodTypeDesc MTD_SAVE_WORLD_CONFIG_AND_RESOURCES =
        MethodTypeDesc.of(CD_COMPLETABLE_FUTURE, CD_WORLD);
    private static final MethodTypeDesc MTD_STORE = MethodTypeDesc.of(CD_STORE);
    private static final MethodTypeDesc MTD_UNIVERSE = MethodTypeDesc.of(CD_UNIVERSE);
    private static final MethodTypeDesc MTD_VOID = MethodTypeDesc.of(ConstantDescs.CD_void);
    private static final MethodTypeDesc MTD_VOID_FLOAT =
        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_float);
    private static final MethodTypeDesc MTD_VOID_RESOURCE_STORAGE =
        MethodTypeDesc.of(ConstantDescs.CD_void, CD_I_RESOURCE_STORAGE);
    private static final MethodTypeDesc MTD_WORLD_CONFIG = MethodTypeDesc.of(CD_WORLD_CONFIG);
    private static final MethodTypeDesc MTD_WORLD_CONFIG_PROVIDER =
        MethodTypeDesc.of(CD_WORLD_CONFIG_PROVIDER);
    private static final MethodTypeDesc MTD_WORLD_CONFIG_PROVIDER_SAVE =
        MethodTypeDesc.of(CD_COMPLETABLE_FUTURE, CD_PATH, CD_WORLD_CONFIG, CD_WORLD);
    private static final MethodTypeDesc MTD_PATH = MethodTypeDesc.of(CD_PATH);

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        String target = transformedName != null ? transformedName : name;
        if (matches(target, PLUGIN_BASE_NAME)) {
            return transformPluginBase(bytes);
        }
        if (matches(target, WORLD_NAME)) {
            return transformWorld(bytes);
        }
        if (matches(target, WORLD_CONFIG_SAVE_SYSTEM_NAME)) {
            return transformWorldConfigSaveSystem(bytes);
        }
        return bytes;
    }

    @Nonnull
    private static byte[] transformPluginBase(@Nonnull byte[] bytes) {
        ClassModel model = CLASS_FILE.parse(bytes);
        if (hasMethod(model.methods(), PLUGIN_REGISTRY_METHOD, MTD_COMPONENT_REGISTRY_PROXY)) {
            return bytes;
        }
        return CLASS_FILE.transformClass(model, ClassTransform.ACCEPT_ALL.andThen(
            ClassTransform.endHandler(PhysicsStoreEarlyTransformer::addPluginRegistryAccessor)));
    }

    @Nonnull
    private static byte[] transformWorld(@Nonnull byte[] bytes) {
        ClassModel model = CLASS_FILE.parse(bytes);
        if (hasField(model.fields(), WORLD_PATCH_MARKER_FIELD, ConstantDescs.CD_boolean)) {
            return bytes;
        }
        boolean fieldPresent = hasField(model.fields(), WORLD_STORE_FIELD, CD_PHYSICS_STORE);
        boolean methodPresent = hasMethod(model.methods(), WORLD_STORE_METHOD, MTD_PHYSICS_STORE);
        var initializePhysicsStore = new InitializePhysicsStoreTransform();
        var startPhysicsStore = new StartPhysicsStoreTransform();
        var tickPhysicsStore = new TickPhysicsStoreTransform();
        var shutdownPhysicsStore = new ShutdownPhysicsStoreTransform();
        ClassTransform transform = ClassTransform.ACCEPT_ALL
            .andThen(ClassTransform.transformingMethodBodies(
                PhysicsStoreEarlyTransformer::isWorldConstructor,
                initializePhysicsStore))
            .andThen(ClassTransform.transformingMethodBodies(
                PhysicsStoreEarlyTransformer::isWorldStartMethod,
                startPhysicsStore))
            .andThen(ClassTransform.transformingMethodBodies(
                PhysicsStoreEarlyTransformer::isWorldTickMethod,
                tickPhysicsStore))
            .andThen(ClassTransform.transformingMethodBodies(
                PhysicsStoreEarlyTransformer::isWorldShutdownMethod,
                shutdownPhysicsStore))
            .andThen(ClassTransform.endHandler(builder -> {
                if (!fieldPresent) {
                    builder.withField(WORLD_STORE_FIELD, CD_PHYSICS_STORE, ClassFile.ACC_PRIVATE);
                }
                if (!methodPresent) {
                    addWorldStoreAccessor(builder);
                }
                builder.withField(WORLD_PATCH_MARKER_FIELD,
                    ConstantDescs.CD_boolean,
                    ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL
                        | ClassFile.ACC_SYNTHETIC);
            }));
        byte[] transformed = CLASS_FILE.transformClass(model, transform);
        requireTransformApplied(WORLD_NAME, "constructor PhysicsStore initialization",
            initializePhysicsStore.injected());
        requireTransformApplied(WORLD_NAME, "onStart PhysicsStore start hook",
            startPhysicsStore.injected());
        requireTransformApplied(WORLD_NAME, "tick PhysicsStore tick hook",
            tickPhysicsStore.injected());
        requireTransformApplied(WORLD_NAME, "onShutdown PhysicsStore shutdown hook",
            shutdownPhysicsStore.injected());
        return transformed;
    }

    @Nonnull
    private static byte[] transformWorldConfigSaveSystem(@Nonnull byte[] bytes) {
        ClassModel model = CLASS_FILE.parse(bytes);
        if (hasField(model.fields(), WORLD_SAVE_PATCH_MARKER_FIELD, ConstantDescs.CD_boolean)) {
            return bytes;
        }
        var replaceSave = new ReplaceSaveWorldConfigAndResourcesTransform();
        ClassTransform transform = ClassTransform.ACCEPT_ALL
            .andThen(ClassTransform.transformingMethodBodies(
                PhysicsStoreEarlyTransformer::isSaveWorldConfigAndResourcesMethod,
                replaceSave))
            .andThen(ClassTransform.endHandler(builder -> builder.withField(
                WORLD_SAVE_PATCH_MARKER_FIELD,
                ConstantDescs.CD_boolean,
                ClassFile.ACC_PRIVATE | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL
                    | ClassFile.ACC_SYNTHETIC)));
        byte[] transformed = CLASS_FILE.transformClass(model, transform);
        requireTransformApplied(WORLD_CONFIG_SAVE_SYSTEM_NAME,
            "saveWorldConfigAndResources resource save hook",
            replaceSave.replaced());
        return transformed;
    }

    private static void addPluginRegistryAccessor(@Nonnull ClassBuilder builder) {
        builder.withMethodBody(PLUGIN_REGISTRY_METHOD,
            MTD_COMPONENT_REGISTRY_PROXY,
            ClassFile.ACC_PUBLIC,
            code -> code.new_(CD_COMPONENT_REGISTRY_PROXY)
                .dup()
                .aload(0)
                .getfield(CD_PLUGIN_BASE, PLUGIN_SHUTDOWN_TASKS_FIELD, CD_COPY_ON_WRITE_ARRAY_LIST)
                .getstatic(CD_PHYSICS_STORE, "REGISTRY", CD_COMPONENT_REGISTRY)
                .invokespecial(CD_COMPONENT_REGISTRY_PROXY,
                    "<init>",
                    MTD_COMPONENT_REGISTRY_PROXY_INIT)
                .areturn());
    }

    private static void addWorldStoreAccessor(@Nonnull ClassBuilder builder) {
        builder.withMethodBody(WORLD_STORE_METHOD,
            MTD_PHYSICS_STORE,
            ClassFile.ACC_PUBLIC,
            code -> code.aload(0)
                .getfield(CD_WORLD, WORLD_STORE_FIELD, CD_PHYSICS_STORE)
                .areturn());
    }

    private static final class InitializePhysicsStoreTransform implements CodeTransform {

        private boolean injected;

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            if (element instanceof ReturnInstruction returnInstruction
                && returnInstruction.typeKind() == TypeKind.VOID) {
                emitInitializePhysicsStore(builder);
                injected = true;
            }
            builder.with(element);
        }

        boolean injected() {
            return injected;
        }
    }

    private static final class StartPhysicsStoreTransform implements CodeTransform {

        private boolean injected;

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            builder.with(element);
            if (!injected && isEntityStoreStartInvoke(element)) {
                emitStartPhysicsStore(builder);
                injected = true;
            }
        }

        boolean injected() {
            return injected;
        }
    }

    private static final class TickPhysicsStoreTransform implements CodeTransform {

        private boolean pendingChunkStore;
        private boolean chunkStoreTickSeen;
        private boolean injected;

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            if (!injected && chunkStoreTickSeen && isWorldConsumeTaskQueueInvoke(element)) {
                emitTickPhysicsStore(builder);
                injected = true;
            }
            builder.with(element);
            if (isWorldChunkStoreField(element)) {
                pendingChunkStore = true;
            } else if (pendingChunkStore && isStoreTickInvoke(element)) {
                chunkStoreTickSeen = true;
                pendingChunkStore = false;
            }
        }

        boolean injected() {
            return injected;
        }
    }

    private static final class ShutdownPhysicsStoreTransform implements CodeTransform {

        private boolean injected;

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
            if (element instanceof ReturnInstruction returnInstruction
                && returnInstruction.typeKind() == TypeKind.VOID) {
                emitShutdownPhysicsStore(builder);
                injected = true;
            }
            builder.with(element);
        }

        boolean injected() {
            return injected;
        }
    }

    private static final class ReplaceSaveWorldConfigAndResourcesTransform implements CodeTransform {

        private boolean replaced;

        @Override
        public void atStart(CodeBuilder builder) {
            emitSaveWorldConfigAndResources(builder);
            replaced = true;
        }

        @Override
        public void accept(CodeBuilder builder, CodeElement element) {
        }

        boolean replaced() {
            return replaced;
        }
    }

    private static void emitInitializePhysicsStore(@Nonnull CodeBuilder code) {
        code.aload(0)
            .new_(CD_PHYSICS_STORE)
            .dup()
            .aload(0)
            .invokespecial(CD_PHYSICS_STORE, "<init>", MTD_PHYSICS_STORE_INIT)
            .putfield(CD_WORLD, WORLD_STORE_FIELD, CD_PHYSICS_STORE);
    }

    private static void emitStartPhysicsStore(@Nonnull CodeBuilder code) {
        code.aload(0)
            .getfield(CD_WORLD, WORLD_STORE_FIELD, CD_PHYSICS_STORE)
            .aload(1)
            .invokestatic(CD_PHYSICS_STORE_HOOKS, "start", MTD_PHYSICS_START);
    }

    private static void emitTickPhysicsStore(@Nonnull CodeBuilder code) {
        code.aload(0)
            .getfield(CD_WORLD, WORLD_STORE_FIELD, CD_PHYSICS_STORE)
            .fload(1)
            .aload(0)
            .getfield(CD_WORLD, WORLD_IS_TICKING_FIELD, ConstantDescs.CD_boolean)
            .aload(0)
            .getfield(CD_WORLD, WORLD_IS_PAUSED_FIELD, ConstantDescs.CD_boolean)
            .invokestatic(CD_PHYSICS_STORE_HOOKS, "tickAfterChunk", MTD_PHYSICS_TICK);
    }

    private static void emitShutdownPhysicsStore(@Nonnull CodeBuilder code) {
        code.aload(0)
            .getfield(CD_WORLD, WORLD_STORE_FIELD, CD_PHYSICS_STORE)
            .invokestatic(CD_PHYSICS_STORE_HOOKS, "shutdown", MTD_PHYSICS_SHUTDOWN);
    }

    private static void emitSaveWorldConfigAndResources(@Nonnull CodeBuilder code) {
        var resourceOnly = code.newLabel();
        code.aload(0)
            .invokevirtual(CD_WORLD, "getWorldConfig", MTD_WORLD_CONFIG)
            .astore(1)
            .aload(1)
            .invokevirtual(CD_WORLD_CONFIG, "isSavingConfig", MTD_BOOLEAN)
            .ifeq(resourceOnly)
            .aload(1)
            .invokevirtual(CD_WORLD_CONFIG, "consumeHasChanged", MTD_BOOLEAN)
            .ifeq(resourceOnly)
            .iconst_4()
            .anewarray(CD_COMPLETABLE_FUTURE)
            .dup()
            .iconst_0();
        emitChunkStoreSave(code);
        code.aastore()
            .dup()
            .iconst_1();
        emitEntityStoreSave(code);
        code.aastore()
            .dup()
            .iconst_2();
        emitPhysicsStoreSave(code);
        code.aastore()
            .dup()
            .iconst_3();
        emitWorldConfigSave(code);
        code.aastore()
            .invokestatic(CD_COMPLETABLE_FUTURE,
                "allOf",
                MTD_COMPLETABLE_FUTURE_ARRAY_TO_FUTURE)
            .areturn()
            .labelBinding(resourceOnly)
            .iconst_3()
            .anewarray(CD_COMPLETABLE_FUTURE)
            .dup()
            .iconst_0();
        emitChunkStoreSave(code);
        code.aastore()
            .dup()
            .iconst_1();
        emitEntityStoreSave(code);
        code.aastore()
            .dup()
            .iconst_2();
        emitPhysicsStoreSave(code);
        code.aastore()
            .invokestatic(CD_COMPLETABLE_FUTURE,
                "allOf",
                MTD_COMPLETABLE_FUTURE_ARRAY_TO_FUTURE)
            .areturn();
    }

    private static void emitChunkStoreSave(@Nonnull CodeBuilder code) {
        code.aload(0)
            .invokevirtual(CD_WORLD, "getChunkStore", MTD_CHUNK_STORE)
            .invokevirtual(CD_CHUNK_STORE, "getStore", MTD_STORE)
            .invokevirtual(CD_STORE, "saveAllResources", MTD_COMPLETABLE_FUTURE);
    }

    private static void emitEntityStoreSave(@Nonnull CodeBuilder code) {
        code.aload(0)
            .invokevirtual(CD_WORLD, "getEntityStore", MTD_ENTITY_STORE)
            .invokevirtual(CD_ENTITY_STORE, "getStore", MTD_STORE)
            .invokevirtual(CD_STORE, "saveAllResources", MTD_COMPLETABLE_FUTURE);
    }

    private static void emitPhysicsStoreSave(@Nonnull CodeBuilder code) {
        code.aload(0)
            .invokevirtual(CD_WORLD, WORLD_STORE_METHOD, MTD_PHYSICS_STORE)
            .invokestatic(CD_PHYSICS_STORE_HOOKS, "saveResources", MTD_PHYSICS_SAVE);
    }

    private static void emitWorldConfigSave(@Nonnull CodeBuilder code) {
        code.invokestatic(CD_UNIVERSE, "get", MTD_UNIVERSE)
            .invokevirtual(CD_UNIVERSE, "getWorldConfigProvider", MTD_WORLD_CONFIG_PROVIDER)
            .aload(0)
            .invokevirtual(CD_WORLD, "getSavePath", MTD_PATH)
            .aload(0)
            .invokevirtual(CD_WORLD, "getWorldConfig", MTD_WORLD_CONFIG)
            .aload(0)
            .invokeinterface(CD_WORLD_CONFIG_PROVIDER, "save", MTD_WORLD_CONFIG_PROVIDER_SAVE);
    }

    private static boolean isWorldConstructor(@Nonnull MethodModel method) {
        return method.methodName().equalsString("<init>")
            && method.methodType().equalsString("(Ljava/lang/String;Ljava/nio/file/Path;"
                + "Lcom/hypixel/hytale/server/core/universe/world/WorldConfig;)V");
    }

    private static boolean isWorldStartMethod(@Nonnull MethodModel method) {
        return method.methodName().equalsString("onStart")
            && method.methodType().equalsString("()V");
    }

    private static boolean isWorldTickMethod(@Nonnull MethodModel method) {
        return method.methodName().equalsString("tick")
            && method.methodType().equalsString("(F)V");
    }

    private static boolean isWorldShutdownMethod(@Nonnull MethodModel method) {
        return method.methodName().equalsString("onShutdown")
            && method.methodType().equalsString("()V");
    }

    private static boolean isSaveWorldConfigAndResourcesMethod(@Nonnull MethodModel method) {
        return method.methodName().equalsString(SAVE_WORLD_CONFIG_AND_RESOURCES_METHOD)
            && method.methodType().equalsString(
                "(Lcom/hypixel/hytale/server/core/universe/world/World;)"
                    + "Ljava/util/concurrent/CompletableFuture;");
    }

    private static boolean isWorldChunkStoreField(@Nonnull CodeElement element) {
        return element instanceof FieldInstruction instruction
            && instruction.owner().asSymbol().equals(CD_WORLD)
            && instruction.name().equalsString(WORLD_CHUNK_STORE_FIELD)
            && instruction.typeSymbol().equals(CD_CHUNK_STORE);
    }

    private static boolean isStoreTickInvoke(@Nonnull CodeElement element) {
        return element instanceof InvokeInstruction instruction
            && instruction.owner().asSymbol().equals(CD_STORE)
            && (instruction.name().equalsString("tick")
                || instruction.name().equalsString("pausedTick"))
            && instruction.typeSymbol().equals(MTD_VOID_FLOAT);
    }

    private static boolean isWorldConsumeTaskQueueInvoke(@Nonnull CodeElement element) {
        return element instanceof InvokeInstruction instruction
            && instruction.owner().asSymbol().equals(CD_WORLD)
            && instruction.name().equalsString("consumeTaskQueue")
            && instruction.typeSymbol().equals(MTD_VOID);
    }

    private static boolean isEntityStoreStartInvoke(@Nonnull CodeElement element) {
        return element instanceof InvokeInstruction instruction
            && instruction.owner().asSymbol().equals(CD_ENTITY_STORE)
            && instruction.name().equalsString("start")
            && instruction.typeSymbol().equals(MTD_VOID_RESOURCE_STORAGE);
    }

    private static boolean isChunkStoreShutdownInvoke(@Nonnull CodeElement element) {
        return element instanceof InvokeInstruction instruction
            && instruction.owner().asSymbol().equals(CD_CHUNK_STORE)
            && instruction.name().equalsString("shutdown")
            && instruction.typeSymbol().equals(MTD_VOID);
    }

    private static void requireTransformApplied(@Nonnull String target,
        @Nonnull String hook,
        boolean applied) {
        if (!applied) {
            throw new IllegalStateException("Could not patch " + hook + " in " + target
                + "; Impulse PhysicsStore early plugin requires the Hytale 0.6.0-pre.3 "
                + "server lifecycle bytecode shape");
        }
    }

    private static boolean hasField(@Nonnull Iterable<? extends ClassElement> elements,
        @Nonnull String name,
        @Nonnull ClassDesc descriptor) {
        for (ClassElement element : elements) {
            if (element instanceof java.lang.classfile.FieldModel field
                && field.fieldName().equalsString(name)
                && field.fieldType().equalsString(descriptor.descriptorString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(@Nonnull Iterable<MethodModel> methods,
        @Nonnull String name,
        @Nonnull MethodTypeDesc descriptor) {
        for (MethodModel method : methods) {
            if (method.methodName().equalsString(name)
                && method.methodType().equalsString(descriptor.descriptorString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(@Nonnull String name, @Nonnull String target) {
        return target.equals(name) || target.replace('.', '/').equals(name);
    }
}
