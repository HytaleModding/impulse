module dev.hytalemodding.impulse.core {
    requires transitive impulse.api;
    requires transitive org.joml;
    requires static jsr305;
    requires static crucible;

    exports dev.hytalemodding.impulse.core.plugin.body;
    exports dev.hytalemodding.impulse.core.plugin.codec;
    exports dev.hytalemodding.impulse.core.plugin.events;
    exports dev.hytalemodding.impulse.core.plugin.joint;
    exports dev.hytalemodding.impulse.core.plugin.modules.control;
    exports dev.hytalemodding.impulse.core.plugin.modules.worldcollision;
    exports dev.hytalemodding.impulse.core.plugin.persistence;
    exports dev.hytalemodding.impulse.core.plugin.physicsstore;
    exports dev.hytalemodding.impulse.core.plugin.physicsstore.components;
    exports dev.hytalemodding.impulse.core.plugin.physicsstore.projection;
    exports dev.hytalemodding.impulse.core.plugin.physicsstore.snapshots;
    exports dev.hytalemodding.impulse.core.plugin.resources;
    exports dev.hytalemodding.impulse.core.plugin.settings;
    exports dev.hytalemodding.impulse.core.plugin.simulation;
    exports dev.hytalemodding.impulse.core.plugin.simulation.view;
    exports dev.hytalemodding.impulse.core.plugin.snapshot;
}
