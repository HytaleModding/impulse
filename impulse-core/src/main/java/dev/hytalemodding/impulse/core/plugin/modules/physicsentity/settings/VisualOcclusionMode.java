package dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings;


public enum VisualOcclusionMode {
    OFF,
    PRIORITY,

    /**
     * Cull entities when the occlusion raycast cannot directly hit them.
     */
    CULL
}
