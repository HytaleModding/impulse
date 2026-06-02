package dev.hytalemodding;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public class NativeBridgeHandler {

    private static final Arena arena = Arena.ofConfined();
    ;
    private static final MemorySegment commandBuffer = arena.allocate(1024 * 1024 * 256);
    
}
