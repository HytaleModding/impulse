package dev.hytalemodding.impulse.core.internal.crucible;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * One proxy-backed Crucible test case.
 */
record CrucibleTestCase(
    String name,
    ContextualTest body,
    String failureMessage) {

    static CrucibleTestCase sync(String name, BooleanTest body, String failureMessage) {
        return new CrucibleTestCase(name,
            context -> CompletableFuture.completedFuture(TestOutcome.from(body.run())),
            failureMessage);
    }

    static CrucibleTestCase async(String name,
        ContextualBooleanTest body,
        String failureMessage) {

        return new CrucibleTestCase(name,
            context -> body.run(context).thenApply(TestOutcome::from),
            failureMessage);
    }

    static CrucibleTestCase asyncResult(String name,
        ContextualTest body,
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

    @FunctionalInterface
    interface ContextualTest {
        CompletionStage<TestOutcome> run(CrucibleContext context);
    }

    record TestOutcome(boolean passed, String failureMessage) {

        static TestOutcome pass() {
            return new TestOutcome(true, "");
        }

        static TestOutcome fail(String failureMessage) {
            return new TestOutcome(false, failureMessage);
        }

        static TestOutcome from(boolean passed) {
            return passed ? pass() : fail("");
        }
    }
}
