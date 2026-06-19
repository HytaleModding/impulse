package dev.hytalemodding.impulse.core.internal.persistence;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import com.hypixel.hytale.server.core.util.BsonUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import org.bson.BsonArray;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;

/**
 * BSON holder file storage for row-native PhysicsStore persistence.
 */
public final class PhysicsStoreHolderStorage {

    private static final int SCHEMA_VERSION = 1;
    private static final String DIRECTORY = "physicsstore";
    private static final String FILE_NAME = "holders.bson";
    private static final String SCHEMA_VERSION_FIELD = "SchemaVersion";
    private static final String HOLDERS_FIELD = "Holders";

    private PhysicsStoreHolderStorage() {
    }

    @Nonnull
    public static CompletableFuture<Void> save(@Nonnull PhysicsStore physicsStore) {
        return save(physicsStore.getStore());
    }

    @Nonnull
    public static CompletableFuture<Void> save(@Nonnull Store<PhysicsStore> store) {
        List<byte[]> holderBlobs = PhysicsStoreHolderPersistence.capturePersistentHolderBlobs(
            store);
        byte[] document = BsonUtil.writeToBytes(document(holderBlobs));
        Path file = file(store.getExternalData());
        return CompletableFuture.runAsync(() -> writeBinaryAtomic(file, document));
    }

    @Nonnull
    public static LoadResult load(@Nonnull Store<PhysicsStore> store) {
        Path file = file(store.getExternalData());
        if (!Files.exists(file)) {
            return LoadResult.missing();
        }

        BsonDocument document;
        try {
            document = BsonUtil.readFromBytes(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read PhysicsStore holder storage: " + file,
                exception);
        }
        if (document == null) {
            throw new IllegalStateException("PhysicsStore holder storage is empty: " + file);
        }
        int schemaVersion = document.getInt32(SCHEMA_VERSION_FIELD, new BsonInt32(0)).getValue();
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported PhysicsStore holder storage schema "
                + schemaVersion + "; expected " + SCHEMA_VERSION);
        }

        BsonArray holders = document.getArray(HOLDERS_FIELD, new BsonArray());
        int loaded = 0;
        for (BsonValue value : holders) {
            Holder<PhysicsStore> holder = PhysicsStoreHolderPersistence.decodeHolder(
                store.getRegistry(),
                value.asBinary().getData());
            store.addEntity(holder, AddReason.LOAD);
            loaded++;
        }
        return new LoadResult(true, loaded);
    }

    @Nonnull
    static Path file(@Nonnull PhysicsStore physicsStore) {
        return physicsStore.getWorld().getSavePath().resolve(DIRECTORY).resolve(FILE_NAME);
    }

    @Nonnull
    private static BsonDocument document(@Nonnull List<byte[]> holderBlobs) {
        BsonArray holders = new BsonArray();
        for (byte[] holderBlob : holderBlobs) {
            holders.add(new BsonBinary(holderBlob));
        }
        return new BsonDocument()
            .append(SCHEMA_VERSION_FIELD, new BsonInt32(SCHEMA_VERSION))
            .append(HOLDERS_FIELD, holders);
    }

    private static void writeBinaryAtomic(@Nonnull Path file, @Nonnull byte[] bytes) {
        try {
            Path parent = file.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            try {
                Files.move(temp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not write PhysicsStore holder storage: " + file,
                exception);
        }
    }

    public record LoadResult(boolean present, int loadedCount) {

        @Nonnull
        private static LoadResult missing() {
            return new LoadResult(false, 0);
        }
    }
}
