package dev.hytalemodding.impulse.rapier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.hytalemodding.impulse.api.PhysicsBody;
import dev.hytalemodding.impulse.api.PhysicsContact;
import dev.hytalemodding.impulse.api.PhysicsSpace;
import java.util.List;
import org.junit.jupiter.api.Test;

class RapierBoundedContactsTest {

    @Test
    void getContactsWithLimitReturnsAtMostRequestedContacts() {
        RapierBackend backend = new RapierBackend();
        backend.init();
        PhysicsSpace space = backend.createSpace();
        try {
            space.setGravity(0.0f, -9.81f, 0.0f);
            addStaticFloor(space, 8, 8);
            addRestingBoxes(space, 8, 8);
            for (int i = 0; i < 180; i++) {
                space.step(1.0f / 30.0f);
            }

            List<PhysicsContact> contacts = space.getContacts(5);

            assertFalse(contacts.isEmpty());
            assertTrue(contacts.size() <= 5);
        } finally {
            space.close();
        }
    }

    private static void addStaticFloor(PhysicsSpace space, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                PhysicsBody body = space.createBox(0.5f, 0.5f, 0.5f, 0.0f);
                body.setPosition(x, 0.0f, z);
                space.addBody(body);
            }
        }
    }

    private static void addRestingBoxes(PhysicsSpace space, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                PhysicsBody body = space.createBox(0.45f, 0.45f, 0.45f, 1.0f);
                body.setPosition(x, 1.05f, z);
                space.addBody(body);
            }
        }
    }
}
