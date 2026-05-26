package dev.analyser.domain.model;

import java.nio.file.Path;
import java.util.Objects;

public record LocalSource(Path rootPath) implements ProjectSource {

    public LocalSource {
        Objects.requireNonNull(rootPath, "rootPath must not be null");
    }
}
