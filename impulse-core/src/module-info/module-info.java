module dev.hytalemodding.impulse.core {
    requires transitive impulse.api;
    requires transitive org.joml;
    requires static jsr305;
    requires static crucible;

    exports dev.hytalemodding.impulse.core.plugin.body;
    exports dev.hytalemodding.impulse.core.plugin.codec;
    exports dev.hytalemodding.impulse.core.plugin.components;
    exports dev.hytalemodding.impulse.core.plugin.events;
    exports dev.hytalemodding.impulse.core.plugin.joint;
    exports dev.hytalemodding.impulse.core.plugin.modules.control;
    exports dev.hytalemodding.impulse.core.plugin.modules.worldcollision;
    exports dev.hytalemodding.impulse.core.plugin.persistence;
    exports dev.hytalemodding.impulse.core.plugin.resources;
    exports dev.hytalemodding.impulse.core.plugin.settings;
    exports dev.hytalemodding.impulse.core.plugin.simulation;
    exports dev.hytalemodding.impulse.core.plugin.simulation.query;
    exports dev.hytalemodding.impulse.core.plugin.simulation.recorder;
    exports dev.hytalemodding.impulse.core.plugin.simulation.view;
    exports dev.hytalemodding.impulse.core.plugin.snapshot;
}
