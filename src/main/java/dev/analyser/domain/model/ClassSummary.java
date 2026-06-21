package dev.analyser.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Summary of a single Java compilation unit extracted from the AST.
 */
public record ClassSummary(
        String packageName,
        String className,
        ClassKind kind,
        String superClass,
        List<String> interfaces,
        List<MethodSignature> methods,
        List<FieldInfo> fields,
        List<String> annotations,
        List<String> imports,
        int lineCount) {

    public ClassSummary {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(className);
        Objects.requireNonNull(kind);
        interfaces = List.copyOf(interfaces);
        methods = List.copyOf(methods);
        fields = List.copyOf(fields);
        annotations = List.copyOf(annotations);
        imports = List.copyOf(imports);
    }

    public String qualifiedName() {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    public enum ClassKind {
        CLASS, INTERFACE, RECORD, ENUM, ANNOTATION
    }

    public record MethodSignature(
            String name,
            String returnType,
            List<String> parameterTypes,
            List<String> annotations,
            boolean isPublic) {

        public MethodSignature {
            parameterTypes = List.copyOf(parameterTypes);
            annotations = List.copyOf(annotations);
        }
    }

    public record FieldInfo(
            String name,
            String type,
            List<String> annotations) {

        public FieldInfo {
            annotations = List.copyOf(annotations);
        }
    }
}
