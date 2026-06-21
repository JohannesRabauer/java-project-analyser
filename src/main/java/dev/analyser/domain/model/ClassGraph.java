package dev.analyser.domain.model;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Directed graph of class relationships extracted from AST analysis.
 */
public final class ClassGraph {

    public enum EdgeKind {
        EXTENDS, IMPLEMENTS, DEPENDS_ON
    }

    public record Edge(String source, String target, EdgeKind kind) {}

    private final Map<String, ClassSummary> nodes;
    private final List<Edge> edges;

    private ClassGraph(Map<String, ClassSummary> nodes, List<Edge> edges) {
        this.nodes = Map.copyOf(nodes);
        this.edges = List.copyOf(edges);
    }

    public static ClassGraph buildFrom(List<ClassSummary> summaries) {
        Map<String, ClassSummary> nodes = new LinkedHashMap<>();
        for (ClassSummary s : summaries) {
            nodes.put(s.qualifiedName(), s);
        }

        Set<String> knownTypes = nodes.keySet();
        List<Edge> edges = new ArrayList<>();

        for (ClassSummary s : summaries) {
            String source = s.qualifiedName();

            if (s.superClass() != null) {
                resolveTarget(s.superClass(), s, knownTypes)
                        .ifPresent(t -> edges.add(new Edge(source, t, EdgeKind.EXTENDS)));
            }

            for (String iface : s.interfaces()) {
                resolveTarget(iface, s, knownTypes)
                        .ifPresent(t -> edges.add(new Edge(source, t, EdgeKind.IMPLEMENTS)));
            }

            for (String imp : s.imports()) {
                if (knownTypes.contains(imp) && !imp.equals(source)) {
                    edges.add(new Edge(source, imp, EdgeKind.DEPENDS_ON));
                }
            }
        }

        return new ClassGraph(nodes, edges);
    }

    public Set<String> classNames() {
        return nodes.keySet();
    }

    public Optional<ClassSummary> getClass(String qualifiedName) {
        return Optional.ofNullable(nodes.get(qualifiedName));
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<Edge> edgesFrom(String qualifiedName) {
        return edges.stream().filter(e -> e.source().equals(qualifiedName)).toList();
    }

    public List<Edge> edgesTo(String qualifiedName) {
        return edges.stream().filter(e -> e.target().equals(qualifiedName)).toList();
    }

    public RelatedClasses relatedTo(String qualifiedName) {
        List<String> dependsOn = edgesFrom(qualifiedName).stream()
                .filter(e -> e.kind() == EdgeKind.DEPENDS_ON)
                .map(Edge::target).toList();
        List<String> dependedOnBy = edgesTo(qualifiedName).stream()
                .filter(e -> e.kind() == EdgeKind.DEPENDS_ON)
                .map(Edge::source).toList();
        List<String> extendedBy = edgesTo(qualifiedName).stream()
                .filter(e -> e.kind() == EdgeKind.EXTENDS)
                .map(Edge::source).toList();
        List<String> implementedBy = edgesTo(qualifiedName).stream()
                .filter(e -> e.kind() == EdgeKind.IMPLEMENTS)
                .map(Edge::source).toList();
        Optional<String> superClass = edgesFrom(qualifiedName).stream()
                .filter(e -> e.kind() == EdgeKind.EXTENDS)
                .map(Edge::target).findFirst();
        List<String> implementsInterfaces = edgesFrom(qualifiedName).stream()
                .filter(e -> e.kind() == EdgeKind.IMPLEMENTS)
                .map(Edge::target).toList();

        return new RelatedClasses(qualifiedName, dependsOn, dependedOnBy,
                superClass.orElse(null), extendedBy, implementsInterfaces, implementedBy);
    }

    public record RelatedClasses(
            String className,
            List<String> dependsOn,
            List<String> dependedOnBy,
            String superClass,
            List<String> extendedBy,
            List<String> implementsInterfaces,
            List<String> implementedBy) {}

    private static Optional<String> resolveTarget(String simpleName, ClassSummary context, Set<String> knownTypes) {
        // Try fully qualified from imports
        for (String imp : context.imports()) {
            if (imp.endsWith("." + simpleName)) {
                if (knownTypes.contains(imp)) {
                    return Optional.of(imp);
                }
            }
        }
        // Try same package
        String samePackage = context.packageName().isEmpty() ? simpleName : context.packageName() + "." + simpleName;
        if (knownTypes.contains(samePackage)) {
            return Optional.of(samePackage);
        }
        return Optional.empty();
    }
}
