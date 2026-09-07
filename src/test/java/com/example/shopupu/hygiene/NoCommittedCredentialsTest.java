package com.example.shopupu.hygiene;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The seed scripts once carried a working admin password as a parameter default.
 * Production was seeded with that script, so the account kept the default — and the
 * repository is public, which made a live ADMIN login readable by anyone. Rotating
 * the password fixed that instance; this test fixes the pattern.
 *
 * <p>Credentials belong in the environment: read them from {@code $env:...} /
 * {@code process.env...} and fail fast when they are missing.
 */
class NoCommittedCredentialsTest {

    private static final Path SCRIPTS = Path.of("scripts");

    /** An assignment of a non-trivial string literal to a secret-looking name. */
    private static final Pattern SECRET_LITERAL = Pattern.compile(
            "(?i)(password|secret|api[_-]?key|token)\\w*\\s*=\\s*[\"']([^\"']{6,})[\"']");

    @Test
    void seedScriptsCarryNoCredentialsOfTheirOwn() throws IOException {
        if (!Files.isDirectory(SCRIPTS)) {
            return; // scripts are optional tooling, not part of the build
        }
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SCRIPTS)) {
            for (Path file : files.filter(Files::isRegularFile).filter(NoCommittedCredentialsTest::isScript).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    var matcher = SECRET_LITERAL.matcher(line);
                    if (matcher.find() && !isEnvironmentLookup(line)) {
                        offenders.add(file + ":" + (i + 1) + " -> " + line.trim());
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "Scripts must read credentials from the environment, never carry them:\n"
                        + String.join("\n", offenders));
    }

    private static boolean isScript(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".ps1") || name.endsWith(".mjs") || name.endsWith(".js")
                || name.endsWith(".sh") || name.endsWith(".sql");
    }

    /** "$env:X" / "process.env.X" reads are the shape we want, even next to a fallback name. */
    private static boolean isEnvironmentLookup(String line) {
        return line.contains("$env:") || line.contains("process.env") || line.contains("${");
    }
}
