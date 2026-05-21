package dev.hytalemodding.impulse.core.internal.crucible;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * One proxy-backed Crucible test case.
 */
record CrucibleTestCase(
    String name,
    ContextualBooleanTest body,
    String failureMessage) {

    static CrucibleTestCase sync(String name, BooleanTest body, String failureMessage) {
        return new CrucibleTestCase(name,
            context -> CompletableFuture.completedFuture(body.run()),
            failureMessage);
    }

    static CrucibleTestCase async(String name,
        ContextualBooleanTest body,
        String failureMessage) {

        return new CrucibleTestCase(name, body, failureMessage);
    }

    @FunctionalInterface
    interface BooleanTest {
        boolean run();
    }

    @FunctionalInterface
    interface ContextualBooleanTest {
        CompletionStage<Boolean> run(CrucibleContext context);
    }
}
