package dev.hytalemodding.impulse.core.internal.crucible;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import dev.hytalemodding.impulse.core.internal.crucible.CrucibleTestCase.TestOutcome;

/**
 * Reflective adapter for Crucible's API.
 *
 * <p>No class in impulse-examples should statically reference Crucible API
 * types because Crucible is an optional directory-loaded mod with its own
 * plugin classloader.</p>
 */
final class CrucibleBridge {

    private final Class<?> asyncTestSuiteClass;
    private final Method registerSuite;
    private final Method pass;
    private final Method fail;
    private final Method error;

    private CrucibleBridge(
        Class<?> asyncTestSuiteClass,
        Method registerSuite,
        Method pass,
        Method fail,
        Method error) {

        this.asyncTestSuiteClass = asyncTestSuiteClass;
        this.registerSuite = registerSuite;
        this.pass = pass;
        this.fail = fail;
        this.error = error;
    }

    static CrucibleBridge create(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> apiClass = Class.forName(
            "ionforgelabs.crucible.api.CrucibleAPI", true, loader);
        Class<?> testSuiteClass = Class.forName(
            "ionforgelabs.crucible.api.TestSuite", true, loader);
        Class<?> asyncTestSuiteClass = Class.forName(
            "ionforgelabs.crucible.api.AsyncTestSuite", true, loader);
        Class<?> resultClass = Class.forName(
            "ionforgelabs.crucible.api.TestResult", true, loader);

        return new CrucibleBridge(
            asyncTestSuiteClass,
            apiClass.getMethod("registerSuite", testSuiteClass),
            resultClass.getMethod("pass", String.class, String.class),
            resultClass.getMethod("fail", String.class, String.class, String.class),
            resultClass.getMethod("error", String.class, String.class, Exception.class));
    }

    void registerSuite(ClassLoader loader, CrucibleSuite suite)
        throws ReflectiveOperationException {

        Object proxy = Proxy.newProxyInstance(loader,
            new Class<?>[] {asyncTestSuiteClass},
            new SuiteInvocationHandler(this, suite));
        registerSuite.invoke(null, proxy);
    }

    private Object pass(String suite, String test) throws ReflectiveOperationException {
        return pass.invoke(null, suite, test);
    }

    private Object fail(String suite, String test, String message)
        throws ReflectiveOperationException {

        return fail.invoke(null, suite, test, message);
    }

    private Object error(String suite, String test, Exception exception)
        throws ReflectiveOperationException {

        return error.invoke(null, suite, test, exception);
    }

    private record SuiteInvocationHandler(CrucibleBridge bridge, CrucibleSuite suite) implements
        InvocationHandler {

        @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return switch (method.getName()) {
                    case "id", "toString" -> suite.id();
                    case "name" -> suite.name();
                    case "description" -> suite.description();
                    case "tags" -> suite.tags();
                    case "run" -> runTests(context(args));
                    case "runAsync" -> runTestsAsync(context(args));
                    case "hashCode" -> suite.id().hashCode();
                    case "equals" -> args != null && args.length > 0 && proxy == args[0];
                    default -> throw new UnsupportedOperationException(
                        "Unsupported Crucible TestSuite method: " + method.getName());
                };
            }

            private CrucibleContext context(Object[] args) {
                Object rawContext = args != null && args.length > 0 ? args[0] : null;
                return new CrucibleContext(rawContext);
            }

            private List<Object> runTests(CrucibleContext context)
                throws ReflectiveOperationException {

                List<Object> results = new ArrayList<>();
                for (CrucibleTestCase test : suite.tests()) {
                    results.add(runTestSync(context, test));
                }
                return results;
            }

            private CompletableFuture<List<Object>> runTestsAsync(CrucibleContext context) {
                CompletableFuture<List<Object>> results = CompletableFuture.completedFuture(
                    new ArrayList<>());
                for (CrucibleTestCase test : suite.tests()) {
                    results = results.thenCompose(current -> runTestAsync(context, test)
                        .thenApply(result -> {
                            current.add(result);
                            return current;
                        }));
                }
                return results;
            }

            private Object runTestSync(CrucibleContext context, CrucibleTestCase test)
                throws ReflectiveOperationException {

                try {
                    TestOutcome outcome = test.body().run(context).toCompletableFuture().join();
                    if (outcome.passed()) {
                        return bridge.pass(suite.id(), test.name());
                    }
                    return bridge.fail(suite.id(),
                        test.name(),
                        failureMessage(test, outcome));
                } catch (CompletionException e) {
                    return bridge.error(suite.id(), test.name(), asException(e));
                } catch (Exception e) {
                    return bridge.error(suite.id(), test.name(), e);
                }
            }

            private CompletableFuture<Object> runTestAsync(CrucibleContext context,
                CrucibleTestCase test) {
                try {
                    return test.body().run(context).handle((outcome, failure) -> {
                        try {
                            if (failure != null) {
                                return bridge.error(suite.id(), test.name(), asException(failure));
                            }
                            if (outcome != null && outcome.passed()) {
                                return bridge.pass(suite.id(), test.name());
                            }
                            return bridge.fail(suite.id(),
                                test.name(),
                                failureMessage(test, outcome));
                        } catch (ReflectiveOperationException e) {
                            throw new CompletionException(e);
                        }
                    }).toCompletableFuture();
                } catch (Exception e) {
                    try {
                        return CompletableFuture.completedFuture(
                            bridge.error(suite.id(), test.name(), e));
                    } catch (ReflectiveOperationException reflective) {
                        return CompletableFuture.failedFuture(reflective);
                    }
                }
            }

            private static String failureMessage(CrucibleTestCase test,
                TestOutcome outcome) {
                if (outcome != null && outcome.failureMessage() != null
                    && !outcome.failureMessage().isBlank()) {
                    return outcome.failureMessage();
                }
                return test.failureMessage();
            }

            private static Exception asException(Throwable failure) {
                Throwable current = failure;
                if (current instanceof CompletionException && current.getCause() != null) {
                    current = current.getCause();
                }
                if (current instanceof Exception exception) {
                    return exception;
                }
                return new RuntimeException(current);
            }
        }
}
