package dev.analyser.domain.model;

import java.net.URI;
import java.util.Objects;

public record GitSource(URI repositoryUrl) implements ProjectSource {

    public GitSource {
        Objects.requireNonNull(repositoryUrl, "repositoryUrl must not be null");

        if (!repositoryUrl.isAbsolute()) {
            throw new IllegalArgumentException("repositoryUrl must be absolute");
        }
    }
}
