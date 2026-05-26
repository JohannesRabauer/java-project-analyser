package dev.analyser.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectSourceTest {

    @Test
    void uc001_shouldCreateALocalSource() {
        var source = new LocalSource(Path.of("/workspace/project"));

        assertEquals(Path.of("/workspace/project"), source.rootPath());
    }

    @Test
    void uc001_shouldRejectANullLocalSourcePath() {
        assertThrows(NullPointerException.class, () -> new LocalSource(null));
    }

    @Test
    void uc001_shouldCreateAGitSource() {
        var source = new GitSource(URI.create("https://github.com/example/project.git"));

        assertEquals(URI.create("https://github.com/example/project.git"), source.repositoryUrl());
    }

    @Test
    void uc001_br015_shouldRejectANonAbsoluteGitUri() {
        assertThrows(IllegalArgumentException.class, () -> new GitSource(URI.create("/example/project.git")));
    }
}
