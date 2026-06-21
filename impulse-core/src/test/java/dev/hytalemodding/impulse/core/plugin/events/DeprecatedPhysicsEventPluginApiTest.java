package dev.hytalemodding.impulse.core.plugin.events;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.PhysicsStore;
import dev.hytalemodding.impulse.core.plugin.modules.physicsentity.PhysicsEntityTypes;
import dev.hytalemodding.impulse.core.plugin.physics.PhysicsWorlds;
import dev.hytalemodding.impulse.core.plugin.settings.PhysicsWorldSettings;
import java.lang.reflect.AnnotatedElement;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class DeprecatedPhysicsEventPluginApiTest {

    @Test
    void marksPhysicsEventValueApiDeprecated() throws NoSuchFieldException {
        assertDeprecated(PhysicsEventFrame.class.getPackage());
        assertDeprecated(PhysicsBodyActivationEvent.class);
        assertDeprecated(PhysicsContactEvent.class);
        assertDeprecated(PhysicsEventCollectionMode.class);
        assertDeprecated(PhysicsEventCollectionMode.DISABLED.getClass().getField("DISABLED"));
        assertDeprecated(PhysicsEventCollectionMode.CONTACTS.getClass().getField("CONTACTS"));
        assertDeprecated(PhysicsEventFrame.class);
        assertDeprecated(PhysicsEventFramePublishedEvent.class);
        assertDeprecated(PhysicsFrameEvent.class);
        assertDeprecated(PhysicsFrameEventKind.class);
        assertDeprecated(PhysicsFrameEventKind.CONTACT.getClass().getField("CONTACT"));
        assertDeprecated(PhysicsFrameEventKind.BODY_ACTIVATION.getClass().getField("BODY_ACTIVATION"));
        assertDeprecated(PhysicsFrameEventKind.JOINT_BREAK.getClass().getField("JOINT_BREAK"));
        assertDeprecated(PhysicsJointBreakEvent.class);
        assertDeprecated(PhysicsSnapshotPublicationEvent.class);
        assertDeprecated(PhysicsStepEvent.class);
    }

    @Test
    void marksPhysicsEventAccessorsDeprecated() throws NoSuchMethodException, NoSuchFieldException {
        assertDeprecated(PhysicsWorlds.class.getMethod("latestEventFrame", Store.class));
        assertDeprecated(PhysicsWorlds.class.getMethod("latestEventFrameAsync", World.class));
        assertDeprecated(PhysicsWorldSettings.class.getField("DEFAULT_EVENT_COLLECTION_MODE"));
        assertDeprecated(PhysicsWorldSettings.class.getMethod("getEventCollectionMode"));
        assertDeprecated(PhysicsWorldSettings.class.getMethod("setEventCollectionMode",
            PhysicsEventCollectionMode.class));
        assertDeprecated(PhysicsEntityTypes.class.getMethod("physicsEventFramePublishedEventType"));
    }

    private static void assertDeprecated(AnnotatedElement element) {
        assertNotNull(element.getAnnotation(Deprecated.class), () -> element + " is not deprecated");
    }
}
