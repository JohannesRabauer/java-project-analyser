package dev.analyser.adapter.out.project;

import dev.analyser.application.port.out.ProjectSourcePort;
import dev.analyser.domain.model.GitSource;
import dev.analyser.domain.model.LocalSource;
import dev.analyser.domain.model.ProjectSource;
import dev.analyser.domain.model.ProjectTree;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.interceptor.Interceptor;

@ApplicationScoped
@Alternative
@Priority(Interceptor.Priority.APPLICATION)
public class FilesystemProjectAdapter implements ProjectSourcePort {

    private final ProjectTreeLoader projectTreeLoader;
    private final GitProjectAdapter gitProjectAdapter;

    public FilesystemProjectAdapter() {
        this(new ProjectTreeLoader());
    }

    private FilesystemProjectAdapter(ProjectTreeLoader projectTreeLoader) {
        this(projectTreeLoader, new GitProjectAdapter(projectTreeLoader));
    }

    FilesystemProjectAdapter(ProjectTreeLoader projectTreeLoader, GitProjectAdapter gitProjectAdapter) {
        this.projectTreeLoader = projectTreeLoader;
        this.gitProjectAdapter = gitProjectAdapter;
    }

    @Override
    public ProjectTree loadProject(ProjectSource source) {
        if (source instanceof LocalSource localSource) {
            return projectTreeLoader.load(localSource.rootPath());
        }

        if (source instanceof GitSource gitSource) {
            return gitProjectAdapter.loadProject(gitSource);
        }

        throw new IllegalArgumentException("Unsupported project source: " + source.getClass().getSimpleName());
    }
}
