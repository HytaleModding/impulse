module dev.hytalemodding.impulse.core {
    requires transitive impulse.api;
    requires transitive org.joml;
    requires static jsr305;
    requires static crucible;

    exports dev.hytalemodding.impulse.core.plugin.codec;
    exports dev.hytalemodding.impulse.core.plugin.components;
    exports dev.hytalemodding.impulse.core.plugin.events;
    exports dev.hytalemodding.impulse.core.plugin.modules.control;
    exports dev.hytalemodding.impulse.core.plugin.modules.physicsentity;
    exports dev.hytalemodding.impulse.core.plugin.modules.physicsentity.components;
    exports dev.hytalemodding.impulse.core.plugin.modules.physicsentity.settings;
    exports dev.hytalemodding.impulse.core.plugin.modules.physicschunk;
    exports dev.hytalemodding.impulse.core.plugin.modules.physicschunk.components;
    exports dev.hytalemodding.impulse.core.plugin.modules.physicschunk.settings;
    exports dev.hytalemodding.impulse.core.plugin.persistence;
    exports dev.hytalemodding.impulse.core.plugin.physics;
    exports dev.hytalemodding.impulse.core.plugin.settings;
    exports dev.hytalemodding.impulse.core.plugin.snapshot;
    exports dev.hytalemodding.impulse.core.plugin.snapshots;
}
