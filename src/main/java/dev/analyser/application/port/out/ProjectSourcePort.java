package dev.analyser.application.port.out;

import dev.analyser.domain.model.ProjectSource;
import dev.analyser.domain.model.ProjectTree;

public interface ProjectSourcePort {

    ProjectTree loadProject(ProjectSource source);
}
