package dev.hytalemodding.impulse.core.internal.modules.physicschunk;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-streaming-pass source-section cache for repeated Hytale chunk lookups.
 */
public final class PhysicsChunkSectionAccessCache {

    private final Long2ObjectMap<BlockChunk> blockChunks = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<BlockSection> blockSections = new Long2ObjectOpenHashMap<>();

    public PhysicsChunkSectionAccessCache() {
    }

    @Nullable
    BlockChunk blockChunk(@Nonnull World world, int chunkX, int chunkZ) {
        long key = ChunkUtil.indexChunk(chunkX, chunkZ);
        if (blockChunks.containsKey(key)) {
            return blockChunks.get(key);
        }

        BlockChunk chunk = loadBlockChunk(world, chunkX, chunkZ);
        blockChunks.put(key, chunk);
        return chunk;
    }

    @Nullable
    BlockSection blockSection(@Nonnull World world, int chunkX, int sectionY, int chunkZ) {
        long key = packSectionKey(chunkX, sectionY, chunkZ);
        if (blockSections.containsKey(key)) {
            return blockSections.get(key);
        }

        BlockSection section = ChunkSectionAccess.blockSection(world, chunkX, sectionY, chunkZ);
        blockSections.put(key, section);
        return section;
    }

    @Nullable
    private static BlockChunk loadBlockChunk(@Nonnull World world, int chunkX, int chunkZ) {
        Ref<ChunkStore> chunkRef = world.getChunkStore()
            .getChunkReference(ChunkUtil.indexChunk(chunkX, chunkZ));
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }
        Store<ChunkStore> store = world.getChunkStore().getStore();
        return store.getComponentConcurrent(chunkRef, BlockChunk.getComponentType());
    }

    private static long packSectionKey(int chunkX, int sectionY, int chunkZ) {
        long x = ((long) chunkX & 0x3FFFFFL) << 42;
        long y = ((long) sectionY & 0x3FFL) << 32;
        long z = (long) chunkZ & 0xFFFFFFFFL;
        return x | y | z;
    }
}
