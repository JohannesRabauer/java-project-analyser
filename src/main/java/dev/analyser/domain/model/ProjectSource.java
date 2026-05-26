package dev.analyser.domain.model;

public sealed interface ProjectSource permits LocalSource, GitSource {
}
