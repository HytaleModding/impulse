package dev.hytalemodding.impulse.core.plugin.events;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hypixel.hytale.component.system.EcsEvent;
import org.junit.jupiter.api.Test;

class PhysicsEventFramePublishedEventTest {

    @Test
    void carriesPublishedFrameAsHytaleEcsEvent() {
        PhysicsEventFrame frame = PhysicsEventFrame.empty(7L);

        PhysicsEventFramePublishedEvent event = new PhysicsEventFramePublishedEvent(frame);

        assertInstanceOf(EcsEvent.class, event);
        assertSame(frame, event.frame());
        assertSame(frame, event.getFrame());
    }
}
