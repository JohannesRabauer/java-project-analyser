package dev.analyser.adapter.out.project;

import dev.analyser.domain.model.ProjectTree;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

final class ProjectTreeLoader {

    private static final List<String> BUILD_DESCRIPTORS = List.of("pom.xml", "build.gradle");
    private static final List<String> README_FILES = List.of("README.md", "README.adoc");

    ProjectTree load(Path rootPath) {
        var absoluteRootPath = validateRootPath(rootPath);

        return new ProjectTree(
                absoluteRootPath,
                collectJavaFiles(absoluteRootPath, Path.of("src/main/java")),
                collectJavaFiles(absoluteRootPath, Path.of("src/test/java")),
                findBuildDescriptor(absoluteRootPath),
                loadReadmeContent(absoluteRootPath));
    }

    Path validateRootPath(Path rootPath) {
        var absoluteRootPath = rootPath.toAbsolutePath().normalize();

        if (!Files.isDirectory(absoluteRootPath)) {
            throw new IllegalArgumentException("Project root must be an existing directory");
        }

        if (!Files.isReadable(absoluteRootPath)) {
            throw new IllegalArgumentException("Project root must be readable");
        }

        return absoluteRootPath;
    }

    private List<Path> collectJavaFiles(Path rootPath, Path sourceDirectory) {
        var directory = guardPath(rootPath, rootPath.resolve(sourceDirectory));
        if (!Files.isDirectory(directory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> guardPath(rootPath, path))
                    .sorted()
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Java source files from " + directory, exception);
        }
    }

    private Optional<Path> findBuildDescriptor(Path rootPath) {
        return BUILD_DESCRIPTORS.stream()
                .map(rootPath::resolve)
                .map(path -> guardPath(rootPath, path))
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .findFirst();
    }

    private Optional<String> loadReadmeContent(Path rootPath) {
        return README_FILES.stream()
                .map(rootPath::resolve)
                .map(path -> guardPath(rootPath, path))
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .findFirst()
                .map(this::readFile);
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file " + path, exception);
        }
    }

    Path guardPath(Path rootPath, Path path) {
        var absolutePath = path.toAbsolutePath().normalize();
        if (!absolutePath.startsWith(rootPath)) {
            throw new IllegalArgumentException("Path must stay inside project root: " + path);
        }
        return absolutePath;
    }
}
