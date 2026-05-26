package dev.analyser.adapter.out.project;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.analyser.application.port.out.ProjectSourcePort;
import dev.analyser.domain.model.LocalSource;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FilesystemProjectAdapterTest {

    private final FilesystemProjectAdapter adapter = new FilesystemProjectAdapter();

    @Test
    void uc001_shouldLoadAMinimalProjectTreeFromTheFilesystem() throws Exception {
        var fixtureRoot = fixturePath("fixtures/minimal-project");

        var projectTree = adapter.loadProject(new LocalSource(fixtureRoot));

        assertAll(
                () -> assertEquals(fixtureRoot.toAbsolutePath().normalize(), projectTree.rootPath()),
                () -> assertEquals(1, projectTree.javaSourceFiles().size()),
                () -> assertEquals(
                        fixtureRoot.resolve("pom.xml").toAbsolutePath().normalize(),
                        projectTree.buildDescriptor().orElseThrow()),
                () -> assertEquals(
                        fixtureRoot.resolve("src/main/java/com/example/minimal/App.java")
                                .toAbsolutePath()
                                .normalize(),
                        projectTree.javaSourceFiles().getFirst()),
                () -> assertEquals(
                        fixtureRoot.resolve("src/test/java/com/example/minimal/AppTest.java")
                                .toAbsolutePath()
                                .normalize(),
                        projectTree.testSourceFiles().getFirst()),
                () -> assertEquals(
                        Files.readString(fixtureRoot.resolve("README.adoc")),
                        projectTree.readmeContent().orElseThrow()),
                () -> assertTrue(projectTree.rootPath().isAbsolute()),
                () -> assertTrue(projectTree.javaSourceFiles().stream().allMatch(Path::isAbsolute)),
                () -> assertTrue(projectTree.testSourceFiles().stream().allMatch(Path::isAbsolute)),
                () -> assertTrue(projectTree.buildDescriptor().orElseThrow().isAbsolute()));
    }

    @Test
    void uc001_shouldReturnEmptyReadmeContentWhenNoReadmeExists() throws Exception {
        var fixtureRoot = fixturePath("fixtures/minimal-project-no-readme");

        var projectTree = adapter.loadProject(new LocalSource(fixtureRoot));

        assertTrue(projectTree.readmeContent().isEmpty());
    }

    @Test
    void uc001_br006_shouldNotModifyTheAnalysedProject() throws Exception {
        var fixtureRoot = fixturePath("fixtures/minimal-project");
        var snapshotBefore = checksumSnapshot(fixtureRoot);

        adapter.loadProject(new LocalSource(fixtureRoot));

        assertEquals(snapshotBefore, checksumSnapshot(fixtureRoot));
    }

    @Test
    void uc001_shouldBeTheDefaultAlternativeForFilesystemSources() {
        assertAll(
                () -> assertTrue(ProjectSourcePort.class.isAssignableFrom(FilesystemProjectAdapter.class)),
                () -> assertTrue(FilesystemProjectAdapter.class.isAnnotationPresent(Alternative.class)),
                () -> assertTrue(FilesystemProjectAdapter.class.isAnnotationPresent(Priority.class)),
                () -> assertEquals(
                        Interceptor.Priority.APPLICATION,
                        FilesystemProjectAdapter.class.getAnnotation(Priority.class).value()));
    }

    private Path fixturePath(String resourcePath) throws URISyntaxException {
        return Path.of(ClassLoader.getSystemResource(resourcePath).toURI());
    }

    private Map<Path, String> checksumSnapshot(Path rootPath)
            throws IOException, NoSuchAlgorithmException {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toMap(
                            path -> rootPath.relativize(path),
                            this::sha256));
        }
    }

    private String sha256(Path path) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to calculate checksum for " + path, exception);
        }
    }
}
