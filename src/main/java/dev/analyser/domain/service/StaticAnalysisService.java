package dev.analyser.domain.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import dev.analyser.domain.model.ClassSummary;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Deep static analysis service performing Checkstyle, PMD, SpotBugs, and OWASP-style checks
 * by walking the JavaParser AST with specialized visitors.
 */
@ApplicationScoped
public class StaticAnalysisService {

    private static final Logger LOG = Logger.getLogger(StaticAnalysisService.class);

    private final JavaParser parser = new JavaParser();

    public record Warning(String rule, String category, String severity, String file, int line, String message) {}

    /**
     * Run all analysis rules on a list of Java source files.
     */
    public List<Warning> analyzeFiles(List<Path> files) {
        var warnings = new ArrayList<Warning>();
        for (var file : files) {
            try {
                var result = parser.parse(file);
                if (result.getResult().isEmpty()) continue;
                var cu = result.getResult().get();
                String fileName = file.getFileName().toString();

                warnings.addAll(runCheckstyleRules(cu, fileName));
                warnings.addAll(runPmdRules(cu, fileName));
                warnings.addAll(runSpotBugsRules(cu, fileName));
                warnings.addAll(runOwaspRules(cu, fileName));
            } catch (IOException e) {
                LOG.warnf(e, "Failed to read/parse source file for static analysis: %s", file);
            }
        }
        return warnings;
    }

    /**
     * Also produce warnings from pre-parsed ClassSummary (for metrics-based rules).
     */
    public List<Warning> analyzeMetrics(List<ClassSummary> classes) {
        var warnings = new ArrayList<Warning>();
        for (var cls : classes) {
            if (cls.methods().size() > 20)
                warnings.add(new Warning("god-class", "DESIGN", "HIGH", cls.qualifiedName(), 0,
                        "Class has " + cls.methods().size() + " methods — consider splitting (PMD: GodClass)"));
            if (cls.lineCount() > 500)
                warnings.add(new Warning("excessive-class-length", "DESIGN", "MEDIUM", cls.qualifiedName(), 0,
                        "Class has " + cls.lineCount() + " lines (Checkstyle: FileLength)"));
            if (cls.imports().size() > 20)
                warnings.add(new Warning("excessive-imports", "DESIGN", "MEDIUM", cls.qualifiedName(), 0,
                        "Class imports " + cls.imports().size() + " types — high coupling (PMD: ExcessiveImports)"));
            for (var m : cls.methods()) {
                if (m.parameterTypes().size() > 5)
                    warnings.add(new Warning("too-many-params", "DESIGN", "MEDIUM", cls.qualifiedName(), 0,
                            "Method " + m.name() + " has " + m.parameterTypes().size() + " params (PMD: ExcessiveParameterList)"));
            }
        }
        return warnings;
    }

    // ========== CHECKSTYLE-STYLE RULES ==========

    private List<Warning> runCheckstyleRules(CompilationUnit cu, String file) {
        var warnings = new ArrayList<Warning>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodDeclaration md, Void arg) {
                super.visit(md, arg);
                // CyclomaticComplexity: count if/for/while/switch/catch/&&/||
                int complexity = countComplexity(md);
                if (complexity > 10)
                    warnings.add(new Warning("cyclomatic-complexity", "COMPLEXITY", "HIGH", file,
                            md.getBegin().map(p -> p.line).orElse(0),
                            "Method " + md.getNameAsString() + " has complexity " + complexity + " (Checkstyle: CyclomaticComplexity, max 10)"));

                // MethodLength
                int length = md.getEnd().map(e -> e.line).orElse(0) - md.getBegin().map(b -> b.line).orElse(0);
                if (length > 50)
                    warnings.add(new Warning("method-too-long", "DESIGN", "MEDIUM", file,
                            md.getBegin().map(p -> p.line).orElse(0),
                            "Method " + md.getNameAsString() + " is " + length + " lines (Checkstyle: MethodLength, max 50)"));
            }

