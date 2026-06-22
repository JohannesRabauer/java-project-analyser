package dev.analyser.domain.service;

import dev.analyser.domain.service.StaticAnalysisService.Warning;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAnalysisServiceTest {

    private final StaticAnalysisService service = new StaticAnalysisService();

    @TempDir
    Path tempDir;

    @Test
    void detectsEmptyCatchBlock() throws IOException {
        var file = writeFixture("EmptyCatch.java", """
                package com.test;
                public class EmptyCatch {
                    public void risky() {
                        try { int x = 1/0; }
                        catch (Exception e) { }
                    }
                }
                """);
        var warnings = service.analyzeFiles(List.of(file));
        assertThat(warnings).anyMatch(w -> w.rule().equals("empty-catch") && w.severity().equals("HIGH"));
    }

    @Test
    void detectsCatchingGenericException() throws IOException {
        var file = writeFixture("GenericCatch.java", """
                package com.test;
                public class GenericCatch {
                    public void risky() {
                        try { int x = 1/0; }
                        catch (Throwable e) { System.out.println(e); }
                    }
                }
                """);
        var warnings = service.analyzeFiles(List.of(file));
        assertThat(warnings).anyMatch(w -> w.rule().equals("catch-generic-exception"));
    }

    @Test
    void detectsSystemPrintln() throws IOException {
        var file = writeFixture("PrintUser.java", """
                package com.test;
                public class PrintUser {
                    public void log() {
                        System.out.println("debug");
                    }
                }
                """);
        var warnings = service.analyzeFiles(List.of(file));
        assertThat(warnings).anyMatch(w -> w.rule().equals("system-println"));
    }

    @Test
    void detectsSqlInjection() throws IOException {
        var file = writeFixture("UnsafeDao.java", """
                package com.test;
                import java.sql.*;
                public class UnsafeDao {
                    public void find(Connection conn, String input) throws Exception {
                        conn.createStatement().executeQuery("SELECT * FROM users WHERE name='" + input + "'");
                    }
                }
                """);
        var warnings = service.analyzeFiles(List.of(file));
        assertThat(warnings).anyMatch(w -> w.rule().equals("sql-injection") && w.severity().equals("CRITICAL"));
    }

    @Test
    void detectsHardcodedSecret() throws IOException {
        var file = writeFixture("Config.java", """
                package com.test;
                public class Config {
                    private String password = "admin123";
                    private String apiKey = "sk-1234567890";
                }
                """);
        var warnings = service.analyzeFiles(List.of(file));
        assertThat(warnings).anyMatch(w -> w.rule().equals("hardcoded-secret") && w.category().equals("SECURITY"));
    }

    @Test
    void detectsInsecureRandom() throws IOException {
        var file = writeFixture("TokenGen.java", """
                package com.test;
                import java.util.Random;
                public class TokenGen {
                    public String generateToken() {
                        Random r = new Random();
                        return String.valueOf(r.nextLong());
                    }
                }
                """);
        var warnings = service.analyzeFiles(List.of(file));
        assertThat(warnings).anyMatch(w -> w.rule().equals("insecure-random") && w.category().equals("SECURITY"));
    }

    @Test
    void detectsStringReferenceEquality() throws IOException {
        var file = writeFixture("BadCompare.java", """
                package com.test;
                public class BadCompare {
                    public boolean check(String input) {
                        return input == "admin";
                    }
                }
                """);
        var warnings = service.analyzeFiles(List.of(file));
        assertThat(warnings).anyMatch(w -> w.rule().equals("string-ref-equality") && w.severity().equals("HIGH"));
    }

    @Test
    void detectsHighCyclomaticComplexity() throws IOException {
        var file = writeFixture("Complex.java", """
                package com.test;
                public class Complex {
                    public int decide(int a, int b, int c, int d) {
                        if (a > 0) { if (b > 0) { if (c > 0) { return 1; } else { return 2; } } }
                        if (d > 0 && a < 10) { for (int i=0; i<10; i++) { if (i % 2 == 0) { a++; } } }
                        while (a > 0) { a--; if (a == 5) break; }
                        switch (b) { case 1: return 1; case 2: return 2; case 3: return 3; default: return 0; }
                    }
                }
                """);
        var warnings = service.analyzeFiles(List.of(file));
        assertThat(warnings).anyMatch(w -> w.rule().equals("cyclomatic-complexity"));
    }

    private Path writeFixture(String name, String content) throws IOException {
        var file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
