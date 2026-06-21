package dev.analyser.domain.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.analyser.domain.model.ClassSummary;
import dev.analyser.domain.model.ClassSummary.*;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class JavaParserAstService {

    private final JavaParser parser = new JavaParser();

    public List<ClassSummary> parseFile(Path file) {
        CompilationUnit cu;
        try {
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.getResult().isEmpty()) {
                return List.of();
            }
            cu = result.getResult().get();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse " + file, e);
        }

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        List<String> imports = cu.getImports().stream()
                .map(imp -> imp.getNameAsString())
                .toList();

        int lineCount = countLines(file);

        List<ClassSummary> summaries = new ArrayList<>();
        for (TypeDeclaration<?> type : cu.getTypes()) {
            summaries.add(extractType(type, packageName, imports, lineCount));
        }
        return summaries;
    }

    private ClassSummary extractType(TypeDeclaration<?> type, String packageName, List<String> imports, int lineCount) {
        String className = type.getNameAsString();
        ClassKind kind = determineKind(type);
        String superClass = null;
        List<String> interfaces = List.of();
        List<String> annotations = type.getAnnotations().stream()
                .map(a -> a.getNameAsString())
                .toList();

        if (type instanceof ClassOrInterfaceDeclaration cid) {
            superClass = cid.getExtendedTypes().stream()
                    .findFirst()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .orElse(null);
            interfaces = cid.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .toList();
        } else if (type instanceof RecordDeclaration rd) {
            interfaces = rd.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .toList();
        } else if (type instanceof EnumDeclaration ed) {
            interfaces = ed.getImplementedTypes().stream()
                    .map(ClassOrInterfaceType::getNameAsString)
                    .toList();
        }

        List<MethodSignature> methods = type.getMethods().stream()
                .map(this::extractMethod)
                .toList();

        List<FieldInfo> fields = type.getFields().stream()
                .flatMap(f -> f.getVariables().stream().map(v -> new FieldInfo(
                        v.getNameAsString(),
                        v.getTypeAsString(),
                        f.getAnnotations().stream().map(a -> a.getNameAsString()).toList())))
                .toList();

        return new ClassSummary(packageName, className, kind, superClass, interfaces,
                methods, fields, annotations, imports, lineCount);
    }

    private MethodSignature extractMethod(MethodDeclaration method) {
        return new MethodSignature(
                method.getNameAsString(),
                method.getTypeAsString(),
                method.getParameters().stream().map(p -> p.getTypeAsString()).toList(),
                method.getAnnotations().stream().map(a -> a.getNameAsString()).toList(),
                method.isPublic());
    }

    private ClassKind determineKind(TypeDeclaration<?> type) {
        if (type instanceof EnumDeclaration) return ClassKind.ENUM;
        if (type instanceof RecordDeclaration) return ClassKind.RECORD;
        if (type instanceof AnnotationDeclaration) return ClassKind.ANNOTATION;
        if (type instanceof ClassOrInterfaceDeclaration cid) {
            return cid.isInterface() ? ClassKind.INTERFACE : ClassKind.CLASS;
        }
        return ClassKind.CLASS;
    }

    private int countLines(Path file) {
        try {
            return (int) Files.lines(file).count();
        } catch (IOException e) {
            return 0;
        }
    }
}
