package dev.hytalemodding.impulse.core.internal.voxel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class ChunkSectionAccess {

    private ChunkSectionAccess() {
    }

    @Nullable
    static BlockSection blockSection(@Nonnull World world, int chunkX, int sectionY, int chunkZ) {
        if (sectionY < 0 || sectionY > ChunkUtil.indexSection(ChunkUtil.HEIGHT_MINUS_1)) {
            return null;
        }

        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReference(chunkX, sectionY, chunkZ);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }

        Store<ChunkStore> store = chunkStore.getStore();
        return store.getComponentConcurrent(sectionRef, BlockSection.getComponentType());
    }
}
