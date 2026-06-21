package dev.analyser.domain.service;

import dev.analyser.domain.model.ClassSummary;
import dev.analyser.domain.model.ClassSummary.ClassKind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JavaParserAstServiceTest {

    private final JavaParserAstService service = new JavaParserAstService();

    @TempDir
    static Path tempDir;
    static Path fixtureFile;

    @BeforeAll
    static void createFixture() throws IOException {
        fixtureFile = tempDir.resolve("OrderService.java");
        Files.writeString(fixtureFile, """
                package com.example.shop;

                import com.example.shop.model.Order;
                import com.example.shop.repo.OrderRepository;
                import jakarta.enterprise.context.ApplicationScoped;

                @ApplicationScoped
                public class OrderService extends BaseService implements Auditable {

                    private final OrderRepository repository;

                    public Order findById(String id) {
                        return repository.findById(id);
                    }

                    public void cancel(String orderId, String reason) {
                        // cancellation logic
                    }
                }
                """);
    }

    @Test
    void parsesClassName() {
        List<ClassSummary> results = service.parseFile(fixtureFile);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).className()).isEqualTo("OrderService");
    }

    @Test
    void parsesPackage() {
        ClassSummary summary = service.parseFile(fixtureFile).get(0);
        assertThat(summary.packageName()).isEqualTo("com.example.shop");
        assertThat(summary.qualifiedName()).isEqualTo("com.example.shop.OrderService");
    }

    @Test
    void parsesKindAsClass() {
        ClassSummary summary = service.parseFile(fixtureFile).get(0);
        assertThat(summary.kind()).isEqualTo(ClassKind.CLASS);
    }

    @Test
    void parsesSuperclassAndInterfaces() {
        ClassSummary summary = service.parseFile(fixtureFile).get(0);
        assertThat(summary.superClass()).isEqualTo("BaseService");
        assertThat(summary.interfaces()).containsExactly("Auditable");
    }

    @Test
    void parsesAnnotations() {
        ClassSummary summary = service.parseFile(fixtureFile).get(0);
        assertThat(summary.annotations()).containsExactly("ApplicationScoped");
    }

    @Test
    void parsesImports() {
        ClassSummary summary = service.parseFile(fixtureFile).get(0);
        assertThat(summary.imports()).contains(
                "com.example.shop.model.Order",
                "com.example.shop.repo.OrderRepository",
                "jakarta.enterprise.context.ApplicationScoped");
    }

    @Test
    void parsesMethods() {
        ClassSummary summary = service.parseFile(fixtureFile).get(0);
        assertThat(summary.methods()).hasSize(2);
        assertThat(summary.methods().get(0).name()).isEqualTo("findById");
        assertThat(summary.methods().get(0).returnType()).isEqualTo("Order");
        assertThat(summary.methods().get(0).parameterTypes()).containsExactly("String");
        assertThat(summary.methods().get(0).isPublic()).isTrue();
    }

    @Test
    void parsesFields() {
        ClassSummary summary = service.parseFile(fixtureFile).get(0);
        assertThat(summary.fields()).hasSize(1);
        assertThat(summary.fields().get(0).name()).isEqualTo("repository");
        assertThat(summary.fields().get(0).type()).isEqualTo("OrderRepository");
    }

    @Test
    void parsesRecords(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("Money.java");
        Files.writeString(file, """
                package com.example;

                public record Money(int amount, String currency) implements Serializable {}
                """);

        ClassSummary summary = service.parseFile(file).get(0);
        assertThat(summary.kind()).isEqualTo(ClassKind.RECORD);
        assertThat(summary.interfaces()).containsExactly("Serializable");
    }

    @Test
    void parsesInterfaces(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("Repository.java");
        Files.writeString(file, """
                package com.example;

                public interface Repository<T> {
                    T findById(String id);
                }
                """);

        ClassSummary summary = service.parseFile(file).get(0);
        assertThat(summary.kind()).isEqualTo(ClassKind.INTERFACE);
        assertThat(summary.methods()).hasSize(1);
    }
}
