package dev.analyser.adapter.out.project;

import dev.analyser.domain.model.GitSource;
import dev.analyser.domain.model.ProjectTree;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class GitProjectAdapter {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https", "git", "ssh", "file");

    private final ProjectTreeLoader projectTreeLoader;
    private final Path checkoutRoot;

    GitProjectAdapter(ProjectTreeLoader projectTreeLoader) {
        this(projectTreeLoader, Path.of(System.getProperty("java.io.tmpdir"), "analyser-checkouts"));
    }

    GitProjectAdapter(ProjectTreeLoader projectTreeLoader, Path checkoutRoot) {
        this.projectTreeLoader = projectTreeLoader;
        this.checkoutRoot = checkoutRoot.toAbsolutePath().normalize();
    }

    ProjectTree loadProject(GitSource source) {
        validateRepositoryUrl(source.repositoryUrl());
        var destination = prepareCloneDirectory(source.repositoryUrl());

        try {
            cloneRepository(source.repositoryUrl(), destination);
            return projectTreeLoader.load(destination);
        } catch (IOException exception) {
            deleteRecursively(destination);
            throw new IllegalStateException("Failed to clone Git repository " + source.repositoryUrl(), exception);
        } catch (InterruptedException exception) {
            deleteRecursively(destination);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Git clone interrupted for " + source.repositoryUrl(), exception);
        } catch (RuntimeException exception) {
            deleteRecursively(destination);
            throw exception;
        }
    }

    private Path prepareCloneDirectory(URI repositoryUrl) {
        try {
            Files.createDirectories(checkoutRoot);
            var directoryName = sanitize(repositoryUrl) + "-" + UUID.randomUUID();
            return Files.createDirectory(checkoutRoot.resolve(directoryName));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare clone directory for " + repositoryUrl, exception);
        }
    }

    private void validateRepositoryUrl(URI repositoryUrl) {
        var scheme = repositoryUrl.getScheme();
        // scp-like SSH syntax (git@host:path) has no URI scheme; allow it explicitly.
        boolean scpLike = scheme == null && repositoryUrl.toString().matches("^[^/]+@[^/]+:.+");
        if (scpLike) {
            return;
        }
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported Git URL scheme: " + scheme + " (allowed: " + ALLOWED_SCHEMES + ")");
        }
    }

    private void cloneRepository(URI repositoryUrl, Path destination) throws IOException, InterruptedException {        var process = new ProcessBuilder(
                        "git",
                        "clone",
                        "--quiet",
                        repositoryUrl.toString(),
                        destination.toString())
                .redirectErrorStream(true)
                .start();

        String output;
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            output = reader.lines().collect(Collectors.joining(System.lineSeparator())).strip();
        }

        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Git clone failed for " + repositoryUrl + (output.isEmpty() ? "" : ": " + output));
        }
    }

    private String sanitize(URI repositoryUrl) {
        var text = repositoryUrl.toString().replaceAll("[^a-zA-Z0-9]+", "-");
        return text.isBlank() ? "repository" : text.replaceAll("(^-+|-+$)", "");
    }

    private void deleteRecursively(Path rootPath) {
        if (!Files.exists(rootPath)) {
            return;
        }

        try (var paths = Files.walk(rootPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort cleanup after failed clone.
                }
            });
        } catch (IOException ignored) {
            // Best effort cleanup after failed clone.
        }
    }
}
