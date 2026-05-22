package dev.hytalemodding.impulse.core.internal.systems.worker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WorkerStepIntegrationGuardrailTest {

    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");

    @Test
    void benchmarkCommandsDoNotExposeWorkerExecutionMode() throws IOException {
        Path repoRoot = repoRoot();

        assertDoesNotExposeWorkerMode(repoRoot.resolve(
            "impulse-examples/src/main/java/dev/hytalemodding/impulse/examples/commands/stress/StressBenchmarkCommand.java"));
    }

    private static void assertDoesNotExposeWorkerMode(Path source) throws IOException {
        String text = Files.readString(source);

        assertFalse(text.contains("ExecutionMode"), source + " must not wire execution settings");
        assertFalse(text.contains("setExecutionMode"), source + " must not enable worker execution");
        assertFalse(text.contains("WORKER"), source + " must not expose the worker enum");
        Matcher matcher = STRING_LITERAL.matcher(text);
        while (matcher.find()) {
            String literal = matcher.group().toLowerCase(Locale.ROOT);
            assertFalse(literal.contains("worker"),
                source + " must not advertise worker benchmark mode");
        }
    }

    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (Files.exists(current.resolve("settings.gradle.kts"))) {
            return current;
        }
        Path parent = current.getParent();
        assertTrue(parent != null && Files.exists(parent.resolve("settings.gradle.kts")),
            "Could not locate Impulse repo root from " + current);
        return parent;
    }
}
