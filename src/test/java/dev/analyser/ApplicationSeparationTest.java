package dev.analyser;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ApplicationSeparationTest {

    @Test
    void uc003_shouldSeparateTheMcpServerFromTheVaadinDemoClient() throws IOException {
        var repositoryRoot = Path.of("").toAbsolutePath().normalize();
        var serverPom = repositoryRoot.resolve("pom.xml");

        assertAll(
                () -> assertTrue(
                        Files.isRegularFile(repositoryRoot.resolve("demo-client/pom.xml")),
                        "expected a standalone demo-client application with its own pom.xml"),
                () -> assertTrue(
                        Files.isRegularFile(repositoryRoot.resolve("demo-client/mvnw")),
                        "expected a standalone demo-client application with its own Maven wrapper"),
                () -> assertFalse(
                        Files.isRegularFile(repositoryRoot.resolve("src/main/java/dev/analyser/adapter/in/ui/McpDemoView.java")),
                        "expected the MCP server application at repository root to no longer bundle the Vaadin demo view"),
                () -> assertFalse(
                        Files.readString(serverPom).contains("vaadin-quarkus-extension"),
                        "expected the MCP server pom to no longer depend on Vaadin"));
    }
}