            @Override
            public void visit(FieldDeclaration fd, Void arg) {
                super.visit(fd, arg);
                // MutableField: non-final non-private fields
                if (!fd.isFinal() && fd.isPublic())
                    warnings.add(new Warning("mutable-public-field", "ENCAPSULATION", "MEDIUM", file,
                            fd.getBegin().map(p -> p.line).orElse(0),
                            "Public mutable field — use getter/setter (Checkstyle: VisibilityModifier)"));
            }
        }, null);
        return warnings;
    }

    // ========== PMD-STYLE RULES ==========

    private List<Warning> runPmdRules(CompilationUnit cu, String file) {
        var warnings = new ArrayList<Warning>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(CatchClause cc, Void arg) {
                super.visit(cc, arg);
                // EmptyCatchBlock
                if (cc.getBody().getStatements().isEmpty())
                    warnings.add(new Warning("empty-catch", "BUG_RISK", "HIGH", file,
                            cc.getBegin().map(p -> p.line).orElse(0),
                            "Empty catch block swallows exception (PMD: EmptyCatchBlock)"));

                // AvoidCatchingGenericException
                String exType = cc.getParameter().getTypeAsString();
                if ("Exception".equals(exType) || "Throwable".equals(exType) || "RuntimeException".equals(exType))
                    warnings.add(new Warning("catch-generic-exception", "BUG_RISK", "MEDIUM", file,
                            cc.getBegin().map(p -> p.line).orElse(0),
                            "Catching " + exType + " is too broad (PMD: AvoidCatchingGenericException)"));
            }

            @Override
            public void visit(MethodCallExpr mc, Void arg) {
                super.visit(mc, arg);
                // SystemPrintln
                if (mc.getScope().map(Object::toString).orElse("").matches("System\\.(out|err)"))
                    warnings.add(new Warning("system-println", "BAD_PRACTICE", "LOW", file,
                            mc.getBegin().map(p -> p.line).orElse(0),
                            "Use a logger instead of System.out/err (PMD: SystemPrintln)"));

                // AvoidPrintStackTrace
                if ("printStackTrace".equals(mc.getNameAsString()))
                    warnings.add(new Warning("print-stack-trace", "BAD_PRACTICE", "MEDIUM", file,
                            mc.getBegin().map(p -> p.line).orElse(0),
                            "Use logger.error() instead of printStackTrace (PMD: AvoidPrintStackTrace)"));
            }

            @Override
            public void visit(IfStmt is, Void arg) {
                super.visit(is, arg);
                // CollapsibleIfStatements
                if (is.getThenStmt() instanceof BlockStmt block && block.getStatements().size() == 1
                        && block.getStatements().get(0) instanceof IfStmt)
                    warnings.add(new Warning("collapsible-if", "DESIGN", "LOW", file,
                            is.getBegin().map(p -> p.line).orElse(0),
                            "Nested if can be collapsed with && (PMD: CollapsibleIfStatements)"));
            }

            @Override
            public void visit(ReturnStmt rs, Void arg) {
                super.visit(rs, arg);
                // UnnecessaryReturn of null
                if (rs.getExpression().map(e -> e instanceof NullLiteralExpr).orElse(false)) {
                    warnings.add(new Warning("return-null", "BUG_RISK", "LOW", file,
                            rs.getBegin().map(p -> p.line).orElse(0),
                            "Returning null — consider Optional or throwing (PMD: ReturnEmptyCollectionRatherThanNull)"));
                }
            }
        }, null);
        return warnings;
    }

    // ========== SPOTBUGS-STYLE RULES ==========

    private List<Warning> runSpotBugsRules(CompilationUnit cu, String file) {
        var warnings = new ArrayList<Warning>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr mc, Void arg) {
                super.visit(mc, arg);
                // Equals on incompatible types / potential NPE
                if ("equals".equals(mc.getNameAsString()) && mc.getArguments().size() == 1) {
                    if (mc.getArguments().get(0) instanceof NullLiteralExpr)
                        warnings.add(new Warning("equals-null", "BUG", "HIGH", file,
                                mc.getBegin().map(p -> p.line).orElse(0),
                                "Calling equals(null) is always false — use == null (SpotBugs: EC_NULL_ARG_TO_EQUALS)"));
                }

                // String.equals with literal on right (NPE risk)
                if ("equals".equals(mc.getNameAsString()) && mc.getScope().isPresent()) {
                    var scope = mc.getScope().get();
                    if (!(scope instanceof StringLiteralExpr) && mc.getArguments().size() == 1
                            && mc.getArguments().get(0) instanceof StringLiteralExpr)
                        warnings.add(new Warning("literal-equals-order", "BUG_RISK", "LOW", file,
                                mc.getBegin().map(p -> p.line).orElse(0),
                                "Put string literal on left of equals() to avoid NPE (SpotBugs: ES_COMPARING_STRINGS_WITH_EQ)"));
                }
            }

            @Override
            public void visit(BinaryExpr be, Void arg) {
                super.visit(be, arg);
                // String comparison with == instead of .equals()
                if (be.getOperator() == BinaryExpr.Operator.EQUALS || be.getOperator() == BinaryExpr.Operator.NOT_EQUALS) {
                    if (be.getLeft() instanceof StringLiteralExpr || be.getRight() instanceof StringLiteralExpr)
                        warnings.add(new Warning("string-ref-equality", "BUG", "HIGH", file,
                                be.getBegin().map(p -> p.line).orElse(0),
                                "Comparing strings with ==/!= instead of .equals() (SpotBugs: ES_COMPARING_STRINGS_WITH_EQ)"));
                }
            }

            @Override
            public void visit(SynchronizedStmt ss, Void arg) {
                super.visit(ss, arg);
                // Synchronize on non-final field
                if (ss.getExpression() instanceof NameExpr)
                    warnings.add(new Warning("sync-on-field", "CONCURRENCY", "MEDIUM", file,
                            ss.getBegin().map(p -> p.line).orElse(0),
                            "Verify synchronized target is final — sync on non-final can miss updates (SpotBugs: IS2_INCONSISTENT_SYNC)"));
            }

            @Override
            public void visit(ObjectCreationExpr oc, Void arg) {
                super.visit(oc, arg);
                // Creating Thread without starting (resource leak hint)
                if ("Thread".equals(oc.getTypeAsString()))
                    warnings.add(new Warning("thread-creation", "CONCURRENCY", "LOW", file,
                            oc.getBegin().map(p -> p.line).orElse(0),
                            "Direct Thread creation — prefer ExecutorService (SpotBugs: DM_DEFAULT_ENCODING)"));
            }
        }, null);
        return warnings;
    }

    // ========== OWASP-STYLE SECURITY RULES ==========

    private List<Warning> runOwaspRules(CompilationUnit cu, String file) {
        var warnings = new ArrayList<Warning>();
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(MethodCallExpr mc, Void arg) {
                super.visit(mc, arg);
                String name = mc.getNameAsString();

                // SQL Injection: string concatenation in query methods
                if (("executeQuery".equals(name) || "executeUpdate".equals(name) || "execute".equals(name))
                        && mc.getArguments().size() == 1 && mc.getArguments().get(0) instanceof BinaryExpr)
                    warnings.add(new Warning("sql-injection", "SECURITY", "CRITICAL", file,
                            mc.getBegin().map(p -> p.line).orElse(0),
                            "Potential SQL injection: string concatenation in query — use PreparedStatement (OWASP A03: Injection)"));

                // Hardcoded credentials
                if (("setPassword".equals(name) || "setSecret".equals(name))
                        && mc.getArguments().size() >= 1 && mc.getArguments().get(0) instanceof StringLiteralExpr)
                    warnings.add(new Warning("hardcoded-secret", "SECURITY", "CRITICAL", file,
                            mc.getBegin().map(p -> p.line).orElse(0),
                            "Hardcoded credential in setPassword/setSecret — use env variable (OWASP A07: Security Misconfiguration)"));

                // Insecure random
                if ("Random".equals(mc.getScope().map(Object::toString).orElse("")) || name.equals("nextInt"))
                    ; // handled below in ObjectCreationExpr

                // Path traversal (File creation from user input patterns)
                if ("File".equals(mc.getScope().map(Object::toString).orElse("")) && "createTempFile".equals(name))
                    ; // usually safe

                // Deserialization
                if ("readObject".equals(name))
                    warnings.add(new Warning("unsafe-deserialization", "SECURITY", "HIGH", file,
                            mc.getBegin().map(p -> p.line).orElse(0),
                            "Java deserialization can lead to RCE — validate input class (OWASP A08: Insecure Deserialization)"));

                // SSRF / URL from user input
                if ("openConnection".equals(name) || "openStream".equals(name))
                    warnings.add(new Warning("potential-ssrf", "SECURITY", "MEDIUM", file,
                            mc.getBegin().map(p -> p.line).orElse(0),
                            "URL.openConnection/openStream — ensure URL is not user-controlled (OWASP A10: SSRF)"));
            }

            @Override
            public void visit(ObjectCreationExpr oc, Void arg) {
                super.visit(oc, arg);
                // Insecure random for security contexts
                if ("Random".equals(oc.getTypeAsString()))
                    warnings.add(new Warning("insecure-random", "SECURITY", "MEDIUM", file,
                            oc.getBegin().map(p -> p.line).orElse(0),
                            "java.util.Random is predictable — use SecureRandom for security (OWASP A02: Cryptographic Failures)"));

                // Weak crypto
                if ("DESKeySpec".equals(oc.getTypeAsString()) || "DES".equals(oc.getTypeAsString()))
                    warnings.add(new Warning("weak-crypto", "SECURITY", "HIGH", file,
                            oc.getBegin().map(p -> p.line).orElse(0),
                            "DES is broken — use AES-256 (OWASP A02: Cryptographic Failures)"));
            }

            @Override
            public void visit(FieldDeclaration fd, Void arg) {
                super.visit(fd, arg);
                // Hardcoded passwords/secrets in field initializers
                for (var v : fd.getVariables()) {
                    String fieldName = v.getNameAsString().toLowerCase();
                    if ((fieldName.contains("password") || fieldName.contains("secret") || fieldName.contains("apikey"))
                            && v.getInitializer().map(i -> i instanceof StringLiteralExpr).orElse(false))
                        warnings.add(new Warning("hardcoded-secret", "SECURITY", "CRITICAL", file,
                                fd.getBegin().map(p -> p.line).orElse(0),
                                "Hardcoded secret in field '" + v.getNameAsString() + "' — use configuration/env (OWASP A07)"));
                }
            }
        }, null);
        return warnings;
    }

    private int countComplexity(MethodDeclaration md) {
        int[] complexity = {1}; // base complexity
        md.accept(new VoidVisitorAdapter<Void>() {
            @Override public void visit(IfStmt n, Void a) { super.visit(n, a); complexity[0]++; }
            @Override public void visit(ForStmt n, Void a) { super.visit(n, a); complexity[0]++; }
            @Override public void visit(ForEachStmt n, Void a) { super.visit(n, a); complexity[0]++; }
            @Override public void visit(WhileStmt n, Void a) { super.visit(n, a); complexity[0]++; }
            @Override public void visit(DoStmt n, Void a) { super.visit(n, a); complexity[0]++; }
            @Override public void visit(SwitchEntry n, Void a) { super.visit(n, a); if (!n.getLabels().isEmpty()) complexity[0]++; }
            @Override public void visit(CatchClause n, Void a) { super.visit(n, a); complexity[0]++; }
            @Override public void visit(ConditionalExpr n, Void a) { super.visit(n, a); complexity[0]++; }
            @Override public void visit(BinaryExpr n, Void a) {
                super.visit(n, a);
                if (n.getOperator() == BinaryExpr.Operator.AND || n.getOperator() == BinaryExpr.Operator.OR) complexity[0]++;
            }
        }, null);
        return complexity[0];
    }
}
