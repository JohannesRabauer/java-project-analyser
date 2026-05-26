package dev.analyser.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProjectTreeTest {

    @Test
    void uc001_br013_shouldKeepProductionAndTestSourcesSeparate() {
        var tree = new ProjectTree(
                Path.of("/workspace/project"),
                List.of(Path.of("src/main/java/dev/analyser/App.java")),
                List.of(Path.of("src/test/java/dev/analyser/AppTest.java")),
                Optional.of(Path.of("pom.xml")),
                Optional.of("# README"));

        assertEquals(List.of(Path.of("src/main/java/dev/analyser/App.java")), tree.javaSourceFiles());
        assertEquals(List.of(Path.of("src/test/java/dev/analyser/AppTest.java")), tree.testSourceFiles());
        assertEquals(Optional.of(Path.of("pom.xml")), tree.buildDescriptor());
        assertEquals(Optional.of("# README"), tree.readmeContent());
    }

    @Test
    void uc001_shouldRejectNonJavaSourceFiles() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectTree(
                        Path.of("/workspace/project"),
                        List.of(Path.of("src/main/java/dev/analyser/App.kt")),
                        List.of(),
                        Optional.of(Path.of("pom.xml")),
                        Optional.empty()));
    }

    @Test
    void uc001_shouldRejectUnsupportedBuildDescriptors() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectTree(
                        Path.of("/workspace/project"),
                        List.of(),
                        List.of(),
                        Optional.of(Path.of("build.xml")),
                        Optional.empty()));
    }

    @Test
    void uc001_shouldRejectBlankReadmeContent() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProjectTree(
                        Path.of("/workspace/project"),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        Optional.of("   ")));
    }
}
