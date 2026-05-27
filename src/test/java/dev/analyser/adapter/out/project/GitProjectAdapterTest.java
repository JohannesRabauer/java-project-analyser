package dev.analyser.adapter.out.project;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.analyser.domain.model.GitSource;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GitProjectAdapterTest {

    @Test
    void uc001_shouldLoadAMinimalProjectTreeFromAGitRepository() throws Exception {
        var workspace = testWorkspace();
        try {
            var sourceRepository = createGitRepository(workspace.resolve("source-repository"), "fixtures/minimal-project");
            var adapter = new GitProjectAdapter(new ProjectTreeLoader(), workspace.resolve("clones"));

            var projectTree = adapter.loadProject(new GitSource(sourceRepository.toUri()));

            assertAll(
                    () -> assertNotEquals(sourceRepository.toAbsolutePath().normalize(), projectTree.rootPath()),
                    () -> assertTrue(projectTree.rootPath().startsWith(workspace.resolve("clones").toAbsolutePath().normalize())),
                    () -> assertEquals(1, projectTree.javaSourceFiles().size()),
                    () -> assertEquals(
                            projectTree.rootPath().resolve("pom.xml"),
                            projectTree.buildDescriptor().orElseThrow()),
                    () -> assertEquals(
                            projectTree.rootPath().resolve("src/main/java/com/example/minimal/App.java"),
                            projectTree.javaSourceFiles().getFirst()),
                    () -> assertEquals(
                            projectTree.rootPath().resolve("src/test/java/com/example/minimal/AppTest.java"),
                            projectTree.testSourceFiles().getFirst()),
                    () -> assertEquals(
                            Files.readString(sourceRepository.resolve("README.adoc")),
                            projectTree.readmeContent().orElseThrow()));
        } finally {
            deleteRecursively(workspace);
        }
    }

    @Test
    void uc001_br006_shouldNotModifyTheSourceGitRepository() throws Exception {
        var workspace = testWorkspace();
        try {
            var sourceRepository = createGitRepository(workspace.resolve("source-repository"), "fixtures/minimal-project");
            var snapshotBefore = checksumSnapshot(sourceRepository);
            var adapter = new GitProjectAdapter(new ProjectTreeLoader(), workspace.resolve("clones"));

            adapter.loadProject(new GitSource(sourceRepository.toUri()));

            assertEquals(snapshotBefore, checksumSnapshot(sourceRepository));
        } finally {
            deleteRecursively(workspace);
        }
    }

    private Path createGitRepository(Path repositoryRoot, String fixturePath) throws Exception {
        copyRecursively(fixturePath(fixturePath), repositoryRoot);
        runGit(repositoryRoot, "init", "--quiet", "--initial-branch=main");
        runGit(repositoryRoot, "config", "user.email", "tests@example.com");
        runGit(repositoryRoot, "config", "user.name", "Test User");
        runGit(repositoryRoot, "add", ".");
        runGit(repositoryRoot, "commit", "--quiet", "-m", "fixture");
        return repositoryRoot;
    }

    private void runGit(Path workingDirectory, String... command) throws Exception {
        var fullCommand = new String[command.length + 1];
        fullCommand[0] = "git";
        System.arraycopy(command, 0, fullCommand, 1, command.length);

        var process = new ProcessBuilder(fullCommand)
                .directory(workingDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        var output = new String(process.getInputStream().readAllBytes());
        var exitCode = process.waitFor();

        assertEquals(0, exitCode, output);
    }

    private Path fixturePath(String resourcePath) throws URISyntaxException {
        return Path.of(ClassLoader.getSystemResource(resourcePath).toURI());
    }

    private Path testWorkspace() {
        return Path.of("target/test-work", "git-project-adapter-" + UUID.randomUUID())
                .toAbsolutePath()
                .normalize();
    }

    private void copyRecursively(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (var path : paths.toList()) {
                var relativePath = source.relativize(path);
                var targetPath = target.resolve(relativePath);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(path, targetPath);
                }
            }
        }
    }

    private Map<Path, String> checksumSnapshot(Path rootPath) throws IOException, NoSuchAlgorithmException {
        try (Stream<Path> paths = Files.walk(rootPath)) {
            return paths
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toMap(path -> rootPath.relativize(path), this::sha256));
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

    private void deleteRecursively(Path rootPath) throws IOException {
        if (!Files.exists(rootPath)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to delete " + path, exception);
                }
            });
        }
    }
}
