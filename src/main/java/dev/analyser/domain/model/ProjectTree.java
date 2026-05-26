package dev.analyser.domain.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProjectTree(
        Path rootPath,
        List<Path> javaSourceFiles,
        List<Path> testSourceFiles,
        Optional<Path> buildDescriptor,
        Optional<String> readmeContent) {

    public ProjectTree {
        Objects.requireNonNull(rootPath, "rootPath must not be null");
        Objects.requireNonNull(javaSourceFiles, "javaSourceFiles must not be null");
        Objects.requireNonNull(testSourceFiles, "testSourceFiles must not be null");
        Objects.requireNonNull(buildDescriptor, "buildDescriptor must not be null");
        Objects.requireNonNull(readmeContent, "readmeContent must not be null");

        javaSourceFiles = List.copyOf(javaSourceFiles);
        testSourceFiles = List.copyOf(testSourceFiles);

        validateJavaFiles(javaSourceFiles, "javaSourceFiles");
        validateJavaFiles(testSourceFiles, "testSourceFiles");
        validateBuildDescriptor(buildDescriptor);
        validateReadmeContent(readmeContent);
    }

    private static void validateJavaFiles(List<Path> sourceFiles, String fieldName) {
        sourceFiles.forEach(path -> {
            Objects.requireNonNull(path, fieldName + " must not contain null");

            if (!path.toString().endsWith(".java")) {
                throw new IllegalArgumentException(fieldName + " must contain only .java files");
            }
        });
    }

    private static void validateBuildDescriptor(Optional<Path> buildDescriptor) {
        buildDescriptor.ifPresent(path -> {
            var fileName = Objects.requireNonNull(path, "buildDescriptor must not contain null")
                    .getFileName();

            if (fileName == null) {
                throw new IllegalArgumentException("buildDescriptor must point to a file");
            }

            var buildFileName = fileName.toString();
            if (!"pom.xml".equals(buildFileName) && !"build.gradle".equals(buildFileName)) {
                throw new IllegalArgumentException("buildDescriptor must be pom.xml or build.gradle");
            }
        });
    }

    private static void validateReadmeContent(Optional<String> readmeContent) {
        readmeContent.ifPresent(content -> {
            if (content.isBlank()) {
                throw new IllegalArgumentException("readmeContent must not be blank");
            }
        });
    }
}
