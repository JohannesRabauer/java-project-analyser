package dev.analyser.domain.model;

import dev.analyser.domain.model.ClassGraph.EdgeKind;
import dev.analyser.domain.model.ClassSummary.ClassKind;
import dev.analyser.domain.model.ClassSummary.FieldInfo;
import dev.analyser.domain.model.ClassSummary.MethodSignature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClassGraphTest {

    @Test
    void buildGraphDetectsExtendsRelationship() {
        var base = classSummary("com.example", "BaseService", ClassKind.CLASS,
                null, List.of(), List.of());
        var child = classSummary("com.example", "OrderService", ClassKind.CLASS,
                "BaseService", List.of(), List.of());

        var graph = ClassGraph.buildFrom(List.of(base, child));

        assertThat(graph.edgesFrom("com.example.OrderService"))
                .anyMatch(e -> e.target().equals("com.example.BaseService") && e.kind() == EdgeKind.EXTENDS);
    }

    @Test
    void buildGraphDetectsImplementsRelationship() {
        var iface = classSummary("com.example", "Auditable", ClassKind.INTERFACE,
                null, List.of(), List.of());
        var impl = classSummary("com.example", "OrderService", ClassKind.CLASS,
                null, List.of("Auditable"), List.of());

        var graph = ClassGraph.buildFrom(List.of(iface, impl));

        assertThat(graph.edgesFrom("com.example.OrderService"))
                .anyMatch(e -> e.target().equals("com.example.Auditable") && e.kind() == EdgeKind.IMPLEMENTS);
    }

    @Test
    void buildGraphDetectsDependsOnFromImports() {
        var repo = classSummary("com.example.repo", "OrderRepository", ClassKind.INTERFACE,
                null, List.of(), List.of());
        var service = classSummary("com.example", "OrderService", ClassKind.CLASS,
                null, List.of(), List.of("com.example.repo.OrderRepository"));

        var graph = ClassGraph.buildFrom(List.of(repo, service));

        assertThat(graph.edgesFrom("com.example.OrderService"))
                .anyMatch(e -> e.target().equals("com.example.repo.OrderRepository") && e.kind() == EdgeKind.DEPENDS_ON);
    }

    @Test
    void relatedToAggregatesAllRelationships() {
        var iface = classSummary("com.example", "Auditable", ClassKind.INTERFACE,
                null, List.of(), List.of());
        var base = classSummary("com.example", "BaseService", ClassKind.CLASS,
                null, List.of(), List.of());
        var repo = classSummary("com.example.repo", "OrderRepository", ClassKind.INTERFACE,
                null, List.of(), List.of());
        var service = classSummary("com.example", "OrderService", ClassKind.CLASS,
                "BaseService", List.of("Auditable"), List.of("com.example.repo.OrderRepository"));
        var consumer = classSummary("com.example.api", "OrderController", ClassKind.CLASS,
                null, List.of(), List.of("com.example.OrderService"));

        var graph = ClassGraph.buildFrom(List.of(iface, base, repo, service, consumer));
        var related = graph.relatedTo("com.example.OrderService");

        assertThat(related.superClass()).isEqualTo("com.example.BaseService");
        assertThat(related.implementsInterfaces()).containsExactly("com.example.Auditable");
        assertThat(related.dependsOn()).containsExactly("com.example.repo.OrderRepository");
        assertThat(related.dependedOnBy()).containsExactly("com.example.api.OrderController");
    }

    @Test
    void relatedToShowsExtendedByAndImplementedBy() {
        var iface = classSummary("com.example", "Repository", ClassKind.INTERFACE,
                null, List.of(), List.of());
        var impl = classSummary("com.example", "JpaRepository", ClassKind.CLASS,
                null, List.of("Repository"), List.of());

        var graph = ClassGraph.buildFrom(List.of(iface, impl));
        var related = graph.relatedTo("com.example.Repository");

        assertThat(related.implementedBy()).containsExactly("com.example.JpaRepository");
    }

    private static ClassSummary classSummary(String pkg, String name, ClassKind kind,
                                             String superClass, List<String> interfaces, List<String> imports) {
        return new ClassSummary(pkg, name, kind, superClass, interfaces,
                List.of(), List.of(), List.of(), imports, 50);
    }
}
